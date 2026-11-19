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

import java.util.List;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ComponentTest
class DefaultGuidedTourConfigurationTest
{
    private static final String LINKS = """
        link1
        
        
        
        link2
        
        link3""";

    @InjectMockComponents
    private DefaultGuidedTourConfiguration defaultGuidedTourConfig;

    @MockComponent
    @Named("guidedtour.configuration.current")
    private ConfigurationSource mainConfiguration;

    @Mock
    private DocumentReference documentReference;

    @Test
    void getDefaultSpace()
    {
        when(this.mainConfiguration.getProperty("defaultSpace", DocumentReference.class)).thenReturn(
            this.documentReference);
        assertEquals(this.documentReference, this.defaultGuidedTourConfig.getDefaultSpace());
    }

    @Test
    void getUsefulLinks()
    {
        when(this.mainConfiguration.getProperty("usefulLinks", "")).thenReturn(LINKS);
        assertEquals(List.of("link1", "link2", "link3"), this.defaultGuidedTourConfig.getUsefulLinks());
    }
}
