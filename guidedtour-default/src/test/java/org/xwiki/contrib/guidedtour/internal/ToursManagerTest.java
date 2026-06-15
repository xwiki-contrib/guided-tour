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

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.guidedtour.api.dtos.TourDTO;
import org.xwiki.contrib.guidedtour.api.enums.TourProperty;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.contrib.guidedtour.internal.util.SolrQueryUtil;
import org.xwiki.job.JobExecutor;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.refactoring.job.EntityRequest;
import org.xwiki.refactoring.job.RefactoringJobs;
import org.xwiki.refactoring.script.RequestFactory;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import javax.inject.Named;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.TOUR_CLASS;

/**
 * Test class for {@link ToursManager}.
 */
@ComponentTest
class ToursManagerTest
{
    private static final String CLASS_PREFIX = "property.XWiki.GuidedTour.TourClass.%s";

    private static final String TOUR_ID = "tourId";

    private final SolrDocumentList solrDocumentList = new SolrDocumentList();

    private final TourDTO tourDTO = new TourDTO(TOUR_ID, "dto Title", true);

    private final TourDTO tourDTOUpdated = new TourDTO(TOUR_ID, "updated title", false);

    @InjectMockComponents
    private ToursManager toursManager;

    @MockComponent
    private Provider<XWikiContext> wikiContextProvider;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    private DocumentReferenceResolver<SolrDocument> solrDocumentReferenceResolver;

    @MockComponent
    private TasksManager tasksManager;

    @MockComponent
    private SolrQueryUtil queryUtil;

    @MockComponent
    private JobExecutor jobExecutor;

    @MockComponent
    private RequestFactory requestFactory;

    @Mock
    private XWikiContext wikiContext;

    @Mock
    private XWiki xwiki;

    @Mock
    private DocumentReference documentReference;

    @Mock
    private XWikiDocument xwikiDocument;

    @Mock
    private BaseObject baseObject;

    @Mock
    private SolrDocument solrDocument;

    @Mock
    private SpaceReference spaceReference;

    @Mock
    private EntityRequest deleteReq;

    @BeforeEach
    void setup() throws XWikiException
    {
        when(this.wikiContextProvider.get()).thenReturn(this.wikiContext);
        when(this.wikiContext.getWiki()).thenReturn(this.xwiki);
        when(this.documentReferenceResolver.resolve(TOUR_ID)).thenReturn(this.documentReference);
        when(this.xwiki.getDocument(this.documentReference, this.wikiContext)).thenReturn(this.xwikiDocument);
        when(this.documentReference.getName()).thenReturn(TOUR_ID);
        when(this.xwikiDocument.newXObject(TOUR_CLASS, this.wikiContext)).thenReturn(this.baseObject);
        when(this.xwikiDocument.getXObject(TOUR_CLASS)).thenReturn(this.baseObject);
        this.solrDocumentList.add(this.solrDocument);
    }

    @Test
    void createTour() throws XWikiException, DuplicatedIdException
    {
        when(this.xwikiDocument.getXObject(TOUR_CLASS)).thenReturn(null);

        this.toursManager.createTour(this.tourDTO);
        verify(this.baseObject, times(1)).set("title", this.tourDTO.getTitle(), this.wikiContext);
        verify(this.xwiki, times(1)).saveDocument(this.xwikiDocument, "Tour created.", this.wikiContext);
    }

    @Test
    void createTourDuplicate()
    {
        DuplicatedIdException exception = assertThrows(DuplicatedIdException.class, () -> {
            this.toursManager.createTour(this.tourDTO);
        });
        assertEquals(exception.getMessage(),
            String.format("A tour with the same ID [%s] already exists.", this.tourDTO.getId()));
    }

    @Test
    void getAllTours() throws Exception
    {
        when(this.queryUtil.executeQuery("class:XWiki.GuidedTour.TourClass",
            "{!q.op=AND} type:DOCUMENT AND -name:TourTemplate",
            List.of(TourProperty.TITLE.formKey(CLASS_PREFIX), TourProperty.IS_ACTIVE.formKey(CLASS_PREFIX)),
            "")).thenReturn(this.solrDocumentList);
        when(this.solrDocument.getFirstValue("property.XWiki.GuidedTour.TourClass.title_string")).thenReturn(
            "tour title");
        when(this.solrDocument.getFirstValue("property.XWiki.GuidedTour.TourClass.isActive_boolean")).thenReturn(true);
        when(this.solrDocumentReferenceResolver.resolve(this.solrDocument, EntityType.DOCUMENT)).thenReturn(
            this.documentReference);
        when(this.tasksManager.getAllTasks(this.documentReference.toString())).thenReturn(new ArrayList<>());

        List<TourDTO> tours = this.toursManager.getAllTours();
        assertEquals(1, tours.size());
        assertEquals("tour title", tours.get(0).getTitle());
        assertTrue(tours.get(0).isActive());
        assertTrue(tours.get(0).getTasksList().isEmpty());
    }

    @Test
    void updateTour() throws Exception
    {
        when(this.baseObject.getOwnerDocument()).thenReturn(this.xwikiDocument);
        this.toursManager.updateTour(this.tourDTOUpdated);
        verify(this.baseObject, times(1)).set("title", this.tourDTOUpdated.getTitle(), this.wikiContext);
        verify(this.baseObject, times(1)).set("isActive", 0, this.wikiContext);
        verify(this.xwiki, times(1)).saveDocument(this.xwikiDocument, "Updated tour object.", this.wikiContext);
    }

    @Test
    void updateTourInvalidId()
    {
        when(this.xwikiDocument.getXObject(TOUR_CLASS)).thenReturn(null);
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            this.toursManager.updateTour(this.tourDTOUpdated);
        });
        assertEquals(exception.getMessage(), String.format("Tour with the given id [%s] does not exist.", TOUR_ID));
    }

    @Test
    void deleteTour() throws Exception
    {
        when(this.documentReference.getLastSpaceReference()).thenReturn(this.spaceReference);
        when(this.requestFactory.createDeleteRequest(
            List.of(this.documentReference.getLastSpaceReference()))).thenReturn(this.deleteReq);
        this.toursManager.deleteTour(TOUR_ID);
        verify(this.jobExecutor, times(1)).execute(RefactoringJobs.DELETE, this.deleteReq);
    }

    @Test
    void deleteTourInvalidId()
    {
        when(this.xwikiDocument.getXObject(TOUR_CLASS)).thenReturn(null);
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            this.toursManager.deleteTour(TOUR_ID);
        });
        assertEquals(exception.getMessage(), String.format("Tour with the given id [%s] does not exist.", TOUR_ID));
    }
}
