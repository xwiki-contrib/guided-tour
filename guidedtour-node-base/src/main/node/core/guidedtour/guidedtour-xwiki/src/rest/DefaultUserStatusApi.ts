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
import { RestError } from "./RestError";
import { StorageManager } from "../StorageManager";
// @ts-expect-error this is a JavaScript file, it is expected to not have types.
import { XWiki } from "../services/xwiki.js";
import { WidgetState } from "@xwiki/contrib-guidedtour-api";
import type { GuidedTourRestClient } from "./GuidedTourRestClient";
import type { UserTourStatus } from "@xwiki/contrib-guidedtour-api";

/**
 * Loads and saves the {@link UserTourStatus} of the current user.
 * The status of logged-in users is kept in their user profile, through the REST API, so that it follows them across
 * browsers. Guest users don't have a profile, so their status is kept in the local storage of the browser.
 *
 * @since 1.0
 * @beta
 */
export class DefaultUserStatusApi {
  /**
   * Chain of pending saves, so that they reach the server in the order they were made.
   */
  private pendingSave: Promise<void> = Promise.resolve();

  /**
   * Whether saving to the user profile is disabled, because the stored status could not be loaded.
   */
  private saveDisabled = false;

  constructor(
    private readonly restClient: GuidedTourRestClient,
    private readonly userReference: string | null,
  ) {}

  /**
   * Load the status of the current user.
   */
  async load(): Promise<UserTourStatus> {
    const status = this.isGuest()
      ? this.getGuestStatus()
      : await this.fetchStatus();
    status.toursStatus ??= {};
    return status;
  }

  /**
   * Save the status of the current user.
   * @param status - The status to save.
   */
  save(status: UserTourStatus): Promise<void> {
    if (this.isGuest()) {
      StorageManager.setStorageKey(
        StorageManager.getGuestStatusStorageKey(),
        JSON.stringify(status),
      );
      return Promise.resolve();
    }
    if (this.saveDisabled) {
      return Promise.resolve();
    }
    // Send a copy, so that later changes don't alter a request that is waiting for its turn.
    const body = structuredClone(status);
    this.pendingSave = this.pendingSave
      .catch(() => {
        // The failure was already reported, don't block the next saves.
      })
      .then(() => this.restClient.request<void>(this.getUrl(), "PUT", body));
    return this.pendingSave;
  }

  /**
   * Fetch the status from the user profile.
   * If the user has no status yet (first time), the default one is used. It is kept in memory only, until the first
   * change creates it in the user profile.
   * If the status could not be fetched, the default one is used too, but saving is disabled for the current page, to
   * avoid overwriting the stored status with the default one.
   */
  private async fetchStatus(): Promise<UserTourStatus> {
    try {
      return await this.restClient.request<UserTourStatus>(
        this.getUrl(),
        "GET",
        undefined,
        [404],
      );
    } catch (e) {
      if (!(e instanceof RestError && e.status === 404)) {
        console.error("Failed to load the guided tour user status.", e);
        this.saveDisabled = true;
      }
      return this.getDefaultStatus();
    }
  }

  /**
   * Get the status of a guest user from the local storage, or the default one if there is none.
   */
  private getGuestStatus(): UserTourStatus {
    const stored = StorageManager.getStorageKey(
      StorageManager.getGuestStatusStorageKey(),
    );
    try {
      return { ...this.getDefaultStatus(), ...JSON.parse(stored ?? "{}") };
    } catch {
      return this.getDefaultStatus();
    }
  }

  private getDefaultStatus(): UserTourStatus {
    return {
      widgetState: WidgetState.OPEN,
      toursStatus: {},
    };
  }

  private isGuest(): boolean {
    return !this.userReference;
  }

  private getUrl(): string {
    return `${XWiki.contextPath}/rest/guidedTour/user`;
  }
}
