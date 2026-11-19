/**
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

import GuidedTourWidgetTask from "../GuidedTourWidgetTask.vue";
import { flushPromises, mount } from "@vue/test-utils";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { describe, expect, it, vi } from "vitest";
import type {
  GuidedTourManager,
  TourTask,
} from "@xwiki/contrib-guidedtour-api";

function createGuidedTourManager(): GuidedTourManager {
  return {
    skipTask: vi.fn().mockResolvedValue(undefined),
    setTaskStatus: vi.fn().mockResolvedValue(undefined),
    getSteps: vi.fn().mockResolvedValue([]),
    startTask: vi.fn().mockResolvedValue(undefined),
  } as unknown as GuidedTourManager;
}

function mountTask(task: TourTask, guidedTourManager: GuidedTourManager) {
  return mount(GuidedTourWidgetTask, {
    props: { task, tourId: "tour1" },
    global: { provide: { GuidedTourManager: guidedTourManager } },
  });
}

describe("GuidedTourWidgetTask", () => {
  it("skips the task, which disables the call to action of its tour", async () => {
    const guidedTourManager = createGuidedTourManager();
    const task: TourTask = {
      id: "task1",
      title: "Task 1",
      order: 1,
      status: TourTaskStatus.TODO,
    };
    const wrapper = mountTask(task, guidedTourManager);

    await wrapper.find(".post-btn").trigger("click");
    await flushPromises();

    expect(guidedTourManager.skipTask).toHaveBeenCalledExactlyOnceWith(task);
    expect(guidedTourManager.setTaskStatus).not.toHaveBeenCalled();
    // The click must not start the task.
    expect(guidedTourManager.startTask).not.toHaveBeenCalled();
  });

  it("resets a task that is done", async () => {
    const guidedTourManager = createGuidedTourManager();
    const task: TourTask = {
      id: "task1",
      title: "Task 1",
      order: 1,
      status: TourTaskStatus.DONE,
    };
    const wrapper = mountTask(task, guidedTourManager);

    await wrapper.find(".post-btn").trigger("click");
    await flushPromises();

    expect(guidedTourManager.setTaskStatus).toHaveBeenCalledExactlyOnceWith(
      task,
      TourTaskStatus.TODO,
    );
    expect(guidedTourManager.skipTask).not.toHaveBeenCalled();
  });
});
