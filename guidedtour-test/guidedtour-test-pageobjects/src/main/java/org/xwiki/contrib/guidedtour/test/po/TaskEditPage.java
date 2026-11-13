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

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.ViewPage;

/**
 * A task page in edit mode: the form editing the task properties and its steps. The steps are listed in a sidebar,
 * which shows the form of one step at a time.
 *
 * @version $Id$
 * @since 0.2
 */
public class TaskEditPage extends InlinePage
{
    private static final String FIELD_PREFIX = "XWiki.GuidedTour.TaskClass_0_";

    private static final By STEP_ITEMS = By.cssSelector(".stepsListContainer .item");

    private static final By ADD_STEP_BUTTON = By.id("add-step-btn");

    private static final By DELETE_STEP_BUTTON = By.className("step-item-delete");

    private static final By DISPLAYED_STEP_CONTENT =
        By.cssSelector(".stepsListContainer dl.itemDisplay:not(.hidden) textarea[id$='_content']");

    /**
     * The step list of the sidebar, once made sortable: this is the last thing done by the script of the task sheet,
     * after listing the steps, setting up the button adding a step and filling the task title.
     */
    private static final By SORTABLE_STEP_LIST = By.cssSelector(".stepsListContainer .item-list.ui-sortable");

    /**
     * Wait until the script of the task sheet is ready to edit the steps. It's loaded asynchronously, after the page.
     */
    public TaskEditPage()
    {
        getDriver().waitUntilCondition(driver -> !getDriver().findElementsWithoutWaiting(SORTABLE_STEP_LIST).isEmpty());
    }

    /**
     * Go to the edit form of the given task.
     *
     * @param taskReference the reference of the task page
     * @return the task edit form
     */
    public static TaskEditPage gotoPage(EntityReference taskReference)
    {
        getUtil().gotoPage(taskReference, "edit", "editor=inline");
        return new TaskEditPage();
    }

    /**
     * @param title the new title of the task
     * @return this form
     */
    public TaskEditPage setTaskTitle(String title)
    {
        GuidedTourForms.setText(getField("title"), title);
        return this;
    }

    /**
     * @param order the new position of the task in its tour
     * @return this form
     */
    public TaskEditPage setOrder(int order)
    {
        GuidedTourForms.setText(getField("order"), String.valueOf(order));
        return this;
    }

    /**
     * @param active whether the task should be displayed in the widget
     * @return this form
     */
    public TaskEditPage setActive(boolean active)
    {
        GuidedTourForms.setBoolean(getField("isActive"), active);
        return this;
    }

    /**
     * @return the titles of the steps in the sidebar (their content), in their order
     */
    public List<String> getStepTitles()
    {
        return TaskPage.getStepTitles(getDriver());
    }

    /**
     * Add a step at the end of the task, and fill its content.
     *
     * @param content the content of the new step
     * @return this form
     */
    public TaskEditPage addStep(String content)
    {
        int stepCount = getStepItems().size();
        getDriver().findElement(ADD_STEP_BUTTON).click();
        // The form of the new step is loaded asynchronously, then displayed.
        getDriver().waitUntilCondition(driver -> getStepItems().size() == stepCount + 1
            && getStepItems().get(stepCount).getAttribute(ATTRIBUTE_CLASS).contains("active"));
        setDisplayedStepContent(content);
        return this;
    }

    /**
     * Replace the content of a step.
     *
     * @param position the position of the step in the task, starting at 0
     * @param content the new content of the step
     * @return this form
     */
    public TaskEditPage setStepContent(int position, String content)
    {
        WebElement stepItem = getStepItems().get(position);
        stepItem.click();
        getDriver().waitUntilCondition(driver -> stepItem.getAttribute(ATTRIBUTE_CLASS).contains("active"));
        setDisplayedStepContent(content);
        return this;
    }

    /**
     * Remove a step from the task. The step is deleted when the task is saved.
     *
     * @param position the position of the step in the task, starting at 0
     * @return this form
     */
    public TaskEditPage deleteStep(int position)
    {
        int stepCount = getStepItems().size();
        getStepItems().get(position).findElement(DELETE_STEP_BUTTON).click();
        getDriver().waitUntilCondition(driver -> getStepItems().size() == stepCount - 1);
        return this;
    }

    @Override
    protected ViewPage createViewPage()
    {
        return new TaskPage();
    }

    private void setDisplayedStepContent(String content)
    {
        getDriver().waitUntilElementIsVisible(DISPLAYED_STEP_CONTENT);
        // Typing updates the title of the step in the sidebar.
        GuidedTourForms.setText(getDriver().findElement(DISPLAYED_STEP_CONTENT), content);
    }

    private List<WebElement> getStepItems()
    {
        return getDriver().findElementsWithoutWaiting(STEP_ITEMS);
    }

    private WebElement getField(String property)
    {
        return getDriver().findElement(By.id(FIELD_PREFIX + property));
    }
}
