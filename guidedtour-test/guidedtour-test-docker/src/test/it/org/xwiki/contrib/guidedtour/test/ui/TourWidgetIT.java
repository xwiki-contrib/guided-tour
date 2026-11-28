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
import java.util.Optional;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourTestUtils;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourTestUtils.Step;
import org.xwiki.contrib.guidedtour.test.po.TourStepPopover;
import org.xwiki.contrib.guidedtour.test.po.TourWidget;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ViewPage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.DONE;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.SKIPPED;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.TODO;

/**
 * Verify which tours and tasks the widget displays, its actions on them, the progress, and how the user status is
 * saved.
 *
 * @version $Id$
 */
@UITest
class TourWidgetIT extends AbstractGuidedTourIT
{
    private static final float PROGRESS_DELTA = 0.0001f;

    private static final String GETTING_STARTED_TOUR_ID = "xwiki:GuidedTour.Tours.GettingStarted.WebHome";

    /**
     * Tasks and tours can be skipped and reset from the widget, which updates the tour status and the progress.
     * Skipping a tour keeps its done tasks, while resetting a tour resets all its tasks.
     */
    @Test
    @Order(1)
    void skipAndResetTasksAndTours(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "Content", "Widget");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Widget tour", true);
        DocumentReference firstTask = this.guidedTour.createTask(tour, "widgetFirstTask", "First task", 0, true);
        this.guidedTour.addSteps(firstTask, Step.withContent("First task, only step"));
        DocumentReference secondTask = this.guidedTour.createTask(tour, "widgetSecondTask", "Second task", 1, true);
        // Closing a task at its last step completes it, so the second task needs two steps to be left before its end.
        this.guidedTour.addSteps(secondTask,
            Step.withContent("Second task, step one"),
            Step.withContent("Second task, step two"));
        String tourId = this.guidedTour.getTourId(tour);

        String userName = this.guidedTour.createUserAndLogin("GuidedTourWidgetUser");
        setup.gotoPage(page);

        TourWidget widget = new TourWidget().waitUntilTaskIsDisplayed("widgetSecondTask");
        assertEquals(TODO, widget.getTourStatus(tourId));
        float initialProgress = widget.getProgress();

        // Skip and reset a task.
        widget.skipTask("widgetFirstTask");
        assertTrue(widget.getProgress() > initialProgress);
        assertEquals(TODO, widget.getTourStatus(tourId));
        widget.resetTask("widgetFirstTask");
        assertEquals(initialProgress, widget.getProgress(), PROGRESS_DELTA);

        // Skip and reset a tour.
        widget.skipTour(tourId);
        assertEquals(SKIPPED, widget.getTaskStatus("widgetFirstTask"));
        assertEquals(SKIPPED, widget.getTaskStatus("widgetSecondTask"));
        widget.resetTour(tourId);
        assertEquals(TODO, widget.getTaskStatus("widgetFirstTask"));
        assertEquals(TODO, widget.getTaskStatus("widgetSecondTask"));
        assertEquals(initialProgress, widget.getProgress(), PROGRESS_DELTA);

        // Skipping a tour only skips the tasks left to do, not the done ones.
        widget.clickTask("widgetFirstTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("First task, only step");
        popover.clickNext();
        widget.waitUntilTaskStatus("widgetFirstTask", DONE);
        // The second task starts automatically: leave it before its end, so that it stays to do.
        popover.waitForStep("Second task, step one");
        popover.pressEscape();
        popover.confirmBrowserAlert();
        // Wait for the leave to be handled (the next tasks aren't started automatically anymore) before checking the
        // status.
        this.guidedTour.waitForUserTourStatus(userName, status -> !status.isCallToActionEnabled(tourId));
        assertEquals(TODO, widget.getTaskStatus("widgetSecondTask"));
        widget.skipTour(tourId);
        assertEquals(SKIPPED, widget.getTaskStatus("widgetSecondTask"));
        assertEquals(DONE, widget.getTaskStatus("widgetFirstTask"));

        // Resetting the tour resets all its tasks, including the done ones.
        widget.resetTour(tourId);
        assertEquals(TODO, widget.getTaskStatus("widgetFirstTask"));
        assertEquals(TODO, widget.getTaskStatus("widgetSecondTask"));
    }

    /**
     * The progress of a user and the state of the widget are saved in the user profile, not in the browser, so each
     * user has their own.
     */
    @Test
    @Order(2)
    void userStatusIsSavedInTheProfile(TestUtils setup, TestReference testReference) throws Exception
    {
        String userName = "GuidedTourPersistenceUser";
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "Content", "Saved");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Persistence tour", true);
        DocumentReference task = this.guidedTour.createTask(tour, "persistenceTask", "Persistence task", 0, true);
        this.guidedTour.addSteps(task, Step.withContent("Only step"));
        String tourId = this.guidedTour.getTourId(tour);

        this.guidedTour.createUserAndLogin(userName);
        setup.gotoPage(page);

        TourWidget widget = new TourWidget();
        widget.clickTask("persistenceTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Only step");
        popover.clickDone();
        widget.waitUntilTaskStatus("persistenceTask", DONE);
        widget.collapse();

        this.guidedTour.waitForUserTourStatus(userName, status -> "COLLAPSED".equals(status.getWidgetState())
            && status.getTaskStatus(tourId, "persistenceTask").filter(DONE::equals).isPresent());

        // The status is loaded from the user profile, even without any data stored in the browser. The widget starts
        // collapsed until the saved state is loaded, so the state is checked both collapsed and expanded.
        this.guidedTour.clearBrowserStorage();
        new ViewPage().reloadPage();
        widget = new TourWidget();
        assertTrue(widget.isWidgetCollapsed(), "The collapsed state of the widget wasn't restored.");
        widget.expand().waitUntilTaskStatus("persistenceTask", DONE);
        this.guidedTour.waitForUserTourStatus(userName, status -> "OPEN".equals(status.getWidgetState()));
        this.guidedTour.clearBrowserStorage();
        new ViewPage().reloadPage();
        assertFalse(new TourWidget().isWidgetCollapsed(), "The expanded state of the widget wasn't restored.");

        // Another user has their own status.
        String otherUserName = "GuidedTourOtherPersistenceUser";
        this.guidedTour.createUserAndLogin(otherUserName);
        setup.gotoPage(page);
        widget = new TourWidget().waitUntilTaskStatus("persistenceTask", TODO);
        widget.skipTask("persistenceTask");
        this.guidedTour.waitForUserTourStatus(otherUserName,
            status -> status.getTaskStatus(tourId, "persistenceTask").filter(SKIPPED::equals).isPresent());

        // The status of the first user didn't change.
        setup.login(userName, GuidedTourTestUtils.PASSWORD);
        setup.gotoPage(page);
        new TourWidget().waitUntilTaskStatus("persistenceTask", DONE);
        assertEquals(Optional.of(DONE), this.guidedTour.getUserTourStatus(userName)
            .flatMap(status -> status.getTaskStatus(tourId, "persistenceTask")));
    }

    /**
     * Guests don't have a profile, so their progress and the state of their widget are saved in the browser.
     */
    @Test
    @Order(3)
    void guestStatusIsSavedInTheBrowser(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "Content", "Guest");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Guest tour", true);
        DocumentReference task = this.guidedTour.createTask(tour, "guestTask", "Guest task", 0, true);
        this.guidedTour.addSteps(task, Step.withContent("Only step"));

        setup.forceGuestUser();
        setup.gotoPage(page);

        TourWidget widget = new TourWidget().waitUntilTaskIsDisplayed("guestTask");
        widget.skipTask("guestTask");
        String tourId = this.guidedTour.getTourId(tour);
        this.guidedTour.waitForGuestStatusInBrowser(
            status -> status.getTaskStatus(tourId, "guestTask").filter(SKIPPED::equals).isPresent());

        new ViewPage().reloadPage();
        widget = new TourWidget().waitUntilTaskStatus("guestTask", SKIPPED);

        // The widget starts collapsed until the saved state is loaded, so the state is checked both collapsed and
        // expanded.
        widget.collapse();
        this.guidedTour.waitForGuestStatusInBrowser(status -> "COLLAPSED".equals(status.getWidgetState()));
        new ViewPage().reloadPage();
        widget = new TourWidget();
        assertTrue(widget.isWidgetCollapsed(), "The collapsed state of the widget wasn't restored.");
        widget.expand();
        this.guidedTour.waitForGuestStatusInBrowser(status -> "OPEN".equals(status.getWidgetState()));
        new ViewPage().reloadPage();
        assertFalse(new TourWidget().isWidgetCollapsed(), "The expanded state of the widget wasn't restored.");

        // Clearing the browser storage resets the progress of the guest.
        this.guidedTour.clearBrowserStorage();
        new ViewPage().reloadPage();
        new TourWidget().waitUntilTaskStatus("guestTask", TODO);
    }

    /**
     * The widget displays the default tour and the active tours with tasks, but not inactive tours, tours without
     * tasks, inactive tasks, tasks without steps, tours whose tasks all have no steps, or tours and tasks the user isn't
     * allowed to view.
     */
    @Test
    @Order(4)
    void inactiveEmptyAndRestrictedToursAndTasks(TestUtils setup, TestReference testReference) throws Exception
    {
        String userName = "GuidedTourVisibilityUser";
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "", "Visibility");

        // Every task has a step, so that only its visibility decides whether it's displayed.
        DocumentReference visibleTour = this.guidedTour.createTour(space, "VisibleTour", "Visible tour", true);
        DocumentReference activeTask =
            this.guidedTour.createTask(visibleTour, "visibilityActiveTask", "Active task", 0, true);
        this.guidedTour.addSteps(activeTask, Step.withContent("Active task, only step"));
        DocumentReference inactiveTask =
            this.guidedTour.createTask(visibleTour, "visibilityInactiveTask", "Inactive task", 1, false);
        this.guidedTour.addSteps(inactiveTask, Step.withContent("Inactive task, only step"));
        DocumentReference restrictedTask =
            this.guidedTour.createTask(visibleTour, "visibilityRestrictedTask", "Restricted task", 2, true);
        this.guidedTour.addSteps(restrictedTask, Step.withContent("Restricted task, only step"));
        this.guidedTour.denyView(restrictedTask, userName);

        DocumentReference inactiveTour = this.guidedTour.createTour(space, "InactiveTour", "Inactive tour", false);
        DocumentReference taskOfInactiveTour =
            this.guidedTour.createTask(inactiveTour, "visibilityTaskOfInactiveTour", "Task of inactive tour", 0, true);
        this.guidedTour.addSteps(taskOfInactiveTour, Step.withContent("Task of inactive tour, only step"));

        DocumentReference emptyTour = this.guidedTour.createTour(space, "EmptyTour", "Empty tour", true);

        DocumentReference restrictedTour = this.guidedTour.createTour(space, "RestrictedTour", "Restricted tour", true);
        DocumentReference taskOfRestrictedTour = this.guidedTour.createTask(restrictedTour,
            "visibilityTaskOfRestrictedTour", "Task of restricted tour", 0, true);
        this.guidedTour.addSteps(taskOfRestrictedTour, Step.withContent("Task of restricted tour, only step"));
        this.guidedTour.denyView(restrictedTour, userName);

        // A tour with only a task without steps, and a tour with a task with steps and a task without steps. A task
        // without steps can't be done, so it's not displayed, nor a tour whose tasks all have no steps.
        DocumentReference steplessTour = this.guidedTour.createTour(space, "SteplessTour", "Stepless tour", true);
        this.guidedTour.createTask(steplessTour, "visibilityOnlySteplessTask", "Only stepless task", 0, true);
        DocumentReference mixedTour = this.guidedTour.createTour(space, "MixedTour", "Mixed tour", true);
        DocumentReference taskWithSteps =
            this.guidedTour.createTask(mixedTour, "visibilityTaskWithSteps", "Task with steps", 0, true);
        this.guidedTour.addSteps(taskWithSteps, Step.withContent("Task with steps, only step"));
        this.guidedTour.createTask(mixedTour, "visibilitySteplessTask", "Stepless task", 1, true);

        String visibleTourId = this.guidedTour.getTourId(visibleTour);
        String restrictedTourId = this.guidedTour.getTourId(restrictedTour);
        String steplessTourId = this.guidedTour.getTourId(steplessTour);
        String mixedTourId = this.guidedTour.getTourId(mixedTour);
        this.guidedTour.createUserAndLogin(userName);
        setup.gotoPage(page);

        TourWidget widget = new TourWidget().waitUntilTaskIsDisplayed("visibilityActiveTask")
            .waitUntilTaskIsDisplayed("visibilityTaskWithSteps");
        assertThat(widget.getTourIds(), containsInAnyOrder(GETTING_STARTED_TOUR_ID, visibleTourId, mixedTourId));
        assertEquals(List.of("visibilityActiveTask"), widget.getVisibleTaskIds(visibleTourId));
        assertEquals(List.of(), widget.getVisibleTaskIds(steplessTourId));
        assertEquals(List.of("visibilityTaskWithSteps"), widget.getVisibleTaskIds(mixedTourId));

        // A user allowed to view the restricted tour and task sees them, but still not the inactive or empty ones.
        setup.loginAsSuperAdmin();
        setup.gotoPage(page);
        // The state of the widget is saved in the profile of superadmin, which other tests collapse.
        widget = new TourWidget().expand().waitUntilTaskIsDisplayed("visibilityRestrictedTask")
            .waitUntilTaskIsDisplayed("visibilityTaskOfRestrictedTour");
        assertEquals(List.of("visibilityActiveTask", "visibilityRestrictedTask"),
            widget.getVisibleTaskIds(visibleTourId));
        assertThat(widget.getTourIds(), hasItem(restrictedTourId));
        assertThat(widget.getTourIds(), not(hasItem(this.guidedTour.getTourId(inactiveTour))));
        assertThat(widget.getTourIds(), not(hasItem(this.guidedTour.getTourId(emptyTour))));
    }
}
