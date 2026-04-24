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
package org.ehrbase.fhir.mapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.ehr.EhrStatus;
import com.nedap.archie.rm.generic.PartySelf;
import com.nedap.archie.rm.support.identification.GenericId;
import com.nedap.archie.rm.support.identification.PartyRef;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;
import org.ehrbase.fhir.mapping.config.ResourceMapping;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OpenEhrFhirMappingServiceTest {

    private OpenEhrFhirMappingService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new OpenEhrFhirMappingService();
        try (InputStream is = getClass().getResourceAsStream("/fhir-mappings/patient-mapping.yml")) {
            service.loadMappings(is);
        }
        try (InputStream is = getClass().getResourceAsStream("/fhir-mappings/observation-blood-pressure-mapping.yml")) {
            service.loadMappings(is);
        }
        try (InputStream is =
                getClass().getResourceAsStream("/fhir-mappings/condition-problem-diagnosis-mapping.yml")) {
            service.loadMappings(is);
        }
    }

    @Nested
    class PatientMappingTest {

        @Test
        void shouldMapEhrStatusToPatient() {
            UUID ehrId = UUID.randomUUID();
            EhrStatus ehrStatus = createEhrStatus("patient-123", "urn:example:namespace", true);

            Patient patient = service.mapToPatient(ehrId, ehrStatus);

            assertThat(patient.getId()).isEqualTo(ehrId.toString());
            assertThat(patient.getIdentifier()).hasSize(1);
            assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo("patient-123");
            assertThat(patient.getIdentifierFirstRep().getSystem()).isEqualTo("urn:example:namespace");
            assertThat(patient.getActive()).isTrue();
        }

        @Test
        void shouldMapInactivePatient() {
            UUID ehrId = UUID.randomUUID();
            EhrStatus ehrStatus = createEhrStatus("patient-456", "urn:other", false);

            Patient patient = service.mapToPatient(ehrId, ehrStatus);

            assertThat(patient.getActive()).isFalse();
        }

        @Test
        void shouldHandleEhrStatusWithNoSubject() {
            UUID ehrId = UUID.randomUUID();
            EhrStatus ehrStatus = new EhrStatus();
            ehrStatus.setArchetypeNodeId("openEHR-EHR-EHR_STATUS.generic.v1");
            ehrStatus.setName(new DvText("EHR Status"));
            ehrStatus.setModifiable(true);

            Patient patient = service.mapToPatient(ehrId, ehrStatus);

            assertThat(patient.getId()).isEqualTo(ehrId.toString());
            assertThat(patient.getIdentifier()).isEmpty();
            assertThat(patient.getActive()).isTrue();
        }
    }

    @Nested
    class ObservationMappingTest {

        @Test
        void shouldMapCompositionToObservation() {
            UUID ehrId = UUID.randomUUID();
            UUID compositionId = UUID.randomUUID();
            int version = 1;

            String compositionJson = createBloodPressureCompositionJson("120", "80");
            ResourceMapping mapping = service.findMappingByFhirType("Observation");

            Observation observation = service.mapToObservation(ehrId, compositionId, version, compositionJson, mapping);

            assertThat(observation.getId()).isEqualTo(service.encodeFhirId(ehrId, compositionId, version));
            assertThat(observation.getSubject().getReference()).isEqualTo("Patient/" + ehrId);
            assertThat(observation.getStatus()).isEqualTo(Observation.ObservationStatus.FINAL);

            assertThat(observation.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://loinc.org");
            assertThat(observation.getCode().getCodingFirstRep().getCode()).isEqualTo("85354-9");
            assertThat(observation.getCode().getCodingFirstRep().getDisplay()).isEqualTo("Blood pressure panel");

            assertThat(observation.getComponent()).hasSize(2);
            assertThat(observation
                            .getComponent()
                            .get(0)
                            .getCode()
                            .getCodingFirstRep()
                            .getCode())
                    .isEqualTo("8480-6");
            assertThat(observation.getComponent().get(0).getValueQuantity().getValue())
                    .isEqualByComparingTo(new BigDecimal("120"));
            assertThat(observation.getComponent().get(0).getValueQuantity().getUnit())
                    .isEqualTo("mmHg");

            assertThat(observation
                            .getComponent()
                            .get(1)
                            .getCode()
                            .getCodingFirstRep()
                            .getCode())
                    .isEqualTo("8462-4");
            assertThat(observation.getComponent().get(1).getValueQuantity().getValue())
                    .isEqualByComparingTo(new BigDecimal("80"));
        }

        @Test
        void shouldHandleNullCompositionJson() {
            UUID ehrId = UUID.randomUUID();
            UUID compositionId = UUID.randomUUID();
            ResourceMapping mapping = service.findMappingByFhirType("Observation");

            Observation observation = service.mapToObservation(ehrId, compositionId, 1, null, mapping);

            assertThat(observation.getId()).isNotNull();
            assertThat(observation.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://loinc.org");
        }

        @Test
        void shouldReverseMapObservationToCompositionJson() {
            UUID ehrId = UUID.randomUUID();
            UUID compositionId = UUID.randomUUID();
            String compositionJson = createBloodPressureCompositionJson("130", "85");
            ResourceMapping mapping = service.findMappingByFhirType("Observation");

            Observation observation = service.mapToObservation(ehrId, compositionId, 1, compositionJson, mapping);
            String resultJson = service.mapFromObservation(observation, mapping);

            assertThat(resultJson).isNotEmpty();
            assertThat(resultJson).contains("130");
            assertThat(resultJson).contains("85");
            assertThat(resultJson).contains("at0004");
            assertThat(resultJson).contains("at0005");
        }
    }

    @Nested
    class ConditionMappingTest {

        @Test
        void shouldMapCompositionToCondition() {
            UUID ehrId = UUID.randomUUID();
            UUID compositionId = UUID.randomUUID();
            int version = 2;

            String compositionJson = createConditionCompositionJson("http://snomed.info/sct", "38341003");
            ResourceMapping mapping = service.findMappingByFhirType("Condition");

            Condition condition = service.mapToCondition(ehrId, compositionId, version, compositionJson, mapping);

            assertThat(condition.getId()).isEqualTo(service.encodeFhirId(ehrId, compositionId, version));
            assertThat(condition.getSubject().getReference()).isEqualTo("Patient/" + ehrId);
            assertThat(condition.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://snomed.info/sct");
            assertThat(condition.getCode().getCodingFirstRep().getCode()).isEqualTo("38341003");
        }

        @Test
        void shouldReverseMapConditionToCompositionJson() {
            UUID ehrId = UUID.randomUUID();
            UUID compositionId = UUID.randomUUID();
            String compositionJson = createConditionCompositionJson("http://snomed.info/sct", "38341003");
            ResourceMapping mapping = service.findMappingByFhirType("Condition");

            Condition condition = service.mapToCondition(ehrId, compositionId, 1, compositionJson, mapping);
            String resultJson = service.mapFromCondition(condition, mapping);

            assertThat(resultJson).contains("http://snomed.info/sct");
            assertThat(resultJson).contains("38341003");
        }
    }

    @Nested
    class FhirIdEncodingTest {

        @Test
        void shouldEncodeAndDecodeFhirId() {
            UUID ehrId = UUID.randomUUID();
            UUID compositionId = UUID.randomUUID();
            int version = 3;

            String encoded = service.encodeFhirId(ehrId, compositionId, version);
            FhirIdComponents decoded = service.decodeFhirId(encoded);

            assertThat(decoded.ehrId()).isEqualTo(ehrId);
            assertThat(decoded.compositionId()).isEqualTo(compositionId);
            assertThat(decoded.version()).isEqualTo(version);
        }

        @Test
        void shouldEncodeWithCorrectFormat() {
            UUID ehrId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            UUID compositionId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

            String encoded = service.encodeFhirId(ehrId, compositionId, 1);

            assertThat(encoded)
                    .isEqualTo("550e8400-e29b-41d4-a716-446655440000.6ba7b810-9dad-11d1-80b4-00c04fd430c8.1");
        }

        @Test
        void shouldThrowOnInvalidFhirId() {
            assertThatThrownBy(() -> service.decodeFhirId("invalid-id")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class MappingLoadingTest {

        @Test
        void shouldLoadAllMappings() {
            assertThat(service.getResourceMappings()).hasSize(3);
        }

        @Test
        void shouldFindMappingByType() {
            assertThat(service.findMappingByFhirType("Patient")).isNotNull();
            assertThat(service.findMappingByFhirType("Observation")).isNotNull();
            assertThat(service.findMappingByFhirType("Condition")).isNotNull();
            assertThat(service.findMappingByFhirType("Unknown")).isNull();
        }
    }

    // ---- Test helpers ----

    private EhrStatus createEhrStatus(String subjectId, String namespace, boolean modifiable) {
        EhrStatus ehrStatus = new EhrStatus();
        ehrStatus.setArchetypeNodeId("openEHR-EHR-EHR_STATUS.generic.v1");
        ehrStatus.setName(new DvText("EHR Status"));
        ehrStatus.setModifiable(modifiable);

        GenericId genericId = new GenericId(subjectId, "PERSON");
        PartyRef partyRef = new PartyRef(genericId, namespace, "PERSON");
        PartySelf subject = new PartySelf(partyRef);
        ehrStatus.setSubject(subject);

        return ehrStatus;
    }

    private String createBloodPressureCompositionJson(String systolic, String diastolic) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        ArrayNode contentArray = root.putArray("content");

        ObjectNode bpObservation = contentArray.addObject();
        bpObservation.put("archetype_node_id", "openEHR-EHR-OBSERVATION.blood_pressure.v2");

        ObjectNode data = bpObservation.putObject("data");
        ArrayNode eventsArray = data.putArray("events");
        ObjectNode event = eventsArray.addObject();
        ObjectNode eventData = event.putObject("data");
        ArrayNode items = eventData.putArray("items");

        ObjectNode systolicItem = items.addObject();
        systolicItem.put("archetype_node_id", "at0004");
        ObjectNode systolicValue = systolicItem.putObject("value");
        systolicValue.put("magnitude", systolic);

        ObjectNode diastolicItem = items.addObject();
        diastolicItem.put("archetype_node_id", "at0005");
        ObjectNode diastolicValue = diastolicItem.putObject("value");
        diastolicValue.put("magnitude", diastolic);

        return root.toString();
    }

    private String createConditionCompositionJson(String terminologySystem, String code) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        ArrayNode contentArray = root.putArray("content");
        ObjectNode evaluation = contentArray.addObject();
        evaluation.put("archetype_node_id", "openEHR-EHR-EVALUATION.problem_diagnosis.v1");

        ObjectNode data = evaluation.putObject("data");
        ArrayNode items = data.putArray("items");

        ObjectNode codeItem = items.addObject();
        codeItem.put("archetype_node_id", "at0002");
        ObjectNode value = codeItem.putObject("value");
        ObjectNode definingCode = value.putObject("defining_code");
        ObjectNode terminologyId = definingCode.putObject("terminology_id");
        terminologyId.put("value", terminologySystem);
        definingCode.put("code_string", code);

        return root.toString();
    }
}
