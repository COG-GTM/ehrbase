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
package org.ehrbase.fhir.mapping.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps a FHIR resource type to its openEHR mapping configuration.
 */
public class ResourceMapping {

    @JsonProperty("fhirResourceType")
    private String fhirResourceType;

    @JsonProperty("openEhrMapping")
    private OpenEhrMappingConfig openEhrMapping;

    public ResourceMapping() {}

    public String getFhirResourceType() {
        return fhirResourceType;
    }

    public void setFhirResourceType(String fhirResourceType) {
        this.fhirResourceType = fhirResourceType;
    }

    public OpenEhrMappingConfig getOpenEhrMapping() {
        return openEhrMapping;
    }

    public void setOpenEhrMapping(OpenEhrMappingConfig openEhrMapping) {
        this.openEhrMapping = openEhrMapping;
    }
}
