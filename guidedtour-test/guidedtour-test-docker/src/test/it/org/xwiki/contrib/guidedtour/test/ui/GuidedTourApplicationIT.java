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
import org.xwiki.contrib.guidedtour.test.po.TourPage;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests for the Tour Application.
 *
 * @version $Id$
 */
@UITest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "", "Visibility");

        DocumentReference visibleTour = this.fixture.createTour(space, "VisibleTour", "Visible tour", true);
        this.fixture.createTask(visibleTour, "visibilityActiveTask", "Active task", 0, true);
        this.fixture.createTask(visibleTour, "visibilityInactiveTask", "Inactive task", 1, false);
        DocumentReference restrictedTask =
            this.fixture.createTask(visibleTour, "visibilityRestrictedTask", "Restricted task", 2, true);
        this.fixture.denyView(restrictedTask, userName);

        DocumentReference inactiveTour = this.fixture.createTour(space, "InactiveTour", "Inactive tour", false);
        this.fixture.createTask(inactiveTour, "visibilityTaskOfInactiveTour", "Task of inactive tour", 0, true);

        this.fixture.createTour(space, "EmptyTour", "Empty tour", true);

        DocumentReference restrictedTour = this.fixture.createTour(space, "RestrictedTour", "Restricted tour", true);
        this.fixture.createTask(restrictedTour, "visibilityTaskOfRestrictedTour", "Task of restricted tour", 0, true);
        this.fixture.denyView(restrictedTour, userName);

        String visibleTourId = this.fixture.getTourId(visibleTour);
        String restrictedTourId = this.fixture.getTourId(restrictedTour);
        this.fixture.createUserAndLogin(userName);
        setup.gotoPage(page);

        TourWidget widget = new TourWidget().waitUntilTaskIsDisplayed("visibilityActiveTask");
        assertThat(widget.getTourIds(), containsInAnyOrder(GETTING_STARTED_TOUR_ID, visibleTourId));
        assertEquals(List.of("visibilityActiveTask"), widget.getVisibleTaskIds(visibleTourId));

        // A user allowed to view the restricted tour and task sees them, but still not the inactive ones.
        setup.loginAsSuperAdmin();
        setup.gotoPage(page);
        widget = new TourWidget().waitUntilTaskIsDisplayed("visibilityRestrictedTask")
            .waitUntilTaskIsDisplayed("visibilityTaskOfRestrictedTour");
        assertEquals(List.of("visibilityActiveTask", "visibilityRestrictedTask"),
            widget.getVisibleTaskIds(visibleTourId));
        assertThat(widget.getTourIds(), hasItem(restrictedTourId));
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
        this.fixture.createPage(pageA, "Page A", "Page A");
        this.fixture.createPage(pageB, "[[Go to page A>>doc:%s||id=\"goToPageA\"]]".formatted(pageAReference),
            "Page B");

        DocumentReference tour = this.fixture.createTour(space, "Tour", "Redirect tour", true);
        DocumentReference task = this.fixture.createTask(tour, "redirectTask", "Redirect task", 0, true);
        this.fixture.addSteps(task,
            Step.withContent("Start on page A").targetPage(pageAReference),
            // A reflex step without element behaves like a regular step.
            Step.withContent("Continue on page B").targetPage(pageBReference).queryParameters("foo=bar").reflex(),
            Step.withContent("Click the link").targetPage(pageBReference).element("#goToPageA").reflex(),
            Step.withContent("Back on page A").targetPage(pageAReference));

        this.fixture.createUserAndLogin("GuidedTourRedirectUser");
        setup.gotoPage(pageB);

        // Starting the task redirects to the page of its first step.
        new TourWidget().clickTask("redirectTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Start on page A");
        assertEquals(setup.getURL(pageA), new ViewPage().getPageURL());

        // Moving to the next step redirects to its page, with its query parameters.
        popover.clickNext();
        popover.waitForStep("Continue on page B");
        assertEquals(setup.getURL(pageB, "view", "foo=bar"), new ViewPage().getPageURL());

        // The next step is on the same page, so there's no redirect.
        assertTrue(popover.hasNextButton());
        popover.clickNext();
        popover.waitForStep("Click the link");
        assertFalse(popover.hasNextButton());

        // The right arrow key clicks the element of the reflex step, whose link leads to the page of the last step.
        popover.pressNextKeyAndWaitForPageReload();
        popover.waitForStep("Back on page A");
        assertEquals(setup.getURL(pageA), new ViewPage().getPageURL());
    }

    /**
     * Deleting a task moves up the next tasks of the tour, removes it from the dependencies of the other tasks, and
     * from the widget.
     */
    @Test
    @Order(3)
    void deleteTask(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Dependencies tour", true);
        DocumentReference firstTask = this.fixture.createTask(tour, "dependencyFirstTask", "First task", 0, true);
        this.fixture.addSteps(firstTask, Step.withContent("First task, only step"));
        DocumentReference secondTask =
            this.fixture.createTask(tour, "dependencySecondTask", "Second task", 1, true, "dependencyFirstTask");
        this.fixture.addSteps(secondTask, Step.withContent("Second task, only step"));
        DocumentReference thirdTask =
            this.fixture.createTask(tour, "dependencyThirdTask", "Third task", 2, true, "dependencySecondTask");
        String tourId = this.fixture.getTourId(tour);

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
        assertFalse(this.fixture.exists(firstTask));

        // The remaining tasks, as the application reads them.
        Task second = this.fixture.getTask(secondTask);
        assertEquals(0, second.getOrder(), second::toString);
        assertEquals(List.of(), second.getDependsOn(), second::toString);

        Task third = this.fixture.getTask(thirdTask);
        assertEquals(1, third.getOrder(), third::toString);
        assertEquals(List.of("dependencySecondTask"), third.getDependsOn(), third::toString);

        // The widget doesn't list the deleted task anymore, once reloaded.
        tourPage.reloadPage();
        widget = new TourWidget().expand().waitUntilTaskIsDisplayed("dependencyThirdTask");
        assertEquals(List.of("dependencySecondTask", "dependencyThirdTask"), widget.getVisibleTaskIds(tourId));
    }
}
