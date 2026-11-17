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
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * The popover displaying the active step of a guided tour task.
 *
 * @version $Id$
 */
public class TourStepPopover extends BaseElement
{
    private static final By NEXT_BUTTON = By.cssSelector(".driver-popover-next-btn");

    private static final By POPOVER = By.cssSelector(".driver-popover");

    /**
     * Wait until the given step is displayed, possibly on another page.
     *
     * @param description the description of the expected step, or a part of it
     * @return this popover
     */
    public TourStepPopover waitForStep(String description)
    {
        getDriver().waitUntilCondition(driver -> {
            List<WebElement> elements =
                getDriver().findElementsWithoutWaiting(By.cssSelector(".driver-popover-description"));
            try {
                return !elements.isEmpty() && elements.get(0).getText().contains(description);
            } catch (StaleElementReferenceException e) {
                return false;
            }
        });
        return this;
    }

    /**
     * Click the element highlighted by the step. A step waiting for this click then moves to the next step.
     */
    public void clickHighlightedElement()
    {
        // The overlay covers the element until the highlight animation reaches it.
        getDriver().waitUntilCondition(driver -> {
            try {
                driver.findElement(By.cssSelector(".driver-active-element")).click();
                return true;
            } catch (ElementClickInterceptedException | StaleElementReferenceException e) {
                return false;
            }
        });
    }

    /**
     * @return {@code true} if the popover has a button to move to the next step
     */
    public boolean hasNextButton()
    {
        return !getDriver().findElementsWithoutWaiting(NEXT_BUTTON).isEmpty();
    }

    /**
     * Click the button that moves to the next step.
     */
    public void clickNext()
    {
        getDriver().findElement(NEXT_BUTTON).click();
    }

    /**
     * Press the right arrow key, which moves to the next step, and wait for the page to be reloaded. On a reflex step,
     * this clicks the highlighted element, which is expected to navigate to another page.
     */
    public void pressNextKeyAndWaitForPageReload()
    {
        getDriver().addPageNotYetReloadedMarker();
        getDriver().createActions().sendKeys(Keys.ARROW_RIGHT).perform();
        getDriver().waitUntilPageIsReloaded();
    }

    /**
     * @return {@code true} if a step is displayed
     */
    public boolean isDisplayed()
    {
        return !getDriver().findElementsWithoutWaiting(POPOVER).isEmpty();
    }

    /**
     * Wait until no step is displayed anymore, after the task is closed.
     */
    public void waitUntilClosed()
    {
        getDriver().waitUntilCondition(driver -> !isDisplayed());
    }
}
