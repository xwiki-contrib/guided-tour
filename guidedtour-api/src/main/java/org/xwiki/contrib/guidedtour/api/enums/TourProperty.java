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
 * Enum representing the properties of a Tour. Each enum constant holds the base key, which is the property name used to
 * access the value in the XWiki object.
 *
 * @version $Id$
 * @since 1.0
 */
@Unstable
public enum TourProperty
{
    /**
     * The title key.
     */
    TITLE("title"),

    /**
     * The isActive key.
     */
    IS_ACTIVE("isActive"),
    /**
     * The dependsOn key. It represents the dependencies of a task on other tasks.
     */
    DEPENDS_ON("dependsOn"),
    /**
     * The order key.
     */
    ORDER("order"),
    /**
     * The element key, representing the CSS selector of the element targeted by the step.
     */
    ELEMENT("element"),
    /**
     * The content key, representing the content to be displayed in the step.
     */
    CONTENT("content"),
    /**
     * The placement key, representing the placement of the step in relation to the target.
     */
    PLACEMENT("placement"),
    /**
     * The backdrop key, representing whether a backdrop should be displayed behind the step.
     */
    BACKDROP("backdrop"),
    /**
     * The reflex key, representing whether the task should progress when interacting with the target element.
     */
    REFLEX("reflex"),
    /**
     * The targetPage key, representing the page to navigate to when the step is reached.
     */
    TARGET_PAGE("targetPage"),
    /**
     * The targetAction key, representing the action to perform on the target page when the step is reached.
     */
    TARGET_ACTION("targetAction"),
    /**
     * The queryParameters key, representing the query parameters to append to the URL when navigating to the target
     * page.
     */
    QUERY_PARAMETERS("queryParameters"),
    /**
     * The description key.
     *
     * @since 0.2
     */
    DESCRIPTION("description");

    private final String baseKey;

    /**
     * Constructor for TourProperty.
     *
     * @param baseKey the base key of the property
     */
    TourProperty(String baseKey)
    {
        this.baseKey = baseKey;
    }

    /**
     * Returns the base key of the property, which is the property name used to access the value in the XWiki object.
     *
     * @return the base key of the property
     */
    public String getBaseKey()
    {
        return this.baseKey;
    }
}
