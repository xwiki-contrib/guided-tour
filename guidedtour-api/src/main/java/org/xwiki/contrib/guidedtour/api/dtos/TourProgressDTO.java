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

import org.xwiki.contrib.guidedtour.api.enums.Status;
import org.xwiki.stability.Unstable;

/**
 * The progress of a user in one tour: the status of its tasks and whether the next task of the tour should be started
 * automatically.
 *
 * @version $Id$
 * @since 1.0
 */
@Unstable
public class TourProgressDTO
{
    private boolean callToAction = true;

    private Map<String, Status> tasksStatus = new HashMap<>();

    /**
     * Checks if the next task of the tour should be started automatically when a task is finished.
     *
     * @return {@code true} if the next task should be started automatically, {@code false} otherwise
     */
    public boolean isCallToAction()
    {
        return this.callToAction;
    }

    /**
     * Sets whether the next task of the tour should be started automatically when a task is finished.
     *
     * @param callToAction {@code true} if the next task should be started automatically, {@code false} otherwise
     */
    public void setCallToAction(boolean callToAction)
    {
        this.callToAction = callToAction;
    }

    /**
     * Gets the status of the tasks of the tour.
     *
     * @return a map containing the task id as key and the task status as value
     */
    public Map<String, Status> getTasksStatus()
    {
        return this.tasksStatus;
    }

    /**
     * Sets the status of the tasks of the tour.
     *
     * @param tasksStatus a map containing the task id as key and the task status as value
     */
    public void setTasksStatus(Map<String, Status> tasksStatus)
    {
        this.tasksStatus = tasksStatus;
    }
}
