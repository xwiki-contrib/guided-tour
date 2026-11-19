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

import GuidedTourWidgetTour from "../GuidedTourWidgetTour.vue";
import { flushPromises, mount } from "@vue/test-utils";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { describe, expect, it, vi } from "vitest";
import type {
  GuidedTourManager,
  TourTour,
} from "@xwiki/contrib-guidedtour-api";

function createGuidedTourManager(): GuidedTourManager {
  return {
    getTours: vi.fn().mockResolvedValue([]),
    getTasks: vi.fn().mockResolvedValue([]),
    skipTour: vi.fn().mockResolvedValue(undefined),
    resetTour: vi.fn().mockResolvedValue(undefined),
  } as unknown as GuidedTourManager;
}

async function mountTour(
  status: TourTaskStatus,
  guidedTourManager: GuidedTourManager,
) {
  const tour: TourTour = { id: "tour1", title: "Tour 1", status };
  const wrapper = mount(GuidedTourWidgetTour, {
    props: { tour },
    global: {
      provide: { GuidedTourManager: guidedTourManager },
      stubs: { GuidedTourWidgetTask: true },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("GuidedTourWidgetTour", () => {
  it("skips the tour when it has tasks left to do", async () => {
    const guidedTourManager = createGuidedTourManager();
    const wrapper = await mountTour(TourTaskStatus.TODO, guidedTourManager);

    await wrapper.find(".guidedtour-tour-header .post-btn").trigger("click");

    expect(guidedTourManager.skipTour).toHaveBeenCalledExactlyOnceWith("tour1");
    expect(guidedTourManager.resetTour).not.toHaveBeenCalled();
    // The click must not toggle the tour.
    expect(wrapper.emitted("toggleCollapseTour")).toBeUndefined();
  });

  it.each([TourTaskStatus.DONE, TourTaskStatus.SKIPPED])(
    "resets the tour when it is %s",
    async (status) => {
      const guidedTourManager = createGuidedTourManager();
      const wrapper = await mountTour(status, guidedTourManager);

      await wrapper.find(".guidedtour-tour-header .post-btn").trigger("click");

      expect(guidedTourManager.resetTour).toHaveBeenCalledExactlyOnceWith(
        "tour1",
      );
      expect(guidedTourManager.skipTour).not.toHaveBeenCalled();
    },
  );
});
