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
package org.ehrbase.plugin.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import java.util.List;
import java.util.Map;
import org.ehrbase.plugin.fhir.provider.ConditionResourceProvider;
import org.ehrbase.plugin.fhir.provider.ObservationResourceProvider;
import org.ehrbase.plugin.fhir.provider.PatientResourceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter;

/**
 * Spring configuration that creates and configures the HAPI FHIR {@link RestfulServer}
 * with R4 context and stub resource providers, and bridges it into the
 * {@link org.springframework.web.servlet.DispatcherServlet} via an {@link HttpRequestHandler}.
 */
@Configuration
public class FhirServerConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public RestfulServer restfulServer(FhirContext fhirContext) {
        RestfulServer server = new RestfulServer(fhirContext);
        server.setDefaultResponseEncoding(EncodingEnum.JSON);
        server.setResourceProviders(List.of(
                new PatientResourceProvider(), new ObservationResourceProvider(), new ConditionResourceProvider()));
        return server;
    }

    @Bean
    public FhirRequestHandler fhirRequestHandler(RestfulServer restfulServer) {
        return new FhirRequestHandler(restfulServer);
    }

    @Bean
    public SimpleUrlHandlerMapping fhirHandlerMapping(HttpRequestHandler fhirRequestHandler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/**", fhirRequestHandler));
        mapping.setOrder(0);
        return mapping;
    }

    @Bean
    public HttpRequestHandlerAdapter httpRequestHandlerAdapter() {
        return new HttpRequestHandlerAdapter();
    }
}
