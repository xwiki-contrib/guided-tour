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
package org.xwiki.contrib.guidedtour.script;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.guidedtour.api.dtos.TaskDTO;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.contrib.guidedtour.internal.TasksManager;
import org.xwiki.query.QueryException;
import org.xwiki.script.service.ScriptService;
import org.xwiki.stability.Unstable;

import com.xpn.xwiki.XWikiException;

/**
 * Default script service for Guided tour application.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("guidedtour")
@Singleton
@Unstable
public class GuidedTourScriptService implements ScriptService
{
    @Inject
    private TasksManager tasksManager;

    /**
     * Get the list of tasks for a given tour and filter them by the given title.
     *
     * @param tourId the id of the tour
     * @param searchedTitle the title to filter the tasks by
     * @return the list of tasks for the given tour and filtered by the given title
     */
    public List<TaskDTO> getTourTasks(String tourId, String searchedTitle)
        throws QueryException, XWikiException, InvalidIdException
    {
        List<TaskDTO> tasks = this.tasksManager.getAllTasks(tourId);
        if (searchedTitle == null || searchedTitle.isEmpty()) {
            return tasks;
        }
        return tasks.stream().filter(task -> task.getTitle().toLowerCase().contains(searchedTitle.toLowerCase()))
            .toList();
    }
}
