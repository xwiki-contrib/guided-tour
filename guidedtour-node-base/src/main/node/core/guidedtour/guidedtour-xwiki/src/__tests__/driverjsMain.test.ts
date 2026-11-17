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

import { driver, getDriverConfigForSteps, wrapTask } from "../driverjsMain";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DefaultGuidedTourManager } from "../rest/DefaultGuidedTourManager";
import type { TourStep, TourTask } from "@xwiki/contrib-guidedtour-api";
import type { Config, Driver, HookOpts } from "driver.js";

function createStep(step: Partial<TourStep> = {}): TourStep {
  return { order: 0, content: "", reflex: false, ...step };
}

function createTask(): TourTask {
  return {
    id: "task1",
    tourId: "tour1",
    title: "Task",
    status: TourTaskStatus.TODO,
    order: 0,
    steps: [
      createStep(),
      createStep({ order: 1 }),
      createStep({ order: 2, element: "#reflex", reflex: true }),
      createStep({ order: 3 }),
    ],
  };
}

/**
 * @returns the element of the reflex step, added to the page.
 */
function addReflexElement(): HTMLElement {
  document.body.innerHTML = '<button id="reflex"></button>';
  return document.getElementById("reflex")!;
}

/**
 * Highlight the given steps one after the other, like driver.js does when the user moves through them.
 */
function highlightSteps(config: Config, indexes: number[], element: Element) {
  for (const index of indexes) {
    const step = config.steps![index];
    config.onHighlightStarted!(step.element ? element : undefined, step, {
      state: { activeIndex: index },
    } as HookOpts);
  }
}

describe("driverjsMain", () => {
  it("advances a reflex step only once after it was left and reached again", async () => {
    const reflexElement = addReflexElement();
    const task = createTask();
    const drive = vi.fn();
    // The reflex step is the active one when its element is clicked.
    const guidedTourManager = {
      activeTask: task,
      activeDriverTask: { getActiveIndex: () => 2, drive },
    } as unknown as DefaultGuidedTourManager;
    const config = await getDriverConfigForSteps(task, guidedTourManager, {});

    // Step 1 > 2 > 3 > 2 > 3, step 3 being left the first time without triggering its reflex action.
    highlightSteps(config, [0, 1, 2, 1, 2], reflexElement);
    reflexElement.click();

    expect(drive).toHaveBeenCalledExactlyOnceWith(3);
  });

  it("stops listening for the reflex action as soon as its step is left", async () => {
    const reflexElement = addReflexElement();
    vi.stubGlobal("XWiki", {
      widgets: {
        Notification: class {
          hide() {}
        },
      },
    });
    const task = createTask();
    const drive = vi.fn();
    // driver.js keeps the reflex step active while it waits for the element of the step being driven to.
    const guidedTourManager = {
      activeTask: task,
      activeDriverTask: wrapTask(
        { getActiveIndex: () => 2, drive } as unknown as Driver,
        {},
      ),
    } as unknown as DefaultGuidedTourManager;
    const config = await getDriverConfigForSteps(task, guidedTourManager, {});

    // Move back from the reflex step, and click its element before the previous step is highlighted.
    highlightSteps(config, [0, 1, 2], reflexElement);
    guidedTourManager.activeDriverTask!.drive(1);
    reflexElement.click();

    expect(drive).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("clicks the element of a reflex step only once when → is pressed repeatedly", async () => {
    const reflexElement = addReflexElement();
    const click = vi.spyOn(reflexElement, "click");
    const task = createTask();
    // driver.js keeps the reflex step active while it waits for the element of the next step.
    const guidedTourManager = {
      activeTask: task,
      activeDriverTask: { getActiveIndex: () => 2, drive: vi.fn() },
    } as unknown as DefaultGuidedTourManager;
    const config = await getDriverConfigForSteps(task, guidedTourManager, {});

    // driver.js calls `onNextClick()` with the element of the active step when → is pressed.
    highlightSteps(config, [0, 1, 2], reflexElement);
    const hookOptions = { state: { activeIndex: 2 } } as HookOpts;
    config.onNextClick!(reflexElement, config.steps![2], hookOptions);
    config.onNextClick!(reflexElement, config.steps![2], hookOptions);

    expect(click).toHaveBeenCalledOnce();
  });

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
});

describe("ending a task", () => {
  /**
   * The ways for the user to end a task, by the destroy reason that driver.js gives for each.
   */
  const endTaskBy: Record<string, () => void> = {
    close: () =>
      document.querySelector<HTMLElement>(".driver-popover-close-btn")!.click(),
    escape: () => pressEscape(),
    overlay: () =>
      document
        .querySelector(".driver-overlay path")!
        .dispatchEvent(new MouseEvent("click", { bubbles: true })),
  };

  function pressEscape() {
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    window.dispatchEvent(new KeyboardEvent("keyup", { key: "Escape" }));
  }

  /**
   * @returns a task started with driver.js, on its first step.
   */
  async function startTask(confirmed: boolean): Promise<Driver> {
    vi.stubGlobal("XWiki", {
      widgets: {
        Notification: class {
          hide() {}
        },
      },
    });
    vi.stubGlobal("confirm", vi.fn().mockReturnValue(confirmed));
    const config = await getDriverConfigForSteps(
      createTask(),
      {} as DefaultGuidedTourManager,
      { "guidedtour.driver.confirmExit": "Stop the task?" },
    );
    config.animate = false;
    const task = driver(config);
    task.drive();
    await vi.waitFor(() =>
      expect(document.querySelector(".driver-overlay")).not.toBeNull(),
    );
    return task;
  }

  let task: Driver | undefined;

  afterEach(() => {
    task?.destroy();
    vi.unstubAllGlobals();
  });

  it.each(Object.keys(endTaskBy))(
    "ends the task with %s only if the user confirms it",
    async (reason) => {
      task = await startTask(false);
      endTaskBy[reason]();
      expect(window.confirm).toHaveBeenCalledExactlyOnceWith("Stop the task?");
      expect(task.isActive()).toBe(true);
      expect(task.getActiveIndex()).toBe(0);

      vi.mocked(window.confirm).mockReturnValue(true);
      endTaskBy[reason]();
      expect(task.isActive()).toBe(false);
    },
  );

  it("ends the task without asking when the code ends it", async () => {
    task = await startTask(false);
    task.destroy();
    expect(task.isActive()).toBe(false);

    task = await startTask(false);
    // Driving to a step that doesn't exist (anymore) ends the task.
    task.drive(10);
    expect(task.isActive()).toBe(false);
    expect(window.confirm).not.toHaveBeenCalled();
  });

  it("doesn't ask again on the Escape keyup that closes the confirmation", async () => {
    task = await startTask(false);
    pressEscape();
    // The browser closes the confirmation on keydown, and sends the keyup to the page.
    window.dispatchEvent(new KeyboardEvent("keyup", { key: "Escape" }));
    expect(window.confirm).toHaveBeenCalledOnce();

    pressEscape();
    expect(window.confirm).toHaveBeenCalledTimes(2);
    expect(task.isActive()).toBe(true);
  });
});
