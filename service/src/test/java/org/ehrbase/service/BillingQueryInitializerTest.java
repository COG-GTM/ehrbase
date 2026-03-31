/*
 * Copyright (c) 2024 vitasystems GmbH.
 *
 * This file is part of project EHRbase
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehrbase.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.jooq.DSLContext;
import org.jooq.InsertReturningStep;
import org.jooq.InsertSetMoreStep;
import org.jooq.InsertSetStep;
import org.jooq.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
class BillingQueryInitializerTest {

    @SuppressWarnings("unchecked")
    @Test
    void seedBillingQueries_insertsForEachQuery() throws Exception {
        // Arrange
        DSLContext mockContext = mock(DSLContext.class);
        ResourcePatternResolver mockResolver = mock(ResourcePatternResolver.class);

        InsertSetStep mockInsertStep = mock(InsertSetStep.class);
        InsertSetMoreStep mockMoreStep = mock(InsertSetMoreStep.class);
        InsertReturningStep mockReturningStep = mock(InsertReturningStep.class);

        when(mockContext.insertInto(any(Table.class))).thenReturn(mockInsertStep);
        when(mockInsertStep.set(any(org.jooq.Field.class), any(Object.class))).thenReturn(mockMoreStep);
        when(mockMoreStep.set(any(org.jooq.Field.class), any(Object.class))).thenReturn(mockMoreStep);
        when(mockMoreStep.onConflictDoNothing()).thenReturn(mockReturningStep);

        Resource[] resources = new Resource[] {
            mockResource("billing__diagnoses.aql", "SELECT 1"),
            mockResource("billing__procedures.aql", "SELECT 2"),
            mockResource("billing__medications.aql", "SELECT 3"),
            mockResource("billing__encounters.aql", "SELECT 4"),
            mockResource("billing__patient_summary.aql", "SELECT 5")
        };
        when(mockResolver.getResources("classpath:billing-queries/*.aql")).thenReturn(resources);

        BillingQueryInitializer initializer = new BillingQueryInitializer(mockContext, mockResolver);

        // Act
        initializer.seedBillingQueries();

        // Assert - insertInto called once per query
        verify(mockContext, times(5)).insertInto(any(Table.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void seedBillingQueries_continuesOnError() throws Exception {
        // Arrange
        DSLContext mockContext = mock(DSLContext.class);
        ResourcePatternResolver mockResolver = mock(ResourcePatternResolver.class);

        InsertSetStep mockInsertStep = mock(InsertSetStep.class);
        InsertSetMoreStep mockMoreStep = mock(InsertSetMoreStep.class);
        InsertReturningStep mockReturningStep = mock(InsertReturningStep.class);

        when(mockContext.insertInto(any(Table.class))).thenReturn(mockInsertStep);
        when(mockInsertStep.set(any(org.jooq.Field.class), any(Object.class))).thenReturn(mockMoreStep);
        when(mockMoreStep.set(any(org.jooq.Field.class), any(Object.class))).thenReturn(mockMoreStep);
        when(mockMoreStep.onConflictDoNothing()).thenReturn(mockReturningStep);
        // First call throws, rest succeed
        when(mockReturningStep.execute())
                .thenThrow(new org.jooq.exception.DataAccessException("duplicate"))
                .thenReturn(1)
                .thenReturn(1);

        Resource[] resources = new Resource[] {
            mockResource("billing__diagnoses.aql", "SELECT 1"),
            mockResource("billing__procedures.aql", "SELECT 2"),
            mockResource("billing__medications.aql", "SELECT 3")
        };
        when(mockResolver.getResources("classpath:billing-queries/*.aql")).thenReturn(resources);

        BillingQueryInitializer initializer = new BillingQueryInitializer(mockContext, mockResolver);

        // Act
        initializer.seedBillingQueries();

        // Assert - all three queries were attempted despite the first one throwing
        verify(mockContext, times(3)).insertInto(any(Table.class));
    }

    @Test
    void filenameToQualifiedName_conversion() {
        assertEquals("billing::diagnoses", BillingQueryInitializer.filenameToQualifiedName("billing__diagnoses.aql"));
        assertEquals(
                "billing::patient_summary",
                BillingQueryInitializer.filenameToQualifiedName("billing__patient_summary.aql"));
        assertEquals("billing::procedures", BillingQueryInitializer.filenameToQualifiedName("billing__procedures.aql"));
        assertEquals(
                "billing::medications", BillingQueryInitializer.filenameToQualifiedName("billing__medications.aql"));
        assertEquals("billing::encounters", BillingQueryInitializer.filenameToQualifiedName("billing__encounters.aql"));
    }

    private static Resource mockResource(String filename, String content) throws Exception {
        Resource resource = mock(Resource.class);
        when(resource.getFilename()).thenReturn(filename);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return resource;
    }
}
