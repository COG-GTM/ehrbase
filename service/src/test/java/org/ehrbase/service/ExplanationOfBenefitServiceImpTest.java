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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.ehrbase.api.exception.ObjectNotFoundException;
import org.ehrbase.api.service.AqlQueryService;
import org.ehrbase.api.service.BillingCodeValidationService;
import org.ehrbase.api.service.StoredQueryService;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExplanationOfBenefitServiceImpTest {

    @Mock
    private AqlQueryService aqlQueryService;

    @Mock
    private StoredQueryService storedQueryService;

    @Mock
    private BillingCodeValidationService billingCodeValidationService;

    private ExplanationOfBenefitServiceImp service;

    @BeforeEach
    void setUp() {
        service = new ExplanationOfBenefitServiceImp(aqlQueryService, storedQueryService, billingCodeValidationService);
    }

    @Test
    void searchByPatient_withValidPatient_returnsFhirBundle() {
        // Arrange
        setupStoredQueryMocks();
        setupEmptyQueryResults();

        // Act
        String result = service.searchByPatient("test-ehr-id", null, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("\"resourceType\":\"Bundle\"");
        assertThat(result).contains("\"type\":\"searchset\"");
    }

    @Test
    void searchByPatient_withDateRange_returnsFhirBundle() {
        // Arrange
        setupStoredQueryMocks();
        setupEmptyQueryResults();

        // Act
        String result = service.searchByPatient("test-ehr-id", "2024-01-01", "2024-12-31");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("\"resourceType\":\"Bundle\"");
    }

    @Test
    void searchByPatient_queryNotFound_returnsEmptyBundle() {
        // Arrange
        when(storedQueryService.retrieveStoredQuery(eq(BillingQueryCatalog.DIAGNOSES), eq(BillingQueryCatalog.VERSION)))
                .thenThrow(new ObjectNotFoundException("StoredQuery", "not found"));
        when(storedQueryService.retrieveStoredQuery(
                        eq(BillingQueryCatalog.PROCEDURES), eq(BillingQueryCatalog.VERSION)))
                .thenThrow(new ObjectNotFoundException("StoredQuery", "not found"));
        when(storedQueryService.retrieveStoredQuery(
                        eq(BillingQueryCatalog.ENCOUNTERS), eq(BillingQueryCatalog.VERSION)))
                .thenThrow(new ObjectNotFoundException("StoredQuery", "not found"));

        // Act
        String result = service.searchByPatient("test-ehr-id", null, null);

        // Assert
        assertThat(result).contains("\"resourceType\":\"Bundle\"");
        assertThat(result).contains("\"total\":0");
    }

    @Test
    void readById_withValidId_returnsFhirEob() {
        // Arrange
        setupDiagAndProcQueryMocks();
        setupEmptyQueryResults();

        // Act
        String result = service.readById("test-ehr-id::test-composition-uid");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("\"resourceType\":\"ExplanationOfBenefit\"");
        assertThat(result).contains("\"status\":\"active\"");
        assertThat(result).contains("\"use\":\"claim\"");
    }

    @Test
    void readById_withInvalidIdFormat_throwsException() {
        // Act & Assert
        assertThatThrownBy(() -> service.readById("invalid-id"))
                .isInstanceOf(ObjectNotFoundException.class)
                .hasMessageContaining("Invalid EOB ID format");
    }

    @Test
    void getCapabilityStatement_returnsValidCapabilityStatement() {
        // Act
        String result = service.getCapabilityStatement();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("\"resourceType\":\"CapabilityStatement\"");
        assertThat(result).contains("\"status\":\"active\"");
        assertThat(result).contains("ExplanationOfBenefit");
        assertThat(result).contains("EHRbase FHIR Billing Bridge");
    }

    @Test
    void searchByPatient_invalidCodesExcluded_returnsFilteredResults() {
        // Arrange
        setupStoredQueryMocks();

        QueryResultDto emptyResult = new QueryResultDto();
        emptyResult.setResultSet(Collections.emptyList());

        when(aqlQueryService.query(any())).thenReturn(emptyResult);

        // Act
        String result = service.searchByPatient("test-ehr-id", null, null);

        // Assert
        assertThat(result).contains("\"resourceType\":\"Bundle\"");
        assertThat(result).contains("\"total\":0");
    }

    private void setupDiagAndProcQueryMocks() {
        var diagQuery = new org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryDefinitionResultDto();
        diagQuery.setQueryText(
                "SELECT e/ehr_id/value as ehr_id FROM EHR e CONTAINS COMPOSITION c CONTAINS EVALUATION eval[openEHR-EHR-EVALUATION.problem_diagnosis.v1]");

        var procQuery = new org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryDefinitionResultDto();
        procQuery.setQueryText(
                "SELECT e/ehr_id/value as ehr_id FROM EHR e CONTAINS COMPOSITION c CONTAINS ACTION a[openEHR-EHR-ACTION.procedure.v1]");

        when(storedQueryService.retrieveStoredQuery(eq(BillingQueryCatalog.DIAGNOSES), eq(BillingQueryCatalog.VERSION)))
                .thenReturn(diagQuery);
        when(storedQueryService.retrieveStoredQuery(
                        eq(BillingQueryCatalog.PROCEDURES), eq(BillingQueryCatalog.VERSION)))
                .thenReturn(procQuery);
    }

    private void setupStoredQueryMocks() {
        var diagQuery = new org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryDefinitionResultDto();
        diagQuery.setQueryText(
                "SELECT e/ehr_id/value as ehr_id FROM EHR e CONTAINS COMPOSITION c CONTAINS EVALUATION eval[openEHR-EHR-EVALUATION.problem_diagnosis.v1]");

        var procQuery = new org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryDefinitionResultDto();
        procQuery.setQueryText(
                "SELECT e/ehr_id/value as ehr_id FROM EHR e CONTAINS COMPOSITION c CONTAINS ACTION a[openEHR-EHR-ACTION.procedure.v1]");

        var encQuery = new org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryDefinitionResultDto();
        encQuery.setQueryText("SELECT e/ehr_id/value as ehr_id FROM EHR e CONTAINS COMPOSITION c");

        when(storedQueryService.retrieveStoredQuery(eq(BillingQueryCatalog.DIAGNOSES), eq(BillingQueryCatalog.VERSION)))
                .thenReturn(diagQuery);
        when(storedQueryService.retrieveStoredQuery(
                        eq(BillingQueryCatalog.PROCEDURES), eq(BillingQueryCatalog.VERSION)))
                .thenReturn(procQuery);
        when(storedQueryService.retrieveStoredQuery(
                        eq(BillingQueryCatalog.ENCOUNTERS), eq(BillingQueryCatalog.VERSION)))
                .thenReturn(encQuery);
    }

    private void setupEmptyQueryResults() {
        QueryResultDto emptyResult = new QueryResultDto();
        emptyResult.setResultSet(Collections.emptyList());
        when(aqlQueryService.query(any())).thenReturn(emptyResult);
    }
}
