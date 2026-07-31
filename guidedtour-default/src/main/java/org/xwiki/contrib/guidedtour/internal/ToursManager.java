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
package org.xwiki.contrib.guidedtour.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.guidedtour.api.dtos.TourDTO;
import org.xwiki.contrib.guidedtour.api.enums.TourProperty;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.contrib.guidedtour.internal.util.QueryUtil;
import org.xwiki.job.JobException;
import org.xwiki.job.JobExecutor;
import org.xwiki.job.Request;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.QueryException;
import org.xwiki.refactoring.job.RefactoringJobs;
import org.xwiki.refactoring.script.RequestFactory;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.TOUR_CLASS;

/**
 * Manages the instance tours. It provides methods to create, retrieve, update and delete tours. Tours are stored as
 * XWiki documents with a TourClass object.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = ToursManager.class)
@Singleton
public class ToursManager
{
    private static final String GET_ALL_TOURS_QUERY = """
        select doc.fullName from XWikiDocument doc, BaseObject obj where doc.translation = 0 and doc.fullName = \
        obj.name and obj.className = :class and doc.name <> :excludeName""";

    private static final String EXCLUDE_NAME = "TourTemplate";

    @Inject
    private Provider<XWikiContext> wikiContextProvider;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private TasksManager tasksManager;

    @Inject
    private QueryUtil queryUtil;

    @Inject
    private JobExecutor jobExecutor;

    @Inject
    private RequestFactory requestFactory;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    /**
     * Creates a new tour based on the provided DTO. The tour is stored as an XWiki document with a TourClass object.
     *
     * @param tourDTO the DTO containing the tour information
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws DuplicatedIdException if a tour with the same ID already exists
     */
    public void createTour(TourDTO tourDTO) throws XWikiException, DuplicatedIdException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        DocumentReference targetDocRef = this.documentReferenceResolver.resolve(tourDTO.getId());
        XWikiDocument targetDoc = wiki.getDocument(targetDocRef, wikiContext);
        BaseObject tourClassObject = targetDoc.getXObject(TOUR_CLASS);
        if (tourClassObject == null) {
            tourClassObject = targetDoc.newXObject(TOUR_CLASS, wikiContext);
            tourClassObject.set(TourProperty.TITLE.getBaseKey(), tourDTO.getTitle(), wikiContext);
            tourClassObject.set(TourProperty.DESCRIPTION.getBaseKey(), tourDTO.getDescription(), wikiContext);
            tourClassObject.set(TourProperty.IS_ACTIVE.getBaseKey(), tourDTO.isActive() ? 1 : 0, wikiContext);
            targetDoc.addXObject(tourClassObject);
            wiki.saveDocument(targetDoc, "Tour created.", wikiContext);
        } else {
            throw new DuplicatedIdException("A tour with the same ID [%s] already exists.", tourDTO.getId());
        }
    }

    /**
     * Retrieves all tours. It executes a HQL query to get all documents with a TourClass object, excluding the
     * template, and maps the results to a list of TourDTOs.
     *
     * @return a JSON string representing the list of tours
     * @throws QueryException if there is an error while executing the HQL query
     * @throws XWikiException if there is an error while interacting with the XWiki API
     */
    public List<TourDTO> getAllTours() throws QueryException, XWikiException, InvalidIdException
    {
        Map<String, Object> parameters =
            Map.of("excludeName", EXCLUDE_NAME, "class", this.localSerializer.serialize(TOUR_CLASS));
        List<DocumentReference> docRefs = this.queryUtil.executeQuery(GET_ALL_TOURS_QUERY, parameters);
        List<TourDTO> tours = new ArrayList<>(docRefs.size());
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        for (DocumentReference documentReference : docRefs) {
            XWikiDocument doc = wiki.getDocument(documentReference, wikiContext);
            BaseObject tourObj = doc.getXObject(TOUR_CLASS);
            String title = tourObj.getStringValue(TourProperty.TITLE.getBaseKey());
            boolean isActive = tourObj.getIntValue(TourProperty.IS_ACTIVE.getBaseKey()) == 1;
            String description = tourObj.getStringValue(TourProperty.DESCRIPTION.getBaseKey());
            TourDTO dto = new TourDTO(documentReference.toString(), title, isActive, description);
            dto.setTasks(this.tasksManager.getAllTasks(documentReference.toString()));
            tours.add(dto);
        }
        return tours;
    }

    /**
     * Updates an existing tour based on the provided DTO. It retrieves the corresponding XWiki document and updates the
     * TourClass object with the new information from the DTO.
     *
     * @param tourDTO the DTO containing the updated tour information
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws InvalidIdException if a tour with the given ID does not exist
     */
    public void updateTour(TourDTO tourDTO) throws XWikiException, InvalidIdException
    {
        BaseObject tourClassObject = getTourClassObject(tourDTO.getId());
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        tourClassObject.set(TourProperty.TITLE.getBaseKey(), tourDTO.getTitle(), wikiContext);
        tourClassObject.set(TourProperty.DESCRIPTION.getBaseKey(), tourDTO.getDescription(), wikiContext);
        tourClassObject.set(TourProperty.IS_ACTIVE.getBaseKey(), tourDTO.isActive() ? 1 : 0, wikiContext);
        XWikiDocument tourDoc = tourClassObject.getOwnerDocument();
        tourDoc.setTitle(tourDTO.getTitle());
        wiki.saveDocument(tourDoc, "Updated tour object.", wikiContext);
    }

    /**
     * Deletes an existing tour based on the provided ID. It retrieves the corresponding XWiki document and deletes the
     * entire tour space using a Refactoring Job to ensure that all the related documents (tasks and steps) are part of
     * the same batch.
     *
     * @param tourId the ID of the tour to be deleted
     * @throws XWikiException if there is an error while interacting with the XWiki API
     * @throws JobException if there is an error while executing the Refactoring Job
     * @throws InvalidIdException if a tour with the given ID does not exist
     */
    public void deleteTour(String tourId) throws XWikiException, JobException, InvalidIdException
    {
        getTourClassObject(tourId);
        DocumentReference targetDocRef = this.documentReferenceResolver.resolve(tourId);
        Request deleteReq = this.requestFactory.createDeleteRequest(List.of(targetDocRef.getLastSpaceReference()));
        this.jobExecutor.execute(RefactoringJobs.DELETE, deleteReq);
    }

    private BaseObject getTourClassObject(String tourId) throws InvalidIdException, XWikiException
    {
        XWikiContext wikiContext = this.wikiContextProvider.get();
        XWiki wiki = wikiContext.getWiki();
        DocumentReference targetDocRef = this.documentReferenceResolver.resolve(tourId);
        XWikiDocument targetDoc = wiki.getDocument(targetDocRef, wikiContext);
        BaseObject tourClassObject = targetDoc.getXObject(TOUR_CLASS);
        if (tourClassObject == null) {
            throw new InvalidIdException("Tour with the given id [%s] does not exist.", tourId);
        }
        return tourClassObject;
    }
}
