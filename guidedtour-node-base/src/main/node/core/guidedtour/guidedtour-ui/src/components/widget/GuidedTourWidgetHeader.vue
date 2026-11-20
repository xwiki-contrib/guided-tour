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
  The header of the GuidedTourWidget panel, with the title, the close button and the progress bar.
-->

<template>
  <div class="header" v-if="!isLoading">
    <div class="top-bar">
      <div class="title-group">
        <span class="icon fa fa-compass" aria-hidden="true" />
        <h2 id="guidedtour-widget-title" class="title">
          {{ t("guidedtour.widget.header.title") }}
        </h2>
      </div>
      <button
        id="widget-close"
        type="button"
        class="btn btn-default close-button"
        :aria-label="t('guidedtour.widget.header.close')"
        @click="emit('closeGuidedTourWidget')"
      >
        <i class="fa-solid fa-x" aria-hidden="true" />
      </button>
    </div>
    <GuidedTourWidgetProgressBar
      :progress="computed(() => progress)"
      :label="t('guidedtour.widget.header.progress')"
    />
  </div>
</template>

<script setup lang="ts">
import GuidedTourWidgetProgressBar from "./GuidedTourWidgetProgressBar.vue";
import { RESOLVER } from "../../injectionKeys";
import { useI18nAdapter } from "@xwiki/platform-localization-adapter-vue";
import { computed, inject } from "vue";
import type { ComputedRef } from "vue";

const emit = defineEmits(["closeGuidedTourWidget"]);

const props = defineProps<{ progress: ComputedRef<number> }>();
// Reactive read-only ref for progress.
const progress = props.progress;

const resolver = inject(RESOLVER)!;
const { t, isLoading } = useI18nAdapter(resolver, {
  prefix: "guidedtour.widget.header.",
  keys: ["title", "close", "progress"],
});
</script>

<style scoped>
/* The title (with its icon) on the left, the close button on the right, both centered vertically. */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5em;
}

/* The icon and the title on the same line, the title wrapping next to the icon when it is too long. */
.title-group {
  display: flex;
  align-items: center;
  gap: 0.5em;
  min-width: 0;
}

.icon {
  flex-shrink: 0;
}

.close-button {
  flex-shrink: 0;
  font-size: inherit;
  padding: 0.3em 0.5em;
}

/* Reset the heading styles of the skin, the heading only gives the structure of the widget. */
.title {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
  font: inherit;
  font-weight: bold;
  color: inherit;
}

.guidedtour-widget .header {
  display: inline-block;
  user-select: none;
  padding: 14px 16px 14px 16px;
  width: 100%;
  overflow-wrap: break-word;
}
</style>
