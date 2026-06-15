/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.guidedtour.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.guidedtour.api.dtos.UserTourStatusDTO;
import org.xwiki.contrib.guidedtour.api.enums.Status;
import org.xwiki.contrib.guidedtour.api.enums.WidgetState;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.USER_TOUR_CLASS;

/**
 * Test class for {@link UserStatusManager}.
 */
@ComponentTest
class UserStatusManagerTest
{
    private static final String TASKS_STATUS_KEY = "tasksStatus";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMockComponents
    private UserStatusManager userStatusManager;

    @MockComponent
    private XWikiContext wikiContext;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument userDocument;

    @Mock
    private BaseObject statusObject;

    @Mock
    private DocumentReference userReference;

    @BeforeEach
    void setup() throws XWikiException
    {
        when(this.wikiContext.getWiki()).thenReturn(this.xwiki);
        when(this.wikiContext.getUserReference()).thenReturn(this.userReference);
        when(this.xwiki.getDocument(this.userReference, this.wikiContext)).thenReturn(this.userDocument);
        when(this.userDocument.getXObject(USER_TOUR_CLASS)).thenReturn(this.statusObject);
        when(this.statusObject.getOwnerDocument()).thenReturn(this.userDocument);
    }

    @Test
    void getUserToursStatus() throws XWikiException, JsonProcessingException, InvalidIdException
    {
        Map<String, Status> tasksStatus = new HashMap<>();
        tasksStatus.put("task1", Status.DONE);
        String tasksStatusJson = this.objectMapper.writeValueAsString(tasksStatus);

        when(this.statusObject.getStringValue(TASKS_STATUS_KEY)).thenReturn(tasksStatusJson);
        when(this.statusObject.getStringValue("widgetState")).thenReturn("OPEN");
        when(this.statusObject.getIntValue("callToAction")).thenReturn(1);

        UserTourStatusDTO result = this.userStatusManager.getUserToursStatus();

        assertEquals(Status.DONE, result.getTasksStatus().get("task1"));
        assertEquals(WidgetState.OPEN, result.getWidgetState());
        assertTrue(result.isCallToAction());
    }

    @Test
    void createUserTourStatus() throws XWikiException, DuplicatedIdException
    {
        when(this.userDocument.getXObject(USER_TOUR_CLASS)).thenReturn(null);
        this.userStatusManager.createUserTourStatus();

        verify(this.userDocument, times(1)).newXObject(USER_TOUR_CLASS, this.wikiContext);
        verify(this.xwiki, times(1)).saveDocument(this.userDocument, "Added guided tour user status object.",
            this.wikiContext);
    }

    @Test
    void createUserTourStatusDuplicate()
    {
        DuplicatedIdException exception = assertThrows(DuplicatedIdException.class, () -> {
            this.userStatusManager.createUserTourStatus();
        });

        assertEquals(String.format("User tour status already exists for user [%s]", this.userReference),
            exception.getMessage());
    }

    @Test
    void updateUserTourStatus() throws XWikiException, JsonProcessingException, InvalidIdException
    {
        Map<String, Status> tasksStatus = new HashMap<>();
        tasksStatus.put("task1", Status.DONE);
        UserTourStatusDTO userTourStatusDTO = new UserTourStatusDTO();
        userTourStatusDTO.setTasksStatus(tasksStatus);
        userTourStatusDTO.setWidgetState("HIDDEN");
        userTourStatusDTO.setCallToAction(false);

        this.userStatusManager.updateUserTourStatus(userTourStatusDTO);
        verify(this.statusObject, times(1)).setLargeStringValue(TASKS_STATUS_KEY,
            this.objectMapper.writeValueAsString(tasksStatus));
        verify(this.statusObject, times(1)).setStringValue("widgetState", "HIDDEN");
        verify(this.statusObject, times(1)).setIntValue("callToAction", 0);
        verify(this.xwiki, times(1)).saveDocument(this.userDocument, "Updated guided tour user status.",
            this.wikiContext);
    }

    @Test
    void updateUserTourStatusInvalidId()
    {
        when(this.userDocument.getXObject(USER_TOUR_CLASS)).thenReturn(null);
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            this.userStatusManager.updateUserTourStatus(new UserTourStatusDTO());
        });
        assertEquals(String.format("User tour status not found for user [%s].", this.userReference),
            exception.getMessage());
    }
}
