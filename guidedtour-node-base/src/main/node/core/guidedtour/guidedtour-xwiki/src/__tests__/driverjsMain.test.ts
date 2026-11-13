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

import { StorageManager } from "../StorageManager";
import {
  XWikiDriverConfig,
  getDriverConfigForSteps,
  wrapTask,
} from "../driverjsMain";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DefaultGuidedTourManager } from "../rest/DefaultGuidedTourManager";
import type { TourStep, TourTask } from "@xwiki/contrib-guidedtour-api";
import type { Config, Driver, HookOpts, PopoverDOM } from "driver.js";

const TRANSLATIONS = {
  "guidedtour.driver.skipAll": "Skip all",
  "guidedtour.driver.loading": "Loading",
  "guidedtour.driver.error": "Error",
};

/**
 * The XWiki notifications shown during the test, in order.
 */
const notifications: {
  text: string;
  type: string;
  hide: ReturnType<typeof vi.fn>;
}[] = [];

function createStep(step: Partial<TourStep> = {}): TourStep {
  return { order: 0, content: "", reflex: false, ...step };
}

/**
 * A task with 4 steps, whose orders start at 1 like the steps created by the REST API.
 */
function createTask(): TourTask {
  return {
    id: "task1",
    tourId: "tour1",
    title: "Task",
    status: TourTaskStatus.TODO,
    order: 0,
    steps: [
      createStep({ order: 1 }),
      createStep({ order: 2 }),
      createStep({ order: 3, element: "#reflex", reflex: true }),
      createStep({ order: 4 }),
    ],
  };
}

/**
 * The guided tour manager running the given task, which is at the given step.
 */
function createGuidedTourManager(task: TourTask, activeIndex = 0) {
  return {
    activeTask: task,
    activeDriverTask: {
      getActiveIndex: vi.fn().mockReturnValue(activeIndex),
      drive: vi.fn(),
      destroy: vi.fn(),
    },
    skipTask: vi.fn().mockResolvedValue(undefined),
    setTaskStatus: vi.fn().mockResolvedValue(undefined),
    disableCallToAction: vi.fn().mockResolvedValue(undefined),
    redirectToExpectedPage: vi.fn().mockReturnValue(false),
  };
}

type GuidedTourManagerMock = ReturnType<typeof createGuidedTourManager>;

function createConfig(
  task: TourTask,
  guidedTourManager: GuidedTourManagerMock,
) {
  return XWikiDriverConfig(
    guidedTourManager as unknown as DefaultGuidedTourManager,
    task,
    TRANSLATIONS,
  );
}

/**
 * The options driver.js passes to its hooks.
 */
function createHookOpts(activeIndex?: number, config: Config = {}): HookOpts {
  return {
    config,
    state: { activeIndex },
    driver: {} as Driver,
    index: activeIndex,
  };
}

function getCurrentStep(task: TourTask) {
  return StorageManager.getStorageKey(
    StorageManager.getTaskCurrentStepStorageKey(task),
  );
}

function setCurrentStep(task: TourTask, stepIndex: number) {
  StorageManager.setStorageKey(
    StorageManager.getTaskCurrentStepStorageKey(task),
    stepIndex.toString(),
  );
}

/**
 * Highlight the given steps one after the other, like driver.js does when the user moves through them.
 */
function highlightSteps(config: Config, indexes: number[], element: Element) {
  for (const index of indexes) {
    const step = config.steps![index];
    config.onHighlightStarted!(
      step.element ? element : undefined,
      step,
      createHookOpts(index, config),
    );
  }
}

describe("driverjsMain", () => {
  beforeEach(() => {
    vi.spyOn(console, "log").mockImplementation(() => {});
    vi.spyOn(console, "debug").mockImplementation(() => {});
    vi.stubGlobal("XWiki", {
      widgets: {
        Notification: class {
          hide = vi.fn();
          constructor(
            public text: string,
            public type: string,
          ) {
            notifications.push(this);
          }
        },
      },
    });
  });

  afterEach(() => {
    sessionStorage.clear();
    notifications.length = 0;
    document.body.replaceChildren();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe("getDriverConfigForSteps", () => {
    it("places the popover of each step as set in XWiki", async () => {
      const task = createTask();
      task.steps![0].placement = "BOTTOM_END";
      const config = await getDriverConfigForSteps(
        task,
        {} as DefaultGuidedTourManager,
        {},
      );

      expect(config.steps![0].popover).toMatchObject({
        side: "bottom",
        align: "end",
      });
      // Without placement, driver.js places the popover by itself.
      expect(config.steps![1].popover!.side).toBeUndefined();
    });

    it("shows the content of each step on its element once visible, with the title of the task", async () => {
      document.body.innerHTML =
        "<div id='content' style='display: none'></div>";
      const task = createTask();
      // The steps sent by the REST API have no title of their own.
      task.steps![0] = createStep({
        element: " #content ",
        content: "Step content",
      });
      const config = await getDriverConfigForSteps(
        task,
        {} as DefaultGuidedTourManager,
        {},
      );

      expect(config.steps![0].popover).toMatchObject({
        title: "Task",
        description: "Step content",
      });
      // driver.js waits for the element of the step as long as this finds nothing.
      const findElement = config.steps![0].element as () => Element | undefined;
      expect(findElement()).toBeUndefined();
      const content = document.querySelector<HTMLElement>("#content")!;
      content.style.display = "block";
      expect(findElement()).toBe(content);
    });

    it("doesn't throw on invalid selectors and ignores blank ones", async () => {
      vi.spyOn(console, "error").mockImplementation(() => {});
      const task = createTask();
      task.steps![0].element = "#invalid[";
      task.steps![1].element = " \t\n ";
      const config = await getDriverConfigForSteps(
        task,
        {} as DefaultGuidedTourManager,
        {},
      );

      // driver.js keeps waiting for the element, until it gives up and the step is handled as having no element found.
      const findElement = config.steps![0].element as () => Element | undefined;
      expect(findElement).not.toThrow();
      expect(findElement()).toBeUndefined();
      // Without element, driver.js shows the popover in the middle of the page.
      expect(config.steps![1].element).toBeUndefined();
    });

    it("rejects a task without steps", async () => {
      vi.spyOn(console, "error").mockImplementation(() => {});
      const task = { ...createTask(), steps: undefined };

      await expect(
        getDriverConfigForSteps(task, {} as DefaultGuidedTourManager, {}),
      ).rejects.toBe("Task has no steps");
    });
  });

  describe("onPopoverRender", () => {
    function createPopoverDOM(): PopoverDOM {
      const create = <K extends keyof HTMLElementTagNameMap>(tag: K) =>
        document.createElement(tag);
      const popover = {
        wrapper: create("div"),
        arrow: create("div"),
        title: create("header"),
        description: create("div"),
        footer: create("footer"),
        progress: create("span"),
        previousButton: create("button"),
        nextButton: create("button"),
        closeButton: create("button"),
        footerButtons: create("span"),
      };
      popover.wrapper.append(popover.title, popover.footer);
      popover.footer.append(popover.footerButtons);
      popover.footerButtons.append(popover.previousButton, popover.nextButton);
      return popover;
    }

    function renderPopover(
      task: TourTask,
      guidedTourManager: GuidedTourManagerMock,
      activeIndex: number,
    ) {
      const config: Config = {
        ...createConfig(task, guidedTourManager),
        steps: task.steps!.map(() => ({})),
      };
      const popover = createPopoverDOM();
      config.onPopoverRender!(popover, createHookOpts(activeIndex, config));
      return { config, popover };
    }

    it("shows the progress of the task and remembers the step", () => {
      const task = createTask();
      task.steps![1].backdrop = true;
      const { config, popover } = renderPopover(
        task,
        createGuidedTourManager(task),
        1,
      );

      expect(popover.progress.innerText).toBe("⬤ ⬤ ◯ ◯ ");
      expect(popover.wrapper.firstElementChild).toBe(popover.progress);
      expect(config.overlayOpacity).toBe(0.3);
      expect(getCurrentStep(task)).toBe("1");
    });

    it("hides the backdrop of a step without one", () => {
      const task = createTask();
      const { config, popover } = renderPopover(
        task,
        createGuidedTourManager(task),
        0,
      );

      expect(popover.progress.innerText).toBe("⬤ ◯ ◯ ◯ ");
      expect(config.overlayOpacity).toBe(0);
    });

    it("shows the navigation buttons of a regular step", () => {
      const task = createTask();
      const { popover } = renderPopover(task, createGuidedTourManager(task), 1);

      expect(popover.nextButton.classList).toContain("btn-primary");
      expect(popover.footerButtons.contains(popover.nextButton)).toBe(true);
      expect(popover.footerButtons.contains(popover.previousButton)).toBe(true);
    });

    it("hides the navigation buttons of a reflex step", () => {
      const task = createTask();
      const { popover } = renderPopover(task, createGuidedTourManager(task), 2);

      expect(popover.footerButtons.contains(popover.nextButton)).toBe(false);
      expect(popover.footerButtons.contains(popover.previousButton)).toBe(
        false,
      );
    });

    it("shows the navigation buttons of a reflex step without element", () => {
      const task = createTask();
      task.steps![2].element = undefined;
      const { popover } = renderPopover(task, createGuidedTourManager(task), 2);

      // There is nothing to click on to move to the next step.
      expect(popover.footerButtons.contains(popover.nextButton)).toBe(true);
      expect(popover.footerButtons.contains(popover.previousButton)).toBe(true);
    });

    it("skips the task with the Skip All link", () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task);
      const { popover } = renderPopover(task, guidedTourManager, 0);

      const skipAll = popover.footer.querySelector<HTMLElement>(
        ".driver-xwiki-skip-all-button",
      )!;
      expect(skipAll.textContent).toBe("Skip all");
      skipAll.click();

      expect(guidedTourManager.skipTask).toHaveBeenCalledExactlyOnceWith(task);
    });
  });

  describe("onDestroyed", () => {
    function destroy(task: TourTask, guidedTourManager: GuidedTourManagerMock) {
      return createConfig(task, guidedTourManager).onDestroyed!(
        undefined,
        {},
        createHookOpts(),
      );
    }

    it("marks the task as done when the last step was reached", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task);
      setCurrentStep(task, 3);

      await destroy(task, guidedTourManager);

      expect(guidedTourManager.setTaskStatus).toHaveBeenCalledExactlyOnceWith(
        task,
        TourTaskStatus.DONE,
      );
      expect(guidedTourManager.disableCallToAction).not.toHaveBeenCalled();
    });

    it("keeps the status of a task closed before the end", async () => {
      // A task that is done can be started again.
      const task = { ...createTask(), status: TourTaskStatus.DONE };
      const guidedTourManager = createGuidedTourManager(task);
      // Closed on the step before the last one.
      setCurrentStep(task, 2);

      await destroy(task, guidedTourManager);

      expect(guidedTourManager.disableCallToAction).toHaveBeenCalledWith(
        "tour1",
      );
      expect(guidedTourManager.setTaskStatus).toHaveBeenCalledExactlyOnceWith(
        task,
        TourTaskStatus.DONE,
      );
    });

    it("keeps the status of a task closed before any step was shown", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task);

      await destroy(task, guidedTourManager);

      expect(guidedTourManager.disableCallToAction).toHaveBeenCalledWith(
        "tour1",
      );
      expect(guidedTourManager.setTaskStatus).toHaveBeenCalledExactlyOnceWith(
        task,
        TourTaskStatus.TODO,
      );
    });

    it("doesn't change the status of a task stopped by the manager", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task);
      (guidedTourManager as Partial<GuidedTourManagerMock>).activeTask =
        undefined;
      setCurrentStep(task, 3);

      await destroy(task, guidedTourManager);

      expect(guidedTourManager.setTaskStatus).not.toHaveBeenCalled();
    });

    it("logs the error when the status of the task can't be saved", async () => {
      const consoleError = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task);
      const error = new Error("Failed");
      guidedTourManager.setTaskStatus.mockRejectedValue(error);
      setCurrentStep(task, 3);

      await expect(destroy(task, guidedTourManager)).resolves.toBeUndefined();
      expect(guidedTourManager.setTaskStatus).toHaveBeenCalledOnce();
      expect(consoleError).toHaveBeenCalledWith(error);
    });
  });

  describe("navigation", () => {
    function clickNext(
      task: TourTask,
      guidedTourManager: GuidedTourManagerMock,
      activeIndex: number,
      element?: Element,
    ) {
      return createConfig(task, guidedTourManager).onNextClick!(
        element,
        {},
        createHookOpts(activeIndex),
      );
    }

    it("moves to the next step", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task, 0);

      await clickNext(task, guidedTourManager, 0);

      expect(guidedTourManager.redirectToExpectedPage).toHaveBeenCalledWith(
        task.steps![1],
      );
      expect(
        guidedTourManager.activeDriverTask.drive,
      ).toHaveBeenCalledExactlyOnceWith(1);
      expect(getCurrentStep(task)).toBe("1");
    });

    it("moves to the previous step", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task, 1);

      await createConfig(task, guidedTourManager).onPrevClick!(
        undefined,
        {},
        createHookOpts(1),
      );

      expect(
        guidedTourManager.activeDriverTask.drive,
      ).toHaveBeenCalledExactlyOnceWith(0);
    });

    it("redirects to the page of the next step", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task, 0);
      guidedTourManager.redirectToExpectedPage.mockReturnValue(true);

      await clickNext(task, guidedTourManager, 0);

      expect(guidedTourManager.activeDriverTask.drive).not.toHaveBeenCalled();
      // The task resumes at the next step once on the page.
      expect(getCurrentStep(task)).toBe("1");
    });

    it("never redirects after a reflex step", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task, 2);
      guidedTourManager.redirectToExpectedPage.mockReturnValue(true);

      // No element: the next button acts as for a regular step.
      await clickNext(task, guidedTourManager, 2);

      expect(guidedTourManager.redirectToExpectedPage).not.toHaveBeenCalled();
      expect(
        guidedTourManager.activeDriverTask.drive,
      ).toHaveBeenCalledExactlyOnceWith(3);
    });

    it("ends the task after the last step", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task, 3);

      await clickNext(task, guidedTourManager, 3);

      expect(guidedTourManager.activeDriverTask.destroy).toHaveBeenCalledOnce();
      expect(guidedTourManager.activeDriverTask.drive).not.toHaveBeenCalled();
    });

    it("moves to the next step when the reflex step shows no element", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task, 2);
      // driver.js shows the popover on this element when the step element isn't found.
      const element = document.createElement("div");
      element.id = "driver-dummy-element";
      const click = vi.spyOn(element, "click");

      await clickNext(task, guidedTourManager, 2, element);

      expect(click).not.toHaveBeenCalled();
      expect(
        guidedTourManager.activeDriverTask.drive,
      ).toHaveBeenCalledExactlyOnceWith(3);
    });
  });

  describe("onHighlightStarted", () => {
    it("skips the task when the element of a step is not in the page", async () => {
      vi.spyOn(console, "error").mockImplementation(() => {});
      const task = createTask();
      task.steps![1].element = "#missing";
      const guidedTourManager = createGuidedTourManager(task, 1);
      const config = await getDriverConfigForSteps(
        task,
        guidedTourManager as unknown as DefaultGuidedTourManager,
        TRANSLATIONS,
      );

      // driver.js gave up waiting for the element.
      config.onHighlightStarted!(
        undefined,
        config.steps![1],
        createHookOpts(1, config),
      );

      expect(notifications).toMatchObject([{ text: "Error", type: "error" }]);
      expect(guidedTourManager.setTaskStatus).toHaveBeenCalledExactlyOnceWith(
        task,
        TourTaskStatus.SKIPPED,
      );
    });

    it("shows a step without element", async () => {
      const task = createTask();
      const guidedTourManager = createGuidedTourManager(task, 1);
      const config = await getDriverConfigForSteps(
        task,
        guidedTourManager as unknown as DefaultGuidedTourManager,
        TRANSLATIONS,
      );

      highlightSteps(config, [1], document.body);

      expect(notifications).toEqual([]);
      expect(guidedTourManager.setTaskStatus).not.toHaveBeenCalled();
    });
  });

  /**
   * The guided tour manager running the given task, at the given step, with its driver wrapped like in
   * DefaultGuidedTourManager.
   * @returns the guided tour manager, the driver.js config of the task, and the mocks of the driver.js driver: `drive`
   *     shows a step.
   */
  async function createWrappedDriver(task: TourTask, activeIndex = 0) {
    const guidedTourManager = createGuidedTourManager(task, activeIndex);
    const { drive, destroy } = guidedTourManager.activeDriverTask;
    wrapTask(
      guidedTourManager.activeDriverTask as unknown as Driver,
      TRANSLATIONS,
    );
    const config = await getDriverConfigForSteps(
      task,
      guidedTourManager as unknown as DefaultGuidedTourManager,
      TRANSLATIONS,
    );
    return { guidedTourManager, config, drive, destroy };
  }

  describe("wrapTask", () => {
    it("shows a loading notification until the step is highlighted", async () => {
      const { guidedTourManager, config, drive } =
        await createWrappedDriver(createTask());

      guidedTourManager.activeDriverTask.drive(1);

      expect(drive).toHaveBeenCalledExactlyOnceWith(1);
      expect(notifications).toMatchObject([
        { text: "Loading", type: "inprogress" },
      ]);
      expect(notifications[0].hide).not.toHaveBeenCalled();

      highlightSteps(config, [1], document.body);

      expect(notifications[0].hide).toHaveBeenCalled();
    });

    it("replaces the loading notification when moving again before the step is highlighted", async () => {
      const { guidedTourManager, drive } =
        await createWrappedDriver(createTask());

      guidedTourManager.activeDriverTask.drive(1);
      guidedTourManager.activeDriverTask.drive(2);

      expect(drive.mock.calls).toEqual([[1], [2]]);
      expect(notifications).toHaveLength(2);
      expect(notifications[0].hide).toHaveBeenCalled();
      expect(notifications[1].hide).not.toHaveBeenCalled();
    });

    it("hides the loading notification when the task is closed while waiting for the element", async () => {
      const { guidedTourManager, destroy } =
        await createWrappedDriver(createTask());

      guidedTourManager.activeDriverTask.drive(1);
      guidedTourManager.activeDriverTask.destroy();

      expect(destroy).toHaveBeenCalledOnce();
      expect(notifications[0].hide).toHaveBeenCalled();
    });
  });

  describe("reflex step", () => {
    /**
     * Show the reflex step of the task (the third one) on the given element, after the first two steps.
     */
    async function showReflexStep(html: string) {
      document.body.innerHTML = html;
      const reflexElement = document.querySelector<HTMLElement>("#reflex")!;
      const wrapped = await createWrappedDriver(createTask(), 2);
      highlightSteps(wrapped.config, [0, 1, 2], reflexElement);
      return { ...wrapped, reflexElement };
    }

    it("moves to the next step when its element is clicked", async () => {
      const { reflexElement, drive } = await showReflexStep(
        "<button id='reflex'></button>",
      );

      reflexElement.click();
      // The listener is removed after the first click.
      reflexElement.click();

      expect(drive).toHaveBeenCalledExactlyOnceWith(3);
    });

    it("does nothing when its element is clicked after the task was closed", async () => {
      const { reflexElement, guidedTourManager, drive } = await showReflexStep(
        "<button id='reflex'></button>",
      );

      guidedTourManager.activeDriverTask.destroy();
      reflexElement.click();

      expect(drive).not.toHaveBeenCalled();
    });

    it("stops listening for the reflex action as soon as its step is left", async () => {
      const { reflexElement, guidedTourManager, drive } = await showReflexStep(
        "<button id='reflex'></button>",
      );

      // driver.js keeps the reflex step active while it waits for the element of the step being driven to.
      guidedTourManager.activeDriverTask.drive(1);
      reflexElement.click();

      expect(drive).toHaveBeenCalledExactlyOnceWith(1);
    });

    it("moves to the next step only once after it was left and reached again", async () => {
      const { reflexElement, config, drive } = await showReflexStep(
        "<button id='reflex'></button>",
      );

      // Left without triggering the reflex action, then reached again.
      highlightSteps(config, [1, 2], reflexElement);
      reflexElement.click();

      expect(drive).toHaveBeenCalledExactlyOnceWith(3);
    });

    it("clicks its element only once when → is pressed repeatedly", async () => {
      const { reflexElement, config, drive } = await showReflexStep(
        "<button id='reflex'></button>",
      );
      const click = vi.spyOn(reflexElement, "click");

      // driver.js calls `onNextClick()` with the element of the active step when → is pressed.
      await config.onNextClick!(reflexElement, {}, createHookOpts(2, config));
      await config.onNextClick!(reflexElement, {}, createHookOpts(2, config));

      expect(click).toHaveBeenCalledOnce();
      expect(drive).toHaveBeenCalledExactlyOnceWith(3);
    });

    describe("on a text input", () => {
      /**
       * Show the reflex step on a text input, and click the input.
       */
      async function clickReflexInput() {
        const wrapped = await showReflexStep("<input type='text' id='reflex'>");
        vi.useFakeTimers();
        wrapped.reflexElement.click();
        return wrapped;
      }

      it("moves to the next step after leaving the user time to type", async () => {
        // The steps are numbered from 1, like the steps created through the REST API.
        const { drive } = await clickReflexInput();

        await vi.advanceTimersByTimeAsync(4999);
        expect(drive).not.toHaveBeenCalled();
        await vi.advanceTimersByTimeAsync(1);
        expect(drive).toHaveBeenCalledExactlyOnceWith(3);
      });

      it("doesn't move when the user left the step while typing", async () => {
        const { guidedTourManager, drive } = await clickReflexInput();

        guidedTourManager.activeDriverTask.drive(1);
        await vi.advanceTimersByTimeAsync(5000);

        expect(drive).toHaveBeenCalledExactlyOnceWith(1);
      });

      it("doesn't move when the user came back to the step while typing", async () => {
        const { guidedTourManager, config, reflexElement, drive } =
          await clickReflexInput();

        guidedTourManager.activeDriverTask.drive(1);
        highlightSteps(config, [1, 2], reflexElement);
        await vi.advanceTimersByTimeAsync(5000);

        expect(drive).toHaveBeenCalledExactlyOnceWith(1);
      });
    });
  });
});
