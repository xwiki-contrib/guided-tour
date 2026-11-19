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

/**
 * The status of a guided tour task, or of a whole tour, as displayed by the guided tour widget.
 *
 * @version $Id$
 * @since 0.2
 */
public enum TourTaskStatus
{
    /**
     * The task (or at least one task of the tour) still has to be done.
     */
    TODO,

    /**
     * The task was skipped (or the tour has no task left to do, and at least one was skipped).
     */
    SKIPPED,

    /**
     * The task was completed (or all the tasks of the tour were completed).
     */
    DONE;

    /**
     * @param value the status name, as used in the CSS classes of the widget and in the stored user status
     * @return the matching status
     * @throws IllegalArgumentException if the value doesn't match any status
     */
    public static TourTaskStatus fromString(String value)
    {
        return Arrays.stream(values()).filter(status -> status.name().equals(value)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown guided tour status [%s].".formatted(value)));
    }
}
