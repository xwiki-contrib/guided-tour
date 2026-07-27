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
const util = {
  /**
   * Useful for locking task progression while redirecting to another page (like after clicking on an URL as part of a
   * step). This flag is true when the page is being unloaded before a redirect (after a `beforeunload` event).
   */
  pageUnloadingFlag: false,
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
   * @returns The `Skip All` element
   */
  makeSkipAllButton(
    guidedTourManager: DefaultGuidedTourManager,
    guidedTourTask: TourTask,
  ): Element {
    const customSkipAll = document.createElement("a");
    customSkipAll.classList.add("driver-xwiki-skip-all-button");

    function onSkipAll() {
      guidedTourManager.setTaskStatus(guidedTourTask, TourTaskStatus.SKIPPED);
    }

    customSkipAll.onclick = onSkipAll;
    customSkipAll.innerHTML = "Skip All"; // TODO: Add translation.
    return customSkipAll;
  },
  /**
   * For use in `waitForElement` below.
   *
   * @param element - The element to check
   * @returns true if the element is visible on the page, false otherwise.
   */
  isElementVisible(element: Element): boolean {
    const style = globalThis.getComputedStyle(element);
    if (style.display == "none") {
      return false;
    } else {
      return true;
    }
  },
  /**
   * Wait until an element is visible on the page.
   *
   * @param selector - css selector for the element to wait for (should be compatible with document.querySelector)
   * @returns a promise which succeeds if the element is found within the time limit, and fails otherwise
   */
  async waitForElement(
    selector: string | undefined,
  ): Promise<Element | undefined> {
    if (!selector) {
      // Return instantly if we're not supposed to wait for an element.
      return;
    }
    return util.retryWithCallback(() => {
      const queriedElement = document.querySelector(selector);
      if (queriedElement && util.isElementVisible(queriedElement)) {
        return queriedElement;
      } else {
        return undefined;
      }
    }, selector);
  },
  /**
   *
   * @param callbackFn - The function to run to test if our goal has been achieved. Should return truthy if achieved,
   *     false otherwise (if we still need to wait)
   * @param probeInterval - time (in ms) to wait after a failed check for the specified element. Decrease this argument
   *                to get a quicker response once the specified element appears in the page.
   * @param maxIntervals - how many probe intervals to wait until rejecting
   * @param consoleName - For debugging, to display in console
   * @returns the return value of callbackFn if successful, or a failed Promise if the timeout is reached.
   */
  async retryWithCallback<T>(
    callbackFn: () => T,
    consoleName = "something",
    probeInterval = 50,
    maxIntervals = 60,
  ): Promise<T> {
    // TODO: Could maybe use MutationObservers here?
    console.debug(`waiting for ${consoleName}...`);
    for (let i = 0; i < maxIntervals; i += 1) {
      const retValue = callbackFn();
      if (retValue) {
        return retValue;
      }
      console.debug(`(${i}/${maxIntervals}) waiting for ${consoleName}...`);
      await new Promise((resolve) => setTimeout(resolve, probeInterval));
    }
    return Promise.reject(
      `Failed to confirm ${consoleName} after waiting ${
        probeInterval * maxIntervals
      } (${probeInterval} * ${maxIntervals}) ms.`,
    );
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
  ) {
    if (step.reflex) {
      popDOM.footerButtons.removeChild(popDOM.nextButton);
      popDOM.footerButtons.removeChild(popDOM.previousButton);
    } else {
      popDOM.nextButton.classList.add("btn", "btn-sm", "btn-primary"); // TODO: Make this an <a> instead of
      // <button>
      popDOM.previousButton.classList.add("btn", "btn-sm"); // TODO: Make this an <a> instead of <button>
    }
    popDOM.footer.appendChild(
      util.makeSkipAllButton(guidedTourManager, guidedTourTask),
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
  async moveToAdjacentStep(
    guidedTourTask: TourTask,
    guidedTourManager: DefaultGuidedTourManager,
    direction: StepDirection,
  ) {
    /*
     * Things to consider:
     * - Is the current step the one I expect?
     * - Am I in the last step?
     * - Am I expecting a redirect?
     * - Wait for element to appear
     */
    if (util.pageUnloadingFlag) {
      // Don't do anything if the page is currently in the process of redirecting.
      return;
    }
    // Cache the current step index, so we can check later (after async operations) if we are in the same step
    // we started in.
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

    // The `.drive()` method is overridden in xwiki to wait for elements to appear in the page, thus making it async (as
    // opposed to driver.js's default non-async method).
    await guidedTourManager.activeDriverTask!.drive(adjacentStepIndex);
  },
};

util.addPageUnloadingListener();

/**
 * This is a function to ensure each call has its own object, and subsequent manipulation doesn't alter the defaults.
 */
function XWikiDriverConfig(
  guidedTourManager: DefaultGuidedTourManager,
  guidedTourTask: TourTask,
): Config {
  console.log("Setting up", guidedTourTask);
  // Old code calls this variable `tour`.
  return {
    nextBtnText: "Next >", // TODO: Add translation.
    prevBtnText: "< Previous", // TODO: Add translation.
    showProgress: true,
    showButtons: ["previous", "next", "close"],
    overlayOpacity: 0.3,
    onPopoverRender: (popDOM, options) => {
      // TODO: Need to handle this better
      const activeIndex = options.state.activeIndex ?? -1;
      util.solveButtons(
        popDOM,
        guidedTourTask.steps![activeIndex],
        guidedTourManager,
        guidedTourTask,
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
    onDestroyed: function (_element, _step, _options) {
      console.debug("onDestroyed", _element, _step, _options, guidedTourTask);
      // The state provided by driver.js is empty when this function is called.
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
        const status =
          currentStepIndex >= guidedTourTask.steps!.length
            ? TourTaskStatus.DONE
            : TourTaskStatus.SKIPPED;
        guidedTourManager.setTaskStatus(guidedTourTask, status);
      }
    },
    onNextClick: async () => {
      await util.moveToAdjacentStep(guidedTourTask, guidedTourManager, "next");
    },
    onPrevClick: async () => {
      await util.moveToAdjacentStep(
        guidedTourTask,
        guidedTourManager,
        "previous",
      );
    },
  };
}

function convertToDriverStep(
  step: TourStep,
  guidedTourTask: TourTask,
): DriveStep {
  return {
    element: step.element,
    popover: {
      title: step.title ?? guidedTourTask.title,
      description: step.content,
    },
  };
}

function getDriverConfigForSteps(
  guidedTourTask: TourTask,
  guidedTourManager: DefaultGuidedTourManager,
) {
  if (!guidedTourTask.steps) {
    console.error("Task has no steps:", guidedTourTask);
    throw "Task has no steps";
  }
  console.log(guidedTourTask.steps);
  const config = XWikiDriverConfig(guidedTourManager, guidedTourTask);
  config.steps = guidedTourTask.steps!.map((step) =>
    convertToDriverStep(step, guidedTourTask),
  );
  return config;
}

function wrapTask(
  guidedTourTask: Driver,
  guidedTourManager: DefaultGuidedTourManager,
): Driver {
  function hasActiveStepIndexChanged(
    previousActiveStepIndex: number | undefined,
    currentStepActiveIndex: number | undefined,
  ) {
    return (
      previousActiveStepIndex !== undefined &&
      currentStepActiveIndex != previousActiveStepIndex
    );
  }
  const _drive = guidedTourTask.drive;
  // eslint-disable-next-line max-statements
  guidedTourTask.drive = async function (stepIndex: number = 0) {
    // TODO: Add translation as part of GUIDEDTOUR-4.
    const loadingNotification = new XWiki.widgets.Notification(
      "Loading task step...",
      "inprogress",
    );
    const currentStepActiveIndex = guidedTourTask.getActiveIndex();
    try {
      const targetedElement = await util.waitForElement(
        guidedTourManager.activeTask!.steps![stepIndex].element,
      );
      if (
        hasActiveStepIndexChanged(
          currentStepActiveIndex,
          guidedTourTask.getActiveIndex(),
        )
      ) {
        // The active step moved while waiting for the element, so don't do anything.
        loadingNotification.hide();
        return;
      }
      bindReflexEvents(
        targetedElement,
        guidedTourManager.activeTask!.steps![stepIndex],
        guidedTourManager,
      );
      StorageManager.setStorageKey(
        StorageManager.getTaskCurrentStepStorageKey(
          guidedTourManager.activeTask!,
        ),
        stepIndex.toString(),
      );
      _drive(stepIndex);
      loadingNotification.hide();
      return;
    } catch (e) {
      if (
        hasActiveStepIndexChanged(
          currentStepActiveIndex,
          guidedTourTask.getActiveIndex(),
        )
      ) {
        // The active step moved while waiting for the element, so don't do anything.
        loadingNotification.hide();
        return;
      }
      // We didn't find the element we wanted. Don't proceed with the task.
      console.error(e);
      // TODO: Add translation as part of GUIDEDTOUR-4.
      loadingNotification.replace(
        new XWiki.widgets.Notification(
          "Error while moving between task steps.",
          "error",
        ),
      );
      // Skip the task since we didn't find the step's targeted element in the page.
      guidedTourManager.setTaskStatus(
        guidedTourManager.activeTask!,
        TourTaskStatus.SKIPPED,
      );
    }
  }.bind(guidedTourTask);
  return guidedTourTask;
}

export { XWikiDriverConfig, driver, getDriverConfigForSteps, wrapTask };

/**
 * Adds a callback that triggers when the HTML element is interacted with.
 *
 * @param element - The element which should be interacted with in order to proceed.
 * @param step - The current step. Used to confirm that the active step is the expected one.
 * @param guidedTourManager - The guidedTourManager Api instance.
 * @param callbackFn - A callback to execute once the element is interacted with.
 */
function bindReflexEvents(
  element: Element | undefined,
  step: TourStep,
  guidedTourManager: DefaultGuidedTourManager,
  callbackFn: () => void = () => {
    const activeIndex = guidedTourManager.activeDriverTask?.getActiveIndex();
    if (activeIndex === undefined) {
      return;
    }
    // Always move to the next step on reflex click.
    void util.moveToAdjacentStep(
      guidedTourManager.activeTask!,
      guidedTourManager,
      "next",
    );
  },
) {
  console.debug("Doing reflex bind");
  if (!step.reflex || element === undefined) {
    if (step.reflex && element === undefined) {
      console.warn("WARNING: reflex step with empty element:", step);
    }
    return;
  }
  const triggerCallback = () => {
    console.debug("Removing reflex listener on ", element);
    element.removeEventListener("click", callback);
    callbackFn();
  };
  const callback = (event: Event) => {
    console.debug(event);
    if (
      event.target instanceof HTMLInputElement &&
      event.target.type == "text"
    ) {
      // Special case for text inputs.
      // Right now, the text input awaits for 5s before continuing, to allow the user to type stuff.
      // TODO: Maybe add a 'match text' setting for advancing the step.
      const msTimeout = 5000;
      new Promise((resolve) => setTimeout(resolve, msTimeout))
        .then(() => {
          console.debug("sloip awoked");
          if (
            step.order != guidedTourManager.activeDriverTask?.getActiveIndex()
          ) {
            triggerCallback();
          }
          return;
        })
        .catch(console.error);
    } else {
      triggerCallback();
    }
  };
  console.debug("Adding reflex listener on ", element);
  element.addEventListener("click", callback);
}

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
