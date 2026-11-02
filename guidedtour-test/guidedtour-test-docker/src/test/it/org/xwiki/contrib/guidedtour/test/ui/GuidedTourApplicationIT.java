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
import org.xwiki.contrib.guidedtour.test.po.TourStepPopover;
import org.xwiki.contrib.guidedtour.test.po.TourWidget;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ViewPage;

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
class GuidedTourApplicationIT
{
    private static final String STEP_CLASS = "XWiki.GuidedTour.StepClass";

    /**
     * The displayed tours should not include inactive tours, or tours which the user has no rights to view.
     */
    @Test
    @Order(1)
    void tourVisibility(TestUtils testUtils, TestReference testReference)
    {
        testUtils.gotoPage(testReference);
        TourWidget tourWidget = new TourWidget();
        assertEquals(List.of("xwiki:GuidedTour.Tours.GettingStarted.WebHome"), tourWidget.getTourIds());
    }

    /**
     * Starting a task, or moving to its next step, should redirect to the page expected by the step, except when
     * leaving a reflex step, which navigates on its own.
     */
    @Test
    @Order(2)
    void redirectToTheExpectedPage(TestUtils setup, TestReference testReference)
    {
        setup.loginAsSuperAdmin();
        SpaceReference space = testReference.getLastSpaceReference();
        DocumentReference pageA = new DocumentReference("PageA", space);
        DocumentReference pageB = new DocumentReference("PageB", space);
        String pageAReference = setup.serializeLocalReference(pageA);
        String pageBReference = setup.serializeLocalReference(pageB);
        setup.createPage(pageA, "Page A");
        setup.createPage(pageB, "[[Go to page A>>doc:%s||id=\"goToPageA\"]]".formatted(pageAReference));

        DocumentReference tour = new DocumentReference("WebHome", new SpaceReference("Tour", space));
        setup.createPage(tour, "", "Redirect tour");
        setup.addObject(tour, "XWiki.GuidedTour.TourClass", "title", "Redirect tour", "isActive", 1);
        DocumentReference task = new DocumentReference("RedirectTask", tour.getLastSpaceReference());
        setup.createPage(task, "", "Redirect task");
        setup.addObject(task, "XWiki.GuidedTour.TaskClass", "title", "Redirect task", "isActive", 1, "order", 0);
        setup.addObject(task, STEP_CLASS, "order", 0, "content", "Start on page A", "targetPage", pageAReference);
        // A reflex step without element behaves like a regular step.
        setup.addObject(task, STEP_CLASS, "order", 1, "content", "Continue on page B", "targetPage", pageBReference,
            "queryParameters", "foo=bar", "reflex", 1);
        setup.addObject(task, STEP_CLASS, "order", 2, "content", "Click the link", "targetPage", pageBReference,
            "element", "#goToPageA", "reflex", 1);
        setup.addObject(task, STEP_CLASS, "order", 3, "content", "Back on page A", "targetPage", pageAReference);

        setup.createUserAndLogin("GuidedTourUser", "pa$$word");
        setup.gotoPage(pageB);

        // Starting the task redirects to the page of its first step.
        new TourWidget().clickTask("RedirectTask");
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
}
