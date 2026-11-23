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
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;

/**
 * Utility class to execute HQL queries.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = QueryUtil.class)
@Singleton
public class QueryUtil
{
    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("document")
    private QueryFilter documentFilter;

    @Inject
    @Named("viewable")
    private QueryFilter viewableFilter;

    /**
     * Executes an HQL query with the given statement and bound parameters and returns the results as a list of
     * {@link DocumentReference}s. The "document" filter resolves the first column (document full name) into a
     * {@link DocumentReference}, and the "viewable" filter removes results the current user does not have the right to
     * view.
     *
     * @param queryString the HQL statement to execute
     * @param bindValues the parameters to bind to the query, can be null or empty if no parameters are needed
     * @return the results of the query as a list of {@link DocumentReference}s
     * @throws QueryException if there is an error executing the query
     */
    public List<DocumentReference> executeQuery(String queryString, Map<String, Object> bindValues)
        throws QueryException
    {
        return executeQuery(queryString, query -> {
            if (bindValues != null) {
                bindValues.forEach(query::bindValue);
            }
        });
    }

    /**
     * Executes an HQL query and returns the results as a list of {@link DocumentReference}s, like
     * {@link #executeQuery(String, Map)}, but lets the caller bind the parameters on the {@link Query}. This is needed
     * to bind a LIKE parameter with {@link Query#bindValue(String)}, which escapes its literal parts.
     *
     * @param queryString the HQL statement to execute
     * @param binder binds the parameters of the query
     * @return the results of the query as a list of {@link DocumentReference}s
     * @throws QueryException if there is an error executing the query
     */
    public List<DocumentReference> executeQuery(String queryString, Consumer<Query> binder) throws QueryException
    {
        Query query = this.queryManager.createQuery(queryString, Query.HQL);
        binder.accept(query);
        query.addFilter(this.documentFilter);
        query.addFilter(this.viewableFilter);
        return query.execute();
    }
}
