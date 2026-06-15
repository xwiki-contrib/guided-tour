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

import com.xpn.xwiki.XWikiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.container.Container;
import org.xwiki.container.servlet.ServletRequest;
import org.xwiki.contrib.guidedtour.api.dtos.StepDTO;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ComponentTest
class DefaultStepsResourceTest
{
    private static final String CSRF_VALUE = "csrfToken";

    private static final String TOUR_ID = "tourId";

    private static final String TASK_ID = "taskId";

    private final StepDTO stepDTO = new StepDTO();

    @InjectMockComponents
    private DefaultStepsResource defaultStepsResource;

    @MockComponent
    private StepsManager stepsManager;

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
    void getTaskSteps() throws XWikiException, InvalidIdException
    {
        List<StepDTO> steps = new ArrayList<>(2);
        steps.add(new StepDTO());
        steps.add(new StepDTO());
        when(this.stepsManager.getAllSteps(TOUR_ID, TASK_ID)).thenReturn(steps);

        Response response = this.defaultStepsResource.getTaskSteps(TOUR_ID, TASK_ID);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(steps, response.getEntity());
        assertEquals("Executing: Steps API: retrieving the steps for task [taskId] from tour [tourId].",
            this.logCapture.getMessage(0));
    }

    @Test
    void createStep()
    {
        Response response = this.defaultStepsResource.createStep(TOUR_ID, TASK_ID, this.stepDTO);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals("Executing: Steps API: creating step for task [taskId] from tour [tourId].",
            this.logCapture.getMessage(0));
    }

    @Test
    void updateTask()
    {
        Response response = this.defaultStepsResource.updateStep(TOUR_ID, TASK_ID, 2, this.stepDTO);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("Executing: Steps API: updating step on position [2] for task [taskId] from tour [tourId].",
            this.logCapture.getMessage(0));
    }

    @Test
    void deleteTask()
    {
        Response response = this.defaultStepsResource.deleteStep(TOUR_ID, TASK_ID, 2);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("Executing: Steps API: removing step on position [2] for task [taskId] from tour [tourId].",
            this.logCapture.getMessage(0));
    }

    @Test
    void deleteTaskError() throws AccessDeniedException
    {
        doThrow(new AccessDeniedException(Right.DELETE, null, null)).when(this.contextualAuthorizationManager)
            .checkAccess(Right.DELETE);
        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            this.defaultStepsResource.deleteStep(TOUR_ID, TASK_ID, 2);
        });
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("Executing: Steps API: removing step on position [2] for task [taskId] from tour [tourId].",
            this.logCapture.getMessage(0));
        assertEquals(
            "Authorization error: Steps API: removing step on position [2] for task [taskId] from tour [tourId].",
            this.logCapture.getMessage(1));
    }
}
