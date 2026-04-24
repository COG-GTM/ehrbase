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

import ca.uhn.fhir.rest.server.RestfulServer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.context.ServletContextAware;

/**
 * Bridges the Spring MVC {@link org.springframework.web.servlet.DispatcherServlet} to the
 * HAPI FHIR {@link RestfulServer}. Since {@code RestfulServer} is itself an {@code HttpServlet},
 * it cannot be dispatched by a {@code DispatcherServlet} directly. This handler delegates
 * every incoming request to the {@code RestfulServer.service()} method, performing lazy
 * initialization on first use.
 */
public class FhirRequestHandler implements HttpRequestHandler, ServletContextAware {

    private final RestfulServer restfulServer;
    private jakarta.servlet.ServletContext servletContext;
    private volatile boolean initialized = false;

    public FhirRequestHandler(RestfulServer restfulServer) {
        this.restfulServer = restfulServer;
    }

    @Override
    public void setServletContext(jakarta.servlet.ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ensureInitialized();
        restfulServer.service(request, response);
    }

    private void ensureInitialized() throws ServletException {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    restfulServer.init(new DelegatingServletConfig());
                    initialized = true;
                }
            }
        }
    }

    /**
     * Minimal {@link jakarta.servlet.ServletConfig} that delegates to the available
     * {@link jakarta.servlet.ServletContext}.
     */
    private class DelegatingServletConfig implements jakarta.servlet.ServletConfig {

        @Override
        public String getServletName() {
            return "fhir-facade";
        }

        @Override
        public jakarta.servlet.ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getInitParameter(String name) {
            return null;
        }

        @Override
        public java.util.Enumeration<String> getInitParameterNames() {
            return java.util.Collections.emptyEnumeration();
        }
    }
}
