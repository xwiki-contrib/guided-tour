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
package org.xwiki.contrib.guidedtour.test.ui;

import java.util.List;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourTestUtils.Step;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourTestUtils.Task;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourTestUtils.Tour;
import org.xwiki.contrib.guidedtour.test.po.TaskEditPage;
import org.xwiki.contrib.guidedtour.test.po.TaskPage;
import org.xwiki.contrib.guidedtour.test.po.TourEditPage;
import org.xwiki.contrib.guidedtour.test.po.TourPage;
import org.xwiki.contrib.guidedtour.test.po.TourStepPopover;
import org.xwiki.contrib.guidedtour.test.po.TourWidget;
import org.xwiki.contrib.guidedtour.test.po.ToursPage;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.DONE;

/**
 * Verify the administration of the guided tours: the list of tours, the tour pages and the task pages, and that the
 * changes made there are taken into account by the widget and the tasks.
 *
 * @version $Id$
 */
@UITest
class TourAdministrationIT extends AbstractGuidedTourIT
{
    /**
     * A task of the default tour, used to know when the widget has loaded the tours.
     */
    private static final String DEFAULT_TOUR_TASK_ID = "PageEdit";

    /**
     * The list of tours can be filtered by title and status, and has actions to view, edit and delete each tour.
     */
    @Test
    @Order(1)
    void toursList(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference activeTour = this.guidedTour.createTour(space, "ActiveTour", "Listed active tour", true);
        DocumentReference listedTask =
            this.guidedTour.createTask(activeTour, "listedActiveTask", "Listed task", 0, true);
        this.guidedTour.addSteps(listedTask, Step.withContent("Listed task, only step"));
        DocumentReference inactiveTour =
            this.guidedTour.createTour(space, "InactiveTour", "Listed inactive tour", false);

        setup.loginAsSuperAdmin();
        ToursPage toursPage = ToursPage.gotoPage();
        // Make sure the widget doesn't cover the list of tours.
        new TourWidget().collapse();

        // Filter by title.
        toursPage.filterByTitle("Listed");
        assertThat(toursPage.getTourTitles(), containsInAnyOrder("Listed active tour", "Listed inactive tour"));
        assertEquals("Yes", toursPage.getTourActiveLabel("Listed active tour"));
        assertEquals("No", toursPage.getTourActiveLabel("Listed inactive tour"));

        // Filter by status.
        toursPage.filterByActive(true);
        assertEquals(List.of("Listed active tour"), toursPage.getTourTitles());
        toursPage.clearFilters().filterByActive(false);
        assertThat(toursPage.getTourTitles(), hasItem("Listed inactive tour"));
        assertThat(toursPage.getTourTitles(), not(hasItem("Listed active tour")));

        // View a tour.
        TourPage tourPage = toursPage.clearFilters().filterByTitle("Listed").viewTour("Listed active tour");
        assertEquals("Listed active tour", tourPage.getDocumentTitle());
        assertEquals(List.of("Listed task"), tourPage.getTaskTitles());

        // Edit a tour.
        toursPage = ToursPage.gotoPage().filterByTitle("Listed");
        TourEditPage tourEditPage = toursPage.editTour("Listed active tour");
        assertEquals("Listed active tour", tourEditPage.getTourTitle());
        tourEditPage.clickCancel();

        // Delete a tour.
        toursPage = ToursPage.gotoPage().filterByTitle("Listed");
        assertTrue(toursPage.deleteTour("Listed inactive tour"));
        assertFalse(this.guidedTour.exists(inactiveTour));
        toursPage = ToursPage.gotoPage().filterByTitle("Listed");
        assertEquals(List.of("Listed active tour"), toursPage.getTourTitles());
    }

    /**
     * The properties of a tour can be edited from its page, which lists its tasks, and the widget takes them into
     * account.
     */
    @Test
    @Order(2)
    void editTour(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "Content", "Tour");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Editable tour", true);
        DocumentReference firstTask = this.guidedTour.createTask(tour, "editableFirstTask", "First task", 0, true);
        this.guidedTour.addSteps(firstTask, Step.withContent("First task, only step"));
        DocumentReference secondTask = this.guidedTour.createTask(tour, "editableSecondTask", "Second task", 1, true);
        this.guidedTour.addSteps(secondTask, Step.withContent("Second task, only step"));
        String tourId = this.guidedTour.getTourId(tour);

        setup.loginAsSuperAdmin();
        TourPage tourPage = TourPage.gotoPage(tour);
        // Make sure the widget doesn't cover the list of tasks.
        new TourWidget().collapse();

        // Filter and view the tasks.
        tourPage.filterTasksByTitle("Second");
        assertEquals(List.of("Second task"), tourPage.getTaskTitles());
        tourPage.clearTaskFilters();
        assertEquals(List.of("First task", "Second task"), tourPage.getTaskTitles());
        TaskPage taskPage = tourPage.viewTask("Second task");
        assertEquals(setup.getURL(secondTask), taskPage.getPageURL());
        assertEquals(List.of("Second task, only step"), taskPage.getStepTitles());

        // Deactivate the tour, which hides it from the widget.
        TourEditPage tourEditPage = TourEditPage.gotoPage(tour);
        tourEditPage.setTourTitle("Edited tour").setDescription("Edited description").setActive(false);
        tourEditPage.clickSaveAndView();
        Tour savedTour = this.guidedTour.getTour(tour);
        assertEquals("Edited tour", savedTour.getTitle(), savedTour::toString);
        assertEquals("Edited description", savedTour.getDescription(), savedTour::toString);
        assertFalse(savedTour.isActive(), savedTour::toString);

        setup.gotoPage(page);
        TourWidget widget = new TourWidget().expand().waitUntilTaskIsDisplayed(DEFAULT_TOUR_TASK_ID);
        assertThat(widget.getTourIds(), not(hasItem(tourId)));

        // Activate it again: the widget displays it, with its new title.
        TourEditPage.gotoPage(tour).setActive(true).clickSaveAndView();
        setup.gotoPage(page);
        widget = new TourWidget().expand().waitUntilTaskIsDisplayed("editableSecondTask");
        assertThat(widget.getTourIds(), hasItem(tourId));
        assertEquals("Edited tour", widget.getTourTitle(tourId));
        assertEquals(List.of("editableFirstTask", "editableSecondTask"), widget.getVisibleTaskIds(tourId));

        // Two tasks of the same tour can be given the same title.
        // FIXME: This shouldn't be possible. Once the titles of the tasks are unique in a tour, saving a duplicate
        // title should be refused, and this check should fail: update it to expect the refusal.
        TaskEditPage.gotoPage(secondTask).setTaskTitle("First task").clickSaveAndView();
        tourPage = TourPage.gotoPage(tour);
        new TourWidget().collapse();
        assertEquals(List.of("First task", "First task"), tourPage.getTaskTitles());
    }

    /**
     * A task can be added from the tour page, then its properties and steps edited from the task page. The widget and
     * the task take the changes into account.
     */
    @Test
    @Order(3)
    void addAndEditTask(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "Content", "Task");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Steps tour", true);
        DocumentReference existingTask =
            this.guidedTour.createTask(tour, "stepsExistingTask", "Existing task", 5, true);
        this.guidedTour.addSteps(existingTask, Step.withContent("Existing task, only step"));
        DocumentReference addedTask = new DocumentReference("stepsAddedTask", tour.getLastSpaceReference());
        String tourId = this.guidedTour.getTourId(tour);

        setup.loginAsSuperAdmin();

        // Add a task from the tour page. It's created inactive, so that it can be completed before being displayed.
        TaskPage taskPage = TourEditPage.gotoPage(tour).addTask("stepsAddedTask");
        this.guidedTour.deleteAfterTest(addedTask);
        assertEquals(setup.getURL(addedTask), taskPage.getPageURL());
        assertFalse(this.guidedTour.getTask(addedTask).isActive());

        TaskEditPage taskEditPage = TaskEditPage.gotoPage(addedTask);
        taskEditPage.setTaskTitle("Added task").setOrder(1).setActive(true).addStep("Added task, step one");
        assertEquals(List.of("Added task, step one"), taskEditPage.getStepTitles());
        taskPage = taskEditPage.clickSaveAndView();
        assertEquals(List.of("Added task, step one"), taskPage.getStepTitles());
        taskEditPage = TaskEditPage.gotoPage(addedTask).addStep("Added task, step two");
        assertEquals(List.of("Added task, step one", "Added task, step two"), taskEditPage.getStepTitles());
        taskPage = taskEditPage.clickSaveAndView();
        assertEquals(List.of("Added task, step one", "Added task, step two"), taskPage.getStepTitles());
        Task savedTask = this.guidedTour.getTask(addedTask);
        assertEquals("Added task", savedTask.getTitle(), savedTask::toString);
        assertEquals(1, savedTask.getOrder(), savedTask::toString);
        assertTrue(savedTask.isActive(), savedTask::toString);

        // The widget displays the task with its title, before the existing task because of its order.
        setup.gotoPage(page);
        TourWidget widget = new TourWidget().expand().waitUntilTaskIsDisplayed("stepsAddedTask");
        assertEquals(List.of("stepsAddedTask", "stepsExistingTask"), widget.getVisibleTaskIds(tourId));
        assertEquals("Added task", widget.getTaskTitle("stepsAddedTask"));

        // Starting the task displays its steps, then the next task of the tour.
        widget.clickTask("stepsAddedTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Added task, step one");
        assertEquals("Added task", popover.getTitle());
        assertEquals(2, popover.getStepCount());
        popover.clickNext();
        popover.waitForStep("Added task, step two");
        popover.clickNext();
        popover.waitForStep("Existing task, only step");
        popover.clickDone();
        // The status is saved asynchronously, so wait for it to be saved before leaving the page.
        this.guidedTour.waitForUserTourStatus(TestUtils.SUPER_ADMIN_CREDENTIALS.getUserName(),
            status -> status.getTaskStatus(tourId, "stepsExistingTask").filter(DONE::equals).isPresent());

        // Add a new step, then edit the first step and remove the second one.
        taskEditPage = TaskEditPage.gotoPage(addedTask).addStep("New last step");
        assertEquals(List.of("Added task, step one", "Added task, step two", "New last step"),
            taskEditPage.getStepTitles());
        taskPage = taskEditPage.clickSaveAndView();
        assertEquals(List.of("Added task, step one", "Added task, step two", "New last step"),
            taskPage.getStepTitles());
        taskEditPage = TaskEditPage.gotoPage(addedTask).setStepContent(0, "Edited step one").deleteStep(1);
        assertEquals(List.of("Edited step one", "New last step"), taskEditPage.getStepTitles());
        taskPage = taskEditPage.clickSaveAndView();
        assertEquals(List.of("Edited step one", "New last step"), taskPage.getStepTitles());

        // The task displays the edited steps.
        setup.gotoPage(page);
        widget = new TourWidget().expand().waitUntilTaskIsDisplayed("stepsAddedTask");
        widget.clickTask("stepsAddedTask");
        popover.waitForStep("Edited step one");
        assertEquals(2, popover.getStepCount());
        popover.clickNext();
        popover.waitForStep("New last step");
        assertEquals(2, popover.getCurrentStepNumber());
        // The other task of the tour is already done, so no task is started next.
        popover.clickDone();
        assertFalse(popover.isDisplayedWithin(3), "No task should have been started.");
    }

    /**
     * Deleting a task moves up the next tasks of the tour, removes it from the dependencies of the other tasks, and
     * from the widget.
     */
    @Test
    @Order(4)
    void deleteTask(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Dependencies tour", true);
        DocumentReference firstTask = this.guidedTour.createTask(tour, "dependencyFirstTask", "First task", 0, true);
        this.guidedTour.addSteps(firstTask, Step.withContent("First task, only step"));
        DocumentReference secondTask =
            this.guidedTour.createTask(tour, "dependencySecondTask", "Second task", 1, true, "dependencyFirstTask");
        this.guidedTour.addSteps(secondTask, Step.withContent("Second task, only step"));
        DocumentReference thirdTask =
            this.guidedTour.createTask(tour, "dependencyThirdTask", "Third task", 2, true, "dependencySecondTask");
        this.guidedTour.addSteps(thirdTask, Step.withContent("Third task, only step"));
        String tourId = this.guidedTour.getTourId(tour);

        setup.loginAsSuperAdmin();
        TourPage tourPage = TourPage.gotoPage(tour);
        TourWidget widget = new TourWidget().expand().waitUntilTaskIsDisplayed("dependencyThirdTask");
        assertEquals(List.of("dependencyFirstTask", "dependencySecondTask", "dependencyThirdTask"),
            widget.getVisibleTaskIds(tourId));
        // Make sure the widget doesn't cover the list of tasks.
        widget.collapse();
        assertEquals(List.of("First task", "Second task", "Third task"), tourPage.getTaskTitles());

        tourPage.deleteTask("First task");
        assertEquals(List.of("Second task", "Third task"), tourPage.getTaskTitles());
        assertFalse(this.guidedTour.exists(firstTask));

        // The remaining tasks, as the application reads them.
        Task second = this.guidedTour.getTask(secondTask);
        assertEquals(0, second.getOrder(), second::toString);
        assertEquals(List.of(), second.getDependsOn(), second::toString);

        Task third = this.guidedTour.getTask(thirdTask);
        assertEquals(1, third.getOrder(), third::toString);
        assertEquals(List.of("dependencySecondTask"), third.getDependsOn(), third::toString);

        // The widget doesn't list the deleted task anymore, once reloaded.
        tourPage.reloadPage();
        widget = new TourWidget().expand().waitUntilTaskIsDisplayed("dependencyThirdTask");
        assertEquals(List.of("dependencySecondTask", "dependencyThirdTask"), widget.getVisibleTaskIds(tourId));
    }
}
