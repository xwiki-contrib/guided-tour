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

import java.util.Optional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourFixture;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourFixture.Step;
import org.xwiki.contrib.guidedtour.test.po.TourStepPopover;
import org.xwiki.contrib.guidedtour.test.po.TourWidget;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.DONE;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.SKIPPED;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.TODO;

/**
 * Verify the actions of the widget on tasks and tours, the progress, and how the user status is saved.
 *
 * @version $Id$
 */
@UITest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TourWidgetIT extends AbstractGuidedTourIT
{
    private static final float PROGRESS_DELTA = 0.0001f;

    /**
     * Tasks and tours can be skipped and reset from the widget, which updates the tour status and the progress.
     * Skipping a tour keeps its done tasks, while resetting a tour resets all its tasks.
     */
    @Test
    @Order(1)
    void skipAndResetTasksAndTours(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "Content", "Widget");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Widget tour", true);
        DocumentReference firstTask = this.fixture.createTask(tour, "widgetFirstTask", "First task", 0, true);
        this.fixture.addSteps(firstTask, Step.withContent("First task, only step"));
        DocumentReference secondTask = this.fixture.createTask(tour, "widgetSecondTask", "Second task", 1, true);
        // Closing a task at its last step completes it, so the second task needs two steps to be left before its end.
        this.fixture.addSteps(secondTask,
            Step.withContent("Second task, step one"),
            Step.withContent("Second task, step two"));
        String tourId = this.fixture.getTourId(tour);

        this.fixture.createUserAndLogin("GuidedTourWidgetUser");
        setup.gotoPage(page);

        TourWidget widget = new TourWidget().waitUntilTaskIsDisplayed("widgetSecondTask");
        assertEquals(TODO, widget.getTourStatus(tourId));
        float initialProgress = widget.getProgress();

        // Skip and reset a task.
        widget.skipTask("widgetFirstTask");
        widget.waitUntilTaskStatus("widgetFirstTask", SKIPPED);
        assertTrue(widget.getProgress() > initialProgress);
        assertEquals(TODO, widget.getTourStatus(tourId));
        widget.resetTask("widgetFirstTask");
        widget.waitUntilTaskStatus("widgetFirstTask", TODO);
        assertEquals(initialProgress, widget.getProgress(), PROGRESS_DELTA);

        // Skip and reset a tour.
        widget.skipTour(tourId);
        widget.waitUntilTaskStatus("widgetFirstTask", SKIPPED)
            .waitUntilTaskStatus("widgetSecondTask", SKIPPED)
            .waitUntilTourStatus(tourId, SKIPPED);
        widget.resetTour(tourId);
        widget.waitUntilTaskStatus("widgetFirstTask", TODO)
            .waitUntilTaskStatus("widgetSecondTask", TODO)
            .waitUntilTourStatus(tourId, TODO);
        assertEquals(initialProgress, widget.getProgress(), PROGRESS_DELTA);

        // Skipping a tour only skips the tasks left to do, not the done ones.
        widget.clickTask("widgetFirstTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("First task, only step");
        popover.clickNext();
        widget.waitUntilTaskStatus("widgetFirstTask", DONE);
        // The second task starts automatically: leave it before its end, so that it stays to do.
        popover.waitForStep("Second task, step one");
        popover.pressEscape();
        popover.waitUntilClosed();
        widget.waitUntilTaskStatus("widgetSecondTask", TODO);
        widget.skipTour(tourId);
        widget.waitUntilTaskStatus("widgetSecondTask", SKIPPED).waitUntilTourStatus(tourId, SKIPPED);
        assertEquals(DONE, widget.getTaskStatus("widgetFirstTask"));

        // Resetting the tour resets all its tasks, including the done ones.
        widget.resetTour(tourId);
        widget.waitUntilTaskStatus("widgetFirstTask", TODO)
            .waitUntilTaskStatus("widgetSecondTask", TODO)
            .waitUntilTourStatus(tourId, TODO);

        // Resetting the tour starts automatically its next tasks again (they were stopped by leaving the second task),
        // so completing the first task starts the second.
        widget.clickTask("widgetFirstTask");
        popover.waitForStep("First task, only step");
        popover.clickNext();
        popover.waitForStep("Second task, step one");
        popover.clickNext();
        popover.waitForStep("Second task, step two");
        popover.clickNext();
        popover.waitUntilClosed();
        widget.waitUntilTaskStatus("widgetSecondTask", DONE).waitUntilTourStatus(tourId, DONE);
        assertEquals(DONE, widget.getTaskStatus("widgetFirstTask"));
        assertTrue(widget.getProgress() > initialProgress);
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
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "Content", "Saved");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Persistence tour", true);
        DocumentReference task = this.fixture.createTask(tour, "persistenceTask", "Persistence task", 0, true);
        this.fixture.addSteps(task, Step.withContent("Only step"));
        String tourId = this.fixture.getTourId(tour);

        this.fixture.createUserAndLogin(userName);
        setup.gotoPage(page);

        TourWidget widget = new TourWidget();
        widget.clickTask("persistenceTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Only step");
        popover.clickNext();
        popover.waitUntilClosed();
        widget.waitUntilTaskStatus("persistenceTask", DONE);
        widget.collapse();

        this.fixture.waitForUserTourStatus(userName, status -> "COLLAPSED".equals(status.getWidgetState())
            && status.getTaskStatus(tourId, "persistenceTask").filter(DONE::equals).isPresent());

        // The status is loaded from the user profile, even without any data stored in the browser. The widget is
        // expanded by default, so it stays collapsed only if the saved state is restored.
        this.fixture.clearBrowserStorage();
        widget.reloadPage();
        widget = new TourWidget();
        assertFalse(widget.isExpandedWithin(3), "The collapsed state of the widget wasn't restored.");
        widget.expand().waitUntilTaskStatus("persistenceTask", DONE);
        this.fixture.waitForUserTourStatus(userName, status -> "OPEN".equals(status.getWidgetState()));

        // Another user has their own status.
        String otherUserName = "GuidedTourOtherPersistenceUser";
        this.fixture.createUserAndLogin(otherUserName);
        setup.gotoPage(page);
        widget = new TourWidget().waitUntilTaskStatus("persistenceTask", TODO);
        widget.skipTask("persistenceTask");
        widget.waitUntilTaskStatus("persistenceTask", SKIPPED);
        this.fixture.waitForUserTourStatus(otherUserName,
            status -> status.getTaskStatus(tourId, "persistenceTask").filter(SKIPPED::equals).isPresent());

        // The status of the first user didn't change.
        setup.login(userName, GuidedTourFixture.PASSWORD);
        setup.gotoPage(page);
        new TourWidget().waitUntilTaskStatus("persistenceTask", DONE);
        assertEquals(Optional.of(DONE),
            this.fixture.getUserTourStatus(userName).flatMap(status -> status.getTaskStatus(tourId, "persistenceTask")));
    }

    /**
     * Guests don't have a profile, so their progress and the state of their widget are saved in the browser.
     */
    @Test
    @Order(3)
    void guestStatusIsSavedInTheBrowser(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "Content", "Guest");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Guest tour", true);
        DocumentReference task = this.fixture.createTask(tour, "guestTask", "Guest task", 0, true);
        this.fixture.addSteps(task, Step.withContent("Only step"));

        setup.forceGuestUser();
        setup.gotoPage(page);

        TourWidget widget = new TourWidget().waitUntilTaskIsDisplayed("guestTask");
        widget.skipTask("guestTask");
        widget.waitUntilTaskStatus("guestTask", SKIPPED);
        this.fixture.waitForGuestStatusInBrowser();

        widget.reloadPage();
        widget = new TourWidget().waitUntilTaskStatus("guestTask", SKIPPED);

        // The widget is expanded by default, so it stays collapsed only if the saved state is restored.
        widget.collapse();
        this.fixture.waitForGuestStatusInBrowser(status -> "COLLAPSED".equals(status.getWidgetState()));
        widget.reloadPage();
        widget = new TourWidget();
        assertFalse(widget.isExpandedWithin(3), "The collapsed state of the widget wasn't restored.");
        widget.expand();
        this.fixture.waitForGuestStatusInBrowser(status -> "OPEN".equals(status.getWidgetState()));

        // Clearing the browser storage resets the progress of the guest.
        this.fixture.clearBrowserStorage();
        widget.reloadPage();
        new TourWidget().waitUntilTaskStatus("guestTask", TODO);
    }
}
