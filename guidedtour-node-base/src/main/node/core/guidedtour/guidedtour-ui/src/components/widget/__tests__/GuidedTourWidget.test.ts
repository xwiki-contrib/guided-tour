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
import GuidedTourWidgetItem from "../GuidedTourWidgetItem.vue";
import GuidedTourWidgetTour from "../GuidedTourWidgetTour.vue";
import GuidedTourWidgetUsefulLink from "../GuidedTourWidgetUsefulLink.vue";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { TourTaskStatus, WidgetState } from "@xwiki/contrib-guidedtour-api";
import { describe, expect, it, vi } from "vitest";
import type {
  GuidedTourManager,
  TourTask,
  TourTour,
} from "@xwiki/contrib-guidedtour-api";

function createGuidedTourManager(
  widgetState: Promise<WidgetState>,
  tours: TourTour[] = [],
  usefulLinks: string[] = [],
): GuidedTourManager {
  return {
    getTours: vi.fn().mockResolvedValue(tours),
    getUsefulLinks: vi.fn().mockResolvedValue(usefulLinks),
    initExistingTask: vi.fn(),
    getWidgetState: vi.fn().mockReturnValue(widgetState),
    setWidgetState: vi.fn().mockResolvedValue(undefined),
  } as unknown as GuidedTourManager;
}

function mountWidget(guidedTourManager: GuidedTourManager) {
  return shallowMount(GuidedTourWidget, { props: { guidedTourManager } });
}

/**
 * A promise resolved by the test, to control when the data is loaded.
 */
function createDeferred<T>() {
  let resolveDeferred: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => (resolveDeferred = resolve));
  return { promise, resolve: resolveDeferred };
}

function createTask(
  id: string,
  status: TourTaskStatus,
  active = true,
): TourTask {
  return { id, title: id, order: 0, status, active };
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
    const widgetState = createDeferred<WidgetState>();
    const wrapper = mountWidget(createGuidedTourManager(widgetState.promise));

    // The widget starts collapsed, the user expands it.
    await toggleWidget(wrapper);
    widgetState.resolve(WidgetState.COLLAPSED);
    await flushPromises();

    expect(wrapper.classes("collapsed")).toBe(false);
  });

  it("shows placeholders until both the tours and useful links are loaded", async () => {
    const usefulLinks = createDeferred<string[]>();
    const wrapper = mountWidget({
      ...createGuidedTourManager(Promise.resolve(WidgetState.OPEN), [
        { id: "tour1", title: "Tour 1", tasksList: [] },
      ]),
      getUsefulLinks: () => usefulLinks.promise,
    } as GuidedTourManager);

    // The tours are loaded, but not the useful links.
    await flushPromises();
    expect(wrapper.findAllComponents(GuidedTourWidgetItem)).toHaveLength(3);
    expect(wrapper.findComponent(GuidedTourWidgetTour).exists()).toBe(false);

    usefulLinks.resolve([]);
    await flushPromises();
    expect(wrapper.findAllComponents(GuidedTourWidgetItem)).toHaveLength(0);
    expect(wrapper.findComponent(GuidedTourWidgetTour).exists()).toBe(true);
  });

  it("resumes the task in progress once the tours are loaded", async () => {
    const tours = createDeferred<TourTour[]>();
    const guidedTourManager = {
      ...createGuidedTourManager(Promise.resolve(WidgetState.OPEN)),
      getTours: () => tours.promise,
    } as GuidedTourManager;
    mountWidget(guidedTourManager);

    await flushPromises();
    expect(guidedTourManager.initExistingTask).not.toHaveBeenCalled();

    tours.resolve([]);
    await flushPromises();
    expect(guidedTourManager.initExistingTask).toHaveBeenCalledOnce();
  });

  it("shows the tours and useful links once they are loaded", async () => {
    const tours: TourTour[] = [
      {
        id: "tour1",
        title: "Tour 1",
        tasksList: [createTask("task1", TourTaskStatus.TODO)],
      },
      {
        id: "tour2",
        title: "Tour 2",
        tasksList: [createTask("task2", TourTaskStatus.TODO)],
      },
    ];
    const guidedTourManager = createGuidedTourManager(
      Promise.resolve(WidgetState.OPEN),
      tours,
      ["<a href='#'>Link</a>"],
    );
    const wrapper = mountWidget(guidedTourManager);
    await flushPromises();

    expect(
      wrapper
        .findAllComponents(GuidedTourWidgetTour)
        .map((tour) => tour.props("tour").id),
    ).toEqual(["tour1", "tour2"]);
    expect(
      wrapper.findComponent(GuidedTourWidgetUsefulLink).props("link"),
    ).toBe("<a href='#'>Link</a>");
    expect(wrapper.find(".guidedtour-useful-links").classes("empty")).toBe(
      false,
    );
  });

  it("tells when there are no tours to show", async () => {
    const wrapper = mountWidget(
      createGuidedTourManager(Promise.resolve(WidgetState.OPEN)),
    );
    await flushPromises();

    expect(wrapper.find(".guidedtour-container").text()).toBe(
      "No tours to show",
    );
    expect(wrapper.find(".guidedtour-useful-links").classes("empty")).toBe(
      true,
    );
  });

  it("collapses and expands a tour", async () => {
    const tour: TourTour = {
      id: "tour1",
      title: "Tour 1",
      tasksList: [createTask("task1", TourTaskStatus.TODO)],
    };
    const wrapper = mountWidget(
      createGuidedTourManager(Promise.resolve(WidgetState.OPEN), [tour]),
    );
    await flushPromises();

    const tourComponent = wrapper.findComponent(GuidedTourWidgetTour);
    const toggleTour = () =>
      tourComponent.vm.$emit("toggleCollapseTour", tourComponent.props("tour"));

    toggleTour();
    expect(tourComponent.props("tour").isCollapsed).toBe(true);
    toggleTour();
    expect(tourComponent.props("tour").isCollapsed).toBe(false);
  });

  it("computes the progress from the active tasks that are not left to do", async () => {
    const tours: TourTour[] = [
      {
        id: "tour1",
        title: "Tour 1",
        tasksList: [
          createTask("task1", TourTaskStatus.DONE),
          createTask("task2", TourTaskStatus.TODO),
        ],
      },
      {
        id: "tour2",
        title: "Tour 2",
        tasksList: [
          createTask("task3", TourTaskStatus.SKIPPED),
          createTask("task4", TourTaskStatus.TODO),
          // Inactive tasks are not shown, so they don't count.
          createTask("task5", TourTaskStatus.DONE, false),
        ],
      },
    ];
    const wrapper = mountWidget(
      createGuidedTourManager(Promise.resolve(WidgetState.OPEN), tours),
    );
    await flushPromises();

    expect(
      wrapper.findComponent(GuidedTourWidgetHeader).props("progress").value,
    ).toBe(0.5);
  });
});
