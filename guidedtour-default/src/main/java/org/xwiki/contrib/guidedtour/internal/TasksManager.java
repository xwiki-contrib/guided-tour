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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.guidedtour.api.dtos.TaskDTO;
import org.xwiki.contrib.guidedtour.api.enums.TourProperty;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.contrib.guidedtour.internal.util.QueryUtil;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.validation.EntityNameValidation;
import org.xwiki.query.QueryException;

import com.google.common.base.Splitter;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.TASK_CLASS;

/**
 * Manages the tasks for the guided tour. It provides methods to create, retrieve, update and delete tasks. Tasks are
 * stored as XWiki documents with a TaskClass object. The document name is the task id and the parent document is the
 * tour document.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = TasksManager.class)
@Singleton
public class TasksManager
{
    private static final String SPACE_KEY = "space";

    private static final String TITLE_FILTER = "titleFilter";

    private static final String CLASS_FILTER = "class";

    private static final String GET_TASK_QUERY = """
        select doc.fullName from XWikiDocument doc, BaseObject obj where doc.translation = 0 and doc.fullName = \
        obj.name and obj.className = :class and doc.space = :space and doc.name = :taskName""";

    private static final String GET_ALL_TASKS_QUERY = """
        select doc.fullName from XWikiDocument doc, BaseObject obj, LongProperty orderProp where doc.translation = \
        0 and doc.fullName = obj.name and obj.className = :class and doc.space = :space and obj.id = \
        orderProp.id.id and orderProp.id.name = 'order' and lower(doc.title) like lower(:titleFilter) escape '\\' \
        order by orderProp.value asc""";

    private static final String TASK_NOT_FOUND_ERROR = "Task with the given id [%s] does not exists.";

    @Inject
    @Named("ReplaceCharacterEntityNameValidation")
    private EntityNameValidation nameValidator;

    @Inject
    private Provider<XWikiContext> wikiContextProvider;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Inject
    private QueryUtil queryUtil;

    /**
     * Creates a new task based on the provided DTO. The task is stored as an {@link XWikiDocument} with a TaskClass
     * object. The order of the task is set to the highest order position available in the tour.
     *
     * @param tourId the id of the tour to which the task belongs
     * @param taskDTO the {@link TaskDTO} containing the task information
     * @return the id of the created task
     * @throws XWikiException if there is an error while creating the task document
     * @throws QueryException if there is an error while querying for existing tasks to determine the order of the
     *     new task
     * @throws DuplicatedIdException if a task with the same id already exists in the tour
     * @throws InvalidIdException if the tour with the given id does not exist
     */
    public String createTask(String tourId, TaskDTO taskDTO)
        throws XWikiException, QueryException, DuplicatedIdException, InvalidIdException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        DocumentReference tourDocRef = getTourReference(tourId);
        String taskId = validateTaskId(taskDTO);
        DocumentReference taskDocRef = this.documentReferenceResolver.resolve(taskId, tourDocRef);
        if (wiki.exists(taskDocRef, wikiContext)) {
            throw new DuplicatedIdException("Task page [%s] already exists.", taskDocRef);
        }
        int highestOrder = getHighestOrder(tourId, taskDTO);
        XWikiDocument taskDoc = wiki.getDocument(taskDocRef, wikiContext);
        taskDoc.setTitle(taskDTO.getTitle());
        BaseObject taskClassObject = taskDoc.newXObject(TASK_CLASS, wikiContext);
        taskDTO.setOrder(++highestOrder);
        populateTaskObject(taskDTO, taskClassObject);
        wiki.saveDocument(taskDoc, "Task created.", wikiContext);
        return taskId;
    }

    /**
     * Retrieves a task based on the provided tour id and task id. It returns a {@link TaskDTO} containing the task
     * information.
     *
     * @param tourId the id of the tour to which the task belongs
     * @param taskId the id of the task to retrieve
     * @return a {@link TaskDTO} containing the task information
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws QueryException if there is an error while executing the HQL query to retrieve the task document
     * @throws InvalidIdException if the task with the given id does not exist in the tour
     */
    public TaskDTO getTask(String tourId, String taskId) throws XWikiException, QueryException, InvalidIdException
    {
        DocumentReference tourDocRef = getTourReference(tourId);
        String parentSpace = this.localSerializer.serialize(tourDocRef.getLastSpaceReference());
        Map<String, Object> parameters = Map.of(SPACE_KEY, parentSpace, "taskName", taskId, CLASS_FILTER,
            this.localSerializer.serialize(TASK_CLASS));
        List<DocumentReference> results = this.queryUtil.executeQuery(GET_TASK_QUERY, parameters);
        if (results.isEmpty()) {
            throw new InvalidIdException(TASK_NOT_FOUND_ERROR, taskId);
        }
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        XWikiDocument doc = wiki.getDocument(results.getFirst(), wikiContext);
        return getTaskDTO(doc);
    }

    /**
     * Retrieves all tasks for a given tour id and with the title containing the given string.
     *
     * @param tourId the id of the tour to which the tasks belong
     * @param filteredTitle the title to filter the tasks by, can be empty or null if no filtering is needed
     * @return a list of {@link TaskDTO} containing the tasks information
     * @throws QueryException if there is an error while executing the HQL query to retrieve the task documents
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws InvalidIdException if the tour with the given id does not exist
     * @since 0.2
     */
    public List<TaskDTO> getAllTasks(String tourId, String filteredTitle)
        throws QueryException, XWikiException, InvalidIdException
    {
        DocumentReference tourDocRef = getTourReference(tourId);
        String parentSpace = this.localSerializer.serialize(tourDocRef.getLastSpaceReference());
        String titleFilter = String.format("%%%s%%", escapeQueryParameter(filteredTitle));
        Map<String, Object> bindValues = new HashMap<>(
            Map.of(SPACE_KEY, parentSpace, TITLE_FILTER, titleFilter, CLASS_FILTER,
                this.localSerializer.serialize(TASK_CLASS)));

        List<DocumentReference> docRefs = this.queryUtil.executeQuery(GET_ALL_TASKS_QUERY, bindValues);
        List<TaskDTO> tasks = new ArrayList<>(docRefs.size());
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        for (DocumentReference taskDocRef : docRefs) {
            XWikiDocument doc = wiki.getDocument(taskDocRef, wikiContext);
            tasks.add(getTaskDTO(doc));
        }
        return tasks;
    }

    /**
     * Calls {@link #getAllTasks(String, String)} with an empty search title.
     *
     * @param tourId the id of the tour to which the tasks belong
     * @return a list of {@link TaskDTO} containing the tasks information
     * @throws QueryException if there is an error while executing the HQL query to retrieve the task documents
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws InvalidIdException if the tour with the given id does not exist
     */
    public List<TaskDTO> getAllTasks(String tourId) throws QueryException, XWikiException, InvalidIdException
    {
        return getAllTasks(tourId, "");
    }

    /**
     * Updates an existing task based on the provided DTO. If the order of the task is modified, it also updates the
     * order of the other tasks in the tour accordingly.
     *
     * @param tourId the id of the tour to which the task belongs
     * @param newDTO the {@link TaskDTO} containing the updated task information
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws QueryException if there is an error while querying for existing tasks to determine the order updates
     * @throws InvalidIdException if the task with the given id does not exist in the tour
     */
    public void updateTask(String tourId, TaskDTO newDTO) throws XWikiException, QueryException, InvalidIdException
    {
        // We get all tasks as we will have to update the order of remaining tasks.
        List<TaskDTO> existingTasks = getAllTasks(tourId);
        TaskDTO oldTask = getTaskDTOFromList(newDTO.getId(), existingTasks);
        int oldOrder = oldTask.getOrder();
        DocumentReference tourDocRef = this.documentReferenceResolver.resolve(tourId);
        if (oldOrder != newDTO.getOrder()) {
            existingTasks.removeIf(task -> task.getId().equals(newDTO.getId()));
            updateTasksOrder(tourDocRef, newDTO.getOrder(), existingTasks, oldOrder);
        }
        updateTaskObject(newDTO, tourDocRef);
    }

    /**
     * Deletes a task based on the provided tour id and task id. It also updates the order of the other tasks in the
     * tour accordingly.
     *
     * @param tourId the id of the tour to which the task belongs
     * @param taskId the id of the task to delete
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws QueryException if there is an error while querying for existing tasks to determine the order updates
     * @throws InvalidIdException if the task with the given id does not exist in the tour
     */
    public void deleteTask(String tourId, String taskId) throws XWikiException, QueryException, InvalidIdException
    {
        // We get all tasks as we will have to update the order of remaining tasks.
        List<TaskDTO> existingTasks = getAllTasks(tourId);
        TaskDTO targetTask = getTaskDTOFromList(taskId, existingTasks);
        existingTasks.remove(targetTask);
        // The existence of the tour document is already checked in the getAllTasks method.
        DocumentReference tourDocRef = this.documentReferenceResolver.resolve(tourId);
        DocumentReference taskDocRef = this.documentReferenceResolver.resolve(taskId, tourDocRef);
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        wiki.deleteAllDocuments(wiki.getDocument(taskDocRef, wikiContext), wikiContext);
        updateRemainingTasks(existingTasks, targetTask, tourDocRef);
    }

    private String escapeQueryParameter(String parameter)
    {
        return StringUtils.defaultIfBlank(parameter, "")
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    private TaskDTO getTaskDTO(XWikiDocument doc)
    {
        BaseObject taskObj = doc.getXObject(TASK_CLASS);
        String title = taskObj.getStringValue(TourProperty.TITLE.getBaseKey());
        String dependsOn = taskObj.getStringValue(TourProperty.DEPENDS_ON.getBaseKey());
        int order = taskObj.getIntValue(TourProperty.ORDER.getBaseKey());
        boolean isActive = taskObj.getIntValue(TourProperty.IS_ACTIVE.getBaseKey()) == 1;

        return new TaskDTO(doc.getDocumentReference().getName(), title, order, isActive,
            Splitter.on(',').omitEmptyStrings().splitToList(dependsOn));
    }

    private String validateTaskId(TaskDTO taskDTO)
    {
        String unvalidatedId = taskDTO.getId();
        if (StringUtils.isBlank(unvalidatedId)) {
            unvalidatedId = taskDTO.getTitle();
        }
        String validatedId = this.nameValidator.transform(unvalidatedId);
        if (StringUtils.isBlank(validatedId)) {
            throw new RuntimeException("Given DTO is missing both id and title, cannot create a task.");
        }
        return validatedId;
    }

    private TaskDTO getTaskDTOFromList(String taskId, List<TaskDTO> existingTasks) throws InvalidIdException
    {
        return existingTasks.stream().filter(task -> task.getId().equals(taskId)).findFirst()
            .orElseThrow(() -> new InvalidIdException(TASK_NOT_FOUND_ERROR, taskId));
    }

    private DocumentReference getTourReference(String referenceString) throws XWikiException, InvalidIdException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        DocumentReference tourDocRef = this.documentReferenceResolver.resolve(referenceString);
        if (wikiContext.getWiki().exists(tourDocRef, wikiContext)) {
            return tourDocRef;
        } else {
            throw new InvalidIdException("Tour with the given id [%s] does not exists.", referenceString);
        }
    }

    /**
     * Check and update the remaining tasks order and dependency list depending on the removed task.
     *
     * @param existingTasks remaining tasks list
     * @param removedTask the task that was removed
     * @param tourDocRef the reference to the tour document
     */
    private void updateRemainingTasks(List<TaskDTO> existingTasks, TaskDTO removedTask, DocumentReference tourDocRef)
        throws XWikiException
    {
        for (TaskDTO task : existingTasks) {
            // We only shift those tasks that have an order greater than the removed task's order, as they need to be
            // shifted down to fill the gap.
            boolean wasOrderModified = shiftOrderIfNeeded(Integer.MAX_VALUE, task, removedTask.getOrder());
            boolean wasDependencyRemoved = removeTaskDependency(task, removedTask.getId());
            // If either the order was shifted or the task dependency list was modified, we update the object to
            // persist the changes.
            if (wasOrderModified || wasDependencyRemoved) {
                updateTaskObject(task, tourDocRef);
            }
        }
    }

    /**
     * If the removed task is a dependency for the given task, we remove it from the list.
     *
     * @param task the task for which we check the list
     * @param removedTaskId the ID of the task to remove from dependencies
     * @return true if the dependency was removed, false otherwise
     * @since 0.2
     */
    private boolean removeTaskDependency(TaskDTO task, String removedTaskId)
    {
        boolean isModified = false;
        if (task.getDependsOn().contains(removedTaskId)) {
            ArrayList<String> updatedDependencies = new ArrayList<>(task.getDependsOn());
            updatedDependencies.remove(removedTaskId);
            task.setDependsOn(updatedDependencies);
            isModified = true;
        }
        return isModified;
    }

    /**
     * Shifts the order of the given task to accommodate a task being moved or deleted. If the task's order is above the
     * previous position and at or below the new position, it is shifted down by one. If the task's order is below the
     * previous position and at or above the new position, it is shifted up by one.
     *
     * @param newOrder the new order position of the moved task
     * @param task the task whose order may need to be shifted
     * @param previousOrder the original order position before the move or deletion
     * @return {@code true} if the task's order was modified, {@code false} otherwise
     * @since 0.2
     */
    private boolean shiftOrderIfNeeded(int newOrder, TaskDTO task, int previousOrder)
    {
        boolean isModified = false;
        if (task.getOrder() > previousOrder && task.getOrder() <= newOrder) {
            task.setOrder(task.getOrder() - 1);
            isModified = true;
        } else if (task.getOrder() < previousOrder && task.getOrder() >= newOrder) {
            task.setOrder(task.getOrder() + 1);
            isModified = true;
        }
        return isModified;
    }

    private void updateTasksOrder(DocumentReference tourRef, int newOrder, List<TaskDTO> existingTasks,
        int previousOrder) throws XWikiException
    {
        for (TaskDTO task : existingTasks) {
            if (shiftOrderIfNeeded(newOrder, task, previousOrder)) {
                updateTaskObject(task, tourRef);
            }
        }
    }

    private void updateTaskObject(TaskDTO newDTO, DocumentReference tourDocRef) throws XWikiException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        DocumentReference taskDocRef = this.documentReferenceResolver.resolve(newDTO.getId(), tourDocRef);
        XWikiDocument taskDoc = wiki.getDocument(taskDocRef, wikiContext);
        taskDoc.setTitle(newDTO.getTitle());
        BaseObject taskClassObject = taskDoc.getXObject(TASK_CLASS);
        populateTaskObject(newDTO, taskClassObject);
        wiki.saveDocument(taskDoc, "Updated task.", wikiContext);
    }

    private void populateTaskObject(TaskDTO taskDTO, BaseObject taskClassObject) throws XWikiException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        taskClassObject.set("title", taskDTO.getTitle(), wikiContext);
        taskClassObject.set("dependsOn", taskDTO.getDependsOn(), wikiContext);
        taskClassObject.set("order", taskDTO.getOrder(), wikiContext);
        taskClassObject.set("isActive", taskDTO.isActive() ? 1 : 0, wikiContext);
    }

    private int getHighestOrder(String tourId, TaskDTO taskDTO)
        throws QueryException, XWikiException, InvalidIdException, DuplicatedIdException
    {
        List<TaskDTO> existingTasks = getAllTasks(tourId);
        int highestOrder = 0;
        if (!existingTasks.isEmpty()) {
            if (existingTasks.stream().anyMatch(task -> task.getOrder() == taskDTO.getOrder())) {
                throw new DuplicatedIdException("A task with the given order already exists.");
            }
            highestOrder = existingTasks.stream().mapToInt(TaskDTO::getOrder).max().orElse(0);
        }
        return highestOrder;
    }
}
