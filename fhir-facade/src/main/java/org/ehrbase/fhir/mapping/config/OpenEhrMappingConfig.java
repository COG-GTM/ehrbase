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
import java.util.List;

/**
 * Configuration for mapping an openEHR type to a FHIR resource.
 */
public class OpenEhrMappingConfig {

    @JsonProperty("type")
    private OpenEhrType type;

    @JsonProperty("templateId")
    private String templateId;

    @JsonProperty("fields")
    private List<FieldMapping> fields;

    public OpenEhrMappingConfig() {}

    public OpenEhrType getType() {
        return type;
    }

    public void setType(OpenEhrType type) {
        this.type = type;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public List<FieldMapping> getFields() {
        return fields;
    }

    public void setFields(List<FieldMapping> fields) {
        this.fields = fields;
    }
}
