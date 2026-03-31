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

import java.util.Map;
import org.ehrbase.api.service.BillingCodeValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class OpenehrBillingValidationControllerTest {

    private static final String CONTEXT_PATH = "https://test.billing.controller/ehrbase/rest";

    private final BillingCodeValidationService mockService = mock();
    private final OpenehrBillingValidationController spyController =
            spy(new OpenehrBillingValidationController(mockService));

    private OpenehrBillingValidationController controller() {
        doReturn(CONTEXT_PATH).when(spyController).getContextPath();
        return spyController;
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(mockService);
    }

    @Test
    void postValidCode_returnsValid() {
        doReturn(true).when(mockService).validateCode("http://hl7.org/fhir/sid/icd-10-cm", "A01.0");

        ResponseEntity<Map<String, Object>> response = controller()
                .validateBillingCodePost(
                        MediaType.APPLICATION_JSON_VALUE,
                        Map.of("code_system", "http://hl7.org/fhir/sid/icd-10-cm", "code", "A01.0"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("valid")).isEqualTo(true);
    }

    @Test
    void postInvalidCode_returnsInvalidWithMessage() {
        doReturn(false).when(mockService).validateCode("http://hl7.org/fhir/sid/icd-10-cm", "INVALID");

        ResponseEntity<Map<String, Object>> response = controller()
                .validateBillingCodePost(
                        MediaType.APPLICATION_JSON_VALUE,
                        Map.of("code_system", "http://hl7.org/fhir/sid/icd-10-cm", "code", "INVALID"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("valid")).isEqualTo(false);
        assertThat(response.getBody().get("message")).isNotNull();
    }

    @Test
    void getValidCode_returnsValid() {
        doReturn(true).when(mockService).validateCode("http://www.ama-assn.org/go/cpt", "99213");

        ResponseEntity<Map<String, Object>> response = controller()
                .validateBillingCodeGet(MediaType.APPLICATION_JSON_VALUE, "http://www.ama-assn.org/go/cpt", "99213");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("valid")).isEqualTo(true);
    }

    @Test
    void getInvalidCode_returnsInvalidWithMessage() {
        doReturn(false).when(mockService).validateCode("http://www.ama-assn.org/go/cpt", "INVALID");

        ResponseEntity<Map<String, Object>> response = controller()
                .validateBillingCodeGet(MediaType.APPLICATION_JSON_VALUE, "http://www.ama-assn.org/go/cpt", "INVALID");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("valid")).isEqualTo(false);
        assertThat(response.getBody().get("message")).isNotNull();
    }
}
