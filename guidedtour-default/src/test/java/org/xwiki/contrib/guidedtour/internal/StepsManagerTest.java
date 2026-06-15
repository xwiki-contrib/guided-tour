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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.guidedtour.api.dtos.StepDTO;
import org.xwiki.contrib.guidedtour.api.enums.Placement;
import org.xwiki.contrib.guidedtour.api.enums.TourProperty;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import javax.inject.Named;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.STEP_CLASS;

/**
 * Test class for {@link StepsManager}.
 */
@ComponentTest
class StepsManagerTest
{
    private final StepDTO stepDTO = new StepDTO();

    @InjectMockComponents
    private StepsManager stepsManager;

    @MockComponent
    private Provider<XWikiContext> wikiContextProvider;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Mock
    private XWikiContext xwikiContext;

    @Mock
    private XWiki xwiki;

    @Mock
    private BaseObject stepObject1;

    @Mock
    private BaseObject stepObject2;

    @Mock
    private BaseObject stepObject3;

    @Mock
    private XWikiDocument taskDocument;

    @Mock
    private DocumentReference tourReference;

    @Mock
    private DocumentReference taskReference;

    @BeforeEach
    void setUp() throws XWikiException
    {
        when(this.wikiContextProvider.get()).thenReturn(this.xwikiContext);
        when(this.xwikiContext.getWiki()).thenReturn(this.xwiki);
        when(this.documentReferenceResolver.resolve("testTour")).thenReturn(this.tourReference);
        when(this.documentReferenceResolver.resolve("testTask", this.tourReference)).thenReturn(this.taskReference);
        when(this.xwiki.exists(this.taskReference, this.xwikiContext)).thenReturn(true);
        when(this.xwiki.exists(this.tourReference, this.xwikiContext)).thenReturn(true);
        when(this.xwiki.getDocument(this.taskReference, this.xwikiContext)).thenReturn(this.taskDocument);

        List<BaseObject> steps = new ArrayList<>();
        steps.add(this.stepObject1);
        steps.add(this.stepObject2);
        steps.add(null);
        steps.add(this.stepObject3);
        when(this.taskDocument.getXObjects(STEP_CLASS)).thenReturn(steps);
        when(this.taskDocument.newXObject(STEP_CLASS, this.xwikiContext)).thenReturn(this.stepObject1);

        when(this.stepObject1.getIntValue(TourProperty.ORDER.getBaseKey())).thenReturn(1);
        when(this.stepObject2.getIntValue(TourProperty.ORDER.getBaseKey())).thenReturn(2);
        when(this.stepObject3.getIntValue(TourProperty.ORDER.getBaseKey())).thenReturn(3);

        when(this.stepObject1.getStringValue(TourProperty.CONTENT.getBaseKey())).thenReturn("content for step 1");
        when(this.stepObject2.getStringValue(TourProperty.CONTENT.getBaseKey())).thenReturn("content for step 2");
        when(this.stepObject3.getStringValue(TourProperty.CONTENT.getBaseKey())).thenReturn("content for step 3");

        when(this.stepObject1.getStringValue(TourProperty.ELEMENT.getBaseKey())).thenReturn("element1");
        when(this.stepObject2.getStringValue(TourProperty.ELEMENT.getBaseKey())).thenReturn("element2");
        when(this.stepObject3.getStringValue(TourProperty.ELEMENT.getBaseKey())).thenReturn("element3");

        when(this.stepObject1.getStringValue(TourProperty.PLACEMENT.getBaseKey())).thenReturn("BOTTOM_START");
        when(this.stepObject2.getStringValue(TourProperty.PLACEMENT.getBaseKey())).thenReturn("TOP_END");
        when(this.stepObject3.getStringValue(TourProperty.PLACEMENT.getBaseKey())).thenReturn("LEFT_CENTER");

        when(this.stepObject1.getIntValue(TourProperty.BACKDROP.getBaseKey())).thenReturn(1);
        when(this.stepObject2.getIntValue(TourProperty.BACKDROP.getBaseKey())).thenReturn(1);
        when(this.stepObject3.getIntValue(TourProperty.BACKDROP.getBaseKey())).thenReturn(0);

        when(this.stepObject1.getIntValue(TourProperty.REFLEX.getBaseKey())).thenReturn(0);
        when(this.stepObject2.getIntValue(TourProperty.REFLEX.getBaseKey())).thenReturn(1);
        when(this.stepObject3.getIntValue(TourProperty.REFLEX.getBaseKey())).thenReturn(0);

        when(this.stepObject1.getStringValue(TourProperty.TARGET_PAGE.getBaseKey())).thenReturn("tp1");
        when(this.stepObject2.getStringValue(TourProperty.TARGET_PAGE.getBaseKey())).thenReturn("tp2");
        when(this.stepObject3.getStringValue(TourProperty.TARGET_PAGE.getBaseKey())).thenReturn("");

        when(this.stepObject1.getStringValue(TourProperty.TARGET_ACTION.getBaseKey())).thenReturn("");
        when(this.stepObject2.getStringValue(TourProperty.TARGET_ACTION.getBaseKey())).thenReturn("");
        when(this.stepObject3.getStringValue(TourProperty.TARGET_ACTION.getBaseKey())).thenReturn("view");

        when(this.stepObject1.getStringValue(TourProperty.QUERY_PARAMETERS.getBaseKey())).thenReturn("param=value");
        when(this.stepObject2.getStringValue(TourProperty.QUERY_PARAMETERS.getBaseKey())).thenReturn("");
        when(this.stepObject3.getStringValue(TourProperty.QUERY_PARAMETERS.getBaseKey())).thenReturn("");

        this.stepDTO.setContent("content for step 4");
        this.stepDTO.setElement("element4");
        this.stepDTO.setPlacement("RIGHT_END");
        this.stepDTO.setBackdrop(true);
        this.stepDTO.setReflex(false);
        this.stepDTO.setTargetPage("tp4");
        this.stepDTO.setTargetAction("edit");
        this.stepDTO.setQueryParameters("param=value");
        this.stepDTO.setOrder(3);
    }

    @Test
    void getSteps() throws XWikiException, InvalidIdException
    {
        List<StepDTO> steps = this.stepsManager.getAllSteps("testTour", "testTask");
        assertEquals(3, steps.size());
        assertEquals("content for step 1", steps.get(0).getContent());
        assertEquals("element2", steps.get(1).getElement());
        assertEquals(Placement.LEFT_CENTER, steps.get(2).getPlacement());
    }

    @Test
    void getStepsInvalidTour()
    {
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            this.stepsManager.getAllSteps("testTourInv", "testTask");
        });

        assertEquals(String.format("Tour with the given id [%s] does not exists.", "testTourInv"),
            exception.getMessage());
    }

    @Test
    void getStepsInvalidTask()
    {
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            this.stepsManager.getAllSteps("testTour", "testTaskInv");
        });

        assertEquals(String.format("Task with the given id [%s] does not exists.", "testTaskInv"),
            exception.getMessage());
    }

    @Test
    void createStep() throws XWikiException, DuplicatedIdException, InvalidIdException
    {
        this.stepDTO.setOrder(-1);
        this.stepsManager.createStep("testTour", "testTask", this.stepDTO);
        verify(this.stepObject1, times(1)).set(TourProperty.PLACEMENT.getBaseKey(), Placement.RIGHT_END,
            this.xwikiContext);
        verify(this.stepObject1, times(1)).set(TourProperty.BACKDROP.getBaseKey(), 1, this.xwikiContext);
        verify(this.stepObject1, times(1)).set(TourProperty.REFLEX.getBaseKey(), 0, this.xwikiContext);
        verify(this.stepObject1, times(1)).set(TourProperty.ORDER.getBaseKey(), 4, this.xwikiContext);
    }

    @Test
    void createStepDuplicate()
    {
        DuplicatedIdException exception = assertThrows(DuplicatedIdException.class, () -> {
            this.stepsManager.createStep("testTour", "testTask", this.stepDTO);
        });

        assertEquals("A step with the given order [3] already exists.", exception.getMessage());
    }

    @Test
    void updateStepSameOrder() throws XWikiException, InvalidIdException
    {
        this.stepsManager.updateStep("testTour", "testTask", 3, this.stepDTO);
        verify(this.stepObject3, times(1)).set(TourProperty.PLACEMENT.getBaseKey(), Placement.RIGHT_END,
            this.xwikiContext);
        verify(this.stepObject3, times(1)).set(TourProperty.BACKDROP.getBaseKey(), 1, this.xwikiContext);
        verify(this.stepObject3, times(1)).set(TourProperty.REFLEX.getBaseKey(), 0, this.xwikiContext);
    }

    @Test
    void updateStepDifferentOrder() throws XWikiException, InvalidIdException
    {
        this.stepDTO.setOrder(1);

        this.stepsManager.updateStep("testTour", "testTask", 3, this.stepDTO);
        verify(this.stepObject3, times(1)).set(TourProperty.PLACEMENT.getBaseKey(), Placement.RIGHT_END,
            this.xwikiContext);
        verify(this.stepObject3, times(1)).set(TourProperty.BACKDROP.getBaseKey(), 1, this.xwikiContext);
        verify(this.stepObject3, times(1)).set(TourProperty.REFLEX.getBaseKey(), 0, this.xwikiContext);
        verify(this.stepObject3, times(1)).set(TourProperty.ORDER.getBaseKey(), 1, this.xwikiContext);
        verify(this.stepObject1, times(1)).set(TourProperty.ORDER.getBaseKey(), 2, this.xwikiContext);
        verify(this.stepObject2, times(1)).set(TourProperty.ORDER.getBaseKey(), 3, this.xwikiContext);
    }

    @Test
    void updateStepInvalidOrder()
    {
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            this.stepsManager.updateStep("testTour", "testTask", 5, this.stepDTO);
        });

        assertEquals("No step was found on the given order position [5].", exception.getMessage());
    }

    @Test
    void deleteStep() throws XWikiException, InvalidIdException
    {
        when(this.stepObject2.getOwnerDocument()).thenReturn(this.taskDocument);

        this.stepsManager.deleteStep("testTour", "testTask", 2);
        verify(this.stepObject3, times(1)).set(TourProperty.ORDER.getBaseKey(), 2, this.xwikiContext);
        verify(this.stepObject1, times(0)).set(eq(TourProperty.ORDER.getBaseKey()), any(), any(XWikiContext.class));
        verify(this.xwiki, times(1)).saveDocument(this.taskDocument, "Removed step 2.", this.xwikiContext);
    }
}
