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

import { DefaultTourManagerApi } from "../DefaultTourManagerApi";
import { TourStore } from "../TourStore";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { GuidedTourRestClient } from "../GuidedTourRestClient";
import type { TourTask, TourTour } from "@xwiki/contrib-guidedtour-api";

vi.mock("../../services/xwiki.js", () => ({
  XWiki: { contextPath: "/xwiki" },
}));

const TOURS_URL = "/xwiki/rest/guidedTour/tours";

const { TODO, DONE, SKIPPED } = TourTaskStatus;

let taskCount = 0;

function createTask(status: TourTaskStatus, active = true): TourTask {
  const id = `task${++taskCount}`;
  return { id, title: id, order: taskCount, status, active };
}

function createTour(tasksList: TourTask[] = []): TourTour {
  return { id: "tour1", title: "Tour 1", tasksList };
}

describe("DefaultTourManagerApi", () => {
  let request: ReturnType<typeof vi.fn>;
  let store: TourStore;
  let api: DefaultTourManagerApi;

  beforeEach(() => {
    request = vi.fn().mockResolvedValue({});
    store = new TourStore({});
    api = new DefaultTourManagerApi(
      { request } as unknown as GuidedTourRestClient,
      store,
    );
    vi.spyOn(console, "debug").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("fetches the tours only once", async () => {
    request.mockResolvedValue([
      createTour(),
      { id: "tour2", title: "Tour 2", tasksList: [] },
    ]);

    const tours = await api.getTours();
    expect(tours.map((tour) => tour.id)).toEqual(["tour1", "tour2"]);
    expect(await api.getTours()).toBe(tours);
    expect(await api.getTour("tour2")).toMatchObject({ id: "tour2" });
    expect(await api.getTour("unknown")).toBeUndefined();

    expect(request).toHaveBeenCalledExactlyOnceWith(TOURS_URL, "GET");
  });

  it("fetches the tours when getting one before they are cached", async () => {
    request.mockResolvedValue([createTour()]);

    expect(await api.getTour("tour1")).toMatchObject({ id: "tour1" });
    expect(store.cache.tours).toHaveLength(1);
  });

  describe("computeToursStatus", () => {
    it.each([
      ["TODO when a task is left to do", [DONE, SKIPPED, TODO], TODO],
      ["SKIPPED when a task was skipped", [DONE, SKIPPED], SKIPPED],
      ["DONE when all tasks are done", [DONE, DONE], DONE],
    ])(
      "computes the status of a tour: %s",
      (_description, statuses, status) => {
        const tour = createTour(statuses.map((s) => createTask(s)));

        api.computeToursStatus([tour]);

        expect(tour.status).toBe(status);
      },
    );

    it("only considers the active tasks to compute the status of a tour", () => {
      const tour = createTour([createTask(DONE), createTask(TODO, false)]);
      const tourWithoutActiveTasks = createTour([createTask(TODO, false)]);

      api.computeToursStatus([tour, tourWithoutActiveTasks]);

      expect(tour.status).toBe(DONE);
      expect(tourWithoutActiveTasks.status).toBe(SKIPPED);
    });

    it("doesn't compute the status of a tour whose tasks aren't fetched yet", () => {
      // The REST API always sends the tasks of the tours, this is a safety net.
      const tour: TourTour = { id: "tour1", title: "Tour 1" };

      api.computeToursStatus([tour]);

      expect(tour.status).toBeUndefined();
    });
  });

  it("creates, updates and deletes tours", async () => {
    const tour = createTour();

    await api.createTour(tour);
    await api.updateTour("tour1", tour);
    await api.deleteTour("tour1");

    expect(request.mock.calls).toEqual([
      [TOURS_URL, "POST", tour],
      [`${TOURS_URL}/tour1`, "PUT", tour],
      [`${TOURS_URL}/tour1`, "DELETE"],
    ]);
  });
});
