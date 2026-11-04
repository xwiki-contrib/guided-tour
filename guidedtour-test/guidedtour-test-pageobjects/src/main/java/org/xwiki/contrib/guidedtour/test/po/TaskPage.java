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
import org.xwiki.test.ui.po.ViewPage;

/**
 * A task page in view mode, displaying the task properties and its steps.
 *
 * @version $Id$
 * @since 0.2
 */
public class TaskPage extends ViewPage
{
    /**
     * The titles of the steps in the sidebar, which are their content.
     */
    static final By STEP_TITLES = By.cssSelector(".stepsListContainer .item .step-item-title");

    /**
     * Go to the given task page.
     *
     * @param taskReference the reference of the task page
     * @return the task page
     */
    public static TaskPage gotoPage(EntityReference taskReference)
    {
        getUtil().gotoPage(taskReference);
        return new TaskPage();
    }

    /**
     * @return the titles of the steps of the task (their content), in their order
     */
    public List<String> getStepTitles()
    {
        return getDriver().findElementsWithoutWaiting(STEP_TITLES).stream().map(WebElement::getText).toList();
    }
}
