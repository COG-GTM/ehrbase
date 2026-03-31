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
package org.ehrbase.api.service;

/**
 * Service interface for FHIR R4 ExplanationOfBenefit operations.
 * Maps openEHR clinical data to FHIR R4 ExplanationOfBenefit resources.
 */
public interface ExplanationOfBenefitService {

    /**
     * Search ExplanationOfBenefit resources by patient identifier.
     *
     * @param patientId the patient identifier (EHR ID or external subject ID)
     * @param fromDate  optional start date filter (ISO 8601)
     * @param toDate    optional end date filter (ISO 8601)
     * @return FHIR R4 Bundle JSON containing matching ExplanationOfBenefit resources
     */
    String searchByPatient(String patientId, String fromDate, String toDate);

    /**
     * Read a single ExplanationOfBenefit resource by its ID.
     * The ID encodes ehr_id::composition_uid.
     *
     * @param eobId the ExplanationOfBenefit identifier
     * @return FHIR R4 ExplanationOfBenefit JSON
     */
    String readById(String eobId);

    /**
     * Returns a FHIR R4 CapabilityStatement advertising supported operations.
     *
     * @return FHIR R4 CapabilityStatement JSON
     */
    String getCapabilityStatement();
}
