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
  pageUnloadingFlag: false,
  /**
   * Useful for blocking task progression while redirecting to another page.
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
      guidedTourManager.activeDriverTask?.destroy();
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
   * @param probeInterval - time (in ms) to wait after a failed check for the specified element
   * @param maxIntervals - how many probe intervals to wait until rejecting
   * @returns a promise which succeeds if the element is found within the time limit, and fails otherwise
   */
  async waitForElement(
    selector: string | undefined,
    probeInterval = 500,
    maxIntervals = 6,
  ): Promise<Element | undefined> {
    if (!selector) {
      // Return instantly if we're not supposed to wait for an element.
      return;
    }
    return util.retryWithCallback(
      () => {
        const queriedElement = document.querySelector(selector);
        if (queriedElement && util.isElementVisible(queriedElement)) {
          return queriedElement;
        } else {
          return undefined;
        }
      },
      probeInterval,
      maxIntervals,
      selector,
    );
  },
  /**
   *
   * @param callbackFn - The function to run to test if our goal has been achieved. Should return truthy if achieved,
   *     false otherwise (if we still need to wait)
   * @param probeInterval - time (in ms) to wait after a failed check for the specified element
   * @param maxIntervals - how many probe intervals to wait until rejecting
   * @param consoleName - For debugging, to display in console
   * @returns undefined for timeout, the return value of callbackFn if successful.
   */
  async retryWithCallback<T>(
    callbackFn: () => T | undefined,
    probeInterval = 50,
    maxIntervals = 60,
    consoleName = "something",
  ) {
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
    console.debug(
      `Failed to confirm ${consoleName} after waiting ${
        probeInterval * maxIntervals
      } (${probeInterval} * ${maxIntervals}) ms.`,
    );
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
  /**
   * This function will wait until the targeted element of the adjacentStep is visible in the page. If the driverJs step changed while waiting for the element, return false.
   *
   * @param thisStepActiveIndex - The index of the current step. Used to determine if the step moved while we were waiting for the element to appear.
   * @param adjacentStep - The step we want to get to, so we can test that its targeted element exists
   * @param guidedTourManager - Reference to the guidedTourManager, used for various API calls
   * @returns - The Element if it was found after waiting for it; false if the active driverJs step changed in the meantime; undefined if waiting for the element timed out
   */
  async waitForAdjacentStepElement(
    thisStepActiveIndex: number,
    adjacentStep: TourStep,
    guidedTourManager: DefaultGuidedTourManager,
  ): Promise<false | Element | undefined> {
    return await util
      .waitForElement(adjacentStep.element)
      .then((targetedElement) => {
        if (
          thisStepActiveIndex !=
          guidedTourManager.activeDriverTask!.getActiveIndex()
        ) {
          // The task moved to some other step while we were waiting, so no need to do anything.
          return false;
        }

        if (
          adjacentStep.element !== undefined &&
          targetedElement === undefined
        ) {
          // We didn't find the expected element in the page, so the task is probably broken, so skip it.
          console.error(
            `Failed to find ${adjacentStep.element} element in the page when going to step ${adjacentStep.order}`,
            adjacentStep,
          );
          new XWiki.widgets.Notification(
            "Failed to find targeted element. Skipping the task.",
            "error",
          );
          guidedTourManager.activeDriverTask!.destroy();
          return false;
        }
        return targetedElement;
      });
  },
  async moveToAdjacentStep(
    guidedTourTask: TourTask,
    guidedTourManager: DefaultGuidedTourManager,
    currentStepActiveIndex: number,
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

    await util
      .waitForAdjacentStepElement(
        currentStepActiveIndex,
        adjacentStep,
        guidedTourManager,
      )
      .then((targetedElement) => {
        util.handleAdjacentStepTransition(
          guidedTourManager,
          adjacentStep,
          adjacentStepIndex,
          targetedElement,
        );
        return;
      })
      .catch((e) => {
        console.error(e);
        // Set the current step index to the right one, since we set it preemptively above to anticipate a redirect.
        // This is so .destroy() sets the right task status (DONE or SKIPPED).
        StorageManager.setStorageKey(
          StorageManager.getTaskCurrentStepStorageKey(guidedTourTask),
          currentStepActiveIndex.toString(),
        );
        guidedTourManager.activeDriverTask!.destroy();
      });
  },
  handleAdjacentStepTransition(
    guidedTourManager: DefaultGuidedTourManager,
    adjacentStep: TourStep,
    adjacentStepIndex: number,
    targetedElement: Element | undefined | false,
  ) {
    if (targetedElement) {
      bindReflexEvents(targetedElement, adjacentStep, guidedTourManager);
      // Move to the adjacent step.
      guidedTourManager.activeDriverTask!.drive(adjacentStepIndex);
      return;
    }

    if (adjacentStep.element !== undefined && targetedElement === undefined) {
      // Something went wrong if we can't find the targeted element. Skip this task.
      guidedTourManager.activeDriverTask!.destroy();
    }
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
      // The state is empty when this function is called.
      const status =
        Number.parseInt(
          StorageManager.getStorageKey(
            StorageManager.getTaskCurrentStepStorageKey(guidedTourTask),
          ) ?? "-1",
        ) +
          1 >=
        guidedTourTask.steps!.length
          ? TourTaskStatus.DONE
          : TourTaskStatus.SKIPPED;
      guidedTourManager.setTaskStatus(guidedTourTask, status);
    },
    // TODO: Remove this linter disable and refactor the function.
    onNextClick: async () => {
      // Cache the current step index, so we can check later (after async operations) if we are in the same step
      // we started in.
      const thisStepActiveIndex =
        guidedTourManager.activeDriverTask!.getActiveIndex()!;
      await util.moveToAdjacentStep(
        guidedTourTask,
        guidedTourManager,
        thisStepActiveIndex,
        "next",
      );
    },
    onPrevClick: async () => {
      // Cache the current step index, so we can check later (after async operations) if we are in the same step
      // we started in.
      const thisStepActiveIndex =
        guidedTourManager.activeDriverTask!.getActiveIndex()!;
      await util.moveToAdjacentStep(
        guidedTourTask,
        guidedTourManager,
        thisStepActiveIndex,
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
  const _drive = guidedTourTask.drive;
  guidedTourTask.drive = async function (stepIndex: number = 0) {
    const loadingNotification = new XWiki.widgets.Notification(
      "Loading task...",
      "inprogress",
    );
    await util
      .waitForElement(guidedTourManager.activeTask!.steps![stepIndex].element)
      .then((element) => {
        bindReflexEvents(
          element,
          guidedTourManager.activeTask!.steps![stepIndex],
          guidedTourManager,
        );
        StorageManager.setStorageKey(
          StorageManager.getTaskStepStorageStorageKey(
            guidedTourManager.activeTask!,
          ),
          JSON.stringify(guidedTourManager.activeTask!.steps!),
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
      })
      .catch((e) => {
        // We didn't find the element we wanted. Don't start the task.
        console.error(e);
        loadingNotification.replace(
          new XWiki.widgets.Notification("Could not start task.", "error"),
        );
        // Skip the task since we didn't find the element for the first step.
        guidedTourManager.setTaskStatus(
          guidedTourManager.activeTask!,
          TourTaskStatus.SKIPPED,
        );
      });
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
      activeIndex,
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
