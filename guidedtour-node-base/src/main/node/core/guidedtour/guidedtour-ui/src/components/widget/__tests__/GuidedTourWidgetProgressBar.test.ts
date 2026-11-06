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

import GuidedTourWidgetProgressBar from "../GuidedTourWidgetProgressBar.vue";
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { computed, ref } from "vue";

function getProgressStyle(wrapper: ReturnType<typeof mount>) {
  return wrapper.find(".completeness-progress").attributes("style");
}

describe("GuidedTourWidgetProgressBar", () => {
  it("fills the bar proportionally to the progress", async () => {
    const progress = ref(0.5);
    const wrapper = mount(GuidedTourWidgetProgressBar, {
      props: { progress: computed(() => progress.value) },
    });

    expect(getProgressStyle(wrapper)).toContain("width: 50%");

    progress.value = 1;
    await wrapper.vm.$nextTick();
    expect(getProgressStyle(wrapper)).toContain("width: 100%");
  });
});
