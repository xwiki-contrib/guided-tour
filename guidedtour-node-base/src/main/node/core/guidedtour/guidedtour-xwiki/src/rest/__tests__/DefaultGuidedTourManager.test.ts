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

import { wrapTask } from "../../driverjsMain";
import { DefaultGuidedTourManager } from "../DefaultGuidedTourManager";
import { TourStore } from "../TourStore";
import { TourTaskStatus, WidgetState } from "@xwiki/contrib-guidedtour-api";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
  TourStep,
  TourTask,
  TourTour,
  UserTourStatus,
} from "@xwiki/contrib-guidedtour-api";
import type { Driver } from "driver.js";

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  loadStatus: vi.fn(),
  saveStatus: vi.fn(),
}));

vi.mock("../../services/xwiki.js", () => ({
  XWiki: { contextPath: "/xwiki" },
}));

vi.mock("../../driverjsMain", () => ({
  driver: vi.fn(),
  getDriverConfigForSteps: vi.fn(),
  wrapTask: vi.fn(),
}));

vi.mock("../GuidedTourRestClient", () => ({
  GuidedTourRestClient: class {
    request = mocks.request;
  },
}));

vi.mock("../DefaultUserStatusApi", () => ({
  DefaultUserStatusApi: class {
    load = mocks.loadStatus;
    save = mocks.saveStatus;
  },
}));

const { TODO, DONE, SKIPPED } = TourTaskStatus;

function createTask(
  id: string,
  order: number,
  status: TourTaskStatus = TODO,
  active = true,
): TourTask {
  return { id, title: id, order, status, active };
}

/**
 * The tours returned by the server, with the default status of their tasks.
 */
function createTours(): TourTour[] {
  return [
    {
      id: "tour1",
      title: "Tour 1",
      tasksList: [
        createTask("task1", 1),
        createTask("task2", 2),
        createTask("inactive", 0, TODO, false),
        createTask("task3", 3),
      ],
    },
    {
      id: "tour2",
      title: "Tour 2",
      tasksList: [createTask("task4", 1), createTask("task5", 2)],
    },
  ];
}

function createUserStatus(
  toursStatus: UserTourStatus["toursStatus"] = {},
): UserTourStatus {
  return { widgetState: WidgetState.OPEN, toursStatus };
}

let manager: DefaultGuidedTourManager;
// A copy of each saved status, since the manager keeps changing the same object.
let savedStatuses: UserTourStatus[];

function getLastSavedStatus(): UserTourStatus {
  return savedStatuses[savedStatuses.length - 1];
}

async function getTask(tourId: string, taskId: string): Promise<TourTask> {
  return (await manager.getTask(tourId, taskId))!;
}

/**
 * Make the given task the one in progress, as if it was started by the user.
 */
async function activateTask(tourId: string, taskId: string) {
  manager.activeTask = await getTask(tourId, taskId);
  manager.activeDriverTask = { destroy: vi.fn() } as unknown as Driver;
}

/**
 * Fake the current page, always Main.WebHome, so that the navigation can be checked without happy-dom performing it.
 * @param action - The current action.
 * @param suffix - The query string and fragment of the current URL.
 * @returns the fake location.
 */
function setCurrentPage(action: string, suffix = "") {
  vi.stubGlobal("XWiki", {
    contextaction: action,
    EntityType: { DOCUMENT: "document" },
    currentDocument: { documentReference: "Main.WebHome" },
    Model: {
      resolve: (reference: string) => reference,
      serialize: (reference: string) => reference,
    },
    Document: function (reference: string) {
      return {
        getURL: (urlAction = "view") =>
          `/xwiki/bin/${urlAction}/${reference.replace(".", "/")}`,
      };
    },
  });
  const location = Object.assign(
    new URL(`http://localhost/xwiki/bin/${action}/Main/WebHome${suffix}`),
    { reload: vi.fn() },
  );
  vi.stubGlobal("location", location);
  return location;
}

function createStep(step: Partial<TourStep> = {}): TourStep {
  return { order: 0, content: "", reflex: false, ...step };
}

async function loadManager(userStatus: UserTourStatus) {
  mocks.loadStatus.mockResolvedValue(userStatus);
  const xm = { userReference: "xwiki:XWiki.Alice" };
  manager = new DefaultGuidedTourManager(xm, new TourStore(xm), {});
  await manager.getTours();
}

describe("DefaultGuidedTourManager", () => {
  beforeEach(() => {
    savedStatuses = [];
    mocks.request.mockResolvedValue(createTours());
    mocks.saveStatus.mockImplementation((status: UserTourStatus) => {
      savedStatuses.push(structuredClone(status));
      return Promise.resolve();
    });
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  describe("user progress", () => {
    it("applies the stored progress to the tasks", async () => {
      await loadManager(
        createUserStatus({
          tour1: { callToAction: true, tasksStatus: { task1: DONE } },
        }),
      );

      expect((await getTask("tour1", "task1")).status).toBe(DONE);
      expect((await getTask("tour1", "task2")).status).toBe(TODO);
    });

    it("saves the status of the loaded tours and keeps the progress of the other tours", async () => {
      const otherTourProgress = {
        callToAction: false,
        tasksStatus: { otherTask: DONE },
      };
      await loadManager(
        createUserStatus({
          otherWikiTour: otherTourProgress,
          tour1: { callToAction: true, tasksStatus: { removedTask: DONE } },
        }),
      );

      await manager.setTaskStatus(await getTask("tour1", "task2"), DONE);

      expect(getLastSavedStatus().toursStatus).toEqual({
        otherWikiTour: otherTourProgress,
        // The removed task is not kept, and the tour that was never started is not saved.
        tour1: {
          callToAction: true,
          tasksStatus: {
            task1: TODO,
            task2: DONE,
            inactive: TODO,
            task3: TODO,
          },
        },
      });
    });

    it("forgets the progress of a tour that is reset", async () => {
      await loadManager(
        createUserStatus({
          tour1: { callToAction: false, tasksStatus: { task1: DONE } },
        }),
      );

      await manager.resetTour("tour1");

      expect(mocks.saveStatus).toHaveBeenCalledTimes(1);
      expect(getLastSavedStatus().toursStatus).toEqual({});
      expect((await getTask("tour1", "task1")).status).toBe(TODO);
    });

    it("skips only the tasks left to do when skipping a tour", async () => {
      await loadManager(
        createUserStatus({
          tour1: { callToAction: true, tasksStatus: { task1: DONE } },
        }),
      );

      await manager.skipTour("tour1");

      expect(mocks.saveStatus).toHaveBeenCalledTimes(1);
      expect(getLastSavedStatus().toursStatus.tour1.tasksStatus).toEqual({
        task1: DONE,
        task2: SKIPPED,
        inactive: SKIPPED,
        task3: SKIPPED,
      });
    });

    it("disables the call to action of the tour when skipping a task", async () => {
      await loadManager(createUserStatus());

      await manager.skipTask(await getTask("tour1", "task1"));

      expect(getLastSavedStatus().toursStatus.tour1).toEqual({
        callToAction: false,
        tasksStatus: {
          task1: SKIPPED,
          task2: TODO,
          inactive: TODO,
          task3: TODO,
        },
      });
    });

    it("remembers the disabled call to action of a tour that has no progress", async () => {
      await loadManager(createUserStatus());

      // The user closes the first task of the tour before its end.
      await manager.disableCallToAction("tour1");
      await manager.setTaskStatus(await getTask("tour1", "task1"), TODO);

      // All the tasks are still to do, but the tour must not start its next tasks automatically anymore.
      expect(getLastSavedStatus().toursStatus.tour1.callToAction).toBe(false);
    });
  });

  describe("when the active task is done", () => {
    it("starts the next task to do of the same tour", async () => {
      await loadManager(
        createUserStatus({
          tour1: { callToAction: true, tasksStatus: { task2: DONE } },
        }),
      );
      const startTask = vi.spyOn(manager, "startTask").mockResolvedValue();
      await activateTask("tour1", "task1");

      await manager.setTaskStatus(manager.activeTask!, DONE);

      // Neither the inactive task, nor the task already done.
      expect(startTask).toHaveBeenCalledExactlyOnceWith(
        await getTask("tour1", "task3"),
        false,
      );
    });

    it("doesn't start any task when the tour has no task left to do", async () => {
      await loadManager(createUserStatus());
      const startTask = vi.spyOn(manager, "startTask").mockResolvedValue();
      await activateTask("tour2", "task5");
      (await getTask("tour2", "task4")).status = DONE;

      await manager.setTaskStatus(manager.activeTask!, DONE);

      expect(startTask).not.toHaveBeenCalled();
    });

    it("doesn't start the next task when the call to action of the tour is disabled", async () => {
      await loadManager(
        createUserStatus({
          tour1: { callToAction: false, tasksStatus: {} },
        }),
      );
      const startTask = vi.spyOn(manager, "startTask").mockResolvedValue();
      await activateTask("tour1", "task1");

      await manager.setTaskStatus(manager.activeTask!, DONE);

      expect(startTask).not.toHaveBeenCalled();
    });
  });

  it("doesn't start the next task when the task done is not the active one", async () => {
    await loadManager(createUserStatus());
    const startTask = vi.spyOn(manager, "startTask").mockResolvedValue();

    await manager.setTaskStatus(await getTask("tour1", "task1"), DONE);

    expect(startTask).not.toHaveBeenCalled();
  });

  describe("redirect to the expected page", () => {
    beforeEach(async () => {
      await loadManager(createUserStatus());
    });

    it("stays on the current page when it matches the step", () => {
      const location = setCurrentPage("edit", "?editor=wiki&other=1#H1");

      expect(
        manager.redirectToExpectedPage(
          createStep({
            targetPage: "Main.WebHome",
            targetAction: "EDIT",
            // The leading "?" is optional, and other query parameters or fragment are allowed.
            queryParameters: "?editor=wiki",
          }),
        ),
      ).toBe(false);
      expect(location.href).toBe(
        "http://localhost/xwiki/bin/edit/Main/WebHome?editor=wiki&other=1#H1",
      );
    });

    it("accepts any action when the step doesn't expect one", () => {
      setCurrentPage("edit");

      expect(manager.redirectToExpectedPage(createStep())).toBe(false);
    });

    it("considers the in-place editor as the edit action", () => {
      setCurrentPage("view", "#edit");

      expect(
        manager.redirectToExpectedPage(createStep({ targetAction: "EDIT" })),
      ).toBe(false);
    });

    it("doesn't redirect without a step", () => {
      setCurrentPage("view");

      expect(manager.redirectToExpectedPage(undefined)).toBe(false);
    });

    it.each([
      [
        "another page",
        { targetPage: "Sandbox.WebHome" },
        "/xwiki/bin/view/Sandbox/WebHome",
      ],
      [
        "another action",
        { targetAction: "EDIT" },
        "/xwiki/bin/edit/Main/WebHome",
      ],
      [
        "other query parameters",
        { queryParameters: "editor=wiki" },
        "/xwiki/bin/view/Main/WebHome?editor=wiki",
      ],
    ])("redirects when the step expects %s", (_, step, expectedPath) => {
      const location = setCurrentPage("view", "?editor=wysiwyg");

      expect(manager.redirectToExpectedPage(createStep(step))).toBe(true);
      expect(location.href).toBe("http://localhost" + expectedPath);
      expect(location.reload).not.toHaveBeenCalled();
    });

    it("reloads the page when only the fragment changes", () => {
      const location = setCurrentPage("view");

      expect(
        manager.redirectToExpectedPage(
          createStep({ queryParameters: "#Comments" }),
        ),
      ).toBe(true);
      expect(location.href).toBe(
        "http://localhost/xwiki/bin/view/Main/WebHome#Comments",
      );
      expect(location.reload).toHaveBeenCalledOnce();
    });
  });

  describe("starting a task", () => {
    const drive = vi.fn();
    let task: TourTask;

    beforeEach(async () => {
      await loadManager(createUserStatus());
      vi.mocked(wrapTask).mockReturnValue({ drive } as unknown as Driver);
      task = {
        ...createTask("task1", 1),
        tourId: "tour1",
        steps: [
          createStep({ targetPage: "Sandbox.WebHome" }),
          createStep({ order: 1, targetAction: "EDIT" }),
        ],
      };
    });

    it("redirects to the page of the first step before starting the task", async () => {
      const location = setCurrentPage("view");

      await manager.startTask(task, false);

      expect(location.href).toBe(
        "http://localhost/xwiki/bin/view/Sandbox/WebHome",
      );
      expect(manager.activeTask).toBeUndefined();
      expect(drive).not.toHaveBeenCalled();
      // The task is resumed from the storage once on the expected page.
      expect(sessionStorage.getItem("guidedtour_activeTask")).toBe(
        "guidedtour_tour1__task1",
      );
      expect(
        sessionStorage.getItem("guidedtour_tour1__task1_currentStep"),
      ).toBe("0");
    });

    it("doesn't redirect when resuming a task", async () => {
      const location = setCurrentPage("view");
      sessionStorage.setItem("guidedtour_tour1__task1_currentStep", "1");

      await manager.startTask(task);

      expect(location.href).toBe(
        "http://localhost/xwiki/bin/view/Main/WebHome",
      );
      expect(manager.activeTask).toBe(task);
      expect(drive).toHaveBeenCalledExactlyOnceWith(1);
    });
  });

  it("ignores the target action of the steps that don't set one", async () => {
    await loadManager(createUserStatus());
    mocks.request.mockResolvedValueOnce([
      createStep({ targetAction: "NOT_SET" }),
      createStep({ order: 1, targetAction: "EDIT" }),
    ]);

    const steps = await manager.getSteps("tour1", "task1");

    expect(steps.map((step) => step.targetAction)).toEqual([undefined, "EDIT"]);
  });

  it("gets and saves the widget state", async () => {
    await loadManager({
      ...createUserStatus(),
      widgetState: WidgetState.COLLAPSED,
    });

    expect(await manager.getWidgetState()).toBe(WidgetState.COLLAPSED);
    await manager.setWidgetState(WidgetState.OPEN);

    expect(getLastSavedStatus().widgetState).toBe(WidgetState.OPEN);
  });
});
