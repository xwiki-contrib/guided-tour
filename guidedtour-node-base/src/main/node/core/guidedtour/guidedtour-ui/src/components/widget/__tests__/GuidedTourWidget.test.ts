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

import GuidedTourWidget from "../GuidedTourWidget.vue";
import GuidedTourWidgetHeader from "../GuidedTourWidgetHeader.vue";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { WidgetState } from "@xwiki/contrib-guidedtour-api";
import { describe, expect, it, vi } from "vitest";
import type { GuidedTourManager } from "@xwiki/contrib-guidedtour-api";

function createGuidedTourManager(
  widgetState: Promise<WidgetState>,
): GuidedTourManager {
  return {
    getTours: vi.fn().mockResolvedValue([]),
    getUsefulLinks: vi.fn().mockResolvedValue([]),
    initExistingTask: vi.fn(),
    getWidgetState: vi.fn().mockReturnValue(widgetState),
    setWidgetState: vi.fn().mockResolvedValue(undefined),
  } as unknown as GuidedTourManager;
}

function mountWidget(guidedTourManager: GuidedTourManager) {
  return shallowMount(GuidedTourWidget, { props: { guidedTourManager } });
}

/**
 * Click on the header of the widget, which collapses or expands it.
 */
async function toggleWidget(wrapper: ReturnType<typeof mountWidget>) {
  wrapper
    .findComponent(GuidedTourWidgetHeader)
    .vm.$emit("closeGuidedTourWidget", false);
  await flushPromises();
}

describe("GuidedTourWidget", () => {
  it.each([
    [WidgetState.COLLAPSED, true],
    [WidgetState.OPEN, false],
  ])("restores the saved widget state %s", async (widgetState, collapsed) => {
    const wrapper = mountWidget(
      createGuidedTourManager(Promise.resolve(widgetState)),
    );
    await flushPromises();

    expect(wrapper.classes("collapsed")).toBe(collapsed);
  });

  it("saves the widget state when it is toggled", async () => {
    const guidedTourManager = createGuidedTourManager(
      Promise.resolve(WidgetState.OPEN),
    );
    const wrapper = mountWidget(guidedTourManager);
    await flushPromises();

    await toggleWidget(wrapper);
    expect(wrapper.classes("collapsed")).toBe(true);
    expect(guidedTourManager.setWidgetState).toHaveBeenLastCalledWith(
      WidgetState.COLLAPSED,
    );

    await toggleWidget(wrapper);
    expect(wrapper.classes("collapsed")).toBe(false);
    expect(guidedTourManager.setWidgetState).toHaveBeenLastCalledWith(
      WidgetState.OPEN,
    );
  });

  it("keeps the state chosen by the user before the saved one is loaded", async () => {
    let loadWidgetState: (widgetState: WidgetState) => void = () => {};
    const wrapper = mountWidget(
      createGuidedTourManager(
        new Promise((resolve) => (loadWidgetState = resolve)),
      ),
    );

    // The widget starts collapsed, the user expands it.
    await toggleWidget(wrapper);
    loadWidgetState(WidgetState.COLLAPSED);
    await flushPromises();

    expect(wrapper.classes("collapsed")).toBe(false);
  });
});
