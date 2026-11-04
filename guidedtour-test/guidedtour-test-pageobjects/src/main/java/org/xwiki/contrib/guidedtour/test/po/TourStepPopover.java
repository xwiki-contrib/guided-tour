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
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * The popover displaying the active step of a guided tour task.
 *
 * @version $Id$
 * @since 0.2
 */
public class TourStepPopover extends BaseElement
{
    /**
     * The id of the element highlighted by driver.js when the step doesn't target any element, in which case the
     * popover is displayed in the middle of the screen.
     */
    public static final String NO_ELEMENT_ID = "driver-dummy-element";

    private static final By POPOVER = By.cssSelector(".driver-popover");

    private static final By TITLE = By.cssSelector(".driver-popover .driver-popover-title");

    private static final By DESCRIPTION = By.cssSelector(".driver-popover .driver-popover-description");

    private static final By PROGRESS = By.cssSelector(".driver-popover .driver-popover-progress-text");

    private static final By NEXT_BUTTON = By.cssSelector(".driver-popover-next-btn");

    private static final By PREVIOUS_BUTTON = By.cssSelector(".driver-popover .driver-popover-prev-btn");

    private static final By CLOSE_BUTTON = By.cssSelector(".driver-popover .driver-popover-close-btn");

    private static final By SKIP_ALL_BUTTON = By.cssSelector(".driver-popover .driver-xwiki-skip-all-button");

    private static final By HIGHLIGHTED_ELEMENT = By.cssSelector(".driver-active-element");

    /**
     * Marker used in the progress text for the steps already seen, including the current one.
     */
    private static final int SEEN_STEP_MARKER = '⬤';

    /**
     * Marker used in the progress text for the next steps.
     */
    private static final int NEXT_STEP_MARKER = '◯';

    /**
     * The duration of the transition between steps (driver.js default is 400ms), plus a safety margin. driver.js
     * displays the popover of a step before the end of this transition, but it registers the step as active only at
     * its end. Closing the task before that (with the next button on the last step, the close button or the Escape key)
     * doesn't call the {@code onDestroyed} hook, so the status of the task isn't updated. A user can't be that fast, but
     * a test can, so we wait for the end of the transition before interacting with the popover.
     */
    private static final long STEP_TRANSITION_DURATION = 600;

    /**
     * When the current step was seen displayed for the first time, or {@code 0} if unknown (e.g. after moving to
     * another step without waiting for it), in which case we wait for the whole transition.
     */
    private long stepDisplayedAt;

    /**
     * Wait until the given step is displayed, possibly on another page.
     *
     * @param description the description of the expected step
     * @return this popover
     */
    public TourStepPopover waitForStep(String description)
    {
        return waitForStep(description, getDriver().getTimeout());
    }

    /**
     * Wait until the given step is displayed, with a custom timeout.
     *
     * @param description the description (content) of the expected step
     * @param timeout the timeout, in seconds
     * @return this popover
     */
    public TourStepPopover waitForStep(String description, int timeout)
    {
        getDriver().waitUntilCondition(driver -> description.equals(getTextWithoutWaiting(DESCRIPTION)), timeout);
        this.stepDisplayedAt = System.currentTimeMillis();
        return this;
    }

    /**
     * @return {@code true} if a step is currently displayed
     */
    public boolean isDisplayed()
    {
        return !getDriver().findElementsWithoutWaiting(POPOVER).isEmpty();
    }

    /**
     * Check whether a step popover is displayed within the given delay. Used to verify that no task is started
     * automatically, which can't be observed otherwise.
     *
     * @param timeout the delay, in seconds
     * @return {@code true} if a step was displayed before the delay expired
     */
    public boolean isDisplayedWithin(int timeout)
    {
        try {
            getDriver().waitUntilCondition(driver -> isDisplayed(), timeout);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * Wait until no step is displayed anymore.
     */
    public void waitUntilClosed()
    {
        getDriver().waitUntilElementDisappears(POPOVER);
    }

    /**
     * @return the title of the current step, which is the title of its task
     */
    public String getTitle()
    {
        return getDriver().findElementWithoutWaitingWithoutScrolling(TITLE).getText();
    }

    /**
     * @return the position of the current step in its task, starting at 1
     */
    public int getCurrentStepNumber()
    {
        return (int) getProgressText().codePoints().filter(c -> c == SEEN_STEP_MARKER).count();
    }

    /**
     * @return the number of steps of the current task
     */
    public int getStepCount()
    {
        return (int) getProgressText().codePoints().filter(c -> c == SEEN_STEP_MARKER || c == NEXT_STEP_MARKER)
            .count();
    }

    /**
     * @return the id of the element highlighted by the current step, or {@link #NO_ELEMENT_ID} if the step doesn't
     *     target any element
     */
    public String getHighlightedElementId()
    {
        return getHighlightedElement().getAttribute(ATTRIBUTE_ID);
    }

    /**
     * Click the element highlighted by the current step. On a reflex step, this moves to the next step.
     */
    public void clickHighlightedElement()
    {
        waitForStepTransition();
        getHighlightedElement().click();
        this.stepDisplayedAt = 0;
    }

    /**
     * Click the element highlighted by the current step, and wait for the page to be reloaded. On a reflex step, this
     * moves to the next step, which is displayed once the new page is loaded (e.g. after clicking a link or a submit
     * button).
     */
    public void clickHighlightedElementAndWaitForPageReload()
    {
        waitForStepTransition();
        getDriver().addPageNotYetReloadedMarker();
        getHighlightedElement().click();
        getDriver().waitUntilPageIsReloaded();
        this.stepDisplayedAt = 0;
    }

    /**
     * Type into the text input highlighted by the current step. On a reflex step, the step moves on a few seconds
     * after the input is clicked, to let the user type.
     *
     * @param text the text to type
     * @return this popover
     */
    public TourStepPopover typeInHighlightedElement(String text)
    {
        waitForStepTransition();
        WebElement input = getHighlightedElement();
        input.click();
        input.sendKeys(text);
        return this;
    }

    /**
     * @return the value of the text input highlighted by the current step
     */
    public String getHighlightedElementValue()
    {
        return getHighlightedElement().getDomProperty("value");
    }

    /**
     * @return {@code true} if the popover has a button to move to the next step
     */
    public boolean hasNextButton()
    {
        return !getDriver().findElementsWithoutWaiting(NEXT_BUTTON).isEmpty();
    }

    /**
     * @return {@code true} if the popover has a button to move to the previous step
     */
    public boolean hasPreviousButton()
    {
        return !getDriver().findElementsWithoutWaiting(PREVIOUS_BUTTON).isEmpty();
    }

    /**
     * Click the button that moves to the next step.
     */
    public void clickNext()
    {
        waitForStepTransition();
        getDriver().findElement(NEXT_BUTTON).click();
        this.stepDisplayedAt = 0;
    }

    /**
     * Click the button that moves to the previous step.
     *
     * @return this popover
     */
    public TourStepPopover clickPrevious()
    {
        waitForStepTransition();
        getDriver().findElement(PREVIOUS_BUTTON).click();
        this.stepDisplayedAt = 0;
        return this;
    }

    /**
     * Press the right arrow key, which moves to the next step, and wait for the page to be reloaded. On a reflex step,
     * this clicks the highlighted element, which is expected to navigate to another page.
     */
    public void pressNextKeyAndWaitForPageReload()
    {
        // driver.js ignores the arrow keys during the transition to the current step.
        waitForStepTransition();
        getDriver().addPageNotYetReloadedMarker();
        getDriver().createActions().sendKeys(Keys.ARROW_RIGHT).perform();
        getDriver().waitUntilPageIsReloaded();
        this.stepDisplayedAt = 0;
    }

    /**
     * Close the task with the close button of the popover. Unless it's the last step, the task stays to do.
     */
    public void close()
    {
        waitForStepTransition();
        getDriver().findElement(CLOSE_BUTTON).click();
    }

    /**
     * Close the task with the Escape key.
     */
    public void pressEscape()
    {
        waitForStepTransition();
        getDriver().createActions().sendKeys(Keys.ESCAPE).perform();
    }

    /**
     * Skip the whole task with the "Skip All" link of the popover.
     */
    public void skipAll()
    {
        getDriver().findElement(SKIP_ALL_BUTTON).click();
    }

    /**
     * Wait for the end of the transition to the current step, see {@link #STEP_TRANSITION_DURATION}. This is a timed
     * wait because driver.js (1.10) keeps the end of the transition in its internal state ({@code
     * __transitionCallback}) without reflecting it in the DOM: the highlighted element, the overlay and the popover are
     * already in their final state, so there's no condition to wait for.
     */
    private void waitForStepTransition()
    {
        long elapsed = this.stepDisplayedAt > 0 ? System.currentTimeMillis() - this.stepDisplayedAt : 0;
        long remaining = STEP_TRANSITION_DURATION - elapsed;
        if (remaining > 0) {
            try {
                Thread.sleep(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private WebElement getHighlightedElement()
    {
        getDriver().waitUntilCondition(driver -> !getDriver().findElementsWithoutWaiting(HIGHLIGHTED_ELEMENT).isEmpty());
        return getDriver().findElementWithoutWaitingWithoutScrolling(HIGHLIGHTED_ELEMENT);
    }

    private String getProgressText()
    {
        return getDriver().findElementWithoutWaitingWithoutScrolling(PROGRESS).getText();
    }

    private String getTextWithoutWaiting(By locator)
    {
        try {
            List<WebElement> elements = getDriver().findElementsWithoutWaiting(locator);
            return elements.isEmpty() ? null : elements.get(0).getText();
        } catch (StaleElementReferenceException e) {
            // The popover is being re-rendered for another step.
            return null;
        }
    }
}
