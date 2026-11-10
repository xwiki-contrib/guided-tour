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
package org.xwiki.contrib.guidedtour.api.enums;

import org.xwiki.stability.Unstable;

/**
 * Enum representing the placement of a step in the guided tour.
 *
 * @version $Id$
 * @since 1.0
 */
@Unstable
public enum Placement
{
    /**
     * The step is placed on the left side of the target element, aligned to the start of the element.
     */
    LEFT_START,
    /**
     * The step is placed on the left side of the target element, aligned to the center of the element.
     */
    LEFT_CENTER,
    /**
     * The step is placed on the left side of the target element, aligned to the end of the element.
     */
    LEFT_END,
    /**
     * The step is placed on the top side of the target element, aligned to the start of the element.
     */
    TOP_START,
    /**
     * The step is placed on the top side of the target element, aligned to the center of the element.
     */
    TOP_CENTER,
    /**
     * The step is placed on the top side of the target element, aligned to the end of the element.
     */
    TOP_END,
    /**
     * The step is placed on the right side of the target element, aligned to the start of the element.
     */
    RIGHT_START,
    /**
     * The step is placed on the right side of the target element, aligned to the center of the element.
     */
    RIGHT_CENTER,
    /**
     * The step is placed on the right side of the target element, aligned to the end of the element.
     */
    RIGHT_END,
    /**
     * The step is placed on the bottom side of the target element, aligned to the start of the element.
     */
    BOTTOM_START,
    /**
     * The step is placed on the bottom side of the target element, aligned to the center of the element.
     */
    BOTTOM_CENTER,
    /**
     * The step is placed on the bottom side of the target element, aligned to the end of the element.
     */
    BOTTOM_END;

    /**
     * Get the Placement from a string value.
     *
     * @param text the string value to convert
     * @return the corresponding Placement, or BOTTOM_START (the default placement of Driver.js) if the string does not
     *     match any placement
     */
    public static Placement fromString(String text)
    {
        for (Placement s : Placement.values()) {
            if (s.name().equalsIgnoreCase(text)) {
                return s;
            }
        }
        return BOTTOM_START;
    }
}
