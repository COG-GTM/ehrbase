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

import com.nedap.archie.rm.datatypes.CodePhrase;
import com.nedap.archie.rm.support.identification.TerminologyId;
import org.ehrbase.api.service.BillingCodeValidationService;
import org.ehrbase.openehr.sdk.validation.terminology.ExternalTerminologyValidation;
import org.ehrbase.openehr.sdk.validation.terminology.TerminologyParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BillingCodeValidationServiceImp implements BillingCodeValidationService {

    private static final Logger LOG = LoggerFactory.getLogger(BillingCodeValidationServiceImp.class);

    private static final String ICD10_SYSTEM = "http://hl7.org/fhir/sid/icd-10-cm";
    private static final String CPT_SYSTEM = "http://www.ama-assn.org/go/cpt";
    private static final String HCPCS_SYSTEM = "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets";
    private static final String NDC_SYSTEM = "http://hl7.org/fhir/sid/ndc";

    private final ExternalTerminologyValidation externalTerminologyValidation;

    public BillingCodeValidationServiceImp(ExternalTerminologyValidation externalTerminologyValidation) {
        this.externalTerminologyValidation = externalTerminologyValidation;
    }

    @Override
    public boolean validateIcd10Code(String code) {
        return validateCode(ICD10_SYSTEM, code);
    }

    @Override
    public boolean validateCptCode(String code) {
        return validateCode(CPT_SYSTEM, code);
    }

    @Override
    public boolean validateHcpcsCode(String code) {
        return validateCode(HCPCS_SYSTEM, code);
    }

    @Override
    public boolean validateNdcCode(String code) {
        return validateCode(NDC_SYSTEM, code);
    }

    @Override
    public boolean validateCode(String codeSystem, String code) {
        try {
            TerminologyParam param = TerminologyParam.ofFhir("//terminology.hl7.org/CodeSystem?url=" + codeSystem);
            param.useCodeSystem();
            param.setCodePhrase(new CodePhrase(new TerminologyId(codeSystem), code));
            if (!externalTerminologyValidation.supports(param)) {
                LOG.warn("Terminology server does not support code system: {}", codeSystem);
                return false;
            }
            var result = externalTerminologyValidation.validate(param);
            return result.isSuccess();
        } catch (Exception e) {
            LOG.warn("Error validating code '{}' in system '{}': {}", code, codeSystem, e.getMessage());
            return false;
        }
    }
}
