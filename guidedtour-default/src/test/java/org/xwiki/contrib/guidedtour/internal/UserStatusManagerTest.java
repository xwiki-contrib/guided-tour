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

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.guidedtour.api.dtos.TourProgressDTO;
import org.xwiki.contrib.guidedtour.api.dtos.UserTourStatusDTO;
import org.xwiki.contrib.guidedtour.api.enums.Status;
import org.xwiki.contrib.guidedtour.api.enums.WidgetState;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.USER_TOUR_CLASS;

/**
 * Test class for {@link UserStatusManager}.
 *
 * @version $Id$
 */
@ComponentTest
class UserStatusManagerTest
{
    private static final String TOURS_STATUS_KEY = "toursStatus";

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
        when(this.userDocument.clone()).thenReturn(this.userDocument);
        when(this.userDocument.getXObject(USER_TOUR_CLASS)).thenReturn(this.statusObject);
        when(this.userDocument.getXObject(USER_TOUR_CLASS, true, this.wikiContext)).thenReturn(this.statusObject);
        when(this.statusObject.getOwnerDocument()).thenReturn(this.userDocument);
    }

    @Test
    void getUserTourStatus() throws XWikiException, JsonProcessingException, InvalidIdException
    {
        // The call to action of tour2 is missing, so it should be enabled by default.
        when(this.statusObject.getStringValue(TOURS_STATUS_KEY)).thenReturn(
            "{\"tour1\":{\"callToAction\":false,\"tasksStatus\":{\"task1\":\"DONE\"}},\"tour2\":{\"tasksStatus\":{}}}");
        when(this.statusObject.getStringValue("widgetState")).thenReturn("OPEN");

        UserTourStatusDTO result = this.userStatusManager.getUserTourStatus();

        assertEquals(WidgetState.OPEN, result.getWidgetState());
        TourProgressDTO tour1 = result.getToursStatus().get("tour1");
        assertFalse(tour1.isCallToAction());
        assertEquals(Map.of("task1", Status.DONE), tour1.getTasksStatus());
        TourProgressDTO tour2 = result.getToursStatus().get("tour2");
        assertTrue(tour2.isCallToAction());
        assertTrue(tour2.getTasksStatus().isEmpty());
    }

    @Test
    void getUserTourStatusWithoutObject()
    {
        when(this.userDocument.getXObject(USER_TOUR_CLASS)).thenReturn(null);

        InvalidIdException exception =
            assertThrows(InvalidIdException.class, () -> this.userStatusManager.getUserTourStatus());

        assertEquals(String.format("User tour status not found for user [%s].", this.userReference),
            exception.getMessage());
    }

    @Test
    void getUserTourStatusGuest()
    {
        when(this.wikiContext.getUserReference()).thenReturn(null);

        SecurityException exception =
            assertThrows(SecurityException.class, () -> this.userStatusManager.getUserTourStatus());

        assertEquals("Guest users cannot store a guided tour status.", exception.getMessage());
    }

    @Test
    void createUserTourStatus() throws XWikiException, DuplicatedIdException, JsonProcessingException
    {
        when(this.userDocument.getXObject(USER_TOUR_CLASS)).thenReturn(null);
        this.userStatusManager.createUserTourStatus();

        verify(this.userDocument).getXObject(USER_TOUR_CLASS, true, this.wikiContext);
        verify(this.statusObject).setLargeStringValue(TOURS_STATUS_KEY, "{}");
        verify(this.statusObject).setStringValue("widgetState", "OPEN");
        verify(this.xwiki, times(1)).saveDocument(this.userDocument, "Added guided tour user status object.", false,
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
    void saveUserTourStatus() throws XWikiException, JsonProcessingException
    {
        TourProgressDTO tourProgress = new TourProgressDTO();
        tourProgress.setCallToAction(false);
        tourProgress.setTasksStatus(Map.of("task1", Status.DONE));
        Map<String, TourProgressDTO> toursStatus = Map.of("tour1", tourProgress);
        UserTourStatusDTO userTourStatusDTO = new UserTourStatusDTO("HIDDEN");
        userTourStatusDTO.setToursStatus(toursStatus);

        assertFalse(this.userStatusManager.saveUserTourStatus(userTourStatusDTO));
        verify(this.statusObject, times(1)).setLargeStringValue(TOURS_STATUS_KEY,
            this.objectMapper.writeValueAsString(toursStatus));
        verify(this.statusObject, times(1)).setStringValue("widgetState", "HIDDEN");
        verify(this.xwiki, times(1)).saveDocument(this.userDocument, "Updated guided tour user status.", true,
            this.wikiContext);
    }

    @Test
    void saveUserTourStatusCreatesObject() throws XWikiException, JsonProcessingException
    {
        when(this.userDocument.getXObject(USER_TOUR_CLASS)).thenReturn(null);
        UserTourStatusDTO userTourStatusDTO = new UserTourStatusDTO("HIDDEN");

        assertTrue(this.userStatusManager.saveUserTourStatus(userTourStatusDTO));

        verify(this.userDocument).getXObject(USER_TOUR_CLASS, true, this.wikiContext);
        verify(this.statusObject).setLargeStringValue(TOURS_STATUS_KEY, "{}");
        verify(this.statusObject).setStringValue("widgetState", "HIDDEN");
        verify(this.xwiki).saveDocument(this.userDocument, "Added guided tour user status object.", false,
            this.wikiContext);
    }

    @Test
    void saveUserTourStatusWithMissingValues() throws XWikiException, JsonProcessingException
    {
        UserTourStatusDTO userTourStatusDTO = new UserTourStatusDTO();
        userTourStatusDTO.setToursStatus(null);

        this.userStatusManager.saveUserTourStatus(userTourStatusDTO);

        verify(this.statusObject).setLargeStringValue(TOURS_STATUS_KEY, "{}");
        verify(this.statusObject).setStringValue("widgetState", "OPEN");
        verify(this.xwiki).saveDocument(this.userDocument, "Updated guided tour user status.", true,
            this.wikiContext);
    }

    @Test
    void saveUserTourStatusGuest() throws XWikiException
    {
        when(this.wikiContext.getUserReference()).thenReturn(null);

        SecurityException exception = assertThrows(SecurityException.class,
            () -> this.userStatusManager.saveUserTourStatus(new UserTourStatusDTO()));

        assertEquals("Guest users cannot store a guided tour status.", exception.getMessage());
        verify(this.xwiki, never()).saveDocument(any(), any(), anyBoolean(), any());
    }
}
