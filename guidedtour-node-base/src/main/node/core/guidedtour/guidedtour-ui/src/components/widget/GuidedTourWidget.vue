<!--
  See the NOTICE file distributed with this work for additional
  information regarding copyright ownership.

  This is free software; you can redistribute it and/or modify it
  under the terms of the GNU Lesser General Public License as
  published by the Free Software Foundation; either version 2.1 of
  the License, or (at your option) any later version.

  This software is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public
  License along with this software; if not, write to the Free
  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  02110-1301 USA, or see the FSF site: http://www.fsf.org.
-->

<!--
  The GuidedTourWidget

  It contains a single default slot.
-->

<template>
  <div
    class="guidedtour-widget"
    :class="{
      collapsed: state.isWidgetCollapsed,
    }"
  >
    <!-- The launcher comes first so that it is focused before the panel when tabbing, but it is displayed below it. -->
    <GuidedTourWidgetLauncher
      :id="LAUNCHER_ID"
      :finished="finishedToursCount"
      :total="availableTours.length"
      :expanded="!state.isWidgetCollapsed"
      :panel-id="PANEL_ID"
      @toggle="toggleWidget"
    />
    <Transition name="guidedtour-panel" :css="animatePanel">
      <div
        v-show="!state.isWidgetCollapsed"
        :id="PANEL_ID"
        class="guidedtour-panel"
        role="region"
        aria-labelledby="guidedtour-widget-title"
        @keydown.esc="closeWidget"
      >
        <GuidedTourWidgetHeader
          @closeGuidedTourWidget="closeWidget"
          :progress="progress.val"
        />
        <div class="guidedtour-widget-content">
          <div
            class="guidedtour-container"
            :aria-busy="state.waitingLoadAsync < 2"
          >
            <!-- FIXME: There should be a better grouping style here, groups shouldn't be sections. -->
            <template
              v-if="
                /* Waiting for 2 async fetches to finish (in any order). FIXME: `<` used for development, but it's some weird behavior if the async loads more times than expected. */
                state.waitingLoadAsync < 2
              "
            >
              <GuidedTourWidgetItem />
              <GuidedTourWidgetItem />
              <GuidedTourWidgetItem />
            </template>
            <template v-else-if="state.tours.length > 0">
              <GuidedTourWidgetTour
                v-for="tour in state.tours"
                :key="tour.id"
                :tour="tour"
                @toggleCollapseTour="
                  (tour: TourTour) => {
                    tour.isCollapsed = !tour.isCollapsed;
                  }
                "
              />
            </template>
            <div v-else>
              <template v-if="state.tours.length == 0">
                <div>No tours to show</div>
              </template>
              <template v-else-if="state.toursLoadError.length > 0">{{
                state.toursLoadError
              }}</template>
              <template v-else>
                Something went terribly wrong. Check the console.</template
              >
            </div>
          </div>
          <div
            class="guidedtour-useful-links"
            v-bind:class="{ empty: state.usefulLinks.length == 0 }"
          >
            <template v-if="state.usefulLinks.length">
              <GuidedTourWidgetUsefulLink
                v-for="(link, index) in state.usefulLinks"
                :key="index"
                :link="link"
              />
            </template>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
//import type { I18n } from "vue-i18n";
import GuidedTourWidgetHeader from "./GuidedTourWidgetHeader.vue";
import GuidedTourWidgetItem from "./GuidedTourWidgetItem.vue";
import GuidedTourWidgetLauncher from "./GuidedTourWidgetLauncher.vue";
import GuidedTourWidgetTour from "./GuidedTourWidgetTour.vue";
import GuidedTourWidgetUsefulLink from "./GuidedTourWidgetUsefulLink.vue";
import { TourTaskStatus, WidgetState } from "@xwiki/contrib-guidedtour-api";
import { computed, nextTick, onMounted, provide, reactive, ref } from "vue";
import type {
  GuidedTourManager,
  TourTask,
  TourTour,
} from "@xwiki/contrib-guidedtour-api";
import type { Reactive } from "vue";

const { guidedTourManager } = defineProps<{
  guidedTourManager: GuidedTourManager;
}>();

provide<GuidedTourManager>("GuidedTourManager", guidedTourManager!);

const state = reactive({
  isWidgetCollapsed: true,
  tours: [] as Reactive<TourTour>[],
  usefulLinks: [] as string[],
  toursLoadError: "",
  waitingLoadAsync: 0,
});
const PANEL_ID = "guidedtour-widget-panel";
const LAUNCHER_ID = "guidedtour-widget-launcher";
// Whether opening and closing the panel is animated. Not when the saved state is restored on page load, so that an
// open panel doesn't fade in on each page, only when the user toggles it.
const animatePanel = ref(false);
// Whether the user toggled the widget before the saved widget state was loaded, in which case the saved state
// shouldn't override the user's choice. Only read once, when the saved state is loaded.
let toggledBeforeLoad = false;
/**
 * Collapse or expand the widget, and save its new state.
 * @param collapsed - Whether the widget should be collapsed.
 */
async function setWidgetCollapsed(collapsed: boolean) {
  animatePanel.value = true;
  state.isWidgetCollapsed = collapsed;
  toggledBeforeLoad = true;
  try {
    await guidedTourManager.setWidgetState(
      collapsed ? WidgetState.COLLAPSED : WidgetState.OPEN,
    );
  } catch (e) {
    console.error(e);
  }
}
/**
 * Collapse or expand the widget, when the launcher is clicked.
 */
async function toggleWidget() {
  await setWidgetCollapsed(!state.isWidgetCollapsed);
}
/**
 * Collapse the widget from inside its panel, and move the focus back to the launcher, since the focused element is
 * hidden with the panel.
 */
async function closeWidget() {
  document.getElementById(LAUNCHER_ID)?.focus();
  await setWidgetCollapsed(true);
}
/**
 * Restore the widget state saved by the user, unless they already toggled the widget.
 */
async function loadWidgetState() {
  try {
    const widgetState = await guidedTourManager.getWidgetState();
    if (!toggledBeforeLoad) {
      state.isWidgetCollapsed = widgetState === WidgetState.COLLAPSED;
    }
  } catch (e) {
    console.error(e);
  }
  // Animate the next changes only, once the restored state is rendered.
  await nextTick();
  animatePanel.value = true;
}
onMounted(() => {
  // TODO: Split these into two Async components, so they can load independently (The links can show up earlier than the tasks, etc...)
  state.waitingLoadAsync = 0;
  guidedTourManager
    .getTours()
    .then((tours) => {
      // The returned tour objects should be reactive, so the changes can be reflected in the UI.
      state.tours = tours;
      // initExistingTask() requires the cache to be already fetched by getTours().
      guidedTourManager.initExistingTask();
      state.waitingLoadAsync++;
      return tours;
    })
    .catch((e) => {
      console.error(e);
      state.waitingLoadAsync++;
      state.toursLoadError = e;
      state.waitingLoadAsync++;
    });
  guidedTourManager
    .getUsefulLinks()
    .then((usefulLinks) => {
      state.usefulLinks = usefulLinks;
      state.waitingLoadAsync++;
      return usefulLinks;
    })
    .catch((e) => {
      console.error(e);
      state.waitingLoadAsync++;
    });
  loadWidgetState();
});
// The tours shown to the user: the active ones with at least one active task.
const availableTours = computed(() =>
  state.tours.filter(
    (tour) => tour.active && tour.tasksList?.some((task) => task.active),
  ),
);
// The available tours with no task left to do, i.e. whose tasks are all done or skipped.
const finishedToursCount = computed(
  () =>
    availableTours.value.filter(
      (tour) =>
        tour.status !== undefined && tour.status !== TourTaskStatus.TODO,
    ).length,
);
// FIXME: The .val property is a workaround, so vue doesn't auto-unwrap the progress, thus making it non-reactive.
const progress = {
  val: computed(() => {
    const allTasks: TourTask[] = state.tours.flatMap((t) => t.tasksList!);
    return (
      allTasks.filter(
        (task: TourTask) =>
          task.active &&
          task.status != undefined &&
          task.status != TourTaskStatus.TODO,
      ).length / allTasks.filter((task) => task.active).length
    );
  }),
};
</script>

<style>
/* Imported for use in all child vue components. */
@import "./styles/variables.css";
/* Imported for use in the modal generated by driverjs. */
@import "./styles/driver.css";
</style>

<style scoped>
.guidedtour-useful-links {
  max-height: 300px;
  .empty {
    max-height: 0px;
  }
}
.guidedtour-widget.dragging * {
  pointer-events: none;
}

/* Same layout as the driver.js checklist: the panel opens above the launcher, in the bottom right corner. */
.guidedtour-widget {
  z-index: 999;
  position: fixed;
  bottom: 20px;
  right: 20px;
  max-width: calc(100vw - 40px);
  display: flex;
  /* The launcher comes first in the DOM, for the tabbing order, but it is displayed below the panel. */
  flex-direction: column-reverse;
  align-items: flex-end;
  gap: 10px;
  user-select: none;

  .guidedtour-panel {
    max-width: 100%;
    overflow: hidden;
    background-color: var(--xwiki-page-content-bg);
    border: 1px solid var(--xwiki-border-color);
    border-radius: 10px;
    box-shadow: 0px 0px 12px 0px #00000033;
  }

  .guidedtour-widget-content {
    overflow: hidden;
    max-height: 500px;
    max-width: 500px;
  }

  .guidedtour-container {
    overflow-x: scroll;
    padding: 0px 16px 14px 16px;
    max-height: 300px;
  }
}

/*
 * Fade the panel in and out, like the driver.js checklist panel. Only the opacity and transform are animated, since
 * animating the size of the panel requires a new layout of the page at each frame, which is choppy.
 */
.guidedtour-panel-enter-active {
  transition:
    opacity 0.2s ease-out,
    transform 0.2s ease-out;
}

.guidedtour-panel-leave-active {
  transition:
    opacity 0.15s ease-in,
    transform 0.15s ease-in;
}

.guidedtour-panel-enter-from,
.guidedtour-panel-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@media (prefers-reduced-motion: reduce) {
  .guidedtour-panel-enter-active,
  .guidedtour-panel-leave-active {
    transition: none;
  }
}
</style>
