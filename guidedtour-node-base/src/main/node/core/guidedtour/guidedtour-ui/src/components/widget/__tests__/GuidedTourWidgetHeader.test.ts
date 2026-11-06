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

import { RESOLVER } from "../../../injectionKeys";
import GuidedTourWidgetHeader from "../GuidedTourWidgetHeader.vue";
import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import { computed } from "vue";
import { createI18n } from "vue-i18n";
import type { Resolver } from "@xwiki/platform-localization-api";

function createResolver(): Resolver {
  return {
    resolve: vi.fn().mockResolvedValue({
      translations: {
        "guidedtour.widget.header.title": "Guided Tour",
        "guidedtour.widget.header.close": "Close",
      },
    }),
  };
}

async function mountHeader(progress = 0, resolver = createResolver()) {
  const wrapper = mount(GuidedTourWidgetHeader, {
    props: { progress: computed(() => progress) },
    global: {
      plugins: [createI18n({ legacy: false, locale: "en" })],
      provide: { [RESOLVER]: resolver },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("GuidedTourWidgetHeader", () => {
  it("shows the translated title and close button", async () => {
    const wrapper = await mountHeader();

    expect(wrapper.find(".title").text()).toBe("Guided Tour");
    expect(wrapper.find("#widget-close").attributes("aria-label")).toBe(
      "Close",
    );
  });

  it("asks to toggle the widget when the top bar is clicked", async () => {
    const wrapper = await mountHeader();

    await wrapper.find(".top-bar").trigger("click");

    expect(wrapper.emitted("closeGuidedTourWidget")).toEqual([[false]]);
  });

  it("asks to close the widget when the close button is clicked", async () => {
    const wrapper = await mountHeader();

    await wrapper.find("#widget-close").trigger("click");

    // The click must not also reach the top bar.
    expect(wrapper.emitted("closeGuidedTourWidget")).toEqual([[true]]);
  });

  it("shows the progress", async () => {
    const wrapper = await mountHeader(0.25);

    expect(
      wrapper.find(".completeness-progress").attributes("style"),
    ).toContain("width: 25%");
  });

  it("shows nothing until the translations are loaded", async () => {
    const wrapper = await mountHeader(0, {
      resolve: () => new Promise(() => {}),
    });

    expect(wrapper.find(".header").exists()).toBe(false);
  });
});
