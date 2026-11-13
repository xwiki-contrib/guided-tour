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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.NotFoundException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.xwiki.test.ui.po.BaseElement;

/**
 * The guided tour widget, displayed on every page, listing the tours and their tasks.
 *
 * @version $Id$
 * @since 0.1
 */
public class TourWidget extends BaseElement
{
    private static final Pattern PROGRESS_STYLE_WIDTH_PATTERN = Pattern.compile("width:\\s*(\\d+(?:\\.\\d+)?)%.*");

    private static final String COLLAPSED = "collapsed";

    private static final By HEADER = By.cssSelector(".guidedtour-widget .header");

    private static final By TOP_BAR = By.cssSelector(".guidedtour-widget .top-bar");

    private static final By TOURS = By.cssSelector(".guidedtour-widget section.guidedtour-tour");

    private static final String TASK_STATUS_PREFIX = "task-";

    private static final String TOUR_STATUS_PREFIX = "tour-";

    private static final String SKIP_ICON = "fa-solid fa-x";

    private static final String RESET_ICON = "fa fa-rotate-right";

    @FindBy(css = ".guidedtour-widget")
    private WebElement widget;

    /**
     * @return the ids of the tours currently visible in the widget
     */
    public List<String> getTourIds()
    {
        return widget.findElements(By.cssSelector("section.guidedtour-tour")).stream()
            .filter(this::isTourVisible)
            .map(this::getElementId)
            .toList();
    }

    /**
     * @param tourId the tour id
     * @return the ids of all visible tasks for the requested tour
     */
    public List<String> getVisibleTaskIds(String tourId)
    {
        if (isCollapsedWithoutWaiting()) {
            return List.of();
        }

        List<WebElement> tours = getDriver().findElementsWithoutWaiting(widget, getTourLocator(tourId));
        if (tours.isEmpty()) {
            return List.of();
        }
        WebElement tour = tours.get(0);
        if (!isTourVisible(tour) || tour.getAttribute(ATTRIBUTE_CLASS).contains(COLLAPSED)) {
            return List.of();
        }

        return getDriver().findElementsWithoutWaiting(tour, By.cssSelector(".guidedtour-task")).stream()
            .filter(WebElement::isDisplayed)
            .map(this::getElementId)
            .toList();
    }

    /**
     * @param tourId the id of the tour (its serialized document reference)
     * @return the title of the tour, as displayed in the widget
     * @since 0.2
     */
    public String getTourTitle(String tourId)
    {
        return getDriver().findElementWithoutWaitingWithoutScrolling(getTourHeaderLocator(tourId))
            .findElement(By.className("tour-title")).getText();
    }

    /**
     * @param taskId the id of the task (the name of its page)
     * @return the title of the task, as displayed in the widget
     * @since 0.2
     */
    public String getTaskTitle(String taskId)
    {
        return getDriver().findElementWithoutWaitingWithoutScrolling(getTaskLocator(taskId))
            .findElement(By.className("guidedtour-widget-item-title")).getText();
    }

    /**
     * Wait for the widget to be loaded, then tell whether it's collapsed. The widget starts collapsed until the state
     * saved by the user is loaded, so once loaded, this tells which state was restored.
     *
     * @return {@code true} if the widget is collapsed, i.e. only its top bar is displayed
     */
    public boolean isWidgetCollapsed()
    {
        waitUntilLoaded();
        return isCollapsedWithoutWaiting();
    }

    private boolean isCollapsedWithoutWaiting()
    {
        return widget.getAttribute(ATTRIBUTE_CLASS).contains(COLLAPSED);
    }

    /**
     * Start a task, by clicking it in the widget.
     *
     * @param taskId the id of the task (the name of its page)
     */
    public void clickTask(String taskId)
    {
        // The tasks are loaded asynchronously.
        waitUntilTaskIsDisplayed(taskId);
        getDriver().findElement(getTaskLocator(taskId)).click();
    }

    /**
     * Skip a task, with its button in the widget, and wait until the widget displays it as skipped.
     *
     * @param taskId the id of the task (the name of its page)
     * @return this widget
     */
    public TourWidget skipTask(String taskId)
    {
        clickButton(getTaskLocator(taskId), SKIP_ICON);
        return waitUntilTaskStatus(taskId, TourTaskStatus.SKIPPED);
    }

    /**
     * Reset a task to do, with its button in the widget, and wait until the widget displays it as to do.
     *
     * @param taskId the id of the task (the name of its page)
     * @return this widget
     */
    public TourWidget resetTask(String taskId)
    {
        clickButton(getTaskLocator(taskId), RESET_ICON);
        return waitUntilTaskStatus(taskId, TourTaskStatus.TODO);
    }

    /**
     * Skip the tasks of a tour left to do, with its button in the widget, and wait until the widget displays the tour
     * as skipped.
     *
     * @param tourId the id of the tour (its serialized document reference)
     * @return this widget
     */
    public TourWidget skipTour(String tourId)
    {
        clickButton(getTourHeaderLocator(tourId), SKIP_ICON);
        return waitUntilTourStatus(tourId, TourTaskStatus.SKIPPED);
    }

    /**
     * Reset all the tasks of a tour to do, with its button in the widget, and wait until the widget displays the tour
     * as to do.
     *
     * @param tourId the id of the tour (its serialized document reference)
     * @return this widget
     */
    public TourWidget resetTour(String tourId)
    {
        clickButton(getTourHeaderLocator(tourId), RESET_ICON);
        return waitUntilTourStatus(tourId, TourTaskStatus.TODO);
    }

    /**
     * @return the progress displayed by the widget, between 0 and 1
     */
    public float getProgress()
    {
        String style = widget.findElement(By.cssSelector(".completeness-progress")).getAttribute("style");
        Matcher matcher = PROGRESS_STYLE_WIDTH_PATTERN.matcher(style);
        if (matcher.matches()) {
            return Float.parseFloat(matcher.group(1)) / 100.0f;
        }

        return 0.0f;
    }

    /**
     * Wait for the widget to be loaded. The widget is a Vue application mounted asynchronously on every page: it starts
     * collapsed, then restores the state saved by the user, and lists the tours. The tours are fetched only once the
     * saved state is loaded, so the widget is then expanded or collapsed as saved. The tasks of each tour are still
     * loaded afterwards, see {@link #waitUntilTaskIsDisplayed(String)}.
     *
     * @return this widget
     * @since 0.2
     */
    public TourWidget waitUntilLoaded()
    {
        getDriver().waitUntilCondition(driver -> !getDriver().findElementsWithoutWaiting(HEADER).isEmpty()
            && !getDriver().findElementsWithoutWaiting(TOURS).isEmpty());
        return this;
    }

    /**
     * Collapse the widget, if it's not already collapsed.
     *
     * @return this widget
     * @since 0.2
     */
    public TourWidget collapse()
    {
        return setCollapsed(true);
    }

    /**
     * Expand the widget, if it's not already expanded.
     *
     * @return this widget
     * @since 0.2
     */
    public TourWidget expand()
    {
        return setCollapsed(false);
    }

    /**
     * Wait until the given task is displayed. The tasks of each tour are loaded asynchronously.
     *
     * @param taskId the id of the task (the name of its page)
     * @return this widget
     * @since 0.2
     */
    public TourWidget waitUntilTaskIsDisplayed(String taskId)
    {
        getDriver().waitUntilElementIsVisible(getTaskLocator(taskId));
        return this;
    }

    /**
     * @param taskId the id of the task (the name of its page)
     * @return the status of the given task, as displayed in the widget
     * @since 0.2
     */
    public TourTaskStatus getTaskStatus(String taskId)
    {
        return readStatus(getTaskLocator(taskId), TASK_STATUS_PREFIX);
    }

    /**
     * Wait until the given task has the expected status.
     *
     * @param taskId the id of the task (the name of its page)
     * @param status the expected status
     * @return this widget
     * @since 0.2
     */
    public TourWidget waitUntilTaskStatus(String taskId, TourTaskStatus status)
    {
        By task = getTaskLocator(taskId);
        getDriver().waitUntilCondition(driver -> hasStatus(task, TASK_STATUS_PREFIX, status));
        return this;
    }

    /**
     * @param tourId the id of the tour (its serialized document reference)
     * @return the status of the given tour, computed from the status of its active tasks
     * @since 0.2
     */
    public TourTaskStatus getTourStatus(String tourId)
    {
        return readStatus(getTourLocator(tourId), TOUR_STATUS_PREFIX);
    }

    /**
     * Wait until the given tour has the expected status.
     *
     * @param tourId the id of the tour (its serialized document reference)
     * @param status the expected status
     * @return this widget
     * @since 0.2
     */
    public TourWidget waitUntilTourStatus(String tourId, TourTaskStatus status)
    {
        By tour = getTourLocator(tourId);
        getDriver().waitUntilCondition(driver -> hasStatus(tour, TOUR_STATUS_PREFIX, status));
        return this;
    }

    private TourWidget setCollapsed(boolean collapsed)
    {
        if (isWidgetCollapsed() != collapsed) {
            // Clicking the top bar toggles the widget (the close button can only collapse it).
            getDriver().findElement(TOP_BAR).click();
            getDriver().waitUntilCondition(driver -> isCollapsedWithoutWaiting() == collapsed);
        }
        return this;
    }

    private void clickButton(By container, String iconClass)
    {
        getDriver().findElement(container)
            .findElement(By.xpath(".//button[contains(@class,'post-btn')]//i[contains(@class,'" + iconClass + "')]/.."))
            .click();
    }

    private boolean hasStatus(By locator, String prefix, TourTaskStatus status)
    {
        try {
            return status == readStatus(locator, prefix);
        } catch (NotFoundException | StaleElementReferenceException | IllegalArgumentException e) {
            // Not rendered yet, being re-rendered, or status not computed yet.
            return false;
        }
    }

    private TourTaskStatus readStatus(By locator, String prefix)
    {
        String classes = getDriver().findElementWithoutWaitingWithoutScrolling(locator).getAttribute(ATTRIBUTE_CLASS);
        String status = Arrays.stream(classes.split("\\s+"))
            .filter(cssClass -> cssClass.startsWith(prefix) && !cssClass.equals(prefix + "title"))
            .map(cssClass -> cssClass.substring(prefix.length())).findFirst().orElse("");
        return TourTaskStatus.fromString(status);
    }

    private boolean isTourVisible(WebElement tourElement)
    {
        String cssClass = tourElement.getAttribute(ATTRIBUTE_CLASS);
        return cssClass != null && !cssClass.contains("hidden");
    }

    private String getElementId(WebElement element)
    {
        return element.getAttribute(ATTRIBUTE_ID);
    }

    private static By getTaskLocator(String taskId)
    {
        return By.xpath("//div[contains(concat(' ', normalize-space(@class), ' '), ' guidedtour-task ') and @id="
            + escapeXPathString(taskId) + "]");
    }

    private static By getTourLocator(String tourId)
    {
        return By.xpath(getTourXPath(tourId));
    }

    /**
     * The header of a tour holds the buttons of the tour itself, while the whole tour section also holds the buttons
     * of its tasks.
     */
    private static By getTourHeaderLocator(String tourId)
    {
        return By.xpath(getTourXPath(tourId)
            + "//*[contains(concat(' ', normalize-space(@class), ' '), ' guidedtour-tour-header ')]");
    }

    private static String getTourXPath(String tourId)
    {
        return "//section[contains(concat(' ', normalize-space(@class), ' '), ' guidedtour-tour ') and @id="
            + escapeXPathString(tourId) + "]";
    }

    private static String escapeXPathString(String value)
    {
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        String[] parts = value.split("\"");
        return "concat(" + Arrays.stream(parts)
            .map(part -> "\"" + part + "\"")
            .collect(Collectors.joining(", '\"', ")) + ")";
    }
}
