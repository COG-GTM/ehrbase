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
package org.ehrbase.rest.openehr.specification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;

@Tag(name = "BILLING_VALIDATION")
public interface BillingValidationApiSpecification {

    @Operation(summary = "Validate a billing code via POST")
    ResponseEntity<Map<String, Object>> validateBillingCodePost(String accept, Map<String, String> request);

    @Operation(summary = "Validate a billing code via GET")
    ResponseEntity<Map<String, Object>> validateBillingCodeGet(String accept, String codeSystem, String code);
}
