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
import type { TourTaskStatus } from "./tourTaskStatus";

/**
 * All possible states of the Guided Tour widget.
 *
 * @since 1.0
 * @beta
 */
enum WidgetState {
  HIDDEN = "HIDDEN",
  OPEN = "OPEN",
  COLLAPSED = "COLLAPSED",
}

/**
 * The progress of a user in one tour.
 *
 * @since 1.0
 * @beta
 */
interface TourProgress {
  /**
   * Whether the next task of the tour should be started automatically when a task is finished. Disabled when the
   * user closes or skips a task of the tour, enabled again when the tour is reset. Enabled when missing.
   */
  callToAction: boolean;
  /**
   * The status of each task of the tour, keyed by task id.
   */
  tasksStatus: Record<string, TourTaskStatus>;
}

/**
 * The progress and preferences of a user regarding the guided tours.
 *
 * @since 1.0
 * @beta
 */
interface UserTourStatus {
  /**
   * The state of the widget: OPEN when it is expanded, COLLAPSED when it is minimized and HIDDEN when it is not
   * visible.
   */
  widgetState: WidgetState;
  /**
   * The progress of the user in each tour, keyed by tour id. A tour without progress is missing.
   */
  toursStatus: Record<string, TourProgress>;
}

export { WidgetState };
export type { TourProgress, UserTourStatus };
