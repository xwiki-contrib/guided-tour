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

/**
 * Functional tests for the Tour Application.
 *
 * @version $Id$
 */
@UITest
class GuidedTourApplicationIT extends AbstractGuidedTourIT
{
    private static final String GETTING_STARTED_TOUR_ID = "xwiki:GuidedTour.Tours.GettingStarted.WebHome";

    /**
     * The widget displays the default tour and the active tours with tasks, but not inactive tours, tours without
     * tasks, inactive tasks, or tours and tasks the user isn't allowed to view.
     */
    @Test
    @Order(1)
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

        // A tour with only a task without steps, and a tour with a task with steps and a task without steps.
        // FIXME: A task without steps can't be done, so it shouldn't be displayed, nor a tour whose tasks all have no
        // steps. For now they are displayed. Once this is fixed, these checks will fail: update them to expect the
        // tasks without steps, and the tour with only such a task, to be hidden.
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
            .waitUntilTaskIsDisplayed("visibilityOnlySteplessTask").waitUntilTaskIsDisplayed("visibilitySteplessTask");
        assertThat(widget.getTourIds(),
            containsInAnyOrder(GETTING_STARTED_TOUR_ID, visibleTourId, steplessTourId, mixedTourId));
        assertEquals(List.of("visibilityActiveTask"), widget.getVisibleTaskIds(visibleTourId));
        assertEquals(List.of("visibilityOnlySteplessTask"), widget.getVisibleTaskIds(steplessTourId));
        assertEquals(List.of("visibilityTaskWithSteps", "visibilitySteplessTask"),
            widget.getVisibleTaskIds(mixedTourId));

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

    /**
     * Starting a task, or moving to its next step, should redirect to the page expected by the step, except when
     * leaving a reflex step, which navigates on its own.
     */
    @Test
    @Order(2)
    void redirectToTheExpectedPage(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference pageA = new DocumentReference("PageA", space);
        DocumentReference pageB = new DocumentReference("PageB", space);
        String pageAReference = setup.serializeLocalReference(pageA);
        String pageBReference = setup.serializeLocalReference(pageB);
        this.guidedTour.createPage(pageA, "Page A", "Page A");
        this.guidedTour.createPage(pageB, "[[Go to page A>>doc:%s||id=\"goToPageA\"]]".formatted(pageAReference),
            "Page B");

        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Redirect tour", true);
        DocumentReference task = this.guidedTour.createTask(tour, "redirectTask", "Redirect task", 0, true);
        this.guidedTour.addSteps(task,
            Step.withContent("Start on page A").targetPage(pageAReference),
            // A reflex step without element has a button to move to the next step, like a regular step.
            Step.withContent("Continue on page B").targetPage(pageBReference).queryParameters("foo=bar").reflex(),
            Step.withContent("Click the link").targetPage(pageBReference).element("#goToPageA").reflex(),
            Step.withContent("Back on page A").targetPage(pageAReference));

        this.guidedTour.createUserAndLogin("GuidedTourRedirectUser");
        setup.gotoPage(pageB);

        // Starting the task redirects to the page of its first step.
        new TourWidget().clickTask("redirectTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Start on page A");
        assertEquals(setup.getURL(pageA), new ViewPage().getPageURL());

        // Moving to the next step redirects to its page, with its query parameters.
        popover.clickNext();
        popover.waitForStep("Continue on page B");
        assertEquals(setup.getURL(pageB, "view", "foo=bar"), new ViewPage().getPageURL());

        // The next step is on the same page.
        // FIXME: Leaving a reflex step never redirects, even when it has no element (so it can't navigate on its own),
        // because driverjsMain.moveToAdjacentStep() checks the reflex flag of the step instead of util.isReflex(). So
        // if the next step was on another page, there would be no redirect. Once fixed, make the next step target
        // another page, and check that moving to it redirects there.
        assertTrue(popover.hasNextButton());
        popover.clickNext();
        popover.waitForStep("Click the link");
        assertFalse(popover.hasNextButton());

        // The right arrow key clicks the element of the reflex step, whose link leads to the page of the last step.
        popover.pressNextKeyAndWaitForPageReload();
        popover.waitForStep("Back on page A");
        assertEquals(setup.getURL(pageA), new ViewPage().getPageURL());
    }
}
