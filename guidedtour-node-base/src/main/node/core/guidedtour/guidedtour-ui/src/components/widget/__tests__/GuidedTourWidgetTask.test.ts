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

function createTask(status: TourTaskStatus): TourTask {
  return { id: "task1", title: "Task 1", order: 1, status };
}

function mountTask(
  task: TourTask,
  guidedTourManager: GuidedTourManager,
  errorHandler?: (error: unknown) => void,
) {
  return mount(GuidedTourWidgetTask, {
    props: { task, tourId: "tour1" },
    global: {
      provide: { GuidedTourManager: guidedTourManager },
      config: { errorHandler },
    },
  });
}

function isWaiting(wrapper: ReturnType<typeof mountTask>) {
  return !wrapper.find(".fa-circle-notch").classes("transparent");
}

/**
 * Make the given method of the manager wait until the test resolves or rejects it.
 */
function holdCall(
  guidedTourManager: GuidedTourManager,
  method: "getSteps" | "skipTask" | "setTaskStatus",
) {
  let resolveCall: () => void = () => {};
  let rejectCall: (error: Error) => void = () => {};
  const promise = new Promise((resolve, reject) => {
    resolveCall = () => resolve(undefined);
    rejectCall = reject;
  });
  vi.mocked(guidedTourManager[method]).mockReturnValue(promise as never);
  return {
    resolve: () => resolveCall(),
    reject: (error: Error) => rejectCall(error),
  };
}

describe("GuidedTourWidgetTask", () => {
  it("skips a task left to do, without starting it", async () => {
    const guidedTourManager = createGuidedTourManager();
    const task = createTask(TourTaskStatus.TODO);
    const wrapper = mountTask(task, guidedTourManager);

    await wrapper.find(".post-btn").trigger("click");
    await flushPromises();

    expect(guidedTourManager.skipTask).toHaveBeenCalledExactlyOnceWith(task);
    expect(guidedTourManager.setTaskStatus).not.toHaveBeenCalled();
    // The click must not start the task.
    expect(guidedTourManager.startTask).not.toHaveBeenCalled();
  });

  it.each(Object.values(TourTaskStatus))(
    "shows the status %s of the task",
    (status) => {
      const wrapper = mountTask(createTask(status), createGuidedTourManager());

      expect(wrapper.classes()).toContain(`task-${status}`);
    },
  );

  it("resets a task that is done", async () => {
    const guidedTourManager = createGuidedTourManager();
    const task = createTask(TourTaskStatus.DONE);
    const wrapper = mountTask(task, guidedTourManager);

    await wrapper.find(".post-btn").trigger("click");
    await flushPromises();

    expect(guidedTourManager.setTaskStatus).toHaveBeenCalledExactlyOnceWith(
      task,
      TourTaskStatus.TODO,
    );
    expect(guidedTourManager.skipTask).not.toHaveBeenCalled();
  });

  it.each([
    ["skipping", TourTaskStatus.TODO, "skipTask"],
    ["resetting", TourTaskStatus.DONE, "setTaskStatus"],
  ] as const)(
    "shows a spinner while %s the task",
    async (_description, status, method) => {
      const guidedTourManager = createGuidedTourManager();
      const call = holdCall(guidedTourManager, method);
      const wrapper = mountTask(createTask(status), guidedTourManager);

      await wrapper.find(".post-btn").trigger("click");
      expect(isWaiting(wrapper)).toBe(true);

      call.resolve();
      await flushPromises();
      expect(isWaiting(wrapper)).toBe(false);
    },
  );

  it("shows a spinner while fetching the steps of the task", async () => {
    const guidedTourManager = createGuidedTourManager();
    const getSteps = holdCall(guidedTourManager, "getSteps");
    const wrapper = mountTask(
      createTask(TourTaskStatus.TODO),
      guidedTourManager,
    );

    await wrapper.find(".guidedtour-task").trigger("click");
    expect(isWaiting(wrapper)).toBe(true);
    expect(guidedTourManager.startTask).not.toHaveBeenCalled();

    getSteps.resolve();
    await flushPromises();
    expect(isWaiting(wrapper)).toBe(false);
  });

  it("starts the task once its steps are fetched", async () => {
    const guidedTourManager = createGuidedTourManager();
    const task = createTask(TourTaskStatus.TODO);
    const wrapper = mountTask(task, guidedTourManager);

    await wrapper.find(".guidedtour-task").trigger("click");
    await flushPromises();

    expect(guidedTourManager.startTask).toHaveBeenCalledExactlyOnceWith(
      task,
      false,
    );
  });

  it("doesn't start the task when its steps can't be fetched", async () => {
    const guidedTourManager = createGuidedTourManager();
    const getSteps = holdCall(guidedTourManager, "getSteps");
    // The component doesn't handle the error (the user gets no feedback yet), so let Vue ignore it instead of failing
    // the test run.
    const wrapper = mountTask(
      createTask(TourTaskStatus.TODO),
      guidedTourManager,
      () => {},
    );

    await wrapper.find(".guidedtour-task").trigger("click");
    getSteps.reject(new Error("Failed"));
    await flushPromises();

    expect(isWaiting(wrapper)).toBe(false);
    expect(guidedTourManager.startTask).not.toHaveBeenCalled();
  });
});
