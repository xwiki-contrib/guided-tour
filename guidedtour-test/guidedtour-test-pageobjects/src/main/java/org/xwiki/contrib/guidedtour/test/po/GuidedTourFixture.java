/*
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
package org.xwiki.contrib.guidedtour.test.po;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rest.model.jaxb.Property;
import org.xwiki.test.integration.junit.LogCaptureConfiguration;
import org.xwiki.test.ui.TestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Creates the tours, tasks and steps used by the functional tests, through the REST API and as superadmin (without
 * changing the user logged in the browser), and removes them after each test.
 *
 * @version $Id$
 * @since 0.2
 */
public class GuidedTourFixture
{
    /**
     * The password of the users created by the tests.
     */
    public static final String PASSWORD = "pa$$word";

    private static final String TOUR_CLASS = "XWiki.GuidedTour.TourClass";

    private static final String TASK_CLASS = "XWiki.GuidedTour.TaskClass";

    private static final String STEP_CLASS = "XWiki.GuidedTour.StepClass";

    private static final String USER_STATUS_CLASS = "XWiki.GuidedTour.UserTourStatusClass";

    private static final String TITLE = "title";

    private static final String IS_ACTIVE = "isActive";

    private static final String ORDER = "order";

    private static final String DEPENDS_ON = "dependsOn";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TestUtils setup;

    /**
     * The pages created by the current test, the last created first, so that tasks are deleted before their tour.
     */
    private final Deque<DocumentReference> createdPages = new ArrayDeque<>();

    /**
     * @param setup the test utilities
     */
    public GuidedTourFixture(TestUtils setup)
    {
        this.setup = setup;
    }

    /**
     * Declare the server logs expected when using the guided tours. They are logged at WARN level, which would fail the
     * tests otherwise.
     *
     * @param logCaptureConfiguration the log capture configuration of the test class
     */
    public static void registerExpectedLogs(LogCaptureConfiguration logCaptureConfiguration)
    {
        logCaptureConfiguration.registerExpected(
            // Each new user starts without a guided tour status, which the widget handles as the default status.
            "Resource not found: User tour status API: getting user tour status object.",
            "User tour status not found for user");
    }

    /**
     * Create a page.
     *
     * @param reference the reference of the page
     * @param content the content of the page, in XWiki 2.1 syntax
     * @param title the title of the page
     * @return the reference of the page
     * @throws Exception if the page can't be created
     */
    public DocumentReference createPage(DocumentReference reference, String content, String title) throws Exception
    {
        asSuperAdmin(() -> this.setup.rest().savePage(reference, content, title));
        this.createdPages.push(reference);
        return reference;
    }

    /**
     * Create a tour, as the home page of a dedicated space.
     *
     * @param parent the space where to create the tour
     * @param name the name of the tour space
     * @param title the title of the tour
     * @param active whether the tour is active, i.e. displayed in the widget
     * @return the reference of the tour page
     * @throws Exception if the tour can't be created
     */
    public DocumentReference createTour(SpaceReference parent, String name, String title, boolean active)
        throws Exception
    {
        DocumentReference tour = new DocumentReference("WebHome", new SpaceReference(name, parent));
        createPage(tour, "", title);
        asSuperAdmin(() -> this.setup.rest().addObject(tour, TOUR_CLASS, TITLE, title, IS_ACTIVE, toBoolean(active)));
        return tour;
    }

    /**
     * Create a task, as a page next to the tour page.
     *
     * @param tour the reference of the tour page
     * @param id the id of the task, which is also the name of its page; it must be unique among all the tours, since
     *     the widget uses it as HTML id
     * @param title the title of the task
     * @param order the position of the task in its tour
     * @param active whether the task is active, i.e. displayed in the widget
     * @param dependsOn the ids of the tasks this task depends on
     * @return the reference of the task page
     * @throws Exception if the task can't be created
     */
    public DocumentReference createTask(DocumentReference tour, String id, String title, int order, boolean active,
        String... dependsOn) throws Exception
    {
        DocumentReference task = new DocumentReference(id, tour.getLastSpaceReference());
        createPage(task, "", title);
        asSuperAdmin(() -> this.setup.rest().addObject(task, TASK_CLASS, TITLE, title, ORDER, order, IS_ACTIVE,
            toBoolean(active), DEPENDS_ON, String.join(",", dependsOn)));
        return task;
    }

    /**
     * Add steps to a task, in the given order.
     *
     * @param task the reference of the task page
     * @param steps the steps to add
     * @throws Exception if the steps can't be added
     */
    public void addSteps(DocumentReference task, Step... steps) throws Exception
    {
        addSteps(task, 0, steps);
    }

    /**
     * Add steps to a task that already has some, after them.
     *
     * @param task the reference of the task page
     * @param firstOrder the position of the first added step in the task, i.e. the number of steps it already has
     * @param steps the steps to add
     * @throws Exception if the steps can't be added
     */
    public void addSteps(DocumentReference task, int firstOrder, Step... steps) throws Exception
    {
        for (int i = 0; i < steps.length; i++) {
            // Steps are numbered from 0, like in the default tours. The order is the position of the step in the task.
            Object[] properties = steps[i].toProperties(firstOrder + i);
            asSuperAdmin(() -> this.setup.rest().addObject(task, STEP_CLASS, properties));
        }
    }

    /**
     * Remove a page created from the user interface (e.g. a task added from the tour page) after the test, like the
     * pages created by this fixture. The pages are removed in the reverse order of their creation.
     *
     * @param reference the reference of the page
     */
    public void deleteAfterTest(DocumentReference reference)
    {
        this.createdPages.push(reference);
    }

    /**
     * Prevent a user from viewing a page.
     *
     * @param reference the page
     * @param userName the name of the user
     * @throws Exception if the right can't be set
     */
    public void denyView(DocumentReference reference, String userName) throws Exception
    {
        deny(reference, userName, "view");
    }

    /**
     * Prevent a user from editing a page.
     *
     * @param reference the page
     * @param userName the name of the user
     * @throws Exception if the right can't be set
     */
    public void denyEdit(DocumentReference reference, String userName) throws Exception
    {
        deny(reference, userName, "edit");
    }

    /**
     * Create a user and log in with it in the browser. The user is deleted when cleaning up.
     *
     * @param userName the name of the user
     * @return the name of the user
     */
    public String createUserAndLogin(String userName)
    {
        this.setup.createUserAndLogin(userName, PASSWORD);
        this.createdPages.push(new DocumentReference(this.setup.getCurrentWiki(), "XWiki", userName));
        return userName;
    }

    /**
     * @param tour the reference of a tour page
     * @return the id of the tour, as used by the widget
     */
    public String getTourId(DocumentReference tour)
    {
        return this.setup.serializeReference(tour);
    }

    /**
     * @param reference the reference of a page
     * @return {@code true} if the page exists
     * @throws Exception if the existence of the page can't be checked
     */
    public boolean exists(DocumentReference reference) throws Exception
    {
        boolean[] exists = new boolean[1];
        asSuperAdmin(() -> exists[0] = this.setup.rest().exists(reference));
        return exists[0];
    }

    /**
     * Read a task, as stored in its page.
     *
     * @param task the reference of a task page
     * @return the task
     * @throws Exception if the task can't be read
     */
    public Task getTask(DocumentReference task) throws Exception
    {
        // The guided tour REST API requires a CSRF token even to read, so read the task object directly.
        return new Task(getObjectProperties(task, TASK_CLASS));
    }

    /**
     * Read a tour, as stored in its page.
     *
     * @param tour the reference of a tour page
     * @return the tour
     * @throws Exception if the tour can't be read
     */
    public Tour getTour(DocumentReference tour) throws Exception
    {
        return new Tour(getObjectProperties(tour, TOUR_CLASS));
    }

    /**
     * @param userName the name of a user
     * @return the guided tour status stored in the profile of the user, if any
     * @throws Exception if the status can't be read
     */
    public Optional<UserTourStatus> getUserTourStatus(String userName) throws Exception
    {
        DocumentReference user = new DocumentReference(this.setup.getCurrentWiki(), "XWiki", userName);
        Map<String, String> properties = getObjectProperties(user, USER_STATUS_CLASS);
        if (properties.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UserTourStatus(properties.get("widgetState"), properties.get("toursStatus")));
    }

    /**
     * Wait until the guided tour status stored in the profile of the user matches the given condition. The widget
     * saves the status asynchronously.
     *
     * @param userName the name of the user
     * @param condition the expected condition
     * @return the stored status
     */
    public UserTourStatus waitForUserTourStatus(String userName, Predicate<UserTourStatus> condition)
    {
        return this.setup.getDriver().waitUntilCondition(driver -> {
            try {
                return getUserTourStatus(userName).filter(condition).orElse(null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read the guided tour status of user [%s].".formatted(userName),
                    e);
            }
        });
    }

    /**
     * Remove the pages and users created by the test, and the guided tour data stored in the browser, so that the next
     * test doesn't resume a task left active by this test.
     *
     * @throws Exception if the pages can't be removed
     */
    public void cleanUp() throws Exception
    {
        clearBrowserStorage();
        List<DocumentReference> pages = new ArrayList<>(this.createdPages);
        this.createdPages.clear();
        asSuperAdmin(() -> {
            for (DocumentReference page : pages) {
                this.setup.rest().delete(page);
            }
        });
    }

    /**
     * Clear the guided tour data stored in the browser: the progress of guests (local storage) and the active task
     * (session storage).
     */
    public void clearBrowserStorage()
    {
        this.setup.getDriver()
            .executeJavascript("try { window.localStorage.clear(); window.sessionStorage.clear(); } catch (e) {}");
    }

    /**
     * Wait until the guided tour status of the guest user is stored in the browser. The widget saves it
     * asynchronously.
     */
    public void waitForGuestStatusInBrowser()
    {
        this.setup.getDriver().waitUntilCondition(driver -> this.setup.getDriver()
            .executeJavascript("return window.localStorage.getItem('guidedtour_guestStatus');") != null);
    }

    /**
     * Wait until the guided tour status of the guest user stored in the browser matches the given condition. The widget
     * saves it asynchronously.
     *
     * @param condition the expected condition
     * @return the stored status
     */
    public UserTourStatus waitForGuestStatusInBrowser(Predicate<UserTourStatus> condition)
    {
        return this.setup.getDriver().waitUntilCondition(driver -> {
            Object stored = this.setup.getDriver()
                .executeJavascript("return window.localStorage.getItem('guidedtour_guestStatus');");
            if (stored == null) {
                return null;
            }
            try {
                JsonNode status = JSON.readTree(stored.toString());
                JsonNode toursStatus = status.path("toursStatus");
                UserTourStatus userTourStatus = new UserTourStatus(status.path("widgetState").asText(null),
                    toursStatus.isMissingNode() ? null : toursStatus.toString());
                return condition.test(userTourStatus) ? userTourStatus : null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to read the guided tour status of the guest user.", e);
            }
        });
    }

    private void deny(DocumentReference reference, String userName, String right) throws Exception
    {
        asSuperAdmin(() -> this.setup.rest().addObject(reference, "XWiki.XWikiRights", "users", "XWiki." + userName,
            "levels", right, "allow", 0));
    }

    private Map<String, String> getObjectProperties(DocumentReference document, String className) throws Exception
    {
        Map<String, String> properties = new HashMap<>();
        asSuperAdmin(() -> {
            org.xwiki.rest.model.jaxb.Object object =
                this.setup.rest().get(new ObjectReference(className + "[0]", document), false);
            if (object != null) {
                for (Property property : object.getProperties()) {
                    properties.put(property.getName(), property.getValue() == null ? "" : property.getValue());
                }
            }
        });
        return properties;
    }

    private void asSuperAdmin(ThrowingRunnable action) throws Exception
    {
        UsernamePasswordCredentials previousCredentials =
            this.setup.setDefaultCredentials(TestUtils.SUPER_ADMIN_CREDENTIALS);
        try {
            action.run();
        } finally {
            this.setup.setDefaultCredentials(previousCredentials);
        }
    }

    private static int toBoolean(boolean value)
    {
        return value ? 1 : 0;
    }

    @FunctionalInterface
    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    /**
     * A step of a task.
     *
     * @version $Id$
     */
    public static final class Step
    {
        private final Map<String, Object> properties = new LinkedHashMap<>();

        private Step(String content)
        {
            this.properties.put("content", content);
        }

        /**
         * @param content the text displayed by the step
         * @return a step displayed in the middle of the screen, on the current page
         */
        public static Step withContent(String content)
        {
            return new Step(content);
        }

        /**
         * @param selector the CSS selector of the element highlighted by the step
         * @return this step
         */
        public Step element(String selector)
        {
            this.properties.put("element", selector);
            return this;
        }

        /**
         * Make the step interactive: the user moves to the next step by interacting with the highlighted element.
         *
         * @return this step
         */
        public Step reflex()
        {
            this.properties.put("reflex", 1);
            return this;
        }

        /**
         * @param page the local reference of the page where the step is displayed; the user is redirected to it when
         *     moving to the step
         * @return this step
         */
        public Step targetPage(String page)
        {
            this.properties.put("targetPage", page);
            return this;
        }

        /**
         * @param action the action expected on the page where the step is displayed ({@code VIEW} or {@code EDIT}); the
         *     user is redirected to it when moving to the step, unless coming from a reflex step
         * @return this step
         */
        public Step targetAction(String action)
        {
            this.properties.put("targetAction", action);
            return this;
        }

        /**
         * @param queryParameters the query parameters expected on the page where the step is displayed
         * @return this step
         */
        public Step queryParameters(String queryParameters)
        {
            this.properties.put("queryParameters", queryParameters);
            return this;
        }

        private Object[] toProperties(int order)
        {
            List<Object> values = new ArrayList<>(List.of(ORDER, order));
            this.properties.forEach((name, value) -> {
                values.add(name);
                values.add(value);
            });
            return values.toArray();
        }
    }

    /**
     * A task, as stored in its page.
     *
     * @version $Id$
     */
    public static final class Task
    {
        /**
         * The separators of the values of the {@code dependsOn} list property, as defined by the task class.
         */
        private static final Pattern DEPENDS_ON_SEPARATORS = Pattern.compile("[|, ]+");

        private final Map<String, String> properties;

        Task(Map<String, String> properties)
        {
            this.properties = properties;
        }

        /**
         * @return the title of the task
         */
        public String getTitle()
        {
            return this.properties.getOrDefault(TITLE, "");
        }

        /**
         * @return {@code true} if the task is active, i.e. displayed in the widget
         */
        public boolean isActive()
        {
            return "1".equals(this.properties.getOrDefault(IS_ACTIVE, "").trim());
        }

        /**
         * @return the position of the task in its tour
         */
        public int getOrder()
        {
            String order = this.properties.getOrDefault(ORDER, "");
            return order.isBlank() ? -1 : Integer.parseInt(order.trim());
        }

        /**
         * @return the ids of the tasks this task depends on
         */
        public List<String> getDependsOn()
        {
            return DEPENDS_ON_SEPARATORS.splitAsStream(this.properties.getOrDefault(DEPENDS_ON, ""))
                .filter(id -> !id.isBlank()).toList();
        }

        @Override
        public String toString()
        {
            return this.properties.toString();
        }
    }

    /**
     * A tour, as stored in its page.
     *
     * @version $Id$
     */
    public static final class Tour
    {
        private final Map<String, String> properties;

        Tour(Map<String, String> properties)
        {
            this.properties = properties;
        }

        /**
         * @return the title of the tour
         */
        public String getTitle()
        {
            return this.properties.getOrDefault(TITLE, "");
        }

        /**
         * @return the description of the tour
         */
        public String getDescription()
        {
            return this.properties.getOrDefault("description", "");
        }

        /**
         * @return {@code true} if the tour is active, i.e. displayed in the widget
         */
        public boolean isActive()
        {
            return "1".equals(this.properties.getOrDefault(IS_ACTIVE, "").trim());
        }

        @Override
        public String toString()
        {
            return this.properties.toString();
        }
    }

    /**
     * The guided tour status stored in the profile of a user.
     *
     * @version $Id$
     */
    public static final class UserTourStatus
    {
        private final String widgetState;

        private final JsonNode toursStatus;

        UserTourStatus(String widgetState, String toursStatus) throws Exception
        {
            this.widgetState = widgetState;
            this.toursStatus = toursStatus == null || toursStatus.isBlank() ? JSON.createObjectNode()
                : JSON.readTree(toursStatus);
        }

        /**
         * @return the state of the widget ({@code OPEN} or {@code COLLAPSED})
         */
        public String getWidgetState()
        {
            return this.widgetState;
        }

        /**
         * @param tourId the id of a tour
         * @param taskId the id of a task of the tour
         * @return the stored status of the task, if any
         */
        public Optional<TourTaskStatus> getTaskStatus(String tourId, String taskId)
        {
            JsonNode status = this.toursStatus.path(tourId).path("tasksStatus").path(taskId);
            return status.isTextual() ? Optional.of(TourTaskStatus.fromString(status.asText())) : Optional.empty();
        }

        /**
         * @param tourId the id of a tour
         * @return {@code true} if the next tasks of the tour are started automatically when a task is completed
         */
        public boolean isCallToActionEnabled(String tourId)
        {
            JsonNode callToAction = this.toursStatus.path(tourId).path("callToAction");
            return callToAction.isMissingNode() || callToAction.asBoolean(true);
        }
    }
}
