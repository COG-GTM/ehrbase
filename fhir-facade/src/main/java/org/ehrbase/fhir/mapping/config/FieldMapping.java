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
 * A single field mapping between an openEHR path and a FHIR path.
 * Either {@code openEhrPath} or {@code value} must be set (but not both).
 */
public class FieldMapping {

    @JsonProperty("fhirPath")
    private String fhirPath;

    @JsonProperty("openEhrPath")
    private String openEhrPath;

    @JsonProperty("value")
    private String value;

    public FieldMapping() {}

    public FieldMapping(String fhirPath, String openEhrPath, String value) {
        this.fhirPath = fhirPath;
        this.openEhrPath = openEhrPath;
        this.value = value;
    }

    public String getFhirPath() {
        return fhirPath;
    }

    public void setFhirPath(String fhirPath) {
        this.fhirPath = fhirPath;
    }

    public String getOpenEhrPath() {
        return openEhrPath;
    }

    public void setOpenEhrPath(String openEhrPath) {
        this.openEhrPath = openEhrPath;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isConstant() {
        return value != null;
    }
}
