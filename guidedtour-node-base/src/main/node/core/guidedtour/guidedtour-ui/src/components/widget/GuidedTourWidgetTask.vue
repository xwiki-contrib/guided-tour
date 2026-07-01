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
    v-if="task.value.active"
    :loading="false"
    :waiting="ref(isWaitingAsync)"
    v-bind:class="{
      ['task-' + task.value.status]: true,
      'guidedtour-task': true,
    }"
    :id="task.value.id"
    @click="onStartTask"
  >
    <template v-slot:pre-btns>
      <!-- This is just for show, it shouldn't do anything. -->
      <button>
        <i class="fa fa-arrow-right" />
      </button>
    </template>
    <template v-slot:item-title>
      {{ task.value.title }}
    </template>
    <template v-slot:post-btns>
      <button
        v-if="task.value.status == TourTaskStatus.TODO"
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
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { inject, reactive, ref, toRefs } from "vue";
import type {
  GuidedTourManager,
  TourTask,
} from "@xwiki/contrib-guidedtour-api";
import type { Ref } from "vue";

const { task, tourId } = defineProps<{
  task: Ref<TourTask>;
  tourId: string;
}>();

const state = reactive({
  isWaitingAsync: false,
});
const { isWaitingAsync } = toRefs(state);
const guidedTourManager: GuidedTourManager = inject(
  "DefaultGuidedTourManager",
)!;
async function onResetTask() {
  isWaitingAsync.value = true;
  await guidedTourManager.setTaskStatus(task.value, TourTaskStatus.TODO);
  isWaitingAsync.value = false;
}

async function onSkipTask() {
  isWaitingAsync.value = true;
  await guidedTourManager.setTaskStatus(task.value, TourTaskStatus.SKIPPED);
  isWaitingAsync.value = false;
}

async function onStartTask() {
  // Fetch the steps manually, so we can show the loader nicely while waiting for the steps to be fetched.
  isWaitingAsync.value = true;
  await guidedTourManager.getSteps(tourId, task.value.id).finally(() => {
    isWaitingAsync.value = false;
  });
  guidedTourManager.startTask(task.value, false);
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

.guidedtour-task.task-DONE {
  text-decoration: line-through;
  color: var(
    --guidedtour-text-color
  ); /* This is not WCAG-compliant, but idk how to do faded out text with good contrast. */
}

.guidedtour-task.task-SKIPPED {
  color: var(--guidedtour-text-color);
}
</style>
