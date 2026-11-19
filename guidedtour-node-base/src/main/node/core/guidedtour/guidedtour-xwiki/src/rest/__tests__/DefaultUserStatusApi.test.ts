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

import { StorageManager } from "../../StorageManager";
import { DefaultUserStatusApi } from "../DefaultUserStatusApi";
import { RestError } from "../RestError";
import { TourTaskStatus, WidgetState } from "@xwiki/contrib-guidedtour-api";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { GuidedTourRestClient } from "../GuidedTourRestClient";
import type { UserTourStatus } from "@xwiki/contrib-guidedtour-api";

vi.mock("../../services/xwiki.js", () => ({
  XWiki: { contextPath: "/xwiki" },
}));

const USER_URL = "/xwiki/rest/guidedTour/user";

const USER_REFERENCE = "xwiki:XWiki.Alice";

function createStatus(): UserTourStatus {
  return {
    widgetState: WidgetState.COLLAPSED,
    toursStatus: {
      tour1: {
        callToAction: false,
        tasksStatus: { task1: TourTaskStatus.DONE },
      },
    },
  };
}

function createWidgetStatus(widgetState: WidgetState): UserTourStatus {
  return { widgetState, toursStatus: {} };
}

function getDefaultStatus(): UserTourStatus {
  return { widgetState: WidgetState.OPEN, toursStatus: {} };
}

describe("DefaultUserStatusApi", () => {
  let request: ReturnType<typeof vi.fn>;
  let restClient: GuidedTourRestClient;

  beforeEach(() => {
    request = vi.fn();
    restClient = { request } as unknown as GuidedTourRestClient;
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  describe("guest user", () => {
    it("loads the default status when nothing is stored", async () => {
      const api = new DefaultUserStatusApi(restClient, null);

      expect(await api.load()).toEqual(getDefaultStatus());
      expect(request).not.toHaveBeenCalled();
    });

    it("loads the status from the local storage", async () => {
      localStorage.setItem(
        StorageManager.getGuestStatusStorageKey(),
        JSON.stringify(createStatus()),
      );
      const api = new DefaultUserStatusApi(restClient, null);

      expect(await api.load()).toEqual(createStatus());
    });

    it("loads the default status when the stored one is invalid", async () => {
      localStorage.setItem(
        StorageManager.getGuestStatusStorageKey(),
        "not json",
      );
      const api = new DefaultUserStatusApi(restClient, null);

      expect(await api.load()).toEqual(getDefaultStatus());
    });

    it("saves the status in the local storage", async () => {
      const api = new DefaultUserStatusApi(restClient, null);

      await api.save(createStatus());

      expect(
        JSON.parse(
          localStorage.getItem(StorageManager.getGuestStatusStorageKey())!,
        ),
      ).toEqual(createStatus());
      expect(request).not.toHaveBeenCalled();
    });
  });

  describe("logged-in user", () => {
    it("loads the status from the user profile", async () => {
      request.mockResolvedValue(createStatus());
      const api = new DefaultUserStatusApi(restClient, USER_REFERENCE);

      expect(await api.load()).toEqual(createStatus());
      expect(request).toHaveBeenCalledWith(USER_URL, "GET", undefined, [404]);
    });

    it("adds the missing tours status", async () => {
      request.mockResolvedValue({ widgetState: WidgetState.OPEN });
      const api = new DefaultUserStatusApi(restClient, USER_REFERENCE);

      expect(await api.load()).toEqual(getDefaultStatus());
    });

    it("uses the default status and keeps saving when the user has no status yet", async () => {
      request.mockRejectedValueOnce(new RestError(404));
      request.mockResolvedValue({});
      const consoleError = vi.spyOn(console, "error");
      const api = new DefaultUserStatusApi(restClient, USER_REFERENCE);

      expect(await api.load()).toEqual(getDefaultStatus());
      await api.save(createStatus());

      expect(request).toHaveBeenLastCalledWith(USER_URL, "PUT", createStatus());
      expect(consoleError).not.toHaveBeenCalled();
    });

    it("uses the default status and stops saving when the status can't be loaded", async () => {
      request.mockRejectedValueOnce(new RestError(500));
      const consoleError = vi
        .spyOn(console, "error")
        .mockImplementation(() => {});
      const api = new DefaultUserStatusApi(restClient, USER_REFERENCE);

      expect(await api.load()).toEqual(getDefaultStatus());
      await api.save(createStatus());

      // Only the GET request, the default status must not overwrite the stored one.
      expect(request).toHaveBeenCalledTimes(1);
      expect(consoleError).toHaveBeenCalled();
    });

    it("sends a copy of the status", async () => {
      request.mockResolvedValue({});
      const api = new DefaultUserStatusApi(restClient, USER_REFERENCE);
      const status = createStatus();

      const save = api.save(status);
      status.widgetState = WidgetState.OPEN;
      await save;

      expect(request).toHaveBeenCalledWith(USER_URL, "PUT", createStatus());
    });

    it("sends the saves in order, even when one fails", async () => {
      const sent: WidgetState[] = [];
      let failFirstSave: (error: Error) => void = () => {};
      request.mockImplementation(
        (_url: string, _method: string, status: UserTourStatus) => {
          sent.push(status.widgetState);
          // Keep the first request pending, until the test makes it fail.
          return sent.length === 1
            ? new Promise((_resolve, reject) => (failFirstSave = reject))
            : Promise.resolve({});
        },
      );
      const api = new DefaultUserStatusApi(restClient, USER_REFERENCE);

      const firstSave = api.save(createWidgetStatus(WidgetState.COLLAPSED));
      const secondSave = api.save(createWidgetStatus(WidgetState.OPEN));
      // Let the first request start: the second one must wait for it to finish.
      await vi.waitFor(() => expect(sent).toHaveLength(1));
      failFirstSave(new RestError(500));

      await Promise.all([
        expect(firstSave).rejects.toThrow(RestError),
        secondSave,
      ]);
      expect(sent).toEqual([WidgetState.COLLAPSED, WidgetState.OPEN]);
    });
  });
});
