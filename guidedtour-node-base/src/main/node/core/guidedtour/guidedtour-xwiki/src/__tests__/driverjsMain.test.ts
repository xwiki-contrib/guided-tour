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

import { getDriverConfigForSteps } from "../driverjsMain";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { describe, expect, it } from "vitest";
import type { DefaultGuidedTourManager } from "../rest/DefaultGuidedTourManager";
import type { TourStep, TourTask } from "@xwiki/contrib-guidedtour-api";

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

describe("driverjsMain", () => {
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
