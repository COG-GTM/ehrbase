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
package org.ehrbase.service;

import ca.uhn.fhir.context.FhirContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ehrbase.api.dto.AqlQueryRequest;
import org.ehrbase.api.exception.ObjectNotFoundException;
import org.ehrbase.api.service.AqlQueryService;
import org.ehrbase.api.service.BillingCodeValidationService;
import org.ehrbase.api.service.ExplanationOfBenefitService;
import org.ehrbase.api.service.StoredQueryService;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryResultDto;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.query.ResultHolder;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link ExplanationOfBenefitService} that maps openEHR clinical data
 * to FHIR R4 ExplanationOfBenefit resources using stored AQL queries.
 */
@Service
public class ExplanationOfBenefitServiceImp implements ExplanationOfBenefitService {

    private static final Logger LOG = LoggerFactory.getLogger(ExplanationOfBenefitServiceImp.class);

    private static final String ICD10_SYSTEM = "http://hl7.org/fhir/sid/icd-10-cm";
    private static final String CPT_SYSTEM = "http://www.ama-assn.org/go/cpt";
    private static final String EOB_ID_SEPARATOR = "::";

    private final FhirContext fhirContext;
    private final AqlQueryService aqlQueryService;
    private final StoredQueryService storedQueryService;
    private final BillingCodeValidationService billingCodeValidationService;

    public ExplanationOfBenefitServiceImp(
            AqlQueryService aqlQueryService,
            StoredQueryService storedQueryService,
            BillingCodeValidationService billingCodeValidationService) {
        this.aqlQueryService = aqlQueryService;
        this.storedQueryService = storedQueryService;
        this.billingCodeValidationService = billingCodeValidationService;
        this.fhirContext = FhirContext.forR4();
    }

    @Override
    public String searchByPatient(String patientId, String fromDate, String toDate) {
        LOG.debug("Searching ExplanationOfBenefit for patient: {}", patientId);

        List<ExplanationOfBenefit> eobList = new ArrayList<>();

        // Query diagnoses
        List<Map<String, Object>> diagnoses = executeBillingQuery(
                BillingQueryCatalog.DIAGNOSES, Map.of("code_system", ICD10_SYSTEM, "ehr_id", patientId));

        // Query procedures
        List<Map<String, Object>> procedures = executeBillingQuery(
                BillingQueryCatalog.PROCEDURES, Map.of("code_system", CPT_SYSTEM, "ehr_id", patientId));

        // Query encounters with date range
        Map<String, Object> encounterParams = new HashMap<>();
        encounterParams.put("ehr_id", patientId);
        if (fromDate != null) {
            encounterParams.put("from_date", fromDate);
        }
        if (toDate != null) {
            encounterParams.put("to_date", toDate);
        }
        List<Map<String, Object>> encounters = executeBillingQuery(BillingQueryCatalog.ENCOUNTERS, encounterParams);

        // Group data by composition_uid and build EOB resources
        Map<String, List<Map<String, Object>>> diagByComposition = groupByField(diagnoses, "composition_uid");
        Map<String, List<Map<String, Object>>> procByComposition = groupByField(procedures, "composition_uid");

        for (Map<String, Object> encounter : encounters) {
            String compositionUid = getStringValue(encounter, "composition_uid");
            if (compositionUid == null) {
                continue;
            }

            ExplanationOfBenefit eob = buildEob(
                    patientId,
                    compositionUid,
                    encounter,
                    diagByComposition.getOrDefault(compositionUid, Collections.emptyList()),
                    procByComposition.getOrDefault(compositionUid, Collections.emptyList()));
            eobList.add(eob);
        }

        // If no encounters found, still create EOBs from diagnoses/procedures
        if (encounters.isEmpty() && (!diagnoses.isEmpty() || !procedures.isEmpty())) {
            Map<String, List<Map<String, Object>>> allByComposition = new HashMap<>(diagByComposition);
            procByComposition.forEach((key, value) -> allByComposition.merge(key, value, (existing, newList) -> {
                List<Map<String, Object>> merged = new ArrayList<>(existing);
                merged.addAll(newList);
                return merged;
            }));

            for (String compositionUid : allByComposition.keySet()) {
                ExplanationOfBenefit eob = buildEob(
                        patientId,
                        compositionUid,
                        null,
                        diagByComposition.getOrDefault(compositionUid, Collections.emptyList()),
                        procByComposition.getOrDefault(compositionUid, Collections.emptyList()));
                eobList.add(eob);
            }
        }

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(eobList.size());
        for (ExplanationOfBenefit eob : eobList) {
            bundle.addEntry().setResource(eob).setFullUrl("ExplanationOfBenefit/" + eob.getId());
        }

        return fhirContext.newJsonParser().encodeResourceToString(bundle);
    }

    @Override
    public String readById(String eobId) {
        LOG.debug("Reading ExplanationOfBenefit by ID: {}", eobId);

        String[] parts = eobId.split(EOB_ID_SEPARATOR, 2);
        if (parts.length < 2) {
            throw new ObjectNotFoundException("ExplanationOfBenefit", "Invalid EOB ID format: " + eobId);
        }

        String ehrId = parts[0];
        String compositionUid = parts[1];

        // Query diagnoses for this composition
        List<Map<String, Object>> diagnoses = executeBillingQuery(
                BillingQueryCatalog.DIAGNOSES, Map.of("code_system", ICD10_SYSTEM, "ehr_id", ehrId));
        diagnoses = diagnoses.stream()
                .filter(d -> compositionUid.equals(getStringValue(d, "composition_uid")))
                .toList();

        // Query procedures for this composition
        List<Map<String, Object>> procedures =
                executeBillingQuery(BillingQueryCatalog.PROCEDURES, Map.of("code_system", CPT_SYSTEM, "ehr_id", ehrId));
        procedures = procedures.stream()
                .filter(p -> compositionUid.equals(getStringValue(p, "composition_uid")))
                .toList();

        ExplanationOfBenefit eob = buildEob(ehrId, compositionUid, null, diagnoses, procedures);

        return fhirContext.newJsonParser().encodeResourceToString(eob);
    }

    @Override
    public String getCapabilityStatement() {
        CapabilityStatement cs = new CapabilityStatement();
        cs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        cs.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
        cs.setFormat(List.of(new org.hl7.fhir.r4.model.CodeType("application/fhir+json")));
        cs.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);

        CapabilityStatement.CapabilityStatementSoftwareComponent software =
                new CapabilityStatement.CapabilityStatementSoftwareComponent();
        software.setName("EHRbase FHIR Billing Bridge");
        cs.setSoftware(software);

        CapabilityStatement.CapabilityStatementRestComponent rest =
                new CapabilityStatement.CapabilityStatementRestComponent();
        rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);

        CapabilityStatement.CapabilityStatementRestResourceComponent resource =
                new CapabilityStatement.CapabilityStatementRestResourceComponent();
        resource.setType("ExplanationOfBenefit");
        resource.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.READ);
        resource.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);

        CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent patientParam =
                new CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent();
        patientParam.setName("patient");
        patientParam.setType(Enumerations.SearchParamType.REFERENCE);
        resource.addSearchParam(patientParam);

        rest.addResource(resource);
        cs.addRest(rest);

        return fhirContext.newJsonParser().encodeResourceToString(cs);
    }

    private ExplanationOfBenefit buildEob(
            String patientId,
            String compositionUid,
            Map<String, Object> encounter,
            List<Map<String, Object>> diagnoses,
            List<Map<String, Object>> procedures) {

        ExplanationOfBenefit eob = new ExplanationOfBenefit();
        eob.setId(patientId + EOB_ID_SEPARATOR + compositionUid);
        eob.setStatus(ExplanationOfBenefit.ExplanationOfBenefitStatus.ACTIVE);
        eob.setUse(ExplanationOfBenefit.Use.CLAIM);

        // Type: institutional
        CodeableConcept type = new CodeableConcept();
        type.addCoding(
                new Coding("http://terminology.hl7.org/CodeSystem/claim-type", "institutional", "Institutional"));
        eob.setType(type);

        // Patient reference
        eob.setPatient(new Reference("Patient/" + patientId));

        // Billable period from encounter
        if (encounter != null) {
            Period period = new Period();
            String startTime = getStringValue(encounter, "start_time");
            String endTime = getStringValue(encounter, "end_time");
            if (startTime != null) {
                period.setStartElement(new org.hl7.fhir.r4.model.DateTimeType(startTime));
            }
            if (endTime != null) {
                period.setEndElement(new org.hl7.fhir.r4.model.DateTimeType(endTime));
            }
            eob.setBillablePeriod(period);
        }

        // Diagnoses
        int diagSequence = 1;
        for (Map<String, Object> diag : diagnoses) {
            String code = getStringValue(diag, "diagnosis_code");
            String codeSystem = getStringValue(diag, "code_system");
            String text = getStringValue(diag, "diagnosis_text");

            if (code != null && codeSystem != null && billingCodeValidationService.validateCode(codeSystem, code)) {
                ExplanationOfBenefit.DiagnosisComponent diagComp = new ExplanationOfBenefit.DiagnosisComponent();
                diagComp.setSequence(diagSequence++);
                CodeableConcept diagCode = new CodeableConcept();
                diagCode.addCoding(new Coding(codeSystem, code, text));
                diagComp.setDiagnosis(diagCode);
                eob.addDiagnosis(diagComp);
            }
        }

        // Procedures
        int procSequence = 1;
        for (Map<String, Object> proc : procedures) {
            String code = getStringValue(proc, "procedure_code");
            String codeSystem = getStringValue(proc, "code_system");
            String text = getStringValue(proc, "procedure_text");

            if (code != null && codeSystem != null && billingCodeValidationService.validateCode(codeSystem, code)) {
                ExplanationOfBenefit.ProcedureComponent procComp = new ExplanationOfBenefit.ProcedureComponent();
                procComp.setSequence(procSequence++);
                CodeableConcept procCode = new CodeableConcept();
                procCode.addCoding(new Coding(codeSystem, code, text));
                procComp.setProcedure(procCode);
                eob.addProcedure(procComp);
            }
        }

        // Line items from procedures
        int itemSequence = 1;
        for (Map<String, Object> proc : procedures) {
            String code = getStringValue(proc, "procedure_code");
            String codeSystem = getStringValue(proc, "code_system");
            String text = getStringValue(proc, "procedure_text");

            if (code != null && codeSystem != null && billingCodeValidationService.validateCode(codeSystem, code)) {
                ExplanationOfBenefit.ItemComponent item = new ExplanationOfBenefit.ItemComponent();
                item.setSequence(itemSequence++);
                CodeableConcept itemCode = new CodeableConcept();
                itemCode.addCoding(new Coding(codeSystem, code, text));
                item.setProductOrService(itemCode);
                eob.addItem(item);
            }
        }

        return eob;
    }

    private List<Map<String, Object>> executeBillingQuery(String queryName, Map<String, Object> parameters) {
        try {
            var storedQuery = storedQueryService.retrieveStoredQuery(queryName, BillingQueryCatalog.VERSION);
            String aqlString = storedQuery.getQueryText();

            AqlQueryRequest request = AqlQueryRequest.prepare(aqlString, parameters, null, null);
            QueryResultDto result = aqlQueryService.query(request);

            return mapResultSet(result);
        } catch (ObjectNotFoundException e) {
            LOG.warn("Billing query '{}' not found: {}", queryName, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.warn("Error executing billing query '{}': {}", queryName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> mapResultSet(QueryResultDto result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (result == null || result.getResultSet() == null) {
            return rows;
        }

        for (ResultHolder holder : result.getResultSet()) {
            Map<String, Object> row = new HashMap<>();
            for (String columnId : holder.columnIds()) {
                List<Object> values = holder.values();
                if (!values.isEmpty()) {
                    row.put(columnId, values.get(0));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private Map<String, List<Map<String, Object>>> groupByField(List<Map<String, Object>> records, String fieldName) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        for (Map<String, Object> record : records) {
            String key = getStringValue(record, fieldName);
            if (key != null) {
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            }
        }
        return grouped;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
