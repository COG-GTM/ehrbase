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

import java.nio.charset.StandardCharsets;
import javax.annotation.PostConstruct;
import org.ehrbase.api.exception.StateConflictException;
import org.ehrbase.api.service.StoredQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ehrbase.billing.queries.seed-on-startup", havingValue = "true", matchIfMissing = false)
public class BillingQueryInitializer {

    private final Logger logger = LoggerFactory.getLogger(BillingQueryInitializer.class);

    private final StoredQueryService storedQueryService;
    private final ResourcePatternResolver resourceResolver;

    public BillingQueryInitializer(StoredQueryService storedQueryService, ResourcePatternResolver resourceResolver) {
        this.storedQueryService = storedQueryService;
        this.resourceResolver = resourceResolver;
    }

    @PostConstruct
    public void seedBillingQueries() {
        try {
            Resource[] resources = resourceResolver.getResources("classpath:billing-queries/*.aql");
            for (Resource resource : resources) {
                seedQuery(resource);
            }
        } catch (Exception e) {
            logger.warn("Failed to discover billing query resources", e);
        }
    }

    private void seedQuery(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            return;
        }
        String qualifiedName = filenameToQualifiedName(filename);
        try {
            String queryText = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            storedQueryService.createStoredQuery(qualifiedName, BillingQueryCatalog.VERSION, queryText, "AQL");
            logger.info("Seeded billing query: {}", qualifiedName);
        } catch (StateConflictException e) {
            logger.info("Billing query already exists, skipping: {}", qualifiedName);
        } catch (Exception e) {
            logger.warn("Failed to seed billing query: {}", qualifiedName, e);
        }
    }

    static String filenameToQualifiedName(String filename) {
        return filename.replace(".aql", "").replace("__", "::");
    }
}
