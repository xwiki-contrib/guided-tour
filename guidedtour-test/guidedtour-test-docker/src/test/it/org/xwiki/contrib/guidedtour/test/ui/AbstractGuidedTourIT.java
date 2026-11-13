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
import org.xwiki.contrib.guidedtour.test.po.GuidedTourTestUtils;
import org.xwiki.test.integration.junit.LogCaptureConfiguration;
import org.xwiki.test.ui.TestUtils;

/**
 * Common setup of the guided tour functional tests: each test creates its own tours, and removes them afterwards, so
 * that they don't show up in the widget of the next tests. The browser storage is cleared after each test too, so
 * that no task left active is resumed on the next page load.
 *
 * @version $Id$
 */
abstract class AbstractGuidedTourIT
{
    protected GuidedTourTestUtils guidedTour;

    @BeforeAll
    void registerExpectedLogs(LogCaptureConfiguration logCaptureConfiguration)
    {
        GuidedTourTestUtils.registerExpectedLogs(logCaptureConfiguration);
    }

    @BeforeEach
    void setUp(TestUtils setup)
    {
        this.guidedTour = new GuidedTourTestUtils(setup);
    }

    @AfterEach
    void cleanUp() throws Exception
    {
        this.guidedTour.cleanUp();
    }
}
