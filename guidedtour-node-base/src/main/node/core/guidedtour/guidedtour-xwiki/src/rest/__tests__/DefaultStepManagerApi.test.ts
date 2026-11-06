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

import { DefaultStepManagerApi } from "../DefaultStepManagerApi";
import { TourStore } from "../TourStore";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { GuidedTourRestClient } from "../GuidedTourRestClient";
import type { TourStep } from "@xwiki/contrib-guidedtour-api";

vi.mock("../../services/xwiki.js", () => ({
  XWiki: { contextPath: "/xwiki" },
}));

const STEPS_URL = "/xwiki/rest/guidedTour/tours/tour1/tasks/task1/steps";

function createStep(step: Partial<TourStep> = {}): TourStep {
  return { order: 0, content: "Step", reflex: false, ...step };
}

describe("DefaultStepManagerApi", () => {
  let request: ReturnType<typeof vi.fn>;
  let store: TourStore;
  let api: DefaultStepManagerApi;

  beforeEach(() => {
    request = vi.fn().mockResolvedValue({});
    store = new TourStore({});
    store.updateTours([
      {
        id: "tour1",
        title: "Tour 1",
        tasksList: [
          {
            id: "task1",
            title: "Task 1",
            order: 0,
            status: TourTaskStatus.TODO,
          },
        ],
      },
    ]);
    api = new DefaultStepManagerApi(
      { request } as unknown as GuidedTourRestClient,
      store,
    );
    vi.spyOn(console, "debug").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("fetches the steps of a task only once", async () => {
    request.mockResolvedValue([createStep(), createStep({ order: 1 })]);

    const steps = await api.getSteps("tour1", "task1");
    expect(steps).toHaveLength(2);
    expect(await api.getSteps("tour1", "task1")).toEqual(steps);

    expect(request).toHaveBeenCalledExactlyOnceWith(STEPS_URL, "GET");
    expect(store.getTourTasks("tour1")[0].steps).toEqual(steps);
  });

  it("fetches the steps of a task that isn't cached", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => {});
    request.mockResolvedValue([createStep()]);

    expect(await api.getSteps("tour1", "unknown")).toEqual([createStep()]);
    expect(request).toHaveBeenCalledExactlyOnceWith(
      "/xwiki/rest/guidedTour/tours/tour1/tasks/unknown/steps",
      "GET",
    );
  });

  it("leaves unset the empty element and target action of the fetched steps", async () => {
    request.mockResolvedValue([
      createStep({ element: "", targetAction: "NOT_SET" }),
      createStep({ element: "#content", targetAction: "EDIT" }),
    ]);

    const steps = await api.getSteps("tour1", "task1");

    expect(steps[0].element).toBeUndefined();
    expect(steps[0].targetAction).toBeUndefined();
    expect(steps[1].element).toBe("#content");
    expect(steps[1].targetAction).toBe("EDIT");
  });

  it("creates, updates and deletes steps", async () => {
    const step = createStep();

    await api.createStep("tour1", "task1", step);
    await api.updateStep("tour1", "task1", 2, step);
    await api.deleteStep("tour1", "task1", 2);

    expect(request.mock.calls).toEqual([
      [STEPS_URL, "POST", step],
      [`${STEPS_URL}/2`, "PUT", step],
      [`${STEPS_URL}/2`, "DELETE"],
    ]);
  });
});
