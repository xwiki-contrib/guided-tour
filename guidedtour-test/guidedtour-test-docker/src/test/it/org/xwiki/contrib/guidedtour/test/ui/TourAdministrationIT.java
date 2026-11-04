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

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourFixture.Step;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourFixture.Task;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourFixture.Tour;
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

/**
 * Verify the administration of the guided tours: the list of tours, the tour pages and the task pages, and that the
 * changes made there are taken into account by the widget and the tasks.
 *
 * @version $Id$
 */
@UITest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
        DocumentReference activeTour = this.fixture.createTour(space, "ActiveTour", "Listed active tour", true);
        this.fixture.createTask(activeTour, "listedActiveTask", "Listed task", 0, true);
        DocumentReference inactiveTour = this.fixture.createTour(space, "InactiveTour", "Listed inactive tour", false);

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
        assertFalse(this.fixture.exists(inactiveTour));
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
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "Content", "Tour");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Editable tour", true);
        DocumentReference firstTask = this.fixture.createTask(tour, "editableFirstTask", "First task", 0, true);
        this.fixture.addSteps(firstTask, Step.withContent("First task, only step"));
        DocumentReference secondTask = this.fixture.createTask(tour, "editableSecondTask", "Second task", 1, true);
        this.fixture.addSteps(secondTask, Step.withContent("Second task, only step"));
        String tourId = this.fixture.getTourId(tour);

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
        Tour savedTour = this.fixture.getTour(tour);
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
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "Content", "Task");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Steps tour", true);
        DocumentReference existingTask =
            this.fixture.createTask(tour, "stepsExistingTask", "Existing task", 5, true);
        this.fixture.addSteps(existingTask, Step.withContent("Existing task, only step"));
        DocumentReference addedTask = new DocumentReference("stepsAddedTask", tour.getLastSpaceReference());
        String tourId = this.fixture.getTourId(tour);

        setup.loginAsSuperAdmin();

        // Add a task from the tour page. It's created inactive, so that it can be completed before being displayed.
        TaskPage taskPage = TourEditPage.gotoPage(tour).addTask("stepsAddedTask");
        this.fixture.deleteAfterTest(addedTask);
        assertEquals(setup.getURL(addedTask), taskPage.getPageURL());
        assertFalse(this.fixture.getTask(addedTask).isActive());

        // Edit its properties and add a step.
        TaskEditPage taskEditPage = TaskEditPage.gotoPage(addedTask);
        taskEditPage.setTaskTitle("Added task").setOrder(1).setActive(true)
            .addStep("Added task, step one");
        // FIXME: Add the second step from the UI, with .addStep("Added task, step two"), once the task sheet can add
        // more than one step without programming rights. The sheet sets the number of a new step object with
        // $step.getXWikiObject().setNumber(), which requires programming rights: without them, every new step form
        // is rendered for the object 0, so only a first step can be added. Meanwhile, the step is added after saving.
        assertEquals(List.of("Added task, step one"), taskEditPage.getStepTitles());
        taskPage = taskEditPage.clickSaveAndView();
        assertEquals(List.of("Added task, step one"), taskPage.getStepTitles());
        this.fixture.addSteps(addedTask, 1, Step.withContent("Added task, step two"));
        taskPage = TaskPage.gotoPage(addedTask);
        assertEquals(List.of("Added task, step one", "Added task, step two"), taskPage.getStepTitles());
        Task savedTask = this.fixture.getTask(addedTask);
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
        popover.clickNext();
        popover.waitUntilClosed();

        // Edit the first step, remove the second one, and add a new one.
        taskEditPage = TaskEditPage.gotoPage(addedTask);
        assertEquals(List.of("Added task, step one", "Added task, step two"), taskEditPage.getStepTitles());
        taskEditPage.setStepContent(0, "Edited step one").deleteStep(1);
        // FIXME: Add the new step from the UI, with .addStep("New last step"), once the task sheet can add a step to a
        // task that already has some without programming rights (see above). Meanwhile, the step is added after saving.
        assertEquals(List.of("Edited step one"), taskEditPage.getStepTitles());
        taskPage = taskEditPage.clickSaveAndView();
        assertEquals(List.of("Edited step one"), taskPage.getStepTitles());
        this.fixture.addSteps(addedTask, 1, Step.withContent("New last step"));
        taskPage = TaskPage.gotoPage(addedTask);
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
        // Leave the task before its end, so that the next task isn't started.
        popover.clickPrevious().waitForStep("Edited step one");
        popover.pressEscape();
        popover.waitUntilClosed();
    }
}
