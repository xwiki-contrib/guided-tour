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
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { TourTask } from "@xwiki/contrib-guidedtour-api";

function createTask(tourId: string, id: string): TourTask {
  return { id, tourId, title: id, order: 0, status: TourTaskStatus.TODO };
}

describe("StorageManager", () => {
  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it.each([
    ["tour1", "task1"],
    // The separator is escaped in the ids.
    ["my__tour", "my__task"],
  ])("parses back the key prefix of tour %s and task %s", (tourId, taskId) => {
    const prefix = StorageManager.getStorageKeyPrefix(
      createTask(tourId, taskId),
    );

    expect(prefix).toMatch(/^guidedtour_/);
    expect(StorageManager.parseStorageKeyPrefix(prefix)).toEqual({
      tourId,
      taskId,
    });
  });

  it("doesn't parse a key that isn't a task key prefix", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});

    expect(StorageManager.parseStorageKeyPrefix("other_key")).toBeUndefined();
  });

  it("keeps the progress of the task in progress in the session storage", () => {
    const task = createTask("tour1", "task1");
    const sessionKeys = [
      StorageManager.getActiveTaskStorageKey(),
      StorageManager.getTaskCurrentStepStorageKey(task),
      StorageManager.getTaskStepStorageStorageKey(task),
    ];

    for (const key of sessionKeys) {
      StorageManager.setStorageKey(key, "value");
      expect(sessionStorage.getItem(key)).toBe("value");
      expect(StorageManager.getStorageKey(key)).toBe("value");
    }
    expect(localStorage).toHaveLength(0);
  });

  it("keeps the other keys in the local storage", () => {
    const key = StorageManager.getGuestStatusStorageKey();

    StorageManager.setStorageKey(key, "value");

    expect(localStorage.getItem(key)).toBe("value");
    expect(StorageManager.getStorageKey(key)).toBe("value");
    expect(sessionStorage).toHaveLength(0);
  });

  it("removes a key set to undefined", () => {
    const key = StorageManager.getActiveTaskStorageKey();
    StorageManager.setStorageKey(key, "value");

    StorageManager.setStorageKey(key, undefined);

    expect(StorageManager.getStorageKey(key)).toBeNull();
  });
});
