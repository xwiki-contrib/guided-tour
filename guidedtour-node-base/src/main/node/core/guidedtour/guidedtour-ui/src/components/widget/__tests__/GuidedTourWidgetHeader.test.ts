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

import { RESOLVER_PROVIDE, t } from "./i18nMock";
import GuidedTourWidgetHeader from "../GuidedTourWidgetHeader.vue";
import { flushPromises, mount } from "@vue/test-utils";
import { useI18nAdapter } from "@xwiki/platform-localization-adapter-vue";
import { describe, expect, it, vi } from "vitest";
import { computed, ref } from "vue";

vi.mock("@xwiki/platform-localization-adapter-vue", () => import("./i18nMock"));

async function mountHeader(progress = 0) {
  const wrapper = mount(GuidedTourWidgetHeader, {
    props: { progress: computed(() => progress) },
    global: { provide: RESOLVER_PROVIDE },
  });
  await flushPromises();
  return wrapper;
}

describe("GuidedTourWidgetHeader", () => {
  it("shows the translated title and close button", async () => {
    const wrapper = await mountHeader();

    expect(wrapper.find(".title").text()).toBe(
      "guidedtour.widget.header.title",
    );
    expect(wrapper.find("#widget-close").attributes("aria-label")).toBe(
      "guidedtour.widget.header.close",
    );
  });

  it("asks to close the widget when the close button is clicked", async () => {
    const wrapper = await mountHeader();

    await wrapper.find("#widget-close").trigger("click");

    expect(wrapper.emitted("closeGuidedTourWidget")).toHaveLength(1);
  });

  it("shows the title as the heading of the widget", async () => {
    const wrapper = await mountHeader();

    expect(wrapper.find("h2#guidedtour-widget-title").text()).toBe(
      "guidedtour.widget.header.title",
    );
  });

  it("shows the progress", async () => {
    const wrapper = await mountHeader(0.25);

    expect(
      wrapper.find(".completeness-progress").attributes("style"),
    ).toContain("width: 25%");
    expect(wrapper.find("[role='progressbar']").attributes("aria-label")).toBe(
      "guidedtour.widget.header.progress",
    );
  });

  it("shows nothing until the translations are loaded", async () => {
    vi.mocked(useI18nAdapter).mockReturnValueOnce({
      t,
      isLoading: ref(true),
    } as never);
    const wrapper = await mountHeader();

    expect(wrapper.find(".header").exists()).toBe(false);
  });
});
