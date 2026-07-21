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
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.guidedtour.api.enums.TourProperty;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.query.SecureQuery;

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
        Query query = this.queryManager.createQuery(qs, "solr");
        query.bindValue("fq", fq);
        query.bindValue("fl", filteredLines);
        query.bindValue("group", true).bindValue("group.field", "fullname").bindValue("group.main", true);
        if (StringUtils.isNotBlank(sort)) {
            query.bindValue("sort", sort);
        }
        // Respect the view rights of the current user.
        ((SecureQuery) query).checkCurrentUser(true);

        return ((QueryResponse) query.execute().get(0)).getResults();
    }

    /**
     * When installing Tour pages through the EM, the boolean fields are indexed as int, so two properties are needed
     * for the bool fields. This method will check both fields, and return the value as a boolean. If the value is
     * missing, it will return {@code false}.
     *
     * @param document the document in which to check for the "isActive" field, as returned by a solr query
     * @param classPrefix the prefix to prepend to the property name, to get the right property
     * @return boolean value of the isActive property, or {@code false} by default.
     */
    public static boolean getIsActiveProperty(SolrDocument document, String classPrefix)
    {
        Object boolField = document.getFirstValue(TourProperty.IS_ACTIVE_BOOL.formKey(classPrefix));
        if (boolField != null) {
            return (Boolean) boolField;
        }
        Object intField = document.getFirstValue(TourProperty.IS_ACTIVE_INT.formKey(classPrefix));
        if (intField != null) {
            return !Objects.equals(intField, 0);
        }
        return false;
    }
}
