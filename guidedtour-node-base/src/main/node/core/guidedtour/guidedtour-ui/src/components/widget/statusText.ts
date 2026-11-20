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
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";

/**
 * The status of a task or tour is only shown with styles (strike-through, faded text), so it is also given as text
 * to the assistive technologies.
 *
 * @param status - The status of the task or tour.
 * @param t - The translation function, with the `guidedtour.widget.status.*` keys loaded.
 * @returns The text describing the status, or nothing when there is nothing left to describe (left to do).
 */
function getStatusText(
  status: TourTaskStatus | undefined,
  t: (key: string) => string,
): string | undefined {
  switch (status) {
    case TourTaskStatus.DONE:
      return t("guidedtour.widget.status.done");
    case TourTaskStatus.SKIPPED:
      return t("guidedtour.widget.status.skipped");
    default:
      return undefined;
  }
}

export { getStatusText };
