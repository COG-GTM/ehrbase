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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.ehrbase.api.exception.StateConflictException;
import org.ehrbase.api.service.StoredQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
class BillingQueryInitializerTest {

    @Test
    void seedBillingQueries_callsCreateForEachQuery() throws Exception {
        // Arrange
        StoredQueryService mockService = mock(StoredQueryService.class);
        ResourcePatternResolver mockResolver = mock(ResourcePatternResolver.class);

        Resource[] resources = new Resource[] {
            mockResource("billing__diagnoses.aql", "SELECT 1"),
            mockResource("billing__procedures.aql", "SELECT 2"),
            mockResource("billing__medications.aql", "SELECT 3"),
            mockResource("billing__encounters.aql", "SELECT 4"),
            mockResource("billing__patient_summary.aql", "SELECT 5")
        };
        when(mockResolver.getResources("classpath:billing-queries/*.aql")).thenReturn(resources);

        BillingQueryInitializer initializer = new BillingQueryInitializer(mockService, mockResolver);

        // Act
        initializer.seedBillingQueries();

        // Assert
        verify(mockService, times(5)).createStoredQuery(anyString(), eq("1.0.0"), anyString(), eq("AQL"));
        verify(mockService).createStoredQuery(eq("billing::diagnoses"), eq("1.0.0"), eq("SELECT 1"), eq("AQL"));
        verify(mockService).createStoredQuery(eq("billing::procedures"), eq("1.0.0"), eq("SELECT 2"), eq("AQL"));
        verify(mockService).createStoredQuery(eq("billing::medications"), eq("1.0.0"), eq("SELECT 3"), eq("AQL"));
        verify(mockService).createStoredQuery(eq("billing::encounters"), eq("1.0.0"), eq("SELECT 4"), eq("AQL"));
        verify(mockService).createStoredQuery(eq("billing::patient_summary"), eq("1.0.0"), eq("SELECT 5"), eq("AQL"));
    }

    @Test
    void seedBillingQueries_idempotent_catchesStateConflict() throws Exception {
        // Arrange
        StoredQueryService mockService = mock(StoredQueryService.class);
        ResourcePatternResolver mockResolver = mock(ResourcePatternResolver.class);

        Resource[] resources = new Resource[] {
            mockResource("billing__diagnoses.aql", "SELECT 1"),
            mockResource("billing__procedures.aql", "SELECT 2"),
            mockResource("billing__medications.aql", "SELECT 3")
        };
        when(mockResolver.getResources("classpath:billing-queries/*.aql")).thenReturn(resources);

        // First query throws StateConflictException
        when(mockService.createStoredQuery(eq("billing::diagnoses"), anyString(), anyString(), anyString()))
                .thenThrow(new StateConflictException("Version already exists"));

        BillingQueryInitializer initializer = new BillingQueryInitializer(mockService, mockResolver);

        // Act
        initializer.seedBillingQueries();

        // Assert - all three queries were attempted despite the first one throwing
        verify(mockService, times(3)).createStoredQuery(anyString(), eq("1.0.0"), anyString(), eq("AQL"));
        verify(mockService).createStoredQuery(eq("billing::procedures"), eq("1.0.0"), eq("SELECT 2"), eq("AQL"));
        verify(mockService).createStoredQuery(eq("billing::medications"), eq("1.0.0"), eq("SELECT 3"), eq("AQL"));
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
