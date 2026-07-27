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
package org.xwiki.contrib.guidedtour.internal.util;

import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;
import org.xwiki.query.internal.DefaultQuery;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link QueryUtil}.
 */
@ComponentTest
class QueryUtilTest
{
    private static final String QUERY_STRING = "select doc.fullName from XWikiDocument doc";

    private final List<DocumentReference> result = List.of(
        new DocumentReference("wiki", List.of("Space"), "Page1"),
        new DocumentReference("wiki", List.of("Space"), "Page2")
    );

    @InjectMockComponents
    private QueryUtil queryUtil;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    @Named("document")
    private QueryFilter documentFilter;

    @MockComponent
    @Named("viewable")
    private QueryFilter viewableFilter;

    @Mock
    private DefaultQuery query;

    @BeforeEach
    void setup() throws QueryException
    {
        when(this.queryManager.createQuery(QUERY_STRING, Query.HQL)).thenReturn(this.query);
        when(this.query.bindValue("param", "value")).thenReturn(this.query);
        when(this.query.addFilter(this.documentFilter)).thenReturn(this.query);
        when(this.query.addFilter(this.viewableFilter)).thenReturn(this.query);
        doReturn(this.result).when(this.query).execute();
    }

    @Test
    void executeQueryWithBindings() throws QueryException
    {
        List<DocumentReference> docs = this.queryUtil.executeQuery(QUERY_STRING, Map.of("param", "value"));
        assertEquals(this.result, docs);
        verify(this.query).bindValue("param", "value");
        verify(this.query).addFilter(this.documentFilter);
        verify(this.query).addFilter(this.viewableFilter);
    }

    @Test
    void executeQueryWithoutBindings() throws QueryException
    {
        List<DocumentReference> docs = this.queryUtil.executeQuery(QUERY_STRING, null);
        assertEquals(this.result, docs);
        verify(this.query).addFilter(this.documentFilter);
        verify(this.query).addFilter(this.viewableFilter);
    }
}
