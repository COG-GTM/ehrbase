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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nedap.archie.rm.ehr.EhrStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ehrbase.fhir.mapping.config.FieldMapping;
import org.ehrbase.fhir.mapping.config.MappingConfiguration;
import org.ehrbase.fhir.mapping.config.ResourceMapping;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;

/**
 * Configuration-driven mapping service that transforms openEHR RM objects to/from FHIR R4 resources.
 */
public class OpenEhrFhirMappingService {

    private static final String FHIR_ID_SEPARATOR = ".";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final List<ResourceMapping> resourceMappings = new ArrayList<>();

    /**
     * Loads all YAML mapping configuration files from the given directory.
     */
    public void loadMappings(Path configDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.yml")) {
            for (Path file : stream) {
                try (InputStream is = Files.newInputStream(file)) {
                    loadMappings(is);
                }
            }
        }
    }

    /**
     * Loads mapping configurations from a YAML input stream.
     */
    public void loadMappings(InputStream yaml) throws IOException {
        MappingConfiguration config = YAML_MAPPER.readValue(yaml, MappingConfiguration.class);
        if (config.getMappings() != null) {
            resourceMappings.addAll(config.getMappings());
        }
    }

    /**
     * Returns all loaded resource mappings.
     */
    public List<ResourceMapping> getResourceMappings() {
        return List.copyOf(resourceMappings);
    }

    /**
     * Finds a resource mapping by FHIR resource type name.
     */
    public ResourceMapping findMappingByFhirType(String fhirResourceType) {
        return resourceMappings.stream()
                .filter(m -> fhirResourceType.equals(m.getFhirResourceType()))
                .findFirst()
                .orElse(null);
    }

    // ---- Read direction: openEHR -> FHIR ----

    /**
     * Maps an EHR_STATUS to a FHIR Patient resource.
     */
    public Patient mapToPatient(UUID ehrId, EhrStatus ehrStatus) {
        Patient patient = new Patient();
        patient.setId(ehrId.toString());

        if (ehrStatus.getSubject() != null
                && ehrStatus.getSubject().getExternalRef() != null
                && ehrStatus.getSubject().getExternalRef().getId() != null) {
            Identifier identifier = new Identifier();
            identifier.setValue(ehrStatus.getSubject().getExternalRef().getId().getValue());
            if (ehrStatus.getSubject().getExternalRef().getNamespace() != null) {
                identifier.setSystem(ehrStatus.getSubject().getExternalRef().getNamespace());
            }
            patient.addIdentifier(identifier);
        }

        patient.setActive(ehrStatus.isModifiable());
        return patient;
    }

    /**
     * Maps a Composition (serialized as JSON) to a FHIR Observation using the given mapping config.
     */
    public Observation mapToObservation(
            UUID ehrId, UUID compositionId, int version, String compositionJson, ResourceMapping mapping) {
        Observation observation = new Observation();
        observation.setId(encodeFhirId(ehrId, compositionId, version));
        observation.setSubject(new Reference("Patient/" + ehrId));
        observation.setStatus(Observation.ObservationStatus.FINAL);

        JsonNode compositionTree = parseJson(compositionJson);

        for (FieldMapping field : mapping.getOpenEhrMapping().getFields()) {
            String resolvedValue = resolveValue(field, compositionTree);
            if (resolvedValue != null) {
                applyObservationField(observation, field.getFhirPath(), resolvedValue);
            }
        }
        return observation;
    }

    /**
     * Maps a Composition (serialized as JSON) to a FHIR Condition using the given mapping config.
     */
    public Condition mapToCondition(
            UUID ehrId, UUID compositionId, int version, String compositionJson, ResourceMapping mapping) {
        Condition condition = new Condition();
        condition.setId(encodeFhirId(ehrId, compositionId, version));
        condition.setSubject(new Reference("Patient/" + ehrId));

        JsonNode compositionTree = parseJson(compositionJson);

        for (FieldMapping field : mapping.getOpenEhrMapping().getFields()) {
            String resolvedValue = resolveValue(field, compositionTree);
            if (resolvedValue != null) {
                applyConditionField(condition, field.getFhirPath(), resolvedValue);
            }
        }
        return condition;
    }

    // ---- Write direction: FHIR -> openEHR ----

    /**
     * Reverse maps a FHIR Observation to an openEHR Composition JSON structure.
     */
    public String mapFromObservation(Observation observation, ResourceMapping mapping) {
        ObjectNode compositionNode = JSON_MAPPER.createObjectNode();

        for (FieldMapping field : mapping.getOpenEhrMapping().getFields()) {
            if (field.getOpenEhrPath() != null) {
                String fhirValue = extractObservationField(observation, field.getFhirPath());
                if (fhirValue != null) {
                    setJsonValueAtPath(compositionNode, field.getOpenEhrPath(), fhirValue);
                }
            }
        }

        return compositionNode.toString();
    }

    /**
     * Reverse maps a FHIR Condition to an openEHR Composition JSON structure.
     */
    public String mapFromCondition(Condition condition, ResourceMapping mapping) {
        ObjectNode compositionNode = JSON_MAPPER.createObjectNode();

        for (FieldMapping field : mapping.getOpenEhrMapping().getFields()) {
            if (field.getOpenEhrPath() != null) {
                String fhirValue = extractConditionField(condition, field.getFhirPath());
                if (fhirValue != null) {
                    setJsonValueAtPath(compositionNode, field.getOpenEhrPath(), fhirValue);
                }
            }
        }

        return compositionNode.toString();
    }

    // ---- ID encoding/decoding ----

    /**
     * Encodes EHR, composition, and version IDs into a composite FHIR ID.
     */
    public String encodeFhirId(UUID ehrId, UUID compositionId, int version) {
        return ehrId.toString() + FHIR_ID_SEPARATOR + compositionId.toString() + FHIR_ID_SEPARATOR + version;
    }

    /**
     * Decodes a composite FHIR ID into its components.
     */
    public FhirIdComponents decodeFhirId(String fhirId) {
        String[] parts = fhirId.split("\\" + FHIR_ID_SEPARATOR, 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid FHIR ID format: " + fhirId);
        }
        return new FhirIdComponents(UUID.fromString(parts[0]), UUID.fromString(parts[1]), Integer.parseInt(parts[2]));
    }

    // ---- Internal helpers ----

    private String resolveValue(FieldMapping field, JsonNode compositionTree) {
        if (field.isConstant()) {
            return field.getValue();
        }
        if (field.getOpenEhrPath() != null && compositionTree != null) {
            return extractFromCompositionJson(compositionTree, field.getOpenEhrPath());
        }
        return null;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON_MAPPER.readTree(json);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Extracts a value from the composition JSON using an openEHR archetype path.
     * Navigates the JSON tree by splitting the path on '/' and matching node names.
     */
    private String extractFromCompositionJson(JsonNode root, String archetypePath) {
        String[] segments = archetypePath.split("/");
        JsonNode current = root;

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }

            String nodeName = segment.contains("[") ? segment.substring(0, segment.indexOf('[')) : segment;
            String archetypeId = segment.contains("[") && segment.contains("]")
                    ? segment.substring(segment.indexOf('[') + 1, segment.indexOf(']'))
                    : null;

            current = findChildNode(current, nodeName, archetypeId);
            if (current == null) {
                return null;
            }
        }

        return current.isValueNode() ? current.asText() : null;
    }

    private JsonNode findChildNode(JsonNode parent, String nodeName, String archetypeId) {
        if (parent == null) {
            return null;
        }

        JsonNode child = parent.get(nodeName);
        if (child != null) {
            if (archetypeId != null && child.isArray()) {
                for (JsonNode element : child) {
                    JsonNode atNode = element.get("archetype_node_id");
                    if (atNode != null && archetypeId.equals(atNode.asText())) {
                        return element;
                    }
                }
                return null;
            }
            return child;
        }

        if (parent.isArray()) {
            for (JsonNode element : parent) {
                JsonNode found = findChildNode(element, nodeName, archetypeId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void applyObservationField(Observation observation, String fhirPath, String value) {
        if (fhirPath.startsWith("Observation.code.coding[0].")) {
            ensureObservationCode(observation);
            Coding coding = observation.getCode().getCodingFirstRep();
            String field = fhirPath.substring("Observation.code.coding[0].".length());
            switch (field) {
                case "system" -> coding.setSystem(value);
                case "code" -> coding.setCode(value);
                case "display" -> coding.setDisplay(value);
                default -> {}
            }
        } else if (fhirPath.startsWith("Observation.component[")) {
            applyObservationComponent(observation, fhirPath, value);
        }
    }

    private void applyObservationComponent(Observation observation, String fhirPath, String value) {
        int componentIdx = extractIndex(fhirPath, "Observation.component[");
        while (observation.getComponent().size() <= componentIdx) {
            observation.addComponent();
        }
        Observation.ObservationComponentComponent component =
                observation.getComponent().get(componentIdx);

        String remainder = fhirPath.substring(fhirPath.indexOf(']') + 2);
        if (remainder.startsWith("code.coding[0].")) {
            String field = remainder.substring("code.coding[0].".length());
            ensureComponentCode(component);
            Coding coding = component.getCode().getCodingFirstRep();
            switch (field) {
                case "system" -> coding.setSystem(value);
                case "code" -> coding.setCode(value);
                case "display" -> coding.setDisplay(value);
                default -> {}
            }
        } else if (remainder.startsWith("valueQuantity.")) {
            String field = remainder.substring("valueQuantity.".length());
            Quantity qty = component.hasValueQuantity() ? component.getValueQuantity() : new Quantity();
            switch (field) {
                case "value" -> qty.setValue(new java.math.BigDecimal(value));
                case "unit" -> qty.setUnit(value);
                case "system" -> qty.setSystem(value);
                case "code" -> qty.setCode(value);
                default -> {}
            }
            component.setValue(qty);
        }
    }

    private void applyConditionField(Condition condition, String fhirPath, String value) {
        if (fhirPath.startsWith("Condition.code.coding[0].")) {
            ensureConditionCode(condition);
            Coding coding = condition.getCode().getCodingFirstRep();
            String field = fhirPath.substring("Condition.code.coding[0].".length());
            switch (field) {
                case "system" -> coding.setSystem(value);
                case "code" -> coding.setCode(value);
                case "display" -> coding.setDisplay(value);
                default -> {}
            }
        }
    }

    private String extractObservationField(Observation observation, String fhirPath) {
        if (fhirPath.startsWith("Observation.component[")) {
            return extractObservationComponentField(observation, fhirPath);
        }
        return null;
    }

    private String extractObservationComponentField(Observation observation, String fhirPath) {
        int componentIdx = extractIndex(fhirPath, "Observation.component[");
        if (componentIdx >= observation.getComponent().size()) {
            return null;
        }
        Observation.ObservationComponentComponent component =
                observation.getComponent().get(componentIdx);

        String remainder = fhirPath.substring(fhirPath.indexOf(']') + 2);
        if (remainder.startsWith("valueQuantity.value") && component.hasValueQuantity()) {
            return component.getValueQuantity().getValue() != null
                    ? component.getValueQuantity().getValue().toPlainString()
                    : null;
        }
        return null;
    }

    private String extractConditionField(Condition condition, String fhirPath) {
        if (fhirPath.startsWith("Condition.code.coding[0].")) {
            if (!condition.hasCode() || condition.getCode().getCoding().isEmpty()) {
                return null;
            }
            Coding coding = condition.getCode().getCodingFirstRep();
            String field = fhirPath.substring("Condition.code.coding[0].".length());
            return switch (field) {
                case "system" -> coding.getSystem();
                case "code" -> coding.getCode();
                case "display" -> coding.getDisplay();
                default -> null;
            };
        }
        return null;
    }

    private void ensureObservationCode(Observation observation) {
        if (!observation.hasCode()) {
            observation.setCode(new CodeableConcept());
        }
        if (observation.getCode().getCoding().isEmpty()) {
            observation.getCode().addCoding();
        }
    }

    private void ensureComponentCode(Observation.ObservationComponentComponent component) {
        if (!component.hasCode()) {
            component.setCode(new CodeableConcept());
        }
        if (component.getCode().getCoding().isEmpty()) {
            component.getCode().addCoding();
        }
    }

    private void ensureConditionCode(Condition condition) {
        if (!condition.hasCode()) {
            condition.setCode(new CodeableConcept());
        }
        if (condition.getCode().getCoding().isEmpty()) {
            condition.getCode().addCoding();
        }
    }

    private int extractIndex(String path, String prefix) {
        int start = prefix.length();
        int end = path.indexOf(']', start);
        return Integer.parseInt(path.substring(start, end));
    }

    /**
     * Sets a value in a JSON tree at the given openEHR path.
     */
    private void setJsonValueAtPath(ObjectNode root, String path, String value) {
        String[] segments = path.split("/");
        ObjectNode current = root;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                continue;
            }

            String nodeName = segment.contains("[") ? segment.substring(0, segment.indexOf('[')) : segment;

            if (i == segments.length - 1) {
                current.put(nodeName, value);
            } else {
                if (!current.has(nodeName) || !current.get(nodeName).isObject()) {
                    current.putObject(nodeName);
                }
                current = (ObjectNode) current.get(nodeName);
            }
        }
    }
}
