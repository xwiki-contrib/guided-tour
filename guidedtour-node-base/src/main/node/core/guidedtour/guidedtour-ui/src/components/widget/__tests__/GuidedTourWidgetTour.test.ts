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

import { RESOLVER_PROVIDE } from "./i18nMock";
import GuidedTourWidgetTask from "../GuidedTourWidgetTask.vue";
import GuidedTourWidgetTour from "../GuidedTourWidgetTour.vue";
import { flushPromises, mount } from "@vue/test-utils";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { describe, expect, it, vi } from "vitest";
import type {
  GuidedTourManager,
  TourTask,
  TourTour,
} from "@xwiki/contrib-guidedtour-api";

vi.mock("@xwiki/platform-localization-adapter-vue", () => import("./i18nMock"));

function createGuidedTourManager(tasks: TourTask[] = []): GuidedTourManager {
  return {
    getTours: vi.fn().mockResolvedValue([]),
    getTasks: vi.fn().mockResolvedValue(tasks),
    skipTour: vi.fn().mockResolvedValue(undefined),
    resetTour: vi.fn().mockResolvedValue(undefined),
  } as unknown as GuidedTourManager;
}

function createTask(id: string, active = true, stepCount = 1): TourTask {
  return {
    id,
    title: id,
    order: 0,
    status: TourTaskStatus.TODO,
    active,
    stepCount,
  };
}

async function mountTour(
  status: TourTaskStatus,
  guidedTourManager: GuidedTourManager,
  tourProperties: Partial<TourTour> = {},
) {
  const tour: TourTour = {
    id: "tour1",
    title: "Tour 1",
    status,
    ...tourProperties,
  };
  const wrapper = mount(GuidedTourWidgetTour, {
    props: { tour },
    global: {
      provide: { GuidedTourManager: guidedTourManager, ...RESOLVER_PROVIDE },
      stubs: {
        GuidedTourWidgetTask: true,
        // Render the default slot directly, which avoids the Vue warnings about
        // Suspense being experimental and expecting a single root node.
        Suspense: { template: "<div><slot /></div>" },
      },
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

  it("asks to collapse or expand the tour when its header is clicked", async () => {
    const wrapper = await mountTour(
      TourTaskStatus.TODO,
      createGuidedTourManager(),
    );

    await wrapper
      .find(".guidedtour-tour-header .guidedtour-widget-item-main")
      .trigger("click");

    expect(wrapper.emitted("toggleCollapseTour")).toEqual([
      [wrapper.props("tour")],
    ]);
  });

  it.each([
    [true, true],
    [false, false],
    [undefined, false],
  ])(
    "collapses the tour when isCollapsed is %s: %s",
    async (isCollapsed, collapsed) => {
      const wrapper = await mountTour(
        TourTaskStatus.TODO,
        createGuidedTourManager(),
        { isCollapsed },
      );

      expect(wrapper.find(".guidedtour-tour").classes("collapsed")).toBe(
        collapsed,
      );
      // The header tells whether the tasks are shown, and the hidden tasks can't be focused.
      const toggle = wrapper.find(
        ".guidedtour-tour-header .guidedtour-widget-item-main",
      );
      expect(toggle.attributes("aria-expanded")).toBe(String(!collapsed));
      const content = wrapper.find(".guidedtour-content");
      expect(toggle.attributes("aria-controls")).toBe(content.attributes("id"));
      expect(content.attributes("inert")).toBe(collapsed ? "" : undefined);
    },
  );

  it("shows the active tasks of the tour that have steps", async () => {
    const guidedTourManager = createGuidedTourManager([
      createTask("task1"),
      createTask("task2", false),
      createTask("task3"),
      createTask("task4", true, 0),
    ]);
    const wrapper = await mountTour(TourTaskStatus.TODO, guidedTourManager);

    expect(
      wrapper
        .findAllComponents(GuidedTourWidgetTask)
        .map((task) => task.props("task").id),
    ).toEqual(["task1", "task3"]);
  });

  it.each([
    ["shows an active tour with tasks", true, [createTask("task1")], false],
    ["hides an inactive tour", false, [createTask("task1")], true],
    ["hides a tour without tasks", true, [], true],
    [
      "hides a tour without active tasks",
      true,
      [createTask("task1", false)],
      true,
    ],
    [
      "hides a tour whose tasks have no steps",
      true,
      [createTask("task1", true, 0)],
      true,
    ],
  ])("%s", async (_description, active, tasksList, hidden) => {
    // The cached tasks of the tour are the ones fetched by the component.
    const wrapper = await mountTour(
      TourTaskStatus.TODO,
      createGuidedTourManager(tasksList),
      { active, tasksList },
    );

    expect(wrapper.find(".guidedtour-tour").classes("hidden")).toBe(hidden);
  });

  it("is a section with the title of the tour as heading", async () => {
    const wrapper = await mountTour(
      TourTaskStatus.TODO,
      createGuidedTourManager(),
    );

    const heading = wrapper.find(".guidedtour-tour-header h3");
    expect(heading.find(".guidedtour-widget-item-main").text()).toBe("Tour 1");
  });

  it.each([
    [TourTaskStatus.TODO, "guidedtour.widget.tour.skip [Tour 1]", "Tour 1"],
    [
      TourTaskStatus.DONE,
      "guidedtour.widget.tour.reset [Tour 1]",
      "Tour 1 guidedtour.widget.status.done",
    ],
    [
      TourTaskStatus.SKIPPED,
      "guidedtour.widget.tour.reset [Tour 1]",
      "Tour 1 guidedtour.widget.status.skipped",
    ],
  ])(
    "names the button acting on a tour that is %s, and tells its status",
    async (status, label, text) => {
      const wrapper = await mountTour(status, createGuidedTourManager());

      const header = wrapper.find(".guidedtour-tour-header");
      expect(header.find(".post-btn").attributes("aria-label")).toBe(label);
      expect(header.find(".guidedtour-widget-item-main").text()).toBe(text);
    },
  );

  it("links its header to its tasks even when its id contains spaces", async () => {
    const wrapper = await mountTour(
      TourTaskStatus.TODO,
      createGuidedTourManager(),
      { id: "xwiki:GuidedTour.Tours.Panel Tour" },
    );

    // aria-controls takes a list of ids separated by spaces, so the id of the tasks must not contain any.
    const controls = wrapper
      .find(".guidedtour-tour-header .guidedtour-widget-item-main")
      .attributes("aria-controls");
    expect(controls).not.toContain(" ");
    expect(wrapper.find(".guidedtour-content").attributes("id")).toBe(controls);
  });
});
