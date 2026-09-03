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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.guidedtour.api.dtos.TaskDTO;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.contrib.guidedtour.internal.util.QueryUtil;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.validation.EntityNameValidation;
import org.xwiki.query.QueryException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.TASK_CLASS;

/**
 * Test class for {@link TasksManager}.
 *
 * @version $Id$
 */
@ComponentTest
class TasksManagerTest
{
    private static final String TOUR_ID = "tourId";

    private static final String TASK_ID1 = "taskId1";

    private static final String TASK_ID2 = "taskId2";

    private static final String VALIDATED_TASK_ID1 = "validatedTaskId1";

    private static final String VALIDATED_TASK_ID2 = "validatedTaskId2";

    private static final String SERIALIZED_CLASS = "XWiki.GuidedTour.TaskClass";

    private static final String GET_ALL_TASKS_QUERY =
        "select doc.fullName from XWikiDocument doc, BaseObject obj, LongProperty orderProp "
            + "where doc.translation = 0 and doc.fullName = obj.name and obj.className = :class "
            + "and doc.space = :space and obj.id = orderProp.id.id and orderProp.id.name = 'order' "
            + "and lower(doc.title) like lower(:titleFilter) escape '\\' order by orderProp.value asc";

    private static final String GET_TASK_QUERY = "select doc.fullName from XWikiDocument doc, BaseObject obj "
        + "where doc.translation = 0 and doc.fullName = obj.name and obj.className = :class and doc.space = :space "
        + "and doc.name = :taskName";

    @InjectMockComponents
    private TasksManager tasksManager;

    @MockComponent
    private XWikiContext wikiContext;

    @MockComponent
    private QueryUtil queryUtil;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    @Named("ReplaceCharacterEntityNameValidation")
    private EntityNameValidation nameValidator;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Mock
    private SpaceReference spaceReference;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument tourDocument;

    @Mock
    private XWikiDocument taskDocument1;

    @Mock
    private XWikiDocument taskDocument2;

    @Mock
    private DocumentReference tourReference;

    @Mock
    private DocumentReference taskReference1;

    @Mock
    private DocumentReference taskReference2;

    @Mock
    private BaseObject taskObject1;

    @Mock
    private BaseObject taskObject2;

    private TaskDTO taskDTO1 = new TaskDTO(TASK_ID1, "task title1", 1, true, new ArrayList<>());

    private TaskDTO taskDTO2 = new TaskDTO(TASK_ID2, "task title2", -1, false, List.of(TASK_ID1));

    @BeforeEach
    void setup() throws XWikiException, QueryException
    {
        when(this.wikiContext.getWiki()).thenReturn(this.xwiki);
        when(this.xwiki.getDocument(this.tourReference, this.wikiContext)).thenReturn(this.tourDocument);
        when(this.xwiki.getDocument(this.taskReference1, this.wikiContext)).thenReturn(this.taskDocument1);
        when(this.xwiki.getDocument(this.taskReference2, this.wikiContext)).thenReturn(this.taskDocument2);
        when(this.xwiki.exists(this.tourReference, this.wikiContext)).thenReturn(true);
        when(this.xwiki.exists(this.taskReference1, this.wikiContext)).thenReturn(true);
        when(this.xwiki.exists(this.taskReference2, this.wikiContext)).thenReturn(true);

        when(this.nameValidator.transform(TASK_ID1)).thenReturn(VALIDATED_TASK_ID1);
        when(this.nameValidator.transform(TASK_ID2)).thenReturn(VALIDATED_TASK_ID2);

        when(this.documentReferenceResolver.resolve(TOUR_ID)).thenReturn(this.tourReference);
        when(this.documentReferenceResolver.resolve(VALIDATED_TASK_ID1, this.tourReference)).thenReturn(
            this.taskReference1);
        when(this.documentReferenceResolver.resolve(VALIDATED_TASK_ID2, this.tourReference)).thenReturn(
            this.taskReference2);

        when(this.tourReference.getLastSpaceReference()).thenReturn(this.spaceReference);
        when(this.localSerializer.serialize(this.spaceReference)).thenReturn("tourSpace");
        when(this.localSerializer.serialize(TASK_CLASS)).thenReturn(SERIALIZED_CLASS);

        when(this.taskReference1.getName()).thenReturn(VALIDATED_TASK_ID1);
        when(this.taskReference2.getName()).thenReturn(VALIDATED_TASK_ID2);
        when(this.taskDocument1.getDocumentReference()).thenReturn(this.taskReference1);
        when(this.taskDocument2.getDocumentReference()).thenReturn(this.taskReference2);
        when(this.taskDocument1.getXObject(TASK_CLASS)).thenReturn(this.taskObject1);
        when(this.taskDocument2.getXObject(TASK_CLASS)).thenReturn(this.taskObject2);

        when(this.queryUtil.executeQuery(GET_ALL_TASKS_QUERY,
            Map.of("space", "tourSpace", "titleFilter", "%%", "class", SERIALIZED_CLASS))).thenReturn(
            List.of(this.taskReference1, this.taskReference2));

        when(this.taskObject1.getStringValue("title")).thenReturn(this.taskDTO1.getTitle());
        when(this.taskObject1.getStringValue("dependsOn")).thenReturn("");
        when(this.taskObject1.getIntValue("order")).thenReturn(1);
        when(this.taskObject1.getIntValue("isActive")).thenReturn(1);

        when(this.taskObject2.getStringValue("title")).thenReturn(this.taskDTO2.getTitle());
        when(this.taskObject2.getStringValue("dependsOn")).thenReturn(VALIDATED_TASK_ID1);
        when(this.taskObject2.getIntValue("order")).thenReturn(2);
        when(this.taskObject2.getIntValue("isActive")).thenReturn(0);
    }

    @Test
    void createTask() throws XWikiException, DuplicatedIdException, InvalidIdException, QueryException
    {
        when(this.xwiki.exists(this.taskReference1, this.wikiContext)).thenReturn(false);
        when(this.taskDocument1.newXObject(TASK_CLASS, this.wikiContext)).thenReturn(this.taskObject1);
        this.taskDTO1.setOrder(0);
        this.tasksManager.createTask(TOUR_ID, this.taskDTO1);

        verify(this.taskDocument1, times(1)).setTitle(this.taskDTO1.getTitle());
        verify(this.taskObject1, times(1)).set("order", 3, this.wikiContext);
        verify(this.taskObject1, times(1)).set("dependsOn", this.taskDTO1.getDependsOn(), this.wikiContext);
        verify(this.xwiki, times(1)).saveDocument(this.taskDocument1, "Task created.", this.wikiContext);
    }

    @Test
    void createTaskSameOrder() throws XWikiException
    {
        when(this.xwiki.exists(this.taskReference1, this.wikiContext)).thenReturn(false);
        this.taskDTO1.setOrder(2);
        DuplicatedIdException exception = assertThrows(DuplicatedIdException.class, () -> {
            this.tasksManager.createTask(TOUR_ID, this.taskDTO1);
        });

        assertEquals("A task with the given order already exists.", exception.getMessage());
    }

    @Test
    void createTaskDuplicate()
    {

        DuplicatedIdException exception = assertThrows(DuplicatedIdException.class, () -> {
            this.tasksManager.createTask(TOUR_ID, this.taskDTO1);
        });

        assertEquals(String.format("Task page [%s] already exists.", this.taskReference1), exception.getMessage());
    }

    @Test
    void getTask() throws Exception
    {
        when(this.queryUtil.executeQuery(GET_TASK_QUERY,
            Map.of("space", "tourSpace", "taskName", TASK_ID2, "class", SERIALIZED_CLASS))).thenReturn(
            List.of(this.taskReference2));

        TaskDTO result = this.tasksManager.getTask(TOUR_ID, TASK_ID2);

        assertEquals(VALIDATED_TASK_ID2, result.getId());
        assertEquals(this.taskDTO2.getTitle(), result.getTitle());
        assertFalse(this.taskDTO2.isActive());
    }

    @Test
    void getTaskInvalidId() throws Exception
    {
        when(this.queryUtil.executeQuery(GET_TASK_QUERY,
            Map.of("space", "tourSpace", "taskName", TASK_ID2, "class", SERIALIZED_CLASS))).thenReturn(List.of());

        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            this.tasksManager.getTask(TOUR_ID, TASK_ID2);
        });

        assertEquals(String.format("Task with the given id [%s] does not exists.", TASK_ID2), exception.getMessage());
    }

    @Test
    void getAllTasks() throws Exception
    {
        List<TaskDTO> tasks = this.tasksManager.getAllTasks(TOUR_ID);

        assertEquals(2, tasks.size());
        assertEquals(VALIDATED_TASK_ID1, tasks.getFirst().getId());
        assertEquals(this.taskDTO1.getTitle(), tasks.getFirst().getTitle());
        assertEquals(1, tasks.getFirst().getOrder());
        assertTrue(tasks.getFirst().isActive());
        assertTrue(tasks.getFirst().getDependsOn().isEmpty());

        assertEquals(VALIDATED_TASK_ID2, tasks.get(1).getId());
        assertEquals(this.taskDTO2.getTitle(), tasks.get(1).getTitle());
        assertEquals(2, tasks.get(1).getOrder());
        assertFalse(tasks.get(1).isActive());
        assertEquals(VALIDATED_TASK_ID1, tasks.get(1).getDependsOn().getFirst());
    }

    @Test
    void getAllTasksFiltered() throws Exception
    {
        when(this.queryUtil.executeQuery(GET_ALL_TASKS_QUERY,
            Map.of("space", "tourSpace", "titleFilter", "%test%", "class", SERIALIZED_CLASS))).thenReturn(
            List.of(this.taskReference1));

        List<TaskDTO> tasks = this.tasksManager.getAllTasks(TOUR_ID, "test");

        assertEquals(1, tasks.size());
        assertEquals(VALIDATED_TASK_ID1, tasks.getFirst().getId());
        assertEquals(this.taskDTO1.getTitle(), tasks.getFirst().getTitle());
        assertEquals(1, tasks.getFirst().getOrder());
        assertTrue(tasks.getFirst().isActive());
        assertTrue(tasks.getFirst().getDependsOn().isEmpty());
    }

    @Test
    void updateTaskSameOrder() throws Exception
    {
        TaskDTO taskDtoUpdate = new TaskDTO(VALIDATED_TASK_ID2, "updated title", 2, true, new ArrayList<>());
        when(this.taskDocument2.getXObject(TASK_CLASS)).thenReturn(this.taskObject2);

        this.tasksManager.updateTask(TOUR_ID, taskDtoUpdate);

        verify(this.taskDocument2, times(1)).setTitle(taskDtoUpdate.getTitle());
        verify(this.taskObject2, times(1)).set("order", 2, this.wikiContext);
        verify(this.taskObject2, times(1)).set("dependsOn", taskDtoUpdate.getDependsOn(), this.wikiContext);
        verify(this.xwiki, times(1)).saveDocument(this.taskDocument2, "Updated task.", this.wikiContext);
    }

    @Test
    void updateTaskDifferentOrder() throws Exception
    {
        when(this.taskDocument1.getXObject(TASK_CLASS)).thenReturn(this.taskObject1);
        when(this.taskDocument2.getXObject(TASK_CLASS)).thenReturn(this.taskObject2);
        TaskDTO taskDtoUpdate = new TaskDTO(VALIDATED_TASK_ID2, "updated title", 1, true, new ArrayList<>());

        this.tasksManager.updateTask(TOUR_ID, taskDtoUpdate);

        verify(this.taskDocument2, times(1)).setTitle(taskDtoUpdate.getTitle());
        verify(this.taskObject2, times(1)).set("order", 1, this.wikiContext);
        verify(this.taskObject2, times(1)).set("dependsOn", taskDtoUpdate.getDependsOn(), this.wikiContext);
        verify(this.xwiki, times(1)).saveDocument(this.taskDocument2, "Updated task.", this.wikiContext);
    }

    @Test
    void updateTaskInvalidTourId()
    {
        String invalidStringId = "TOUR_ID";
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            this.tasksManager.updateTask(invalidStringId, new TaskDTO());
        });

        assertEquals(String.format("Tour with the given id [%s] does not exists.", invalidStringId),
            exception.getMessage());
    }

    @Test
    void deleteTask() throws Exception
    {
        when(this.taskDocument2.getXObject(TASK_CLASS)).thenReturn(this.taskObject2);
        this.tasksManager.deleteTask(TOUR_ID, VALIDATED_TASK_ID1);

        verify(this.xwiki, times(1)).deleteAllDocuments(this.taskDocument1, this.wikiContext);
    }

    @Test
    void deleteTaskNotFound() throws Exception
    {
        when(this.taskDocument2.getXObject(TASK_CLASS)).thenReturn(this.taskObject2);

        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            this.tasksManager.deleteTask(TOUR_ID, "randomId");
        });

        verify(this.xwiki, times(0)).deleteAllDocuments(this.taskDocument1, this.wikiContext);
        assertEquals("Task with the given id [randomId] does not exists.", exception.getMessage());
    }
}
