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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.internal.AbstractDocumentConfigurationSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;

/**
 * Guided Tour configuration source corresponding to the current wiki.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named(GuidedTourConfigurationSource.HINT)
@Singleton
public class GuidedTourConfigurationSource extends AbstractDocumentConfigurationSource
{
    /**
     * The hint for this component.
     */
    public static final String HINT = "guidedtour.configuration.current";

    private static final List<String> SPACE = Arrays.asList("GuidedTour", "Code");

    private static final LocalDocumentReference CONFIG_DOC = new LocalDocumentReference(SPACE, "Configuration");

    private static final LocalDocumentReference CONFIG_CLASS = new LocalDocumentReference(SPACE, "ConfigurationClass");

    @Override
    protected LocalDocumentReference getClassReference()
    {
        return CONFIG_CLASS;
    }

    @Override
    protected DocumentReference getDocumentReference()
    {
        return new DocumentReference(CONFIG_DOC, this.getCurrentWikiReference());
    }

    @Override
    protected String getCacheId()
    {
        return HINT;
    }
}
