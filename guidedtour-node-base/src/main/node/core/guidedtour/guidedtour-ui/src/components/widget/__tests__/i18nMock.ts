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
import { RESOLVER } from "../../../injectionKeys";
import { vi } from "vitest";
import { ref } from "vue";
import type { Resolver } from "@xwiki/platform-localization-api";

/**
 * Mock of `@xwiki/platform-localization-adapter-vue`, to use with:
 * `vi.mock("@xwiki/platform-localization-adapter-vue", () => import("./i18nMock"));`
 *
 * Like in xwiki-platform, the tests check which translation is used and with which parameters, not its text, which
 * belongs to ApplicationResources.properties. So a translation is rendered as its key, followed by its parameters.
 */
function t(key: string, parameters?: unknown[]): string {
  return parameters?.length ? `${key} [${parameters.join(", ")}]` : key;
}

/**
 * The translations are loaded at once. Use `mockReturnValueOnce` to simulate translations that are still loading.
 */
const useI18nAdapter = vi.fn(() => ({ t, isLoading: ref(false) }));

/**
 * The resolver isn't used by the mock, it is only provided so that the components can inject it.
 */
const RESOLVER_PROVIDE = { [RESOLVER]: {} as Resolver };

export { RESOLVER_PROVIDE, t, useI18nAdapter };
