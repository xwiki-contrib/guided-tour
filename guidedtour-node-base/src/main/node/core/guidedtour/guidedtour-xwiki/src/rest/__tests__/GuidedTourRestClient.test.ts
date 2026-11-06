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

import { GuidedTourRestClient } from "../GuidedTourRestClient";
import { RestError } from "../RestError";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ notification: vi.fn() }));

vi.mock("../../services/xwiki.js", () => ({
  XWiki: { widgets: { Notification: mocks.notification } },
}));

const TOURS_URL = "/xwiki/rest/guidedTour/tours";

describe("GuidedTourRestClient", () => {
  let fetchMock: ReturnType<typeof vi.fn>;
  const client = new GuidedTourRestClient();

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    mocks.notification.mockReset();
  });

  it("returns the JSON body of a GET request", async () => {
    fetchMock.mockResolvedValue(Response.json([{ id: "tour1" }]));

    expect(await client.request(TOURS_URL, "GET")).toEqual([{ id: "tour1" }]);
    expect(fetchMock).toHaveBeenCalledWith(
      TOURS_URL,
      expect.objectContaining({ method: "GET", body: undefined }),
    );
    expect(fetchMock.mock.calls[0][1].headers).not.toHaveProperty(
      "Content-Type",
    );
  });

  it.each(["POST", "PUT"] as const)(
    "sends a JSON body with %s",
    async (method) => {
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

      expect(await client.request(TOURS_URL, method, { id: "tour1" })).toEqual(
        {},
      );
      expect(fetchMock).toHaveBeenCalledWith(TOURS_URL, {
        method,
        headers: expect.objectContaining({
          "Content-Type": "application/json",
        }),
        body: JSON.stringify({ id: "tour1" }),
      });
    },
  );

  it.each([
    [401, "You don't have the rights to perform this operation.", "error"],
    [404, "The requested resource was not found.", "error"],
    [409, "The resource already exists.", "warning"],
    [500, "An unexpected error occurred.", "error"],
  ])(
    "notifies the user of a %s error",
    async (status, message, notificationType) => {
      fetchMock.mockResolvedValue(new Response(null, { status }));

      const request = client.request(TOURS_URL, "DELETE");

      await expect(request).rejects.toBeInstanceOf(RestError);
      await expect(request).rejects.toMatchObject({ status });
      expect(mocks.notification).toHaveBeenCalledExactlyOnceWith(
        message,
        notificationType,
      );
    },
  );

  it("doesn't notify the user of an error handled by the caller", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 404 }));

    await expect(
      client.request(TOURS_URL, "GET", undefined, [404]),
    ).rejects.toMatchObject({ status: 404 });
    expect(mocks.notification).not.toHaveBeenCalled();
  });
});
