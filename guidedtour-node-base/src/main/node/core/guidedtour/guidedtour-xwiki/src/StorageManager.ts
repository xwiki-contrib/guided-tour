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
import type { TourTask } from "@xwiki/contrib-guidedtour-api";

export class StorageManager {
  static parseStorageKeyPrefix(
    key: string,
  ): { taskId: string; tourId: string } | undefined {
    const regexp = new RegExp(/^guidedtour_(.*)__(.*)$/, "g");
    const matches = key.matchAll(regexp).next();
    const len = 0;
    if (matches.value === undefined || matches.value?.length != 3) {
      console.error(
        `Error parsing "${key}": Found ${len} matches, but I wanted exactly 2.`,
      );
      return undefined;
    }
    // Also unescape the ids
    const result = {
      tourId: matches.value[1].replaceAll("_|_", "__"),
      taskId: matches.value[2].replaceAll("_|_", "__"),
    };
    return result;
  }

  static getStorageKeyPrefix(task: TourTask): string {
    return StorageManager.getStorageKeyPrefixStr(task.tourId!, task.id!);
  }

  static getStorageKeyPrefixStr(tourId: string, taskId: string): string {
    return `guidedtour_${tourId.replaceAll("__", "_|_")}__${taskId.replaceAll("__", "_|_")}`;
  }

  static getActiveTaskStorageKey(): string {
    return "guidedtour_activeTask";
  }

  static getTaskCurrentStepStorageKey(task: TourTask): string {
    return this.getStorageKeyPrefix(task) + "_currentStep";
  }

  static getTaskStepStorageStorageKey(task: TourTask): string {
    return this.getStorageKeyPrefix(task) + "_steps";
  }

  /**
   * Get the Storage in which a key should be kept (sessionStorage or localStorage).
   * Keys pertaining to tasks that are in progress should be kept in sessionStorage, to ensure a task can only be
   * completed in the browser tab it was started.
   * Only the following keys use sessionStorage:
   * - TASK_ID_currentStep - Holds the current step of the active task
   * - TASK_ID_steps - Caches the steps of the active task, to avoid refetching if the task takes users to another page
   * - guidedtour_activeTask - The TASK_ID of the active task
   *
   * @param key - the storage key name to locate
   * @returns a Storage location where the key should be kept
   */
  static getStorageLocationForKey(key: string): Storage {
    return key.endsWith("_currentStep") ||
      key.endsWith("_steps") ||
      key == StorageManager.getActiveTaskStorageKey()
      ? globalThis.sessionStorage
      : globalThis.localStorage;
  }

  static getStorageKey(key: string) {
    const storageLocation = this.getStorageLocationForKey(key);
    return storageLocation.getItem(key);
  }

  static setStorageKey(key: string, stepIndex?: string) {
    const storageLocation = this.getStorageLocationForKey(key);
    if (stepIndex === undefined) {
      storageLocation.removeItem(key);
    } else {
      storageLocation.setItem(key, stepIndex);
    }
  }
}
