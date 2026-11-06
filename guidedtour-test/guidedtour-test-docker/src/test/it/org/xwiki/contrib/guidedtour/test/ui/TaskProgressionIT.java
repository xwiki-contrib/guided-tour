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
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "Content", "Steps");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Navigation tour", true);
        DocumentReference task = this.guidedTour.createTask(tour, "navigationTask", "Navigation task", 0, true);
        this.guidedTour.addSteps(task,
            Step.withContent("Step one"),
            Step.withContent("Step two").element("#document-title"),
            Step.withContent("Step three"));

        String tourId = this.guidedTour.getTourId(tour);

        String userName = this.guidedTour.createUserAndLogin("GuidedTourNavigationUser");
        setup.gotoPage(page);

        new TourWidget().clickTask("navigationTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Step one");
        // Steps have no title of their own: they display the title of their task.
        assertEquals("Navigation task", popover.getTitle());
        assertEquals(1, popover.getCurrentStepNumber());
        assertEquals(3, popover.getStepCount());
        // A step without element is displayed in the middle of the screen.
        assertTrue(popover.isCentered());

        popover.clickNext();
        popover.waitForStep("Step two");
        assertEquals(2, popover.getCurrentStepNumber());
        assertTrue(popover.isHighlighting("#document-title"));

        // The active task is resumed at the current step after a page reload.
        new ViewPage().reloadPage();
        popover = new TourStepPopover().waitForStep("Step two");
        assertEquals(2, popover.getCurrentStepNumber());

        assertTrue(popover.hasPreviousButton());
        popover.clickPrevious().waitForStep("Step one");
        assertEquals(1, popover.getCurrentStepNumber());

        // Leaving the task before its last step keeps it to do. Wait for the leave to be handled (the next tasks aren't
        // started automatically anymore) before checking the status.
        popover.pressEscape();
        this.guidedTour.waitForUserTourStatus(userName, status -> !status.isCallToActionEnabled(tourId));
        assertEquals(TODO, new TourWidget().getTaskStatus("navigationTask"));
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
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "Content", "Tasks");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Progression tour", true);
        DocumentReference firstTask = this.guidedTour.createTask(tour, "progressionFirstTask", "First task", 0, true);
        this.guidedTour.addSteps(firstTask,
            Step.withContent("First task, step one"),
            Step.withContent("First task, step two"));
        DocumentReference inactiveTask =
            this.guidedTour.createTask(tour, "progressionInactiveTask", "Inactive task", 1, false);
        this.guidedTour.addSteps(inactiveTask, Step.withContent("Inactive task, only step"));
        DocumentReference lastTask = this.guidedTour.createTask(tour, "progressionLastTask", "Last task", 2, true);
        this.guidedTour.addSteps(lastTask, Step.withContent("Last task, only step"));
        String tourId = this.guidedTour.getTourId(tour);

        this.guidedTour.createUserAndLogin("GuidedTourProgressionUser");
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
        popover.clickDone();

        widget.waitUntilTaskStatus("progressionLastTask", DONE);
        // The inactive task isn't taken into account.
        widget.waitUntilTourStatus(tourId, DONE);
    }

    /**
     * Closing a task before its end, or skipping it (with the "Skip All" link of the popover, or from the widget),
     * stops starting automatically the next tasks of its tour, but not of the other tours. A closed task stays to do,
     * a skipped task is skipped.
     */
    @Test
    @Order(3)
    void closingOrSkippingATaskStopsStartingTheNextTasks(TestUtils setup, TestReference testReference)
        throws Exception
    {
        String userName = "GuidedTourCloseUser";
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "Content", "Close");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Close tour", true);
        DocumentReference firstTask = this.guidedTour.createTask(tour, "closeFirstTask", "First task", 0, true);
        this.guidedTour.addSteps(firstTask,
            Step.withContent("First task, step one"),
            Step.withContent("First task, step two"));
        DocumentReference secondTask = this.guidedTour.createTask(tour, "closeSecondTask", "Second task", 1, true);
        this.guidedTour.addSteps(secondTask, Step.withContent("Second task, only step"));
        String tourId = this.guidedTour.getTourId(tour);

        DocumentReference otherTour = this.guidedTour.createTour(space, "OtherTour", "Other close tour", true);
        DocumentReference otherFirstTask =
            this.guidedTour.createTask(otherTour, "closeOtherFirstTask", "Other first task", 0, true);
        this.guidedTour.addSteps(otherFirstTask, Step.withContent("Other first task, only step"));
        DocumentReference otherSecondTask =
            this.guidedTour.createTask(otherTour, "closeOtherSecondTask", "Other second task", 1, true);
        this.guidedTour.addSteps(otherSecondTask, Step.withContent("Other second task, only step"));
        String otherTourId = this.guidedTour.getTourId(otherTour);

        this.guidedTour.createUserAndLogin(userName);
        setup.gotoPage(page);

        TourWidget widget = new TourWidget();
        widget.clickTask("closeFirstTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("First task, step one");
        popover.close();

        // Once the close is handled (the next tasks aren't started automatically anymore), the task is still to do.
        // The other tour isn't affected.
        assertTrue(this.guidedTour.waitForUserTourStatus(userName, status -> !status.isCallToActionEnabled(tourId))
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
        this.guidedTour.waitForUserTourStatus(userName,
            status -> status.getTaskStatus(tourId, "closeFirstTask").filter(DONE::equals).isPresent());
        assertFalse(popover.isDisplayedWithin(3), "The next task shouldn't have been started.");
        assertEquals(TODO, widget.getTaskStatus("closeSecondTask"));

        // Resetting the tour starts automatically its next tasks again. A tour can be reset only once none of its tasks
        // is left to do.
        widget.skipTask("closeSecondTask").resetTour(tourId);
        assertEquals(TODO, widget.getTaskStatus("closeFirstTask"));
        assertEquals(TODO, widget.getTaskStatus("closeSecondTask"));
        this.guidedTour.waitForUserTourStatus(userName, status -> status.isCallToActionEnabled(tourId));

        // Skipping a task from the widget stops starting the next tasks of its tour automatically, and only them.
        widget.skipTask("closeFirstTask");
        this.guidedTour.waitForUserTourStatus(userName, status -> !status.isCallToActionEnabled(tourId)
            && status.getTaskStatus(tourId, "closeFirstTask").filter(SKIPPED::equals).isPresent()
            && status.isCallToActionEnabled(otherTourId));
        widget.skipTask("closeSecondTask").resetTour(tourId);
        this.guidedTour.waitForUserTourStatus(userName, status -> status.isCallToActionEnabled(tourId));

        // So does skipping a task with the "Skip All" link of the popover.
        widget.clickTask("closeFirstTask");
        popover.waitForStep("First task, step one");
        popover.skipAll();
        widget.waitUntilTaskStatus("closeFirstTask", SKIPPED);
        assertEquals(TODO, widget.getTaskStatus("closeSecondTask"));
        this.guidedTour.waitForUserTourStatus(userName, status -> !status.isCallToActionEnabled(tourId)
            && status.getTaskStatus(tourId, "closeFirstTask").filter(SKIPPED::equals).isPresent());

        // The next tasks of the other tour are still started automatically.
        widget.clickTask("closeOtherFirstTask");
        popover.waitForStep("Other first task, only step");
        popover.clickNext();
        popover.waitForStep("Other second task, only step");
        popover.clickDone();
        widget.waitUntilTourStatus(otherTourId, DONE);
        // The skipped tour is left as it was.
        assertEquals(SKIPPED, widget.getTaskStatus("closeFirstTask"));
        assertEquals(TODO, widget.getTaskStatus("closeSecondTask"));

        // Skipping the whole tour from the widget also stops starting its next tasks automatically: once its tasks are
        // reset one by one, completing the first one doesn't start the next one.
        // FIXME: This currently fails: skipping a tour only changes the status of its tasks, without disabling the call
        // to action of the tour like skipping a task does, so the next task is started. Skipping a tour should disable
        // the call to action of the tour, like skipping a task. This is checked last, so that the checks above still
        // run while it fails.
        widget.skipTask("closeSecondTask").resetTour(tourId);
        this.guidedTour.waitForUserTourStatus(userName, status -> status.isCallToActionEnabled(tourId));
        widget.skipTour(tourId).resetTask("closeFirstTask").resetTask("closeSecondTask");
        widget.clickTask("closeFirstTask");
        popover.waitForStep("First task, step one");
        popover.clickNext();
        popover.waitForStep("First task, step two");
        popover.clickNext();
        widget.waitUntilTaskStatus("closeFirstTask", DONE);
        this.guidedTour.waitForUserTourStatus(userName,
            status -> status.getTaskStatus(tourId, "closeFirstTask").filter(DONE::equals).isPresent());
        assertFalse(popover.isDisplayedWithin(3), "The next task shouldn't have been started.");
        assertEquals(TODO, widget.getTaskStatus("closeSecondTask"));
    }

    /**
     * A reflex step has no button to move to the next step: the user has to act on the highlighted element. Clicking a
     * button moves to the next step at once, while a text input leaves the user some time to type.
     */
    @Test
    @Order(4)
    void reflexSteps(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space),
            "{{html}}<button type=\"button\" id=\"gtReflexButton\">Click me</button>"
                + "<input type=\"text\" id=\"gtReflexInput\"/>{{/html}}",
            "Reflex");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Reflex tour", true);
        DocumentReference task = this.guidedTour.createTask(tour, "reflexTask", "Reflex task", 0, true);
        this.guidedTour.addSteps(task,
            Step.withContent("Click the button").element("#gtReflexButton").reflex(),
            Step.withContent("Type your name").element("#gtReflexInput").reflex(),
            Step.withContent("Thanks for typing"));

        this.guidedTour.createUserAndLogin("GuidedTourReflexUser");
        setup.gotoPage(page);

        TourWidget widget = new TourWidget();
        widget.clickTask("reflexTask");
        TourStepPopover popover = new TourStepPopover().waitForStep("Click the button");
        assertTrue(popover.isHighlighting("#gtReflexButton"));
        assertFalse(popover.hasNextButton());
        assertFalse(popover.hasPreviousButton());

        // Clicking the button moves to the next step.
        popover.clickHighlightedElement();
        popover.waitForStep("Type your name");
        assertTrue(popover.isHighlighting("#gtReflexInput"));
        assertFalse(popover.hasNextButton());

        // The step waits 5 seconds after the click before moving on, to let the user type.
        long clickedAt = popover.typeInHighlightedElement("Alice");
        assertEquals("Alice", popover.getHighlightedElementValue());
        popover.waitForStep("Thanks for typing", 15);
        long elapsed = System.currentTimeMillis() - clickedAt;
        assertTrue(elapsed >= 5000, "The step moved on after only %d ms.".formatted(elapsed));
        assertTrue(popover.hasNextButton());

        popover.clickDone();
        widget.waitUntilTaskStatus("reflexTask", DONE);
    }

    /**
     * A task can span several pages and actions, like editing a page: the task continues on the edit form after the
     * user clicks the edit button, and back on the page once saved. A user who can't edit the page can't do the task,
     * so it's skipped.
     */
    @Test
    @Order(5)
    void editPageTaskAcrossPages(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page =
            this.guidedTour.createPage(new DocumentReference("Page", space), "Content to edit", "Edit page");
        String pageReference = setup.serializeLocalReference(page);
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Edit page tour", true);
        DocumentReference task = this.guidedTour.createTask(tour, "editPageTask", "Edit page task", 0, true);
        // Steps like the ones of the "Edit a Page" task of the default tour, without its introduction step on the
        // sandbox, and with a last step once the page is saved.
        this.guidedTour.addSteps(task,
            Step.withContent("Press edit now.").element("#contentmenu #tmEdit a.btn").reflex()
                .targetPage(pageReference).targetAction("VIEW"),
            Step.withContent("You can write stuff now!")
                .element("#xwikieditcontent:not(.inplace-editing-buttons),#xwikicontent.cke_editable")
                .targetPage(pageReference).targetAction("EDIT"),
            Step.withContent("Now, press this button to finish editing.")
                .element(".realtime-action-done,.bottombuttons .btn[name=\"action_save\"]").reflex()
                .targetPage(pageReference).targetAction("EDIT"),
            Step.withContent("The page is saved.").targetPage(pageReference).targetAction("VIEW"));

        String editorName = this.guidedTour.createUserAndLogin("GuidedTourEditPageUser");
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
        popover.clickDone();
        new TourWidget().waitUntilTaskStatus("editPageTask", DONE);
        this.guidedTour.waitForUserTourStatus(editorName,
            status -> status.getTaskStatus(this.guidedTour.getTourId(tour), "editPageTask").filter(DONE::equals)
                .isPresent());

        // A user who can't edit the page doesn't have the edit button, so the task is skipped.
        String viewerName = "GuidedTourEditPageViewer";
        this.guidedTour.denyEdit(page, viewerName);
        this.guidedTour.createUserAndLogin(viewerName);
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
    @Order(6)
    void missingElementSkipsTheTask(TestUtils setup, TestReference testReference) throws Exception
    {
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference page = this.guidedTour.createPage(new DocumentReference("Page", space), "Content", "Missing");
        DocumentReference tour = this.guidedTour.createTour(space, "Tour", "Missing element tour", true);
        DocumentReference task =
            this.guidedTour.createTask(tour, "missingElementTask", "Missing element task", 0, true);
        this.guidedTour.addSteps(task, Step.withContent("Never displayed").element("#gtMissingElement"));

        this.guidedTour.createUserAndLogin("GuidedTourMissingElementUser");
        setup.gotoPage(page);

        TourWidget widget = new TourWidget();
        widget.clickTask("missingElementTask");
        // The element is awaited for a few seconds before giving up.
        widget.waitForNotificationErrorMessage("Error while moving between task steps.");
        widget.waitUntilTaskStatus("missingElementTask", SKIPPED);
        assertFalse(new TourStepPopover().isDisplayed());
    }
}
