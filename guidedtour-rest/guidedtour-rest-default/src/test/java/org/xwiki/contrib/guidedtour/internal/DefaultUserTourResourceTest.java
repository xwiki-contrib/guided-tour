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
import com.xpn.xwiki.XWikiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.container.Container;
import org.xwiki.container.servlet.ServletRequest;
import org.xwiki.contrib.guidedtour.api.dtos.UserTourStatusDTO;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Test of {@link DefaultUserTourResource}.
 *
 * @version $Id$
 * @since 1.0
 */
@ComponentTest
class DefaultUserTourResourceTest
{
    private static final String CSRF_VALUE = "csrfToken";

    private final UserTourStatusDTO userTourStatus = new UserTourStatusDTO("hidden", true);

    @InjectMockComponents
    private DefaultUserTourResource userTourResource;

    @MockComponent
    private UserStatusManager userStatusManager;

    @MockComponent
    private ContextualAuthorizationManager contextualAuthorizationManager;

    @MockComponent
    private Provider<Container> containerProvider;

    @MockComponent
    private CSRFToken csrf;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.DEBUG);

    @Mock
    private Container container;

    @Mock
    private ServletRequest request;

    @Mock
    private HttpServletRequest httpServletRequest;

    @BeforeEach
    void setup()
    {
        when(this.containerProvider.get()).thenReturn(this.container);
        when(this.container.getRequest()).thenReturn(this.request);
        when(this.request.getParameter("csrf")).thenReturn(CSRF_VALUE);
        when(this.csrf.isTokenValid(CSRF_VALUE)).thenReturn(true);
        when(this.request.getRequest()).thenReturn(this.httpServletRequest);
        when(this.httpServletRequest.getHeader("xwiki-form-token")).thenReturn(CSRF_VALUE);
    }

    @Test
    void getUserTourStatus() throws XWikiException, InvalidIdException, JsonProcessingException
    {
        when(this.userStatusManager.getUserToursStatus()).thenReturn(this.userTourStatus);

        Response response = this.userTourResource.getUserTourStatus();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(this.userTourStatus, response.getEntity());
        assertEquals("Executing: User tour status API: getting user tour status object.",
            this.logCapture.getMessage(0));
    }

    @Test
    void getAvailableToursInvalidCSRF()
    {
        when(this.csrf.isTokenValid(CSRF_VALUE)).thenReturn(false);

        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            this.userTourResource.getUserTourStatus();
        });
        assertEquals("Executing: User tour status API: getting user tour status object.",
            this.logCapture.getMessage(0));
        assertEquals("Authorization error: User tour status API: getting user tour status object.",
            this.logCapture.getMessage(1));
        assertEquals(401, exception.getResponse().getStatus());
    }

    @Test
    void getAvailableToursNoViewRights() throws AccessDeniedException
    {
        doThrow(new AccessDeniedException(Right.VIEW, null, null)).when(this.contextualAuthorizationManager)
            .checkAccess(Right.VIEW);
        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            this.userTourResource.getUserTourStatus();
        });
        assertEquals("Executing: User tour status API: getting user tour status object.",
            this.logCapture.getMessage(0));
        assertEquals("Authorization error: User tour status API: getting user tour status object.",
            this.logCapture.getMessage(1));
        assertEquals(401, exception.getResponse().getStatus());
    }

    @Test
    void createTour()
    {
        Response response = this.userTourResource.createUserTourStatus();
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals("Executing: User tour status API: creating new user tour status object.",
            this.logCapture.getMessage(0));
    }

    @Test
    void createTourDuplicated() throws XWikiException, DuplicatedIdException
    {
        doThrow(new DuplicatedIdException("duplicate id")).when(this.userStatusManager).createUserTourStatus();
        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            this.userTourResource.createUserTourStatus();
        });
        assertEquals(Response.Status.CONFLICT.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("Executing: User tour status API: creating new user tour status object.",
            this.logCapture.getMessage(0));
        assertEquals("Conflict: User tour status API: creating new user tour status object.",
            this.logCapture.getMessage(1));
    }

    @Test
    void updateTour()
    {

        Response response = this.userTourResource.updateUserTourStatus(this.userTourStatus);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("Executing: User tour status API: updating user tour status object.",
            this.logCapture.getMessage(0));
    }

    @Test
    void updateTourInvalidId() throws XWikiException, InvalidIdException, JsonProcessingException
    {
        doThrow(new InvalidIdException("invalid id")).when(this.userStatusManager)
            .updateUserTourStatus(this.userTourStatus);
        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            this.userTourResource.updateUserTourStatus(this.userTourStatus);
        });
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("Executing: User tour status API: updating user tour status object.",
            this.logCapture.getMessage(0));
        assertEquals("Resource not found: User tour status API: updating user tour status object.",
            this.logCapture.getMessage(1));
    }

    @Test
    void deleteTourError() throws XWikiException, InvalidIdException, JsonProcessingException
    {
        doThrow(new RuntimeException("invalid id")).when(this.userStatusManager)
            .updateUserTourStatus(this.userTourStatus);
        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            this.userTourResource.updateUserTourStatus(this.userTourStatus);
        });
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("Executing: User tour status API: updating user tour status object.",
            this.logCapture.getMessage(0));
        assertEquals("Internal error: User tour status API: updating user tour status object.",
            this.logCapture.getMessage(1));
    }
}
