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
import { XWikiDriverConfig } from "../driverjsMain";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { describe, expect, it, vi } from "vitest";
import type { DefaultGuidedTourManager } from "../rest/DefaultGuidedTourManager";
import type { TourTask } from "@xwiki/contrib-guidedtour-api";
import type { Driver } from "driver.js";

// Kept apart from driverjsMain.test.ts: once the page is unloading, driverjsMain stays in that state.
describe("driverjsMain while the page is unloading", () => {
  it("doesn't move to another step", async () => {
    vi.spyOn(console, "log").mockImplementation(() => {});
    const task: TourTask = {
      id: "task1",
      tourId: "tour1",
      title: "Task",
      status: TourTaskStatus.TODO,
      order: 0,
      steps: [
        { order: 1, content: "", reflex: false },
        { order: 2, content: "", reflex: false },
      ],
    };
    const drive = vi.fn();
    const config = XWikiDriverConfig(
      {
        activeDriverTask: { getActiveIndex: () => 0, drive },
        redirectToExpectedPage: () => false,
      } as unknown as DefaultGuidedTourManager,
      task,
      {},
    );

    window.dispatchEvent(new Event("beforeunload"));
    await config.onNextClick!(
      undefined,
      {},
      { config: {}, state: { activeIndex: 0 }, driver: {} as Driver, index: 0 },
    );

    expect(drive).not.toHaveBeenCalled();
    expect(
      StorageManager.getStorageKey(
        StorageManager.getTaskCurrentStepStorageKey(task),
      ),
    ).toBeNull();
  });
});
