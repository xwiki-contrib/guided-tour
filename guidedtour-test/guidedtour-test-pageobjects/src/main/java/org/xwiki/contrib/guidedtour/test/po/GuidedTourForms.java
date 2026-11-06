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

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

/**
 * Helpers to fill the fields displayed by the guided tour sheets in edit mode.
 *
 * @version $Id$
 * @since 0.2
 */
final class GuidedTourForms
{
    private GuidedTourForms()
    {
    }

    /**
     * Replace the value of a text input or text area.
     *
     * @param field the field
     * @param value the new value
     */
    static void setText(WebElement field, String value)
    {
        field.clear();
        field.sendKeys(value);
    }

    /**
     * Set the value of a boolean property, displayed either as a select (Yes / No) or as a checkbox.
     *
     * @param field the field
     * @param value the new value
     */
    static void setBoolean(WebElement field, boolean value)
    {
        if ("select".equalsIgnoreCase(field.getTagName())) {
            new Select(field).selectByValue(value ? "1" : "0");
        } else if (field.isSelected() != value) {
            field.click();
        }
    }
}
