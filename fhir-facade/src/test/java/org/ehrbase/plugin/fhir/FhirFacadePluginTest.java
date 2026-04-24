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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginWrapper;

class FhirFacadePluginTest {

    @Test
    void contextPathReturnsFhir(@TempDir Path tempDir) {
        DefaultPluginManager pluginManager = new DefaultPluginManager();
        DefaultPluginDescriptor descriptor =
                new DefaultPluginDescriptor("fhir-facade", "", FhirFacadePlugin.class.getName(), "1.0.0", "", "", "");
        PluginWrapper wrapper =
                new PluginWrapper(pluginManager, descriptor, tempDir, FhirFacadePlugin.class.getClassLoader());
        FhirFacadePlugin plugin = new FhirFacadePlugin(wrapper);

        assertNotNull(plugin);
        assertEquals("/fhir", plugin.getContextPath());
    }
}
