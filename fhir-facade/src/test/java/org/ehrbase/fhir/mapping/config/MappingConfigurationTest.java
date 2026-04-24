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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class MappingConfigurationTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void shouldLoadPatientMappingFromYaml() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fhir-mappings/patient-mapping.yml")) {
            MappingConfiguration config = yamlMapper.readValue(is, MappingConfiguration.class);

            assertThat(config.getMappings()).hasSize(1);

            ResourceMapping mapping = config.getMappings().get(0);
            assertThat(mapping.getFhirResourceType()).isEqualTo("Patient");
            assertThat(mapping.getOpenEhrMapping().getType()).isEqualTo(OpenEhrType.EHR_STATUS);
            assertThat(mapping.getOpenEhrMapping().getFields()).hasSize(3);
        }
    }

    @Test
    void shouldLoadObservationMappingFromYaml() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fhir-mappings/observation-blood-pressure-mapping.yml")) {
            MappingConfiguration config = yamlMapper.readValue(is, MappingConfiguration.class);

            assertThat(config.getMappings()).hasSize(1);

            ResourceMapping mapping = config.getMappings().get(0);
            assertThat(mapping.getFhirResourceType()).isEqualTo("Observation");
            assertThat(mapping.getOpenEhrMapping().getType()).isEqualTo(OpenEhrType.COMPOSITION);
            assertThat(mapping.getOpenEhrMapping().getTemplateId()).isEqualTo("blood_pressure.v1");
            assertThat(mapping.getOpenEhrMapping().getFields()).hasSizeGreaterThan(5);
        }
    }

    @Test
    void shouldLoadConditionMappingFromYaml() throws Exception {
        try (InputStream is =
                getClass().getResourceAsStream("/fhir-mappings/condition-problem-diagnosis-mapping.yml")) {
            MappingConfiguration config = yamlMapper.readValue(is, MappingConfiguration.class);

            assertThat(config.getMappings()).hasSize(1);

            ResourceMapping mapping = config.getMappings().get(0);
            assertThat(mapping.getFhirResourceType()).isEqualTo("Condition");
            assertThat(mapping.getOpenEhrMapping().getType()).isEqualTo(OpenEhrType.COMPOSITION);
            assertThat(mapping.getOpenEhrMapping().getTemplateId()).isEqualTo("problem_diagnosis.v1");
        }
    }

    @Test
    void shouldDistinguishConstantFromDynamicFields() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fhir-mappings/observation-blood-pressure-mapping.yml")) {
            MappingConfiguration config = yamlMapper.readValue(is, MappingConfiguration.class);
            ResourceMapping mapping = config.getMappings().get(0);

            FieldMapping constantField = mapping.getOpenEhrMapping().getFields().stream()
                    .filter(f -> "Observation.code.coding[0].system".equals(f.getFhirPath()))
                    .findFirst()
                    .orElseThrow();
            assertThat(constantField.isConstant()).isTrue();
            assertThat(constantField.getValue()).isEqualTo("http://loinc.org");

            FieldMapping dynamicField = mapping.getOpenEhrMapping().getFields().stream()
                    .filter(f -> "Observation.component[0].valueQuantity.value".equals(f.getFhirPath()))
                    .findFirst()
                    .orElseThrow();
            assertThat(dynamicField.isConstant()).isFalse();
            assertThat(dynamicField.getOpenEhrPath()).isNotNull();
        }
    }
}
