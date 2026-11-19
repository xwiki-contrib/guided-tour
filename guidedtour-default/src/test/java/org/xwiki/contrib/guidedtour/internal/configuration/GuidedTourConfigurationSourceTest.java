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
package org.xwiki.contrib.guidedtour.internal.configuration;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ComponentTest
class GuidedTourConfigurationSourceTest
{
    private static final List<String> SPACE = Arrays.asList("GuidedTour", "Code");

    private static final LocalDocumentReference CONFIG_CLASS = new LocalDocumentReference(SPACE, "ConfigurationClass");

    @InjectMockComponents
    private GuidedTourConfigurationSource guidedTourConfigurationSource;

    @Mock
    private WikiReference wikiReference;

    @Test
    void getClassReference()
    {
        assertEquals(CONFIG_CLASS, this.guidedTourConfigurationSource.getClassReference());
    }

    @Test
    void getCacheId()
    {
        assertEquals("guidedtour.configuration.current", this.guidedTourConfigurationSource.getCacheId());
    }
}
