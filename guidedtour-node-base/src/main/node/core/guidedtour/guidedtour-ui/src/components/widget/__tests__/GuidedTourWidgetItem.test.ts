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

import GuidedTourWidgetItem from "../GuidedTourWidgetItem.vue";
import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import { ref } from "vue";
import type { Ref } from "vue";

describe("GuidedTourWidgetItem", () => {
  it.each([
    ["it is loading", { loading: true, waiting: ref(false) }],
    // This is how the placeholders are declared.
    ["it has no state", {}],
    ["it has no waiting state", { loading: false }],
  ])("shows a placeholder when %s", (_description, props) => {
    const wrapper = mount(GuidedTourWidgetItem, {
      props,
      slots: { "item-title": "Title" },
    });

    expect(wrapper.find(".guidedtour-widget-item").classes()).toContain(
      "loading",
    );
    expect(wrapper.text()).toBe("");
  });

  it("shows its content and handles clicks once loaded", async () => {
    const onClick = vi.fn();
    const wrapper = mount(GuidedTourWidgetItem, {
      props: { loading: false, waiting: ref(false), id: "item1", onClick },
      slots: {
        "pre-btns": "<button class='pre'>pre</button>",
        "item-title": "Title",
        "post-btns": "<button class='post'>post</button>",
      },
    });

    const item = wrapper.find(".guidedtour-widget-item");
    expect(item.classes()).not.toContain("loading");
    expect(item.attributes("id")).toBe("item1");
    expect(wrapper.find(".pre-btns .pre").exists()).toBe(true);
    expect(wrapper.find(".guidedtour-widget-item-title").text()).toBe("Title");
    expect(wrapper.find(".post-btns .post").exists()).toBe(true);

    await item.trigger("click");
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("shows the spinner only while waiting", async () => {
    const waiting: Ref<boolean> = ref(false);
    const wrapper = mount(GuidedTourWidgetItem, {
      props: { loading: false, waiting },
    });
    const spinner = () => wrapper.find(".fa-circle-notch");

    expect(spinner().classes()).toContain("transparent");

    waiting.value = true;
    await wrapper.vm.$nextTick();
    expect(spinner().classes()).not.toContain("transparent");
  });

  it("replaces the spinner with the loading-icon slot", () => {
    const wrapper = mount(GuidedTourWidgetItem, {
      props: { loading: false, waiting: ref(false) },
      slots: { "loading-icon": "<i class='custom-icon'></i>" },
    });

    expect(wrapper.find(".custom-icon").exists()).toBe(true);
    expect(wrapper.find(".fa-circle-notch").exists()).toBe(false);
  });
});
