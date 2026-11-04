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
import org.xwiki.contrib.guidedtour.test.po.TourStepPopover;
import org.xwiki.contrib.guidedtour.test.po.TourWidget;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.editor.WikiEditPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.DONE;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.SKIPPED;
import static org.xwiki.contrib.guidedtour.test.po.TourTaskStatus.TODO;

/**
 * Verify the progression through the steps of a task, including the interactive (reflex) steps, and from a task to
 * the next tasks of its tour.
 *
 * @version $Id$
 */
@UITest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskProgressionIT extends AbstractGuidedTourIT
{
    /**
     * The user can move forward and backward between the steps of a task, and an interrupted task is resumed at the
     * same step after a page reload.
     */
    @Test
    @Order(1)
    void navigateBetweenStepsAndResumeAfterReload(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "Content", "Steps");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Navigation tour", true);
        DocumentReference task = this.fixture.createTask(tour, "navigationTask", "Navigation task", 0, true);
        this.fixture.addSteps(task,
            Step.withContent("Step one"),
            Step.withContent("Step two").element("#document-title"),
            Step.withContent("Step three"));

        this.fixture.createUserAndLogin("GuidedTourNavigationUser");
        setup.gotoPage(page);

        new TourWidget().clickTask("navigationTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Step one");
        // Steps without their own title display the title of their task.
        assertEquals("Navigation task", popover.getTitle());
        assertEquals(1, popover.getCurrentStepNumber());
        assertEquals(3, popover.getStepCount());
        // A step without element is displayed in the middle of the screen.
        assertEquals(TourStepPopover.NO_ELEMENT_ID, popover.getHighlightedElementId());

        popover.clickNext();
        popover.waitForStep("Step two");
        assertEquals(2, popover.getCurrentStepNumber());
        assertEquals("document-title", popover.getHighlightedElementId());

        // The active task is resumed at the current step after a page reload.
        new ViewPage().reloadPage();
        popover = new TourStepPopover().waitForStep("Step two");
        assertEquals(2, popover.getCurrentStepNumber());

        assertTrue(popover.hasPreviousButton());
        popover.clickPrevious().waitForStep("Step one");
        assertEquals(1, popover.getCurrentStepNumber());

        // Leaving the task before its last step keeps it to do.
        popover.pressEscape();
        popover.waitUntilClosed();
        new TourWidget().waitUntilTaskStatus("navigationTask", TODO);
    }

    /**
     * Completing a task starts automatically the next active task of its tour, and the tour is done once all its
     * active tasks are done.
     */
    @Test
    @Order(2)
    void completingATaskStartsTheNextActiveTask(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "Content", "Tasks");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Progression tour", true);
        DocumentReference firstTask = this.fixture.createTask(tour, "progressionFirstTask", "First task", 0, true);
        this.fixture.addSteps(firstTask,
            Step.withContent("First task, step one"),
            Step.withContent("First task, step two"));
        DocumentReference inactiveTask =
            this.fixture.createTask(tour, "progressionInactiveTask", "Inactive task", 1, false);
        this.fixture.addSteps(inactiveTask, Step.withContent("Inactive task, only step"));
        DocumentReference lastTask = this.fixture.createTask(tour, "progressionLastTask", "Last task", 2, true);
        this.fixture.addSteps(lastTask, Step.withContent("Last task, only step"));
        String tourId = this.fixture.getTourId(tour);

        this.fixture.createUserAndLogin("GuidedTourProgressionUser");
        setup.gotoPage(page);

        TourWidget widget = new TourWidget().waitUntilTaskIsDisplayed("progressionLastTask");
        assertEquals(List.of("progressionFirstTask", "progressionLastTask"), widget.getVisibleTaskIds(tourId));

        // Completing the last step completes the task.
        widget.clickTask("progressionFirstTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("First task, step one");
        popover.clickNext();
        popover.waitForStep("First task, step two");
        popover.clickNext();
        widget.waitUntilTaskStatus("progressionFirstTask", DONE);
        assertEquals(TODO, widget.getTourStatus(tourId));

        // The next active task starts automatically, skipping the inactive one.
        popover.waitForStep("Last task, only step");
        assertEquals("Last task", popover.getTitle());
        assertEquals(1, popover.getStepCount());
        popover.clickNext();
        popover.waitUntilClosed();

        widget.waitUntilTaskStatus("progressionLastTask", DONE);
        // The inactive task isn't taken into account.
        widget.waitUntilTourStatus(tourId, DONE);
    }

    /**
     * Closing a task before its end, or skipping it (with the "Skip All" link of the popover, or from the widget), stops
     * starting automatically the next tasks of its tour, but not of the other tours. A closed task stays to do, a
     * skipped task is skipped.
     */
    @Test
    @Order(3)
    void closingOrSkippingATaskStopsStartingTheNextTasks(TestUtils setup, TestReference testReference)
        throws Exception
    {
        String userName = "GuidedTourCloseUser";
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "Content", "Close");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Close tour", true);
        DocumentReference firstTask = this.fixture.createTask(tour, "closeFirstTask", "First task", 0, true);
        this.fixture.addSteps(firstTask,
            Step.withContent("First task, step one"),
            Step.withContent("First task, step two"));
        DocumentReference secondTask = this.fixture.createTask(tour, "closeSecondTask", "Second task", 1, true);
        this.fixture.addSteps(secondTask, Step.withContent("Second task, only step"));
        String tourId = this.fixture.getTourId(tour);

        DocumentReference otherTour = this.fixture.createTour(space, "OtherTour", "Other close tour", true);
        DocumentReference otherFirstTask =
            this.fixture.createTask(otherTour, "closeOtherFirstTask", "Other first task", 0, true);
        this.fixture.addSteps(otherFirstTask, Step.withContent("Other first task, only step"));
        DocumentReference otherSecondTask =
            this.fixture.createTask(otherTour, "closeOtherSecondTask", "Other second task", 1, true);
        this.fixture.addSteps(otherSecondTask, Step.withContent("Other second task, only step"));
        String otherTourId = this.fixture.getTourId(otherTour);

        this.fixture.createUserAndLogin(userName);
        setup.gotoPage(page);

        TourWidget widget = new TourWidget();
        widget.clickTask("closeFirstTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("First task, step one");
        popover.close();
        popover.waitUntilClosed();

        // Once the close is handled (the next tasks aren't started automatically anymore), the task is still to do.
        // The other tour isn't affected.
        assertTrue(this.fixture.waitForUserTourStatus(userName, status -> !status.isCallToActionEnabled(tourId))
            .isCallToActionEnabled(otherTourId));
        widget.waitUntilTaskStatus("closeFirstTask", TODO);

        // Completing the task afterwards doesn't start the next one.
        widget.clickTask("closeFirstTask");
        popover.waitForStep("First task, step one");
        popover.clickNext();
        popover.waitForStep("First task, step two");
        popover.clickNext();
        widget.waitUntilTaskStatus("closeFirstTask", DONE);
        // The next task would be started right after the status is saved, so wait for it to be saved before checking.
        this.fixture.waitForUserTourStatus(userName,
            status -> status.getTaskStatus(tourId, "closeFirstTask").filter(DONE::equals).isPresent());
        assertFalse(popover.isDisplayedWithin(3), "The next task shouldn't have been started.");
        assertEquals(TODO, widget.getTaskStatus("closeSecondTask"));

        // Resetting the tour starts automatically its next tasks again, until the "Skip All" link skips the task. A tour
        // can be reset only once none of its tasks is left to do.
        widget.skipTask("closeSecondTask");
        widget.waitUntilTourStatus(tourId, SKIPPED);
        // Skipping a task from the widget also stops starting the next tasks of its tour automatically, and only them.
        this.fixture.waitForUserTourStatus(userName, status -> !status.isCallToActionEnabled(tourId)
            && status.getTaskStatus(tourId, "closeSecondTask").filter(SKIPPED::equals).isPresent()
            && status.isCallToActionEnabled(otherTourId));
        widget.resetTour(tourId);
        widget.waitUntilTaskStatus("closeFirstTask", TODO);
        widget.waitUntilTaskStatus("closeSecondTask", TODO);
        this.fixture.waitForUserTourStatus(userName, status -> status.isCallToActionEnabled(tourId));
        widget.clickTask("closeFirstTask");
        popover.waitForStep("First task, step one");
        popover.skipAll();
        popover.waitUntilClosed();

        widget.waitUntilTaskStatus("closeFirstTask", SKIPPED);
        assertEquals(TODO, widget.getTaskStatus("closeSecondTask"));
        this.fixture.waitForUserTourStatus(userName, status -> !status.isCallToActionEnabled(tourId)
            && status.getTaskStatus(tourId, "closeFirstTask").filter(SKIPPED::equals).isPresent());

        // The next tasks of the other tour are still started automatically.
        widget.clickTask("closeOtherFirstTask");
        popover.waitForStep("Other first task, only step");
        popover.clickNext();
        popover.waitForStep("Other second task, only step");
        popover.clickNext();
        popover.waitUntilClosed();
        widget.waitUntilTourStatus(otherTourId, DONE);
        // The skipped tour is left as it was.
        assertEquals(SKIPPED, widget.getTaskStatus("closeFirstTask"));
        assertEquals(TODO, widget.getTaskStatus("closeSecondTask"));
    }

    /**
     * A reflex step has no button to move to the next step: the user has to click the highlighted element.
     */
    @Test
    @Order(4)
    void reflexStepOnClick(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space),
            "{{html}}<button type=\"button\" id=\"gtReflexButton\">Click me</button>{{/html}}", "Reflex click");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Reflex click tour", true);
        DocumentReference task = this.fixture.createTask(tour, "reflexClickTask", "Reflex click task", 0, true);
        this.fixture.addSteps(task,
            Step.withContent("Click the button").element("#gtReflexButton").reflex(),
            Step.withContent("Button clicked"));

        this.fixture.createUserAndLogin("GuidedTourReflexClickUser");
        setup.gotoPage(page);

        TourWidget widget = new TourWidget();
        widget.clickTask("reflexClickTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Click the button");
        assertEquals("gtReflexButton", popover.getHighlightedElementId());
        assertFalse(popover.hasNextButton());
        assertFalse(popover.hasPreviousButton());

        popover.clickHighlightedElement();
        popover.waitForStep("Button clicked");
        assertTrue(popover.hasNextButton());

        popover.clickNext();
        popover.waitUntilClosed();
        widget.waitUntilTaskStatus("reflexClickTask", DONE);
    }

    /**
     * A reflex step on a text input leaves the user some time to type, before moving to the next step.
     */
    @Test
    @Order(5)
    void reflexStepOnTextInput(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space),
            "{{html}}<input type=\"text\" id=\"gtReflexInput\"/>{{/html}}", "Reflex input");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Reflex input tour", true);
        DocumentReference task = this.fixture.createTask(tour, "reflexInputTask", "Reflex input task", 0, true);
        this.fixture.addSteps(task,
            Step.withContent("Type your name").element("#gtReflexInput").reflex(),
            Step.withContent("Thanks for typing"));

        this.fixture.createUserAndLogin("GuidedTourReflexInputUser");
        setup.gotoPage(page);

        new TourWidget().clickTask("reflexInputTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Type your name");
        assertFalse(popover.hasNextButton());

        // Measured before typing, so that the elapsed time is never shorter than the delay of the step.
        long clickedAt = System.currentTimeMillis();
        popover.typeInHighlightedElement("Alice");
        // The step waits 5 seconds after the click before moving on, to let the user type.
        assertEquals("Alice", popover.getHighlightedElementValue());
        popover.waitForStep("Thanks for typing", 15);
        long elapsed = System.currentTimeMillis() - clickedAt;
        assertTrue(elapsed >= 5000, "The step moved on after only %d ms.".formatted(elapsed));
    }

    /**
     * A task can span several pages and actions, like editing a page: the task continues on the edit form after the
     * user clicks the edit button, and back on the page once saved. A user who can't edit the page can't do the task,
     * so it's skipped.
     */
    @Test
    @Order(6)
    void editPageTaskAcrossPages(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page =
            this.fixture.createPage(new DocumentReference("Page", space), "Content to edit", "Edit page");
        String pageReference = setup.serializeLocalReference(page);
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Edit page tour", true);
        DocumentReference task = this.fixture.createTask(tour, "editPageTask", "Edit page task", 0, true);
        // The same steps as the "Edit a Page" task of the default tour.
        this.fixture.addSteps(task,
            Step.withContent("Press edit now.").element("#contentmenu #tmEdit a.btn").reflex()
                .targetPage(pageReference).targetAction("VIEW"),
            Step.withContent("You can write stuff now!")
                .element("#xwikieditcontent:not(.inplace-editing-buttons),#xwikicontent.cke_editable")
                .targetPage(pageReference).targetAction("EDIT"),
            Step.withContent("Now, press this button to finish editing.")
                .element(".realtime-action-done,.bottombuttons .btn[name=\"action_save\"]").reflex()
                .targetPage(pageReference).targetAction("EDIT"),
            Step.withContent("The page is saved.").targetPage(pageReference).targetAction("VIEW"));

        String editorName = this.fixture.createUserAndLogin("GuidedTourEditPageUser");
        setup.gotoPage(page);

        TourWidget widget = new TourWidget();
        widget.clickTask("editPageTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Press edit now.");
        assertFalse(popover.hasNextButton());

        // Clicking the edit button opens the edit form, where the task continues.
        popover.clickHighlightedElementAndWaitForPageReload();
        popover.waitForStep("You can write stuff now!");
        assertEquals("Content to edit", new WikiEditPage().getContent());
        popover.clickNext();
        popover.waitForStep("Now, press this button to finish editing.");

        // Saving goes back to the page, where the task continues until its end.
        popover.clickHighlightedElementAndWaitForPageReload();
        popover.waitForStep("The page is saved.");
        assertEquals(setup.getURL(page), new ViewPage().getPageURL());
        popover.clickNext();
        popover.waitUntilClosed();
        new TourWidget().waitUntilTaskStatus("editPageTask", DONE);
        this.fixture.waitForUserTourStatus(editorName,
            status -> status.getTaskStatus(this.fixture.getTourId(tour), "editPageTask").filter(DONE::equals)
                .isPresent());

        // A user who can't edit the page doesn't have the edit button, so the task is skipped.
        String viewerName = "GuidedTourEditPageViewer";
        this.fixture.denyEdit(page, viewerName);
        this.fixture.createUserAndLogin(viewerName);
        setup.gotoPage(page);
        widget = new TourWidget();
        widget.clickTask("editPageTask");
        widget.waitForNotificationErrorMessage("Error while moving between task steps.");
        widget.waitUntilTaskStatus("editPageTask", SKIPPED);
        assertFalse(new TourStepPopover().isDisplayed());
    }

    /**
     * A task whose step targets an element missing from the page can't be done, so it's skipped.
     */
    @Test
    @Order(7)
    void missingElementSkipsTheTask(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.fixture.createPage(new DocumentReference("Page", space), "Content", "Missing");
        DocumentReference tour = this.fixture.createTour(space, "Tour", "Missing element tour", true);
        DocumentReference task = this.fixture.createTask(tour, "missingElementTask", "Missing element task", 0, true);
        this.fixture.addSteps(task, Step.withContent("Never displayed").element("#gtMissingElement"));

        this.fixture.createUserAndLogin("GuidedTourMissingElementUser");
        setup.gotoPage(page);

        TourWidget widget = new TourWidget();
        widget.clickTask("missingElementTask");
        // The element is awaited for a few seconds before giving up.
        widget.waitForNotificationErrorMessage("Error while moving between task steps.");
        widget.waitUntilTaskStatus("missingElementTask", SKIPPED);
        assertFalse(new TourStepPopover().isDisplayed());
    }
}
