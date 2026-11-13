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
package org.xwiki.contrib.guidedtour.test.po;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.ViewPage;

/**
 * A tour page in edit mode: the form editing the tour properties, with the button adding tasks.
 *
 * @version $Id$
 * @since 0.2
 */
public class TourEditPage extends InlinePage
{
    private static final String FIELD_PREFIX = "XWiki.GuidedTour.TourClass_0_";

    private static final By ADD_TASK_BUTTON = By.cssSelector("a[data-target='#addTaskFormModal']");

    private static final By ADD_TASK_TITLE = By.id("taskTitle");

    private static final By ADD_TASK_CONFIRM = By.cssSelector("#addTaskFormModal .btn-primary");

    /**
     * Go to the edit form of the given tour.
     *
     * @param tourReference the reference of the tour page
     * @return the tour edit form
     */
    public static TourEditPage gotoPage(EntityReference tourReference)
    {
        getUtil().gotoPage(tourReference, "edit", "editor=inline");
        return new TourEditPage();
    }

    /**
     * @return the title of the tour, as currently entered in the form
     */
    public String getTourTitle()
    {
        return getField("title").getDomProperty("value");
    }

    /**
     * @param title the new title of the tour
     * @return this form
     */
    public TourEditPage setTourTitle(String title)
    {
        GuidedTourForms.setText(getField("title"), title);
        return this;
    }

    /**
     * @param description the new description of the tour
     * @return this form
     */
    public TourEditPage setDescription(String description)
    {
        GuidedTourForms.setText(getField("description"), description);
        return this;
    }

    /**
     * @param active whether the tour should be displayed in the widget
     * @return this form
     */
    public TourEditPage setActive(boolean active)
    {
        GuidedTourForms.setBoolean(getField("isActive"), active);
        return this;
    }

    /**
     * Add a task to the tour, with the "Add task" modal, and wait to be redirected to the page of the new task. The
     * task is created inactive.
     *
     * @param title the title of the new task, also used as its id
     * @return the page of the new task
     */
    public TaskPage addTask(String title)
    {
        getDriver().findElement(ADD_TASK_BUTTON).click();
        getDriver().waitUntilElementIsVisible(ADD_TASK_TITLE);
        WebElement titleInput = getDriver().findElement(ADD_TASK_TITLE);
        titleInput.clear();
        titleInput.sendKeys(title);
        getDriver().addPageNotYetReloadedMarker();
        getDriver().findElement(ADD_TASK_CONFIRM).click();
        waitForNotificationSuccessMessage("Task successfully created!");
        // The modal redirects to the new task once it's created.
        getDriver().waitUntilPageIsReloaded();
        return new TaskPage();
    }

    @Override
    protected ViewPage createViewPage()
    {
        return new TourPage();
    }

    private WebElement getField(String property)
    {
        return getDriver().findElement(By.id(FIELD_PREFIX + property));
    }
}
