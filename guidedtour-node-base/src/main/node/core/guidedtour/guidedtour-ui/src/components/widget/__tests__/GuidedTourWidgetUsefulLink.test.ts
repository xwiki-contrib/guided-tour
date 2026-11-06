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

import GuidedTourWidgetUsefulLink from "../GuidedTourWidgetUsefulLink.vue";
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

describe("GuidedTourWidgetUsefulLink", () => {
  it("renders the HTML of the link", () => {
    const wrapper = mount(GuidedTourWidgetUsefulLink, {
      props: { link: "<a href='/xwiki/bin/view/Help'>Help</a>" },
    });

    const link = wrapper.find(".useful-link a");
    expect(link.attributes("href")).toBe("/xwiki/bin/view/Help");
    expect(link.text()).toBe("Help");
  });
});
