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
package org.ehrbase.rest.openehr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.ehrbase.api.service.ExplanationOfBenefitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class OpenehrExplanationOfBenefitControllerTest {

    private final ExplanationOfBenefitService mockEobService = mock();
    private final OpenehrExplanationOfBenefitController spyController =
            spy(new OpenehrExplanationOfBenefitController(mockEobService));

    @BeforeEach
    void setUp() {
        Mockito.reset(mockEobService);
    }

    @Test
    void searchByPatient_withPatientId_returns200WithBundle() {
        // Arrange
        String expectedBundle = "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":0}";
        doReturn(expectedBundle).when(mockEobService).searchByPatient("patient-123", null, null);

        // Act
        ResponseEntity<String> response =
                spyController.searchByPatient("application/fhir+json", "patient-123", null, null);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedBundle);
        verify(mockEobService).searchByPatient("patient-123", null, null);
    }

    @Test
    void searchByPatient_withDateRange_delegatesToService() {
        // Arrange
        String expectedBundle = "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":1}";
        doReturn(expectedBundle).when(mockEobService).searchByPatient("patient-123", "2024-01-01", "2024-12-31");

        // Act
        ResponseEntity<String> response =
                spyController.searchByPatient("application/fhir+json", "patient-123", "2024-01-01", "2024-12-31");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedBundle);
        verify(mockEobService).searchByPatient("patient-123", "2024-01-01", "2024-12-31");
    }

    @Test
    void readById_withValidId_returns200WithEob() {
        // Arrange
        String expectedEob =
                "{\"resourceType\":\"ExplanationOfBenefit\",\"id\":\"ehr-1::comp-1\",\"status\":\"active\"}";
        doReturn(expectedEob).when(mockEobService).readById("ehr-1::comp-1");

        // Act
        ResponseEntity<String> response = spyController.readById("application/fhir+json", "ehr-1::comp-1");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedEob);
        verify(mockEobService).readById("ehr-1::comp-1");
    }

    @Test
    void metadata_returns200WithCapabilityStatement() {
        // Arrange
        String expectedCs = "{\"resourceType\":\"CapabilityStatement\",\"status\":\"active\"}";
        doReturn(expectedCs).when(mockEobService).getCapabilityStatement();

        // Act
        ResponseEntity<String> response = spyController.metadata("application/fhir+json");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedCs);
        verify(mockEobService).getCapabilityStatement();
    }
}
