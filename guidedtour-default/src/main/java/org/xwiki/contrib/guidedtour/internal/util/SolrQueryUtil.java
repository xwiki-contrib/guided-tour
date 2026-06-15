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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.xwiki.component.annotation.Component;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

/**
 * Utility class to execute Solr queries.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = SolrQueryUtil.class)
@Singleton
public class SolrQueryUtil
{
    private static final String REFERENCE_KEY = "reference";

    private static final String WIKI_KEY = "wiki";

    private static final String SPACES_KEY = "spaces";

    private static final String NAME_KEY = "name";

    @Inject
    private QueryManager queryManager;

    /**
     * Executes a Solr query with the given parameters and returns the results as a {@link SolrDocumentList}.
     *
     * @param qs the query string to execute
     * @param fq the filter query to apply to the results
     * @param fl the list of fields to return in the results
     * @param sort the sorting criteria to apply to the results, can be null or empty if no sorting is needed
     * @return the results of the query as a {@link SolrDocumentList}
     * @throws QueryException if there is an error executing the query
     */
    public SolrDocumentList executeQuery(String qs, String fq, List<String> fl, String sort) throws QueryException
    {
        List<String> filteredLines = new ArrayList<>(fl);
        filteredLines.add(REFERENCE_KEY);
        filteredLines.add(WIKI_KEY);
        filteredLines.add(SPACES_KEY);
        filteredLines.add(NAME_KEY);
        Query query = queryManager.createQuery(qs, "solr");
        query.bindValue("fq", fq);
        query.bindValue("fl", filteredLines);
        query.bindValue("group", true).bindValue("group.field", "fullname").bindValue("group.main", true);
        if (sort != null && !sort.isEmpty()) {
            query.bindValue("sort", sort);
        }
        return ((QueryResponse) query.execute().get(0)).getResults();
    }
}
