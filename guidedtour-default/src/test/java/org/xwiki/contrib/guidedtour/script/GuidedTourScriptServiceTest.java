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

import com.xpn.xwiki.XWikiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.guidedtour.api.dtos.TaskDTO;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.contrib.guidedtour.internal.TasksManager;
import org.xwiki.query.QueryException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link GuidedTourScriptService}.
 */
@ComponentTest
class GuidedTourScriptServiceTest
{
    private static final String TOUR_ID = "tourId";

    private final List<TaskDTO> tasks = new ArrayList<>();
    @InjectMockComponents
    private GuidedTourScriptService guidedTourScriptService;
    @MockComponent
    private TasksManager tasksManager;

    @BeforeEach
    void setup() throws QueryException, XWikiException, InvalidIdException
    {
        TaskDTO task1 = new TaskDTO();
        task1.setTitle("TASK TITLE 1");
        TaskDTO task2 = new TaskDTO();
        task2.setTitle("task title 2");
        TaskDTO task3 = new TaskDTO();
        task3.setTitle("random name 3");
        this.tasks.add(task1);
        this.tasks.add(task2);
        this.tasks.add(task3);
        when(this.tasksManager.getAllTasks(TOUR_ID)).thenReturn(this.tasks);
    }

    @Test
    void getTourTasks() throws Exception
    {
        List<TaskDTO> result = this.guidedTourScriptService.getTourTasks(TOUR_ID, "");
        assertEquals(3, result.size());
    }

    @Test
    void getTourTasksTitleFilter() throws Exception
    {
        List<TaskDTO> result = this.guidedTourScriptService.getTourTasks(TOUR_ID, "TiTlE");
        assertEquals(2, result.size());
        assertEquals("TASK TITLE 1", result.get(0).getTitle());
        assertEquals("task title 2", result.get(1).getTitle());
    }
}
