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
 * Enum representing the type of action.
 *
 * @version $Id$
 * @since 1.0
 */
@Unstable
public enum ActionType
{
    /**
     * Not set: no specific action is expected, the step can be displayed whatever the current action is.
     */
    NOT_SET,
    /**
     * Edit action: represents an action that requires the user to edit content or settings.
     */
    EDIT,
    /**
     * View action: represents an action that requires the user to view content or settings without making any changes.
     */
    VIEW,
    /**
     * Delete action: represents an action that requires the user to delete a document.
     */
    DELETE,
    /**
     * Create action: represents an action that requires the user to create a new document.
     */
    CREATE,
    /**
     * Admin action: represents an action that requires the user to access the administration of a page or wiki.
     */
    ADMIN;

    /**
     * Get the ActionType from a string value.
     *
     * @param text the string value to convert
     * @return the corresponding ActionType, or {@link #NOT_SET} if the string does not match any action type
     */
    public static ActionType fromString(String text)
    {
        for (ActionType s : ActionType.values()) {
            if (s.name().equalsIgnoreCase(text)) {
                return s;
            }
        }
        return NOT_SET;
    }
}
