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
  The button that opens and closes the panel of the GuidedTourWidget. It is always shown, and displays how many of
  the available tours are finished.
-->

<template>
  <button
    v-if="!isLoading"
    :id="id"
    type="button"
    class="guidedtour-widget-launcher"
    :aria-expanded="expanded"
    :aria-controls="panelId"
    @click="emit('toggle')"
  >
    <span class="icon fa fa-compass" aria-hidden="true" />
    <span class="guidedtour-widget-launcher-text">{{
      t("guidedtour.widget.launcher.title")
    }}</span>
    <span v-if="total > 0" class="guidedtour-widget-launcher-count">
      <span aria-hidden="true">{{ finished }}/{{ total }}</span>
      <span class="sr-only">{{
        t("guidedtour.widget.launcher.progress", [finished, total])
      }}</span>
    </span>
  </button>
</template>

<script setup lang="ts">
import { RESOLVER } from "../../injectionKeys";
import { useI18nAdapter } from "@xwiki/platform-localization-adapter-vue";
import { inject } from "vue";

const { id, finished, total, expanded, panelId } = defineProps<{
  /** The id of the launcher button, so that the widget can move the focus to it. */
  id: string;
  /** The number of available tours that have no task left to do. */
  finished: number;
  /** The number of available tours. */
  total: number;
  /** Whether the panel controlled by the launcher is open. */
  expanded: boolean;
  /** The id of the panel controlled by the launcher. */
  panelId: string;
}>();

const emit = defineEmits(["toggle"]);

const resolver = inject(RESOLVER)!;
const { t, isLoading } = useI18nAdapter(resolver, {
  prefix: "guidedtour.widget.launcher.",
  keys: ["title", "progress"],
});
</script>

<style scoped>
/* Same look as the driver.js checklist launcher, with the colors of the XWiki color theme. */
.guidedtour-widget-launcher {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  box-sizing: border-box;
  padding: 9px 14px;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.3;
  color: var(--text-color);
  background-color: var(--xwiki-page-content-bg);
  border: 1px solid var(--xwiki-border-color);
  border-radius: 8px;
  box-shadow: 0 2px 8px #0000001f;
  cursor: pointer;
  transition: background-color 0.1s ease;
}

.guidedtour-widget-launcher:hover {
  background-color: var(--xwiki-background-secondary-color);
}

.guidedtour-widget-launcher:focus-visible {
  outline: 2px solid var(--brand-primary);
  outline-offset: 2px;
}

.guidedtour-widget-launcher-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guidedtour-widget-launcher-count {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-muted);
}
</style>
