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
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.ui.po.ViewPage;

/**
 * A tour page in view mode, displaying the tour properties and the list of its tasks.
 *
 * @version $Id$
 * @since 0.2
 */
public class TourPage extends ViewPage
{
    private static final String TASKS_LIVE_DATA_ID = "tourTasksList";

    private static final String TITLE_COLUMN = "Title";

    private static final By CONFIRM_DELETE_TASK = By.cssSelector("#confirmDeleteTaskModal .btn-danger");

    /**
     * Go to the given tour page.
     *
     * @param tourReference the reference of the tour page
     * @return the tour page
     */
    public static TourPage gotoPage(EntityReference tourReference)
    {
        getUtil().gotoPage(tourReference);
        return new TourPage();
    }

    /**
     * @return the table listing the tasks of the tour
     */
    private TableLayoutElement getTasksTable()
    {
        return new LiveDataElement(TASKS_LIVE_DATA_ID).getTableLayout();
    }

    /**
     * @return the titles of the tasks of the tour, in their order
     */
    public List<String> getTaskTitles()
    {
        return getTasksTable().getAllCells(TITLE_COLUMN).stream().map(WebElement::getText).toList();
    }

    /**
     * Only list the tasks whose title contains the given text, and wait for the list to be updated.
     *
     * @param title the text to look for in the task titles
     * @return this page
     */
    public TourPage filterTasksByTitle(String title)
    {
        getTasksTable().filterColumn(TITLE_COLUMN, title);
        return this;
    }

    /**
     * Remove the filters of the list of tasks, and wait for the list to be updated.
     *
     * @return this page
     */
    public TourPage clearTaskFilters()
    {
        LiveDataElement tasks = new LiveDataElement(TASKS_LIVE_DATA_ID);
        tasks.clearAllFilters();
        tasks.waitUntilReady();
        return this;
    }

    /**
     * Open a task, with the link of its title in the list of tasks.
     *
     * @param title the title of the task
     * @return the task page
     */
    public TaskPage viewTask(String title)
    {
        WebElement link = getTasksTable().getCell(TITLE_COLUMN, getRowNumber(title)).findElement(By.tagName("a"));
        getDriver().addPageNotYetReloadedMarker();
        link.click();
        getDriver().waitUntilPageIsReloaded();
        return new TaskPage();
    }

    /**
     * Delete a task, confirming the deletion, and wait for the list of tasks to be updated.
     *
     * @param title the title of the task to delete
     */
    public void deleteTask(String title)
    {
        int taskCount = getTaskTitles().size();
        int rowNumber = getRowNumber(title);
        TableLayoutElement tasksTable = getTasksTable();
        tasksTable.clickAction(rowNumber, "delete");
        getDriver().waitUntilElementIsVisible(CONFIRM_DELETE_TASK);
        getDriver().findElement(CONFIRM_DELETE_TASK).click();
        waitForNotificationSuccessMessage("Task successfully deleted!");
        tasksTable.waitUntilRowCountEqualsTo(taskCount - 1);
    }

    private int getRowNumber(String title)
    {
        List<String> titles = getTaskTitles();
        int rowNumber = titles.indexOf(title) + 1;
        if (rowNumber == 0) {
            throw new IllegalArgumentException("No task with the title [%s] in %s.".formatted(title, titles));
        }
        return rowNumber;
    }
}
