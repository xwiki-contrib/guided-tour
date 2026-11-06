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

import { DefaultTaskManagerApi } from "../DefaultTaskManagerApi";
import { TourStore } from "../TourStore";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { GuidedTourRestClient } from "../GuidedTourRestClient";
import type { TourTask } from "@xwiki/contrib-guidedtour-api";

vi.mock("../../services/xwiki.js", () => ({
  XWiki: { contextPath: "/xwiki" },
}));

const TASKS_URL = "/xwiki/rest/guidedTour/tours/tour1/tasks";

function createTask(id: string): TourTask {
  return { id, title: id, order: 0, status: TourTaskStatus.TODO };
}

function getTaskIds(tasks: TourTask[] | undefined) {
  return tasks?.map((task) => task.id);
}

describe("DefaultTaskManagerApi", () => {
  let request: ReturnType<typeof vi.fn>;
  let store: TourStore;
  let api: DefaultTaskManagerApi;

  beforeEach(() => {
    request = vi.fn().mockResolvedValue({});
    store = new TourStore({});
    store.updateTours([{ id: "tour1", title: "Tour 1", tasksList: [] }]);
    api = new DefaultTaskManagerApi(
      { request } as unknown as GuidedTourRestClient,
      store,
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("getTasks", () => {
    it("fetches the tasks of a tour only once", async () => {
      request.mockResolvedValue([createTask("task1"), createTask("task2")]);

      expect(getTaskIds(await api.getTasks("tour1"))).toEqual([
        "task1",
        "task2",
      ]);
      expect(await api.getTask("tour1", "task2")).toMatchObject({
        id: "task2",
        tourId: "tour1",
      });
      expect(await api.getTask("tour1", "unknown")).toBeUndefined();

      expect(request).toHaveBeenCalledExactlyOnceWith(TASKS_URL, "GET");
      expect(getTaskIds(store.getTourTasks("tour1"))).toEqual([
        "task1",
        "task2",
      ]);
    });

    it("fetches the tasks of a tour that isn't cached", async () => {
      vi.spyOn(console, "warn").mockImplementation(() => {});
      request.mockResolvedValue([createTask("task1")]);

      expect(getTaskIds(await api.getTasks("unknown"))).toEqual(["task1"]);
      expect(request).toHaveBeenCalledExactlyOnceWith(
        "/xwiki/rest/guidedTour/tours/unknown/tasks",
        "GET",
      );
    });

    it("returns the cached tasks of a tour", async () => {
      store.updateTourTasks("tour1", [createTask("task1")]);

      expect(getTaskIds(await api.getTasks("tour1"))).toEqual(["task1"]);
      expect(request).not.toHaveBeenCalled();
    });
  });

  it("creates and updates tasks", async () => {
    const task = createTask("task1");

    await api.createTask("tour1", task);
    await api.updateTask("tour1", "task1", task);

    expect(request.mock.calls).toEqual([
      [TASKS_URL, "POST", task],
      [`${TASKS_URL}/task1`, "PUT", task],
    ]);
  });

  it("deletes a task and removes it from the cache", async () => {
    store.updateTourTasks("tour1", [createTask("task1"), createTask("task2")]);

    await api.deleteTask("tour1", "task1");

    expect(request).toHaveBeenCalledExactlyOnceWith(
      `${TASKS_URL}/task1`,
      "DELETE",
    );
    expect(getTaskIds(store.getTourTasks("tour1"))).toEqual(["task2"]);
  });

  it("deletes a task of a tour that isn't cached", async () => {
    await api.deleteTask("unknown", "task1");

    expect(request).toHaveBeenCalledExactlyOnceWith(
      "/xwiki/rest/guidedTour/tours/unknown/tasks/task1",
      "DELETE",
    );
  });
});
