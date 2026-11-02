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
import { StorageManager } from "./StorageManager";
import { TourTaskStatus } from "@xwiki/contrib-guidedtour-api";
import { driver } from "driver.js";
import type { DefaultGuidedTourManager } from "./rest/DefaultGuidedTourManager";
import type { TourStep, TourTask } from "@xwiki/contrib-guidedtour-api";
import type { Config, DriveStep, Driver, PopoverDOM } from "driver.js";

type StepDirection = "next" | "previous";

/**
 * How long (in ms) driver.js waits for a step's element to become visible before giving up on the task.
 */
const WAIT_FOR_ELEMENT_TIMEOUT = 3000;

/**
 * How long (in ms) a reflex step on a text input waits after the click, to let the user type, before moving on.
 */
const TEXT_INPUT_REFLEX_DELAY = 5000;

const util = {
  /**
   * Useful for locking task progression while redirecting to another page (like after clicking on an URL as part of a
   * step). This flag is true when the page is being unloaded before a redirect (after a `beforeunload` event).
   */
  pageUnloadingFlag: false,
  /**
   * Shown while driver.js waits for the element of the step being driven to.
   */
  loadingNotification: undefined as { hide(): void } | undefined,
  /**
   * Add listener so `pageUnloadingFlag` is set to true on `beforeunload` event trigger.
   */
  addPageUnloadingListener() {
    window.addEventListener("beforeunload", () => {
      util.pageUnloadingFlag = true;
    });
  },
  /**
   * Do the necessary setup for rendering the `Skip All` link.
   * @param guidedTourManager - API
   * @param guidedTourTask - the task to make the button for
   * @param translations - the translations used for the label of the button
   * @returns The `Skip All` element
   */
  makeSkipAllButton(
    guidedTourManager: DefaultGuidedTourManager,
    guidedTourTask: TourTask,
    translations: Record<string, string>,
  ): Element {
    const customSkipAll = document.createElement("a");
    customSkipAll.classList.add("driver-xwiki-skip-all-button");

    /**
     * Skip the task when the `Skip All` link is clicked.
     */
    async function onSkipAll() {
      // Skipping the task also stops starting the next tasks of its tour automatically.
      await guidedTourManager.skipTask(guidedTourTask);
    }

    customSkipAll.onclick = onSkipAll;
    customSkipAll.textContent = translations["guidedtour.driver.skipAll"];
    return customSkipAll;
  },
  /**
   * @param selector - css selector for the element to find (should be compatible with document.querySelector)
   * @returns the element, if it exists and is visible on the page
   */
  findVisibleElement(selector: string): Element | undefined {
    const queriedElement = document.querySelector(selector);
    if (
      queriedElement &&
      globalThis.getComputedStyle(queriedElement).display != "none"
    ) {
      return queriedElement;
    } else {
      return undefined;
    }
  },
  hideLoadingNotification() {
    util.loadingNotification?.hide();
    util.loadingNotification = undefined;
  },
  /**
   * Figure out if the given step should be considered a reflex step.
   *
   * @param step - the TourStep to check
   * @returns true if the step should be treated as a reflex step (advancing on click), false otherwise
   */
  isReflex(step: TourStep): boolean {
    return step.reflex && !!step.element;
  },
  /**
   * Drive to the given step, showing a loading notification until the step is highlighted.
   */
  driveToStep(
    driverTask: Driver,
    stepIndex: number,
    translations: Record<string, string>,
  ) {
    util.hideLoadingNotification();
    util.loadingNotification = new XWiki.widgets.Notification(
      translations["guidedtour.driver.loading"],
      "inprogress",
    );
    driverTask.drive(stepIndex);
  },
  /**
   * Decides which buttons should be visible in the modal, and updates the DOM.
   * @param popDOM - The DOM of the driverjs modal
   * @param step - The current step
   */
  solveButtons(
    popDOM: PopoverDOM,
    step: TourStep,
    guidedTourManager: DefaultGuidedTourManager,
    guidedTourTask: TourTask,
    translations: Record<string, string>,
  ) {
    if (util.isReflex(step)) {
      popDOM.footerButtons.removeChild(popDOM.nextButton);
      popDOM.footerButtons.removeChild(popDOM.previousButton);
    } else {
      // Drop the driver.js button styles, so that the XWiki ones apply.
      popDOM.nextButton.classList.remove("driver-popover-footer-btn");
      popDOM.previousButton.classList.remove("driver-popover-footer-btn");
      popDOM.nextButton.classList.add("btn", "btn-sm", "btn-primary"); // TODO: Make this an <a> instead of
      // <button>
      popDOM.previousButton.classList.add("btn", "btn-sm"); // TODO: Make this an <a> instead of <button>
    }
    popDOM.footer.appendChild(
      util.makeSkipAllButton(guidedTourManager, guidedTourTask, translations),
    );
  },
  getAdjacentStep(
    guidedTourTask: TourTask,
    currentStepActiveIndex: number,
    direction: StepDirection,
  ): TourStep | undefined {
    const stepOffset = direction == "next" ? 1 : -1;
    return guidedTourTask.steps?.[currentStepActiveIndex + stepOffset];
  },
  // eslint-disable-next-line max-statements
  async moveToAdjacentStep(
    guidedTourTask: TourTask,
    guidedTourManager: DefaultGuidedTourManager,
    direction: StepDirection,
    translations: Record<string, string>,
  ) {
    if (util.pageUnloadingFlag) {
      // Don't do anything if the page is currently in the process of redirecting.
      return;
    }
    const currentStepActiveIndex =
      guidedTourManager.activeDriverTask!.getActiveIndex()!;
    const adjacentStep = util.getAdjacentStep(
      guidedTourTask,
      currentStepActiveIndex,
      direction,
    );
    if (!adjacentStep) {
      // End the tour, there are no more steps.
      guidedTourManager.activeDriverTask!.destroy();
      return;
    }

    const adjacentStepIndex = guidedTourTask.steps!.indexOf(adjacentStep);
    // Set the storage key prematurely, in case a reflex action caused a redirect.
    StorageManager.setStorageKey(
      StorageManager.getTaskCurrentStepStorageKey(guidedTourTask),
      adjacentStepIndex.toString(),
    );

    // If the adjacent step is on another page, redirect to it. Reflex actions never redirect, since they might navigate
    // on their own (e.g. "Save and view").
    if (
      !guidedTourTask.steps![currentStepActiveIndex].reflex &&
      guidedTourManager.redirectToExpectedPage(adjacentStep)
    ) {
      return;
    }

    util.driveToStep(
      guidedTourManager.activeDriverTask!,
      adjacentStepIndex,
      translations,
    );
  },
};

util.addPageUnloadingListener();

/**
 * This is a function to ensure each call has its own object, and subsequent manipulation doesn't alter the defaults.
 */
function XWikiDriverConfig(
  guidedTourManager: DefaultGuidedTourManager,
  guidedTourTask: TourTask,
  translations: Record<string, string>,
): Config {
  console.log("Setting up", guidedTourTask);
  // Old code calls this variable `tour`.
  return {
    nextBtnText: translations["guidedtour.driver.next"],
    prevBtnText: translations["guidedtour.driver.previous"],
    showProgress: true,
    showButtons: ["previous", "next", "close"],
    overlayOpacity: 0.3,
    waitForElement: WAIT_FOR_ELEMENT_TIMEOUT,
    onHighlightStarted: (element, step) => {
      util.hideLoadingNotification();
      if (element === undefined && step.element !== undefined) {
        // driver.js gave up waiting for the step's targeted element. Skip the task, don't proceed with it.
        console.error("Element not found for step:", step);
        new XWiki.widgets.Notification(
          translations["guidedtour.driver.error"],
          "error",
        );
        void guidedTourManager.setTaskStatus(
          guidedTourTask,
          TourTaskStatus.SKIPPED,
        );
      }
    },
    onPopoverRender: (popDOM, options) => {
      // TODO: Need to handle this better
      const activeIndex = options.state.activeIndex ?? -1;
      util.solveButtons(
        popDOM,
        guidedTourTask.steps![activeIndex],
        guidedTourManager,
        guidedTourTask,
        translations,
      );

      popDOM.progress.style.display = "";
      popDOM.progress.innerText =
        "⬤ ".repeat(activeIndex + 1) +
        "◯ ".repeat(options.config.steps!.length - activeIndex - 1);
      options.config.overlayOpacity = guidedTourTask.steps![activeIndex]
        .backdrop
        ? 0.3
        : 0;
      popDOM.wrapper.insertBefore(popDOM.progress, popDOM.title);

      // The user will see this step, so update the storage key.
      StorageManager.setStorageKey(
        StorageManager.getTaskCurrentStepStorageKey(guidedTourTask),
        activeIndex.toString(),
      );
    },
    /**
     * Set the status of the task when it is closed: DONE if the user reached the end, otherwise keep its status and
     * stop starting the next tasks of its tour automatically.
     */
    onDestroyed: async function (_element, _step, _options) {
      console.debug("onDestroyed", _element, _step, _options, guidedTourTask);
      util.hideLoadingNotification();
      if (guidedTourManager.activeTask === undefined) {
        // The task status was already set by an external command, so don't recompute the status here.
        return;
      } else {
        const currentStepIndex =
          Number.parseInt(
            StorageManager.getStorageKey(
              StorageManager.getTaskCurrentStepStorageKey(guidedTourTask),
            ) ?? "-1",
          ) + 1;
        try {
          if (currentStepIndex >= guidedTourTask.steps!.length) {
            await guidedTourManager.setTaskStatus(
              guidedTourTask,
              TourTaskStatus.DONE,
            );
          } else {
            // The user closed the task before the end (close button or click outside): keep its status, but don't
            // start the next tasks of its tour automatically anymore.
            await guidedTourManager.disableCallToAction(guidedTourTask.tourId!);
            await guidedTourManager.setTaskStatus(
              guidedTourTask,
              guidedTourTask.status,
            );
          }
        } catch (e) {
          console.error(e);
        }
      }
    },
    // Also called by driver.js when the targeted element of a reflex step is clicked (see `advanceOnClick`).
    onNextClick: async (element, step, options) => {
      if (
        step.advanceOnClick &&
        element instanceof HTMLInputElement &&
        element.type == "text"
      ) {
        // Special case for text inputs: wait before continuing, to allow the user to type stuff.
        // TODO: Maybe add a 'match text' setting for advancing the step.
        await new Promise((resolve) =>
          setTimeout(resolve, TEXT_INPUT_REFLEX_DELAY),
        );
        if (options.driver.getActiveIndex() !== options.index) {
          // The active step changed in the meantime.
          return;
        }
      }
      util.moveToAdjacentStep(
        guidedTourTask,
        guidedTourManager,
        "next",
        translations,
      );
    },
    onPrevClick: () => {
      util.moveToAdjacentStep(
        guidedTourTask,
        guidedTourManager,
        "previous",
        translations,
      );
    },
  };
}

function convertToDriverStep(
  step: TourStep,
  guidedTourTask: TourTask,
): DriveStep {
  const selector = step.element;
  return {
    // driver.js waits (see `waitForElement`) while this returns nothing, so only hidden elements are waited for too.
    element: selector
      ? ((() => util.findVisibleElement(selector)) as () => Element)
      : undefined,
    advanceOnClick: step.reflex,
    popover: {
      title: step.title ?? guidedTourTask.title,
      description: step.content,
    },
  };
}

async function getDriverConfigForSteps(
  guidedTourTask: TourTask,
  guidedTourManager: DefaultGuidedTourManager,
  translations: Record<string, string>,
): Promise<Config> {
  if (!guidedTourTask.steps) {
    console.error("Task has no steps:", guidedTourTask);
    throw "Task has no steps";
  }
  console.log(guidedTourTask.steps);
  const config = XWikiDriverConfig(
    guidedTourManager,
    guidedTourTask,
    translations,
  );
  config.steps = guidedTourTask.steps!.map((step) =>
    convertToDriverStep(step, guidedTourTask),
  );
  return config;
}

const { driveToStep, hideLoadingNotification } = util;

export {
  XWikiDriverConfig,
  driveToStep,
  driver,
  getDriverConfigForSteps,
  hideLoadingNotification,
};

// FIXME: From old TourJS.xml
/*
  // TODO: Check for unused translation strings at the end of development.

// FIXME: From old TourJS.xml
      // TODO: Check precondition for next step;
      // TODO: Check if the next step is on the right page (to account for href redirects, etc);
  // Helper to bind click events, TODO: could be deleted.
  function bindFloaterClickEvent(selector, callback) {
    $('.guidedtour-widget ' + selector).on('click', (event) => {
      callback(event);
    });
  };

  bindFloaterClickEvent('.top-bar', (event) => {
    window.localStorage.setItem('TourFloaterCollapsed', document.querySelector('.guidedtour-widget').classList.toggle('collapsed'));
  });

  if (window.localStorage.getItem('guidedtour-widget-position-x')) {
    // FIXME: Could be XSS i think, if someone edits this key. But that's how the right side panel works too.
    // FIXME: Clamp the allowed values, so the widget is always visible on the screen. Maybe set the value as percentage of screen width? In the dragging functions I mean.
    document.querySelector('.guidedtour-widget').style.left = window.localStorage.getItem('guidedtour-widget-position-x');
  }

  // Definitions.
  /*
   * Function to set up a draggable element, for the widget.
   * Taken from https://www.w3schools.com/howto/howto_js_draggable.asp
   *\/
  function dragElement(elmnt) {
    console.debug(elmnt)
    if (!elmnt.classList.contains('draggable')) {
      return;
    }
    var pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;
    // otherwise, move the DIV from anywhere inside the DIV:
    elmnt.onmousedown = dragMouseDown;

    function dragMouseDown(e) {
      e = e || window.event;
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      // get the mouse cursor position at startup:
      pos3 = e.clientX;
      pos4 = e.clientY;
      document.onmouseup = closeDragElement;
      // call a function whenever the cursor moves:
      document.onmousemove = elementDrag;
    }

    function elementDrag(e) {
      e = e || window.event;
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      document.body.style.setProperty('cursor', 'grabbing', 'important');
      elmnt.classList.add('dragging');
      // calculate the new cursor position:
      pos1 = pos3 - e.clientX;
      pos2 = pos4 - e.clientY;
      pos3 = e.clientX;
      pos4 = e.clientY;
      // set the element's new position:
      //elmnt.style.top = (elmnt.offsetTop - pos2) + "px"; // Commented so the drag only goes side-to-side, not up-down.
      // TODO: Make sure the widget doesn't end up outside the window post-window-resize.
      elmnt.style.left = (elmnt.offsetLeft - pos1) + "px";
    }

    function closeDragElement(e) {
      // Stop moving when mouse button is released:
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      document.onmouseup = null;
      document.onmousemove = null;
      document.body.style.cursor = "";
      elmnt.classList.remove('dragging');
      window.localStorage.setItem('guidedtour-widget-position-x', elmnt.style.left);
    }
  }
});
*/
