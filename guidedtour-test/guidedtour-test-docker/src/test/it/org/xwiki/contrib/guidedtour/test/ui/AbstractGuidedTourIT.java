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
package org.xwiki.contrib.guidedtour.test.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.xwiki.contrib.guidedtour.test.po.GuidedTourFixture;
import org.xwiki.test.integration.junit.LogCaptureConfiguration;
import org.xwiki.test.ui.TestUtils;

/**
 * Common setup of the guided tour functional tests: each test creates its own tours and user, and removes its tours
 * afterwards, so that they don't show up in the widget of the next tests.
 *
 * @version $Id$
 */
abstract class AbstractGuidedTourIT
{
    protected GuidedTourFixture fixture;

    @BeforeAll
    void registerExpectedLogs(LogCaptureConfiguration logCaptureConfiguration)
    {
        GuidedTourFixture.registerExpectedLogs(logCaptureConfiguration);
    }

    @BeforeEach
    void createFixture(TestUtils setup)
    {
        this.fixture = new GuidedTourFixture(setup);
        // A previous test may have left a task active, which would be resumed on the next page load and block the page.
        this.fixture.clearBrowserStorage();
    }

    @AfterEach
    void cleanUpFixture() throws Exception
    {
        this.fixture.cleanUp();
    }
}
