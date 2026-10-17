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
  <GuidedTourWidgetItem
    :loading="false"
    :waiting="ref(isWaitingAsync)"
    v-bind:class="{
      ['task-' + task.status]: true,
      'task-dependent': hasRemainingDependentTasks(),
      'guidedtour-task': true,
    }"
    :id="task.id"
    :hint="getDependencyHint()"
    @click="onStartTask"
  >
    <template v-slot:pre-btns>
      <!-- Icon to denote that a task has uncompleted dependencies. -->
      <i
        v-if="hasRemainingDependentTasks()"
        ref="prebtns"
        class="always-show fa-solid fa-table-cells-row-lock"
      />
      <i v-else class="fa fa-arrow-right" />
    </template>
    <template v-slot:item-title>
      {{ task.title }}
    </template>
    <template v-slot:post-btns>
      <button
        v-if="task.status == TourTaskStatus.TODO"
        class="post-btn"
        @click.stop="onSkipTask"
      >
        <i class="fa-solid fa-x" />
      </button>
      <button v-else class="post-btn" @click.stop="onResetTask">
        <i class="fa fa-rotate-right" />
      </button>
    </template>
  </GuidedTourWidgetItem>
</template>

<script setup lang="ts">
import GuidedTourWidgetItem from "./GuidedTourWidgetItem.vue";
// @ts-expect-error this is a JavaScript file, it is expected to not have types.
import { XWiki } from "../../services/xwiki.js";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { inject, reactive, ref, toRefs, useTemplateRef } from "vue";
import type {
  GuidedTourManager,
  TourTask,
} from "@xwiki/contrib-guidedtour-api";
import type { Reactive } from "vue";

const { task, tourId } = defineProps<{
  task: Reactive<TourTask>;
  tourId: string;
}>();

const prebtns = useTemplateRef("prebtns");
const state = reactive({
  isWaitingAsync: false,
});
const { isWaitingAsync } = toRefs(state);
const guidedTourManager: GuidedTourManager = inject("GuidedTourManager")!;
async function onResetTask() {
  isWaitingAsync.value = true;
  await guidedTourManager.setTaskStatus(task, TourTaskStatus.TODO);
  isWaitingAsync.value = false;
}

const dependentTasks: TourTask[] = task.dependsOn
  ? (
      await Promise.all(
        task.dependsOn!.map(async (taskId: string) => {
          return await guidedTourManager.getTask(task.tourId!, taskId);
        }),
      )
    )
      .filter((dep) => dep !== undefined)
      .filter((dep) => dep.active && dep.id != task.id)
  : [];

function getRemainingDependentTasks(): TourTask[] {
  return dependentTasks.filter(
    (dep: TourTask) => dep.status !== TourTaskStatus.DONE,
  );
}

function hasRemainingDependentTasks(): boolean {
  return getRemainingDependentTasks().length > 0;
}

/**
 * Get the tooltip/subtitle of this task, to tell the user which tasks were not completed.
 */
function getDependencyHint() {
  if (dependentTasks.length == 0) {
    // This task has no dependencies, so show no hint.
    return "";
  }
  let uncompletedDeps: TourTask[] = getRemainingDependentTasks();
  if (uncompletedDeps.length == 0) {
    // TODO: Add translation strings.
    return "All dependencies completed";
  } else {
    // TODO: Add translation strings.
    return (
      "Depends on: " +
      uncompletedDeps.map((task: TourTask) => task.title).join(", ")
    );
  }
}

function playShakeAnimation(element: HTMLElement | null) {
  if (element) {
    element.classList.remove("shake-anim");
    void element.offsetWidth; // force reflow
    element.classList.add("shake-anim");
  }
}

async function onSkipTask() {
  isWaitingAsync.value = true;
  await guidedTourManager.setTaskStatus(task, TourTaskStatus.SKIPPED);
  isWaitingAsync.value = false;
}

async function onStartTask() {
  if (getRemainingDependentTasks().length > 0) {
    // Short circuit if trying to start a task with uncompleted dependencies.
    // The UI code above will handle displaying the warning.
    playShakeAnimation(prebtns.value);
    new XWiki.widgets.Notification(getDependencyHint(), "error");
    return;
  }
  // Fetch the steps manually, so we can show the loader nicely while waiting for the steps to be fetched.
  isWaitingAsync.value = true;
  await guidedTourManager.getSteps(tourId, task.id).finally(() => {
    isWaitingAsync.value = false;
  });
  guidedTourManager.startTask(task, false);
}
</script>

<style scoped>
.guidedtour-task.loading-content {
  background: linear-gradient(
    to left,
    var(--guidedtour-text-color) 0%,
    var(--guidedtour-text-color) 25%,
    var(--guidedtour-background-color-secondary) 30%,
    var(--guidedtour-background-color-secondary) 35%,
    var(--guidedtour-text-color) 40%,
    var(--guidedtour-text-color) 75%,
    var(--guidedtour-background-color-secondary) 80%,
    var(--guidedtour-background-color-secondary) 85%,
    var(--guidedtour-text-color) 90%
  );
  background-size: 200% 100%;
  animation: loading-shimmer 4s linear infinite;
}

@keyframes loading-shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}
.guidedtour-task:hover {
  background: var(--guidedtour-background-color-secondary) 100%;
}

@keyframes shake {
  10%,
  90% {
    transform: translateX(calc(var(--shake-magnitude) * -0.5));
  }
  30%,
  50%,
  70% {
    transform: translateX(calc(var(--shake-magnitude) * -1));
  }
  20%,
  40%,
  60%,
  80% {
    transform: translateX(var(--shake-magnitude));
  }
}
.shake-anim {
  animation: shake 0.65s;
}

.guidedtour-task.task-DONE :deep(.guidedtour-widget-item-title) {
  text-decoration: line-through;
}
.guidedtour-task.task-DONE {
  color: var(
    --guidedtour-text-color
  ); /* This is not WCAG-compliant, but idk how to do faded out text with good contrast. */
}
.guidedtour-task.task-dependent {
  background: var(--guidedtour-background-color-secondary) 100%;
}
.guidedtour-task.task-SKIPPED {
  color: var(--guidedtour-text-color);
}

/* TODO tasks with uncompleted dependencies cannot be skipped. (But you can reset a task with uncompleted dependencies) */
.task-dependent.task-TODO .post-btn {
  visibility: hidden;
}
</style>
