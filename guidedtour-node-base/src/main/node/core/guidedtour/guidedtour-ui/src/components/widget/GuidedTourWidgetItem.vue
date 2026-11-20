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
  A row of the GuidedTourWidget: a button running the main action of the row (e.g. starting a task), followed by
  buttons for secondary actions (e.g. skipping it). Without a loading state, it shows a placeholder instead.
-->
<template>
  <template
    v-if="
      props.loading ||
      props.waiting === undefined ||
      props.loading === undefined
    "
  >
    <!-- The placeholders are only decorative, the loading state is announced by the container. -->
    <div class="guidedtour-widget-item loading" aria-hidden="true" />
  </template>
  <template v-else>
    <div
      class="guidedtour-widget-item"
      :id="props.id"
      :aria-busy="props.waiting!.value"
    >
      <!-- Headings can't be inside buttons, so the heading (if any) wraps the main button instead. -->
      <component
        :is="props.headingLevel ? `h${props.headingLevel}` : 'div'"
        class="guidedtour-widget-item-heading"
      >
        <button
          type="button"
          class="guidedtour-widget-item-main"
          :aria-expanded="props.expanded"
          :aria-controls="props.controls"
          @click="props.onClick"
        >
          <span class="pre-btns" aria-hidden="true">
            <slot name="pre-btns" />
          </span>
          <span class="guidedtour-widget-item-title"
            ><slot name="item-title"
          /></span>
          <slot name="loading-icon">
            <i
              style="--fa-animation-timing: ease-in-out"
              aria-hidden="true"
              :class="{
                transparent: !props.waiting!.value,
                ['fa-solid fa-circle-notch fa-spin']: true,
              }"
            />
          </slot>
        </button>
      </component>
      <span class="post-btns">
        <slot name="post-btns" />
      </span>
    </div>
  </template>
</template>

<script setup lang="ts">
import type { Ref } from "vue";

const props = defineProps<{
  loading?: boolean;
  waiting?: Ref<boolean>;
  id?: string;
  onClick?: () => void;
  /** Whether the content controlled by the main button is expanded, when it controls some content. */
  expanded?: boolean;
  /** The id of the content controlled by the main button, if any. */
  controls?: string;
  /** The level of the heading wrapping the main button, when the row is the heading of a section. */
  headingLevel?: number;
}>();
</script>

<style scoped>
.guidedtour-widget-item.loading {
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

/* No padding: the main button and the secondary actions fill the row, so that every part of it can be clicked. */
.guidedtour-widget-item {
  min-height: 2em;
  margin: 0.2em;
  display: flex;
  align-items: stretch;
  border-radius: 0.65em;
  transition: background-color 0.1s ease;
}

.guidedtour-widget-item:hover {
  background: var(--guidedtour-background-color-secondary) 100%;
}

/* Reset the heading styles of the skin, the heading only gives the structure of the widget. */
.guidedtour-widget-item-heading {
  display: flex;
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
  font: inherit;
  color: inherit;
}

/* The main button fills the row, so that the whole row (except the secondary actions) runs the main action. */
.guidedtour-widget-item-main {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 10px;
  margin: 0;
  padding: 0.5em;
  border: 0;
  border-radius: 0.65em;
  background: none;
  font: inherit;
  color: inherit;
  text-align: start;
  cursor: pointer;
}

/* The secondary actions come from the slots of the parent component, hence :deep(). */
/* Inside the main button, since it fills the row and the tour headers clip what overflows. */
.guidedtour-widget-item-main:focus-visible {
  outline: 2px solid var(--brand-primary);
  outline-offset: -2px;
}

.post-btns :deep(button:focus-visible) {
  outline: 2px solid var(--brand-primary);
  outline-offset: 2px;
  border-radius: 4px;
}

.fa-circle-notch.transparent {
  opacity: 0;
}
.fa-circle-notch {
  opacity: 1;
  transition: opacity 0.15s ease;
}

/* The secondary actions are shown on hover, and when the row has the keyboard focus. */
.guidedtour-widget-item:hover .pre-btns *:not(.always-show),
.guidedtour-widget-item:hover .post-btns *:not(.always-show),
.guidedtour-widget-item:focus-within .pre-btns *:not(.always-show),
.guidedtour-widget-item:focus-within .post-btns *:not(.always-show) {
  opacity: 1;
}

.pre-btns *:not(.always-show),
.post-btns *:not(.always-show) {
  text-decoration: none;
  color: var(--guidedtour-text-color);
  flex-shrink: 0;
  background: none;
  border: none;
  opacity: 0;
  transition: opacity 0.15s ease;
}

/* At least 24x24px, the minimum target size required by WCAG 2.5.8. */
.post-btns :deep(button) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  min-height: 24px;
  padding: 0;
  cursor: pointer;
  flex-shrink: 0;
  background: none;
  border: none;
}

.post-btns {
  display: flex;
  align-items: center;
  margin-left: auto;
  padding: 0 0.5em;
}
</style>
