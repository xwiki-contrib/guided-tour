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
import { createI18n } from "vue-i18n";
import type {
  Query,
  Resolver,
  Translations,
} from "@xwiki/platform-localization-api";
import type { I18n } from "vue-i18n";

function buildRequest(
  translationsURL: string,
  locale: string,
  prefix: string,
  keys: string[],
) {
  const usp = new URLSearchParams({
    locale: locale,
    prefix: prefix,
  });
  for (const key of keys) {
    usp.append("key", key);
  }
  return `${translationsURL}?${usp.toString()}`;
}

async function getTranslations(
  locale: string,
  prefix: string,
  keys: string[],
): Promise<Translations> {
  const translationsURL = `${XWiki.contextPath}/rest/wikis/${encodeURIComponent(
    XWiki.currentWiki,
  )}/localization/translations`;
  const input = buildRequest(translationsURL, locale, prefix, keys);

  const res = await fetch(input, {
    headers: {
      Accept: "application/json",
    },
  });

  const translations = (await res.json()).translations;
  const resMap: { [key: string]: string } = {};
  for (const value of translations) {
    resMap[value.key] = value.rawSource;
  }
  return resMap;
}

/**
 * Build the translation resolver.
 * @param locale - the current locale
 * @param i18n - the i18n instance to populate
 * @since 0.2
 * @beta
 */
function buildTranslations(locale: string, i18n: I18n): Resolver {
  return {
    async resolve(query: Query) {
      const { prefix = "", keys } = query as {
        prefix?: string;
        keys: string[];
      };
      const translations = await getTranslations(locale, prefix, keys);
      i18n.global.setLocaleMessage(locale, translations);
      const requestedKeys = keys.map((k) => `${prefix}${k}`);
      const resolvedKeys = Object.keys(translations);
      const missed = requestedKeys.filter((k) => !resolvedKeys.includes(k));
      return { translations, missed };
    },
  };
}

/**
 * Initialize the translation system: creates a vue-i18n instance and builds the resolver.
 * @returns the resolver and the i18n instance to install on the Vue app
 * @since 0.2
 * @beta
 */
function initTranslations(): { resolver: Resolver; i18n: I18n } {
  const locale = navigator.language;
  const i18n = createI18n({
    locale,
    fallbackLocale: "en",
    messages: {},
  });
  const resolver = buildTranslations(locale, i18n as I18n);
  return { resolver, i18n: i18n as I18n };
}

export { initTranslations };
