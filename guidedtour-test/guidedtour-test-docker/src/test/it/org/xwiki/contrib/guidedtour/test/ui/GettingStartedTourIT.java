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
import org.xwiki.contrib.guidedtour.test.po.TourStepPopover;
import org.xwiki.contrib.guidedtour.test.po.TourWidget;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.CreatePagePage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the tasks of the Getting Started tour, installed with the application.
 *
 * @version $Id$
 */
@UITest
class GettingStartedTourIT extends AbstractGuidedTourIT
{
    /**
     * The "Create a page" task creates the tutorial page of the user from the template that it offers in the Sandbox,
     * with the image that the next tasks use attached.
     */
    @Test
    @Order(1)
    void createTutorialPage(TestUtils setup) throws Exception
    {
        // The Sandbox of the standard flavor, where the task starts.
        this.guidedTour.createPage(new DocumentReference("xwiki", "Sandbox", "WebHome"), "", "Sandbox");
        DocumentReference tutorialPage = new DocumentReference("xwiki", List.of("Sandbox", "Tutorial page"), "WebHome");
        this.guidedTour.deleteAfterTest(tutorialPage);

        this.guidedTour.createUserAndLogin("GuidedTourTutorialUser");
        setup.gotoPage("Main", "WebHome");

        new TourWidget().clickTask("CreatePage");
        TourStepPopover popover = new TourStepPopover().waitForStep("Welcome to XWiki! This tour shows you the basics."
            + " You start by creating your own tutorial page in the Sandbox.");
        popover.clickNext();
        popover.waitForStep("Click Create to add a new page.").clickHighlightedElementAndWaitForPageReload();

        popover.waitForStep("This is the title of your new page. Type one that includes your name, for example"
            + " \"Tutorial page of Alice\", then click Next.");
        CreatePagePage createPage = new CreatePagePage();
        String templateProvider = "GuidedTour.Tours.GettingStarted.Code.TutorialPageTemplateProvider";
        assertThat(createPage.getAvailableTemplates(), hasItem(templateProvider));
        createPage.getDocumentPicker().setTitle("Tutorial page");
        popover.clickNext();
        popover.waitForStep("Select the Guided Tour template.");
        createPage.setTemplate(templateProvider);
        popover.waitForStep("Click Create.").clickHighlightedElementAndWaitForPageReload();
        popover.waitForStep("This is the editor, where you write the content of your page. You can change it now or"
            + " later, then click Next.");
        popover.clickNext();
        popover.waitForStep("Click Done to save your page.").clickHighlightedElementAndWaitForPageReload();
        popover.waitForStep("Congratulations, you created your tutorial page! The next tasks happen on this page.");

        assertTrue(setup.rest().exists(new AttachmentReference("xwiki-logo.png", tutorialPage)));
    }
}
