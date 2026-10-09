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

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.guidedtour.api.dtos.TourProgressDTO;
import org.xwiki.contrib.guidedtour.api.dtos.UserTourStatusDTO;
import org.xwiki.contrib.guidedtour.api.enums.WidgetState;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.model.reference.DocumentReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.USER_TOUR_CLASS;

/**
 * Manages the user status for the guided tour. It provides methods to create, retrieve and update the user status.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = UserStatusManager.class)
@Singleton
public class UserStatusManager
{
    private static final String TOURS_STATUS_KEY = "toursStatus";

    private static final String WIDGET_STATE_KEY = "widgetState";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private Provider<XWikiContext> wikiContextProvider;

    /**
     * Retrieves the user tour status for the current user.
     *
     * @return the user tour status and preferences
     * @throws XWikiException if there is an error while retrieving the user document
     * @throws JsonProcessingException if there is an error while processing the JSON data
     * @throws InvalidIdException if the user tour status is not found for the current user
     */
    public UserTourStatusDTO getUserTourStatus() throws XWikiException, JsonProcessingException, InvalidIdException
    {
        BaseObject userTourStatusObject = getUserDocument().getXObject(USER_TOUR_CLASS);
        if (userTourStatusObject == null) {
            throw new InvalidIdException("User tour status not found for user [%s].",
                this.wikiContextProvider.get().getUserReference());
        }
        UserTourStatusDTO userTourStatus = new UserTourStatusDTO();
        String storedJson = userTourStatusObject.getStringValue(TOURS_STATUS_KEY);
        if (!storedJson.isEmpty()) {
            userTourStatus.setToursStatus(
                this.objectMapper.readValue(storedJson, new TypeReference<Map<String, TourProgressDTO>>()
                {
                }));
        }
        userTourStatus.setWidgetState((userTourStatusObject.getStringValue(WIDGET_STATE_KEY)));
        return userTourStatus;
    }

    /**
     * Creates a user tour status object for the current user if it doesn't exist, with the default status: an open
     * widget and no tour progress (the call to action is enabled for every tour by default).
     *
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws DuplicatedIdException if a user tour status already exists for the current user
     * @throws JsonProcessingException if there is an error while processing the JSON data
     */
    public void createUserTourStatus() throws XWikiException, DuplicatedIdException, JsonProcessingException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWikiDocument userDoc = getUserDocument();
        if (userDoc.getXObject(USER_TOUR_CLASS) == null) {
            addUserTourStatus(new UserTourStatusDTO(WidgetState.OPEN.toString()), userDoc.clone());
        } else {
            throw new DuplicatedIdException("User tour status already exists for user [%s]",
                wikiContext.getUserReference());
        }
    }

    /**
     * Saves the user tour status for the current user based on the provided DTO. The user tour status object is created
     * if it doesn't exist yet. Updates are saved as minor edits, to avoid cluttering the user document history.
     *
     * @param userTourStatus the DTO containing the user tour status information to save
     * @return {@code true} if the user tour status object didn't exist and has been created, {@code false} if it has
     *     been updated
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws JsonProcessingException if there is an error while processing the JSON data
     */
    public boolean saveUserTourStatus(UserTourStatusDTO userTourStatus) throws XWikiException, JsonProcessingException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        // Clone the cached document, so that it isn't left modified if the save fails.
        XWikiDocument userDoc = getUserDocument().clone();
        BaseObject userTourStatusObject = userDoc.getXObject(USER_TOUR_CLASS);
        if (userTourStatusObject == null) {
            addUserTourStatus(userTourStatus, userDoc);
            return true;
        }
        setUserTourStatusValues(userTourStatus, userTourStatusObject);
        wikiContext.getWiki().saveDocument(userDoc, "Updated guided tour user status.", true, wikiContext);
        return false;
    }

    private void addUserTourStatus(UserTourStatusDTO tourStatusDTO, XWikiDocument userDoc)
        throws JsonProcessingException, XWikiException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        BaseObject userTourStatusObject = userDoc.getXObject(USER_TOUR_CLASS, true, wikiContext);
        setUserTourStatusValues(tourStatusDTO, userTourStatusObject);
        wikiContext.getWiki().saveDocument(userDoc, "Added guided tour user status object.", false, wikiContext);
    }

    private void setUserTourStatusValues(UserTourStatusDTO tourStatusDTO, BaseObject userTourStatusObject)
        throws JsonProcessingException
    {
        Map<String, TourProgressDTO> toursStatus =
            tourStatusDTO.getToursStatus() != null ? tourStatusDTO.getToursStatus() : Map.of();
        WidgetState widgetState =
            tourStatusDTO.getWidgetState() != null ? tourStatusDTO.getWidgetState() : WidgetState.OPEN;
        userTourStatusObject.setLargeStringValue(TOURS_STATUS_KEY, this.objectMapper.writeValueAsString(toursStatus));
        userTourStatusObject.setStringValue(WIDGET_STATE_KEY, widgetState.toString());
    }

    private XWikiDocument getUserDocument() throws XWikiException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        DocumentReference userDocRef = wikiContext.getUserReference();
        if (userDocRef == null) {
            // Guest users don't have a profile to store the status in, they keep it in the browser storage.
            throw new SecurityException("Guest users cannot store a guided tour status.");
        }
        return wikiContext.getWiki().getDocument(userDocRef, wikiContext);
    }
}
