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

import { DefaultStepManagerApi } from "./DefaultStepManagerApi";
import { DefaultTaskManagerApi } from "./DefaultTaskManagerApi";
import { DefaultTourManagerApi } from "./DefaultTourManagerApi";
import { DefaultUserStatusApi } from "./DefaultUserStatusApi";
import { GuidedTourRestClient } from "./GuidedTourRestClient";
import { StorageManager } from "../StorageManager";
import { driver, getDriverConfigForSteps, wrapTask } from "../driverjsMain";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { DocumentReference } from "@xwiki/platform-model-api";
import type { TourStore } from "./TourStore";
import type {
  GuidedTourManager,
  TourProgress,
  TourStep,
  TourTask,
  TourTour,
  UserTourStatus,
  WidgetState,
} from "@xwiki/contrib-guidedtour-api";
import type { Driver } from "driver.js";

/**
 * Facade that implements {@link GuidedTourManager} by delegating to the three
 * Default*ManagerApi classes and orchestrating driver.js tour playback.
 *
 * @since 1.0
 * @beta
 */
export class DefaultGuidedTourManager implements GuidedTourManager {
  private readonly defaultTourManagerApi: DefaultTourManagerApi;
  private readonly defaultTaskManagerApi: DefaultTaskManagerApi;
  private readonly defaultStepManagerApi: DefaultStepManagerApi;
  private readonly userStatusApi: DefaultUserStatusApi;

  /**
   * The progress and preferences of the current user, loaded once per page.
   */
  private userStatus?: Promise<UserTourStatus>;

  /**
   * The currently active driver.js instance, if a task is in progress.
   */
  activeDriverTask?: Driver;

  /**
   * The currently active task, if a task is in progress.
   */
  activeTask?: TourTask;
  /**
   * Various tour/step caches.
   */
  sharedStore: TourStore;

  /**
   * @param xm - xwikiMeta object, for access to some context and metadata.
   * @param sharedStore - Shared in-memory cache for tours, tasks, and steps.
   */
  constructor(
    // @ts-expect-error xwikiMeta is from a JavaScript file, it is expected to not have types.
    private readonly xm,
    sharedStore: TourStore,
    private readonly translations: Record<string, string>,
  ) {
    this.sharedStore = sharedStore;
    const restClient = new GuidedTourRestClient();
    this.defaultTourManagerApi = new DefaultTourManagerApi(
      restClient,
      sharedStore,
    );
    this.defaultTaskManagerApi = new DefaultTaskManagerApi(
      restClient,
      sharedStore,
    );
    this.defaultStepManagerApi = new DefaultStepManagerApi(
      restClient,
      sharedStore,
    );
    this.userStatusApi = new DefaultUserStatusApi(restClient, xm.userReference);
  }

  /**
   * Get the progress and preferences of the current user, loading them on the first call.
   */
  private getUserStatus(): Promise<UserTourStatus> {
    this.userStatus ??= this.userStatusApi.load().then((status) => {
      this.sharedStore.setUserToursStatus(status.toursStatus);
      return status;
    });
    return this.userStatus;
  }

  /**
   * Save the user status, with the current status of the tasks loaded on this page.
   */
  private async saveUserStatus() {
    const userStatus = await this.getUserStatus();
    // Update the progress of the tours loaded on this page. The tours that are not loaded here (e.g. tours from other
    // wikis) keep their stored progress.
    for (const tour of this.sharedStore.cache.tours) {
      if (tour.tasksList === undefined) {
        continue;
      }
      const progress = this.getTourProgress(userStatus, tour.id);
      // Replace the stored statuses of the tour instead of merging them, so that we always keep only the existing 
      // tasks of the tour, and not any tasks that were removed from the tour.
      progress.tasksStatus = Object.fromEntries(
        tour.tasksList.map((task) => [task.id, task.status]),
      );
      if (
        progress.callToAction &&
        tour.tasksList.every((task) => task.status === TourTaskStatus.TODO)
      ) {
        // Nothing to remember for this tour, it is the same as a tour that was never started.
        delete userStatus.toursStatus[tour.id];
      }
    }
    await this.userStatusApi.save(userStatus);
  }

  /**
   * Get the progress of the user in a tour, creating it with the default values if the user has none yet.
   */
  private getTourProgress(
    userStatus: UserTourStatus,
    tourId: string,
  ): TourProgress {
    userStatus.toursStatus[tourId] ??= { callToAction: true, tasksStatus: {} };
    return userStatus.toursStatus[tourId];
  }

  /**
   * Whether the next task of a tour should be started automatically when a task of the tour is finished.
   */
  private async isCallToActionEnabled(tourId: string): Promise<boolean> {
    return (
      (await this.getUserStatus()).toursStatus[tourId]?.callToAction !== false
    );
  }

  async getWidgetState(): Promise<WidgetState> {
    return (await this.getUserStatus()).widgetState;
  }

  async setWidgetState(widgetState: WidgetState): Promise<void> {
    const userStatus = await this.getUserStatus();
    userStatus.widgetState = widgetState;
    await this.userStatusApi.save(userStatus);
  }

  /**
   * Stop starting the next tasks of a tour automatically, because the user closed or skipped one of its tasks.
   * The change is saved along with the status of the task, by {@link setTaskStatus}.
   * @param tourId - The id of the tour.
   */
  async disableCallToAction(tourId: string): Promise<void> {
    this.getTourProgress(await this.getUserStatus(), tourId).callToAction =
      false;
  }

  async skipTask(task: TourTask): Promise<void> {
    await this.disableCallToAction(task.tourId!);
    await this.setTaskStatus(task, TourTaskStatus.SKIPPED);
  }

  async resetTour(tourId: string): Promise<void> {
    // Resetting an entire tour starts its next tasks automatically again.
    this.getTourProgress(await this.getUserStatus(), tourId).callToAction =
      true;
    await this.setTourTasksStatus(tourId, TourTaskStatus.TODO, () => true);
  }

  async skipTour(tourId: string): Promise<void> {
    // Only skip the tasks left to do, keep the ones already done or skipped.
    await this.setTourTasksStatus(
      tourId,
      TourTaskStatus.SKIPPED,
      (task) => task.status === TourTaskStatus.TODO,
    );
  }

  /**
   * Set the status of the tasks of a tour, then save the user status with a single request.
   * @param tourId - The id of the tour.
   * @param status - The new status of the tasks.
   * @param filter - Selects the tasks of the tour whose status should change.
   */
  private async setTourTasksStatus(
    tourId: string,
    status: TourTaskStatus,
    filter: (task: TourTask) => boolean,
  ): Promise<void> {
    const tasks = (await this.getTasks(tourId)).filter(filter);
    for (const task of tasks) {
      task.status = status;
    }
    const tour = await this.defaultTourManagerApi.getTour(tourId);
    if (tour !== undefined) {
      this.defaultTourManagerApi.computeToursStatus([tour]);
    }
    if (this.activeTask !== undefined && tasks.includes(this.activeTask)) {
      this.destroyActiveTask();
    }
    await this.saveUserStatus();
  }

  async getTours(): Promise<TourTour[]> {
    // The user status is needed to set the status of the tasks.
    await this.getUserStatus();
    const tours = await this.defaultTourManagerApi.getTours();

    this.defaultTourManagerApi.computeToursStatus(tours ?? []);
    return tours;
  }

  async createTour(tour: TourTour): Promise<void> {
    await this.defaultTourManagerApi.createTour(tour);
  }

  async deleteTour(tourId: string): Promise<void> {
    await this.defaultTourManagerApi.deleteTour(tourId);
  }

  async updateTour(tourId: string, tour: TourTour): Promise<void> {
    await this.defaultTourManagerApi.updateTour(tourId, tour);
  }

  /**
   * Delete a step by delegating to {@link DefaultStepManagerApi}.
   */
  async deleteStep(
    tourId: string,
    taskId: string,
    stepId: number,
  ): Promise<void> {
    await this.defaultStepManagerApi.deleteStep(tourId, taskId, stepId);
  }

  /**
   * Update a step by delegating to {@link DefaultStepManagerApi}.
   */
  async updateStep(
    tourId: string,
    taskId: string,
    stepId: number,
    stepData: TourStep,
  ): Promise<void> {
    await this.defaultStepManagerApi.updateStep(
      tourId,
      taskId,
      stepId,
      stepData,
    );
  }

  /**
   * Create a step by delegating to {@link DefaultStepManagerApi}.
   */
  async createStep(
    tourId: string,
    taskId: string,
    stepData: TourStep,
  ): Promise<void> {
    await this.defaultStepManagerApi.createStep(tourId, taskId, stepData);
  }

  getTask(tourId: string, taskId: string): Promise<TourTask | undefined> {
    return this.defaultTaskManagerApi.getTask(tourId, taskId);
  }

  getTasks(tourId: string): Promise<TourTask[]> {
    return this.defaultTaskManagerApi.getTasks(tourId);
  }

  updateTask(
    tourId: string,
    taskId: string,
    taskData: TourTask,
  ): Promise<void> {
    return this.defaultTaskManagerApi.updateTask(tourId, taskId, taskData);
  }

  createTask(tourId: string, taskData: TourTask): Promise<void> {
    return this.defaultTaskManagerApi.createTask(tourId, taskData);
  }

  deleteTask(tourId: string, taskId: string): Promise<void> {
    return this.defaultTaskManagerApi.deleteTask(tourId, taskId);
  }

  /**
   * Get the sandbox space document reference name.
   * TODO: Make this configurable via Admin Settings.
   */
  getSandboxSpace(): Promise<string> {
    return Promise.resolve(new DocumentReference("Sandbox.WebHome").name);
  }

  /**
   * Get useful links to display in the widget.
   * TODO: Get these from the Admin section.
   */
  getUsefulLinks(): Promise<string[]> {
    const usefulLinks: string[] = [
      // "<a>Useful link 1</a>",
      // "<a>Useful link 2</a>",
    ];
    return Promise.resolve(usefulLinks);
  }

  /**
   * Start a guided tour task.
   * Fetches the steps, creates a driver.js instance, wraps it for session
   * persistence, and begins the tour at the remembered or first step.
   * @param task - The task to start.
   * @param remember - Whether to resume from a saved step index.
   */
  async startTask(task: TourTask, remember = true): Promise<void> {
    // Fetch or get the cached steps.
    task.steps ??= await this.getSteps(task.tourId!, task.id);
    const stepIndex = remember
      ? Number.parseInt(
          StorageManager.getStorageKey(
            StorageManager.getTaskCurrentStepStorageKey(task),
          ) ?? "0",
        )
      : 0;
    const config = await getDriverConfigForSteps(task, this, this.translations);
    const driverTour = driver(config);
    StorageManager.setStorageKey(
      StorageManager.getActiveTaskStorageKey(),
      StorageManager.getStorageKeyPrefix(task),
    );
    StorageManager.setStorageKey(
      StorageManager.getTaskStepStorageStorageKey(task),
      JSON.stringify(task.steps!),
    );

    this.activeTask = task;
    this.activeDriverTask = wrapTask(driverTour, this, this.translations);
    this.activeDriverTask.drive(stepIndex);
  }

  /**
   * TODO: Discuss why is this needed
   */
  setupStep(step: TourStep): void {
    console.log(step);
  }

  /**
   * Check session storage for an in-progress task and resume it. Shows a notification if an error was encountered.
   * Called on page load to recover tours that span multiple pages.
   */
  // eslint-disable-next-line max-statements
  async initExistingTask() {
    // FIXME: This should be moved somewhere else, but idk where. `GuidedTourWidget.vue` ? idk
    const existingActiveTask = StorageManager.getStorageKey(
      StorageManager.getActiveTaskStorageKey(),
    );
    if (existingActiveTask) {
      const parsedIds =
        StorageManager.parseStorageKeyPrefix(existingActiveTask);
      if (parsedIds === undefined) {
        console.error("No good task parsing value:", parsedIds);
        new XWiki.widgets.Notification(
          this.translations["guidedtour.driver.error.initExistingTask"],
          "error",
        );
      } else {
        // Populate the cache by fetching all tours first.
        await this.getTours();
        const task = await this.getTask(
          parsedIds["tourId"],
          parsedIds["taskId"],
        );
        if (task !== undefined) {
          await this.startTask(task, true);
        } else {
          console.error(
            "Tried to get task for ",
            parsedIds,
            ", it didn't work.",
          );
          new XWiki.widgets.Notification(
            this.translations["guidedtour.driver.error.initExistingTask"],
            "error",
          );
        }
      }
    }
  }

  /**
   * Update the status of a task and clear the associated session storage keys.
   * If the given task is the currently active task, the task will be instantly ended.
   * @param task - The task whose status to change.
   * @param status - The new status (TODO, SKIPPED, or DONE).
   */
  async setTaskStatus(task: TourTask, status: TourTaskStatus): Promise<void> {
    task.status = status;
    this.defaultTourManagerApi.computeToursStatus(
      Array.of((await this.defaultTourManagerApi.getTour(task.tourId!))!),
    );
    const wasActive = task === this.activeTask;
    if (wasActive) {
      // Since we're setting the task status, it means we're done with all steps. So destroy the active task.
      this.destroyActiveTask();
    }
    // Sync with storage.
    await this.saveUserStatus();
    if (
      wasActive &&
      status === TourTaskStatus.DONE &&
      (await this.isCallToActionEnabled(task.tourId!))
    ) {
      await this.startNextTask(task);
    }
  }

  /**
   * Start the next task to do in the tour of the given task, if there is one left. Never moves to another tour.
   * @param task - The task that was just finished.
   */
  private async startNextTask(task: TourTask): Promise<void> {
    const nextTask = (await this.getTasks(task.tourId!))
      .filter((t) => t.active && t.status === TourTaskStatus.TODO)
      .sort((a, b) => a.order - b.order)[0];
    if (nextTask !== undefined) {
      await this.startTask(nextTask, false);
    }
  }

  /**
   * Delete all data pertaining to the current task in progress.
   * Deletes the active task object, and the Session Storage keys for current step index and cached steps.
   * Will also end the active task which is in progress.
   */
  private destroyActiveTask() {
    const currentStepKey = StorageManager.getTaskCurrentStepStorageKey(
      this.activeTask!,
    );
    const stepStorageKey = StorageManager.getTaskStepStorageStorageKey(
      this.activeTask!,
    );

    this.activeTask = undefined;
    // this.activeTask is used as a flag to tell `onDestroy()` to not re-compute the task status.
    this.activeDriverTask?.destroy();
    this.activeDriverTask = undefined;
    // Update the storage keys last, since they could be used in `onDestroy()` to compute the task status.
    StorageManager.setStorageKey(currentStepKey, undefined);
    StorageManager.setStorageKey(stepStorageKey, undefined);
    StorageManager.setStorageKey(
      StorageManager.getActiveTaskStorageKey(),
      undefined,
    );
  }

  /**
   * Get all steps for a task by delegating to {@link DefaultStepManagerApi}.
   */
  async getSteps(tourId: string, taskId: string): Promise<TourStep[]> {
    // FIXME: This parsing step should be moved elsewhere.
    let parsedCachedSteps;
    try {
      parsedCachedSteps = JSON.parse(
        StorageManager.getStorageKey(
          StorageManager.getTaskStepStorageStorageKey(
            (await this.getTask(tourId, taskId))!,
          ),
        ) ?? "",
      ) as TourStep[];
      console.info("Using cached steps:", parsedCachedSteps);
    } catch {
      console.info("No cached guidedtour steps.");
    }
    return (
      parsedCachedSteps ??
      (await this.defaultStepManagerApi.getSteps(tourId, taskId))
    );
  }
}
