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
 * Enum representing the properties of a Tour. Each enum constant has a base key and an optional suffix to form the
 * complete key used in the solr query for the XWiki Object.
 *
 * @version $Id$
 * @since 1.0
 */
@Unstable
public enum TourProperty
{
    /**
     * The title key as a base key and string suffix representing the field type.
     */
    TITLE("title", String.class.getSimpleName().toLowerCase()),
    /**
     * The isActive key as a base key and boolean suffix representing the field type. When installing Tour pages from
     * page exports, the boolean fields are indexed as int, so two properties are needed for the bool fields.
     */
    IS_ACTIVE_INT(TourProperty.IS_ACTIVE_PROP_NAME, "int"),
    /**
     * The isActive key as a base key and boolean suffix representing the field type. When installing Tour pages from
     * page exports, the boolean fields are indexed as int, so two properties are needed for the bool fields.
     */
    IS_ACTIVE_BOOL(TourProperty.IS_ACTIVE_PROP_NAME, "boolean"),
    /**
     * The dependsOn key as a base key and string suffix representing the field type. It represents the dependencies of
     * a task on other tasks.
     */
    DEPENDS_ON("dependsOn", String.class.getSimpleName().toLowerCase()),
    /**
     * The order key as a base key and long suffix representing the field type.
     */
    ORDER("order", Long.class.getSimpleName().toLowerCase()),
    /**
     * The element key as a base key without suffix, representing the CSS selector of the element targeted by the step.
     */
    ELEMENT("element", null),
    /**
     * The content key as a base key without suffix, representing the content to be displayed in the step.
     */
    CONTENT("content", null),
    /**
     * The placement key as a base key without suffix, representing the placement of the step in relation to the
     * target.
     */
    PLACEMENT("placement", null),
    /**
     * The backdrop key as a base key without suffix, representing whether a backdrop should be displayed behind the
     * step.
     */
    BACKDROP("backdrop", null),
    /**
     * The reflex key as a base key without suffix, representing whether the task should progress when interacting with
     * the target element.
     */
    REFLEX("reflex", null),
    /**
     * The targetPage key as a base key without suffix, representing the page to navigate to when the step is reached.
     */
    TARGET_PAGE("targetPage", null),
    /**
     * The targetAction key as a base key without suffix, representing the action to perform on the target page when the
     * step is reached.
     */
    TARGET_ACTION("targetAction", null),
    /**
     * The queryParameters key as a base key without suffix, representing the query parameters to append to the URL when
     * navigating to the target page.
     */
    QUERY_PARAMETERS("queryParameters", null);

    private static final String IS_ACTIVE_PROP_NAME = "isActive";

    private final String baseKey;

    private final String suffix;

    /**
     * Constructor for TourProperty.
     *
     * @param baseKey the base key of the property
     * @param suffix the suffix to form the complete key, or null if no suffix is needed
     */
    TourProperty(String baseKey, String suffix)
    {
        this.baseKey = baseKey;
        this.suffix = suffix;
    }

    /**
     * Returns the base key of the property, without the suffix.
     *
     * @return the base key of the property
     */
    public String getBaseKey()
    {
        return baseKey;
    }

    /**
     * If a suffix is defined, it appends it to the base key with an underscore.
     *
     * @return the base key and suffix concatenated with an underscore if the suffix is not null, otherwise just the
     *     base key
     */
    public String getObjectKey()
    {
        return (suffix == null) ? baseKey : baseKey + "_" + suffix;
    }

    /**
     * Formats the value from {@link #getObjectKey()} and a given prefix.
     *
     * @param stringFormat the string format to use, which should include a placeholder for the object key
     * @return the formatted key with the given format and the object key
     */
    public String formKey(String stringFormat)
    {
        return String.format(stringFormat, getObjectKey());
    }
}
