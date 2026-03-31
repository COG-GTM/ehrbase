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
package org.ehrbase.service.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.jayway.jsonpath.internal.JsonContext;
import java.util.Set;
import org.ehrbase.openehr.sdk.validation.terminology.TerminologyParam;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BillingCodeValidationTest {

    @Test
    void additionalAcceptedApis_areIncludedInSupports() {
        String baseUrl = "http://terminology.local";
        Set<String> additionalApis = Set.of("//custom.billing.org");

        FhirTerminologyValidation validation = spy(new FhirTerminologyValidation(
                baseUrl, true, org.springframework.web.reactive.function.client.WebClient.create(), additionalApis));

        TerminologyParam param = TerminologyParam.ofFhir("//custom.billing.org/CodeSystem?url=http://example.org/cs");

        JsonContext jsonContext = mock(JsonContext.class);
        Mockito.when(jsonContext.read("$.total", int.class)).thenReturn(1);
        doReturn(jsonContext).when(validation).internalGet(Mockito.anyString());

        assertTrue(validation.supports(param));
    }

    @Test
    void billingApiPrefix_amaAssn_isAccepted() {
        String baseUrl = "http://terminology.local";
        Set<String> billingApis = Set.of("//www.ama-assn.org", "//www.cms.gov", "//terminology.hl7.org");

        FhirTerminologyValidation validation = spy(new FhirTerminologyValidation(
                baseUrl, true, org.springframework.web.reactive.function.client.WebClient.create(), billingApis));

        TerminologyParam param =
                TerminologyParam.ofFhir("//www.ama-assn.org/CodeSystem?url=http://www.ama-assn.org/go/cpt");

        JsonContext jsonContext = mock(JsonContext.class);
        Mockito.when(jsonContext.read("$.total", int.class)).thenReturn(1);
        doReturn(jsonContext).when(validation).internalGet(Mockito.anyString());

        assertTrue(validation.supports(param));
    }

    @Test
    void billingApiPrefix_terminologyHl7_isAccepted() {
        String baseUrl = "http://terminology.local";
        Set<String> billingApis = Set.of("//www.ama-assn.org", "//www.cms.gov", "//terminology.hl7.org");

        FhirTerminologyValidation validation = spy(new FhirTerminologyValidation(
                baseUrl, true, org.springframework.web.reactive.function.client.WebClient.create(), billingApis));

        TerminologyParam param =
                TerminologyParam.ofFhir("//terminology.hl7.org/CodeSystem?url=http://hl7.org/fhir/sid/icd-10-cm");

        JsonContext jsonContext = mock(JsonContext.class);
        Mockito.when(jsonContext.read("$.total", int.class)).thenReturn(1);
        doReturn(jsonContext).when(validation).internalGet(Mockito.anyString());

        assertTrue(validation.supports(param));
    }

    @Test
    void billingApiPrefix_cmsGov_isAccepted() {
        String baseUrl = "http://terminology.local";
        Set<String> billingApis = Set.of("//www.ama-assn.org", "//www.cms.gov", "//terminology.hl7.org");

        FhirTerminologyValidation validation = spy(new FhirTerminologyValidation(
                baseUrl, true, org.springframework.web.reactive.function.client.WebClient.create(), billingApis));

        TerminologyParam param = TerminologyParam.ofFhir(
                "//www.cms.gov/CodeSystem?url=http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets");

        JsonContext jsonContext = mock(JsonContext.class);
        Mockito.when(jsonContext.read("$.total", int.class)).thenReturn(1);
        doReturn(jsonContext).when(validation).internalGet(Mockito.anyString());

        assertTrue(validation.supports(param));
    }

    @Test
    void unsupportedApiPrefix_isRejected() {
        String baseUrl = "http://terminology.local";

        FhirTerminologyValidation validation = spy(new FhirTerminologyValidation(baseUrl));

        TerminologyParam param = TerminologyParam.ofFhir("//unknown.example.org/CodeSystem?url=http://example.org/cs");

        assertFalse(validation.supports(param));
    }

    @Test
    void fourArgConstructor_delegatesFromThreeArgConstructor() {
        FhirTerminologyValidation validation = spy(new FhirTerminologyValidation(
                "http://test.local", false, org.springframework.web.reactive.function.client.WebClient.create()));

        TerminologyParam param = TerminologyParam.ofFhir("//fhir.hl7.org/CodeSystem?url=http://example.org/cs");

        JsonContext jsonContext = mock(JsonContext.class);
        Mockito.when(jsonContext.read("$.total", int.class)).thenReturn(1);
        doReturn(jsonContext).when(validation).internalGet(Mockito.anyString());

        assertTrue(validation.supports(param));
    }
}
