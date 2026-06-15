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
import org.xwiki.contrib.guidedtour.api.dtos.TaskDTO;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.query.QueryException;
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
class DefaultTasksResourceTest
{
    private static final String CSRF_VALUE = "csrfToken";

    private static final String TOUR_ID = "tourId";

    private final TaskDTO taskDTO = new TaskDTO("taskId", "title", 1, true, new ArrayList<>());

    @InjectMockComponents
    private DefaultTasksResource defaultTasksResource;

    @MockComponent
    private TasksManager tasksManager;

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
    void setup() throws QueryException, XWikiException, DuplicatedIdException, InvalidIdException
    {
        when(this.containerProvider.get()).thenReturn(this.container);
        when(this.container.getRequest()).thenReturn(this.request);
        when(this.request.getParameter("csrf")).thenReturn(CSRF_VALUE);
        when(this.csrf.isTokenValid(CSRF_VALUE)).thenReturn(true);
        when(this.request.getRequest()).thenReturn(this.httpServletRequest);
        when(this.httpServletRequest.getHeader("xwiki-form-token")).thenReturn(CSRF_VALUE);
        when(this.tasksManager.createTask(TOUR_ID, this.taskDTO)).thenReturn(this.taskDTO.getId());
    }

    @Test
    void getTourTasks() throws QueryException, XWikiException, InvalidIdException
    {
        List<TaskDTO> tasks = new ArrayList<>(2);
        tasks.add(new TaskDTO("id1", "title", 1, true, new ArrayList<>()));
        tasks.add(new TaskDTO("id2", "title", 1, true, List.of("id1")));
        when(this.tasksManager.getAllTasks(TOUR_ID)).thenReturn(tasks);

        Response response = this.defaultTasksResource.getTourTasks(TOUR_ID);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(tasks, response.getEntity());
        assertEquals("Executing: Tasks API: retrieving the tasks for tour [tourId].", this.logCapture.getMessage(0));
    }

    @Test
    void getTourTask() throws QueryException, XWikiException, InvalidIdException
    {
        when(this.tasksManager.getTask(TOUR_ID, this.taskDTO.getId())).thenReturn(this.taskDTO);

        Response response = this.defaultTasksResource.getTourTask(TOUR_ID, this.taskDTO.getId());
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(this.taskDTO, response.getEntity());
        assertEquals("Executing: Tasks API: retrieving the task [taskId] from tour [tourId].",
            this.logCapture.getMessage(0));
    }

    @Test
    void createTask()
    {
        Response response = this.defaultTasksResource.createTask(TOUR_ID, this.taskDTO);
        assertEquals(response.getEntity(), this.taskDTO.getId());
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals("Executing: Tasks API: creating task [taskId] for tour [tourId].", this.logCapture.getMessage(0));
    }

    @Test
    void updateTask()
    {
        Response response = this.defaultTasksResource.updateTask(TOUR_ID, this.taskDTO.getId(), this.taskDTO);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("Executing: Tasks API: updating task [taskId] from tour [tourId].", this.logCapture.getMessage(0));
    }

    @Test
    void updateTaskDifferentIds()
    {
        Response response = this.defaultTasksResource.updateTask(TOUR_ID, "taskIdDif", this.taskDTO);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("Path and Body ID mismatch for given task.", response.getEntity());
        assertEquals("Executing: Tasks API: updating task [taskId] from tour [tourId].", this.logCapture.getMessage(0));
    }

    @Test
    void deleteTask()
    {
        Response response = this.defaultTasksResource.deleteTask(TOUR_ID, this.taskDTO.getId());
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("Executing: Tasks API: removing task [taskId] from tour [tourId].", this.logCapture.getMessage(0));
    }

    @Test
    void deleteTaskError() throws AccessDeniedException
    {
        doThrow(new AccessDeniedException(Right.DELETE, null, null)).when(this.contextualAuthorizationManager)
            .checkAccess(Right.DELETE);
        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            this.defaultTasksResource.deleteTask(TOUR_ID, this.taskDTO.getId());
        });
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("Executing: Tasks API: removing task [taskId] from tour [tourId].", this.logCapture.getMessage(0));
        assertEquals("Authorization error: Tasks API: removing task [taskId] from tour [tourId].",
            this.logCapture.getMessage(1));
    }
}
