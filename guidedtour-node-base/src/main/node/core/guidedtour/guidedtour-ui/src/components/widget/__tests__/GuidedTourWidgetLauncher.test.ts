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
import GuidedTourWidgetLauncher from "../GuidedTourWidgetLauncher.vue";
import { flushPromises, mount } from "@vue/test-utils";
import { useI18nAdapter } from "@xwiki/platform-localization-adapter-vue";
import { describe, expect, it, vi } from "vitest";
import { ref } from "vue";

vi.mock("@xwiki/platform-localization-adapter-vue", () => import("./i18nMock"));

async function mountLauncher(
  props: { finished?: number; total?: number; expanded?: boolean } = {},
) {
  const wrapper = mount(GuidedTourWidgetLauncher, {
    props: {
      id: "launcher",
      finished: 1,
      total: 3,
      expanded: false,
      panelId: "panel",
      ...props,
    },
    global: { provide: RESOLVER_PROVIDE },
  });
  await flushPromises();
  return wrapper;
}

describe("GuidedTourWidgetLauncher", () => {
  it("shows the title and how many tours are finished", async () => {
    const wrapper = await mountLauncher();

    expect(wrapper.find(".guidedtour-widget-launcher-text").text()).toBe(
      "guidedtour.widget.launcher.title",
    );
    const count = wrapper.find(".guidedtour-widget-launcher-count");
    expect(count.find("[aria-hidden='true']").text()).toBe("1/3");
    expect(count.find(".sr-only").text()).toBe(
      "guidedtour.widget.launcher.progress [1, 3]",
    );
  });

  it("doesn't show the count when there are no tours", async () => {
    const wrapper = await mountLauncher({ finished: 0, total: 0 });

    expect(wrapper.find(".guidedtour-widget-launcher-count").exists()).toBe(
      false,
    );
  });

  it.each([true, false])(
    "tells whether the panel is expanded: %s",
    async (expanded) => {
      const wrapper = await mountLauncher({ expanded });

      const button = wrapper.find("button");
      expect(button.attributes("aria-expanded")).toBe(String(expanded));
      expect(button.attributes("aria-controls")).toBe("panel");
    },
  );

  it("asks to toggle the widget when clicked", async () => {
    const wrapper = await mountLauncher();

    await wrapper.find("button").trigger("click");

    expect(wrapper.emitted("toggle")).toHaveLength(1);
  });

  it("shows nothing until the translations are loaded", async () => {
    vi.mocked(useI18nAdapter).mockReturnValueOnce({
      t,
      isLoading: ref(true),
    } as never);
    const wrapper = await mountLauncher();

    expect(wrapper.find("button").exists()).toBe(false);
  });
});
