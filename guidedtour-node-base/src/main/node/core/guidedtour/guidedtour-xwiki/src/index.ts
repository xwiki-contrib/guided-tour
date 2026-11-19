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

import { DefaultGuidedTourManager } from "./rest/DefaultGuidedTourManager";
import { TourStore } from "./rest/TourStore";
// @ts-expect-error this is a JavaScript file, it is expected to not have types.
import { loadById } from "./services/require.js";

/**
 * Resolve the driver translations from the XWiki localization webjar.
 *
 * The webjar is only available at runtime in the XWiki environment, so it is loaded dynamically.
 * @returns the resolved translations, keyed by the full translation key.
 */
async function getTranslations(): Promise<Record<string, string>> {
  const webjarModule = "xwiki-platform-localization-webjar";
  const { resolver } = await import(/* @vite-ignore */ webjarModule);
  const { translations } = await resolver.resolve({
    prefix: "guidedtour.driver.",
    keys: [
      "next",
      "previous",
      "skipAll",
      "loading",
      "error",
      "error.initExistingTask",
    ],
  });
  return translations;
}

/**
 * The main API of the GuidedTour app.
 * @since 1.0
 * @beta
 */
const guidedTourManager: Promise<DefaultGuidedTourManager> = loadById(
  "xwiki-meta",
  // @ts-expect-error this is a JavaScript file, it is expected to not have types.
).then(async (xwikiMeta) => {
  const sharedStore = new TourStore(xwikiMeta);
  const translations = await getTranslations();
  return new DefaultGuidedTourManager(xwikiMeta, sharedStore, translations);
});
export { type DefaultGuidedTourManager, guidedTourManager };
