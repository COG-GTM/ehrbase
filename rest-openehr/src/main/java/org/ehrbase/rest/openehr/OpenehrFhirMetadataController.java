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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Objects;
import org.ehrbase.api.service.ExplanationOfBenefitService;
import org.ehrbase.rest.BaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnMissingBean(name = "primaryopenehrfhirmetadatacontroller")
@ConditionalOnProperty(name = "ehrbase.rest.fhir.enabled", havingValue = "true")
@RestController
@RequestMapping(
        path = BaseController.FHIR_API_CONTEXT_PATH + "/metadata",
        produces = {"application/fhir+json", APPLICATION_JSON_VALUE})
public class OpenehrFhirMetadataController extends BaseController {

    private final ExplanationOfBenefitService eobService;

    @Autowired
    public OpenehrFhirMetadataController(ExplanationOfBenefitService eobService) {
        this.eobService = Objects.requireNonNull(eobService);
    }

    @GetMapping
    public ResponseEntity<String> metadata(@RequestHeader(value = ACCEPT, required = false) String accept) {
        String capabilityStatement = eobService.getCapabilityStatement();
        return ResponseEntity.ok(capabilityStatement);
    }
}
