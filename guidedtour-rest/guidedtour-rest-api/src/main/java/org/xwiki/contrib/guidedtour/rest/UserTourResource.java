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
package org.xwiki.contrib.guidedtour.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.contrib.guidedtour.api.dtos.UserTourStatusDTO;
import org.xwiki.rest.XWikiRestComponent;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.stability.Unstable;

/**
 * Exposes the user tour status through REST APIs.
 *
 * @version $Id$
 * @since 1.0
 */
@Unstable
@Path("/guidedTour/user")
public interface UserTourResource extends XWikiRestComponent
{
    /**
     * Access the user tour status.
     *
     * @return the user status and 200 status code if the user tour status exists, 404 if the object is not found, 401
     *     if the user lacks rights or if the CSRF token is invalid and 500 if any other error occurs
     * @throws XWikiRestException if any error occurs during the retrieval
     */
    @GET
    Response getUserTourStatus() throws XWikiRestException;

    /**
     * Create the user tour status.
     *
     * @return 201 status code if the object has been created successfully, 401 if the user lacks rights or if the CSRF
     *     token is invalid, 409 if the object already exists and 500 if any other error occurs
     * @throws XWikiRestException if any error occurs during the creation
     */
    @POST
    Response createUserTourStatus() throws XWikiRestException;

    /**
     * Update the user tour status.
     *
     * @param userTourStatus the new user tour status
     * @return 201 status code if the user tour status didn't exist and has been created, 200 if it has been updated,
     *     401 if the user lacks rights or if the CSRF token is invalid and 500 if any other error occurs
     * @throws XWikiRestException if any error occurs during the update
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateUserTourStatus(UserTourStatusDTO userTourStatus) throws XWikiRestException;
}
