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

import { TourStore } from "../TourStore";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { afterEach, describe, expect, it, vi } from "vitest";
import { isReactive } from "vue";
import type {
  TourStep,
  TourTask,
  TourTour,
} from "@xwiki/contrib-guidedtour-api";

const { TODO, DONE } = TourTaskStatus;

function createTask(id: string, status: TourTaskStatus = TODO): TourTask {
  return { id, title: id, order: 0, status };
}

function createTours(): TourTour[] {
  return [
    {
      id: "tour1",
      title: "Tour 1",
      tasksList: [createTask("task1"), createTask("task2")],
    },
    { id: "tour2", title: "Tour 2", tasksList: [] },
  ];
}

function createStore(tours: TourTour[] = createTours()): TourStore {
  const store = new TourStore({ userReference: "xwiki:XWiki.Alice" });
  store.updateTours(tours);
  return store;
}

describe("TourStore", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("holds the reference of the current user", () => {
    expect(createStore().currentUserReference).toBe("xwiki:XWiki.Alice");
  });

  it("caches the tours and links their tasks to them", () => {
    const store = createStore();

    expect(store.cache.tours.map((tour) => tour.id)).toEqual([
      "tour1",
      "tour2",
    ]);
    expect(store.cache.toursMap.get("tour2")).toBe(1);
    expect(isReactive(store.cache.tours[0])).toBe(true);
    expect(store.getTourTasks("tour1").map((task) => task.tourId)).toEqual([
      "tour1",
      "tour1",
    ]);
  });

  it("applies the progress of the user to the tasks", () => {
    const store = new TourStore({ userReference: "xwiki:XWiki.Alice" });
    store.setUserToursStatus({
      tour1: { callToAction: true, tasksStatus: { task2: DONE } },
      tour2: { callToAction: true, tasksStatus: { task3: DONE } },
    });

    store.updateTours(createTours());
    store.updateTourTasks("tour2", [createTask("task3")]);

    expect(store.getTourTasks("tour1").map((task) => task.status)).toEqual([
      TODO,
      DONE,
    ]);
    expect(store.getTourTasks("tour2")[0]).toMatchObject({
      tourId: "tour2",
      status: DONE,
    });
  });

  it("replaces the cached tours, keeping the same array", () => {
    const store = createStore();
    const tours = store.cache.tours;

    store.updateTours([{ id: "tour3", title: "Tour 3", tasksList: [] }]);

    expect(store.cache.tours).toBe(tours);
    expect(tours.map((tour) => tour.id)).toEqual(["tour3"]);
    expect([...store.cache.toursMap.keys()]).toEqual(["tour3"]);
  });

  it("has no tasks for a tour that is unknown or without tasks", () => {
    const store = createStore();

    expect(store.getTourTasks("unknown")).toEqual([]);
    expect(store.getTourTasks("tour2")).toEqual([]);
  });

  it("purges the cache when updating the tasks of an unknown tour", () => {
    vi.spyOn(console, "warn").mockImplementation(() => {});
    const store = createStore();

    store.updateTourTasks("unknown", [createTask("task3")]);

    expect(store.cache.tours).toHaveLength(0);
    expect(store.cache.toursMap.size).toBe(0);
  });

  it("caches the steps of a task", () => {
    const store = createStore();
    const steps: TourStep[] = [{ order: 0, content: "Step", reflex: false }];

    store.updateTaskSteps("tour1", "task2", steps);

    expect(store.getTourTasks("tour1")[1].steps).toEqual(steps);
  });

  it.each([
    ["tour", "unknown", "task1"],
    ["task", "tour1", "unknown"],
  ])(
    "purges the cache when updating the steps of an unknown %s",
    (_description, tourId, taskId) => {
      vi.spyOn(console, "warn").mockImplementation(() => {});
      const store = createStore();

      store.updateTaskSteps(tourId, taskId, []);

      expect(store.cache.tours).toHaveLength(0);
    },
  );
});
