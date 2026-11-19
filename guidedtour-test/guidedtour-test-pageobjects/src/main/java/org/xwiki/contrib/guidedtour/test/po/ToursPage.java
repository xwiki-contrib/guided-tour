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

import org.openqa.selenium.WebElement;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.test.ui.po.DeletePageConfirmationPage;
import org.xwiki.test.ui.po.DeletingPage;
import org.xwiki.test.ui.po.ViewPage;

/**
 * The page listing all the guided tours ({@code GuidedTour.Tours.WebHome}).
 *
 * @version $Id$
 * @since 0.2
 */
public class ToursPage extends ViewPage
{
    private static final String TOURS_LIVE_DATA_ID = "guided-tours-overview";

    private static final String TITLE_COLUMN = "Title";

    private static final String IS_ACTIVE_COLUMN = "Is active";

    /**
     * Go to the page listing all the guided tours.
     *
     * @return the page listing the tours
     */
    public static ToursPage gotoPage()
    {
        getUtil().gotoPage(List.of("GuidedTour", "Tours"), "WebHome", "view", (String) null);
        return new ToursPage();
    }

    /**
     * @return the table listing the tours
     */
    private TableLayoutElement getToursTable()
    {
        return new LiveDataElement(TOURS_LIVE_DATA_ID).getTableLayout();
    }

    /**
     * @return the titles of the tours currently listed, in the displayed order
     */
    public List<String> getTourTitles()
    {
        return getToursTable().getAllCells(TITLE_COLUMN).stream().map(WebElement::getText).toList();
    }

    /**
     * @param title the title of a listed tour
     * @return {@code "Yes"} or {@code "No"}, as displayed in the "Is active" column of the tour
     */
    public String getTourActiveLabel(String title)
    {
        return getToursTable().getCell(IS_ACTIVE_COLUMN, getRowNumber(title)).getText();
    }

    /**
     * Only list the tours whose title contains the given text, and wait for the list to be updated.
     *
     * @param title the text to look for in the tour titles
     * @return this page
     */
    public ToursPage filterByTitle(String title)
    {
        getToursTable().filterColumn(TITLE_COLUMN, title);
        return this;
    }

    /**
     * Only list the active, or inactive, tours, and wait for the list to be updated.
     *
     * @param active {@code true} to list the active tours, {@code false} to list the inactive ones
     * @return this page
     */
    public ToursPage filterByActive(boolean active)
    {
        // The boolean filter of the Live Data labels its options "True" and "False".
        getToursTable().filterColumn(IS_ACTIVE_COLUMN, active ? "True" : "False");
        return this;
    }

    /**
     * Remove the filters of the list of tours, and wait for the list to be updated.
     *
     * @return this page
     */
    public ToursPage clearFilters()
    {
        LiveDataElement tours = new LiveDataElement(TOURS_LIVE_DATA_ID);
        tours.clearAllFilters();
        tours.waitUntilReady();
        return this;
    }

    /**
     * Open a tour, with the "View" action of its row.
     *
     * @param title the title of the tour
     * @return the tour page
     */
    public TourPage viewTour(String title)
    {
        clickActionAndWaitForPageReload(title, "view");
        return new TourPage();
    }

    /**
     * Edit a tour, with the "Edit" action of its row.
     *
     * @param title the title of the tour
     * @return the tour edit form
     */
    public TourEditPage editTour(String title)
    {
        clickActionAndWaitForPageReload(title, "edit");
        return new TourEditPage();
    }

    /**
     * Delete a tour page, with the "Delete" action of its row, confirming the deletion and waiting for it to finish.
     * Only the tour page is deleted, not its tasks.
     *
     * @param title the title of the tour
     * @return {@code true} if the tour was deleted successfully
     */
    public boolean deleteTour(String title)
    {
        clickActionAndWaitForPageReload(title, "delete");
        DeletePageConfirmationPage confirmation = new DeletePageConfirmationPage();
        if (confirmation.hasAffectChildrenOption()) {
            confirmation.setAffectChildren(false);
        }
        DeletingPage deleting = confirmation.confirmDeletePage();
        deleting.waitUntilFinished();
        return deleting.isSuccess();
    }

    private void clickActionAndWaitForPageReload(String title, String action)
    {
        int rowNumber = getRowNumber(title);
        getDriver().addPageNotYetReloadedMarker();
        getToursTable().clickAction(rowNumber, action);
        getDriver().waitUntilPageIsReloaded();
    }

    private int getRowNumber(String title)
    {
        List<String> titles = getTourTitles();
        int rowNumber = titles.indexOf(title) + 1;
        if (rowNumber == 0) {
            throw new IllegalArgumentException("No tour with the title [%s] in %s.".formatted(title, titles));
        }
        return rowNumber;
    }
}
