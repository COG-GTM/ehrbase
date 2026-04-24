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

import org.pf4j.PluginWrapper;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * PF4J plugin that boots a HAPI FHIR R4 server inside EHRbase's plugin system.
 * The plugin is registered under the context path {@code /fhir} relative to the
 * configured {@code plugin-manager.plugin-context-path}.
 */
public class FhirFacadePlugin extends org.ehrbase.plugin.WebMvcEhrBasePlugin {

    public FhirFacadePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getContextPath() {
        return "/fhir";
    }

    @Override
    protected DispatcherServlet buildDispatcherServlet() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(FhirServerConfig.class);
        return new DispatcherServlet(context);
    }
}
