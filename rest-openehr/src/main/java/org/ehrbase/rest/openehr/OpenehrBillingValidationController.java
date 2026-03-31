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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.ehrbase.api.service.BillingCodeValidationService;
import org.ehrbase.rest.BaseController;
import org.ehrbase.rest.openehr.specification.BillingValidationApiSpecification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnMissingBean(name = "primaryopenehrbillingvalidationcontroller")
@RestController
@RequestMapping(
        path = BaseController.API_CONTEXT_PATH_WITH_VERSION + "/definition/billing/validate",
        produces = {APPLICATION_JSON_VALUE})
public class OpenehrBillingValidationController extends BaseController implements BillingValidationApiSpecification {

    private final BillingCodeValidationService billingCodeValidationService;

    public OpenehrBillingValidationController(BillingCodeValidationService billingCodeValidationService) {
        this.billingCodeValidationService = Objects.requireNonNull(billingCodeValidationService);
    }

    @Override
    @PostMapping
    public ResponseEntity<Map<String, Object>> validateBillingCodePost(
            @RequestHeader(value = ACCEPT, required = false) String accept, @RequestBody Map<String, String> request) {

        String codeSystem = request.get("code_system");
        String code = request.get("code");
        return doValidate(codeSystem, code);
    }

    @Override
    @GetMapping
    public ResponseEntity<Map<String, Object>> validateBillingCodeGet(
            @RequestHeader(value = ACCEPT, required = false) String accept,
            @RequestParam(value = "code_system") String codeSystem,
            @RequestParam(value = "code") String code) {

        return doValidate(codeSystem, code);
    }

    private ResponseEntity<Map<String, Object>> doValidate(String codeSystem, String code) {
        Map<String, Object> response = new HashMap<>();
        boolean valid = billingCodeValidationService.validateCode(codeSystem, code);
        response.put("valid", valid);
        if (!valid) {
            response.put("message", "Code '%s' is not valid in code system '%s'".formatted(code, codeSystem));
        }
        return ResponseEntity.ok(response);
    }
}
