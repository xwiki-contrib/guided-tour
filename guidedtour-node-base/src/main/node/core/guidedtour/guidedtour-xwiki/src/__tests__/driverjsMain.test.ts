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

import { getDriverConfigForSteps, wrapTask } from "../driverjsMain";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { describe, expect, it, vi } from "vitest";
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
});
