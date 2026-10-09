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
package org.xwiki.contrib.guidedtour.api.dtos;

import java.util.HashMap;
import java.util.Map;

import org.xwiki.contrib.guidedtour.api.enums.WidgetState;
import org.xwiki.stability.Unstable;

/**
 * User tour status DTO used to represent the status of the user progress and preferences regarding the guided tour.
 *
 * @version $Id$
 * @since 1.0
 */
@Unstable
public class UserTourStatusDTO
{
    private WidgetState widgetState;

    private Map<String, TourProgressDTO> toursStatus;

    /**
     * Default constructor.
     */
    public UserTourStatusDTO()
    {
        this.toursStatus = new HashMap<>();
    }

    /**
     * Constructor for UserTourStatusDTO.
     *
     * @param widgetState the state of the widget representing a value from the {@link WidgetState} enum
     */
    public UserTourStatusDTO(String widgetState)
    {
        this.toursStatus = new HashMap<>();
        this.widgetState = WidgetState.fromString(widgetState);
    }

    /**
     * Gets the progress of the user in each tour.
     *
     * @return a map containing the tour id as key and the progress of the user in that tour as value
     */
    public Map<String, TourProgressDTO> getToursStatus()
    {
        return this.toursStatus;
    }

    /**
     * Sets the progress of the user in each tour.
     *
     * @param toursStatus a map containing the tour id as key and the progress of the user in that tour as value
     */
    public void setToursStatus(Map<String, TourProgressDTO> toursStatus)
    {
        this.toursStatus = toursStatus;
    }

    /**
     * Gets the widget state.
     *
     * @return the state of the widget representing a value from the {@link WidgetState} enum
     */
    public WidgetState getWidgetState()
    {
        return this.widgetState;
    }

    /**
     * Sets the widget state.
     *
     * @param widgetState the state of the widget representing a value from the {@link WidgetState} enum
     */
    public void setWidgetState(String widgetState)
    {
        this.widgetState = WidgetState.fromString(widgetState);
    }
}
