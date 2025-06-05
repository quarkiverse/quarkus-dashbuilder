/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quarkiverse.dashbuilder.deployment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot
@ConfigMapping(prefix = "quarkus.dashbuilder")
public interface DashbuilderConfig {

    /**
     * The web path where Dashbuilder dashboards will be available.
     * By default, this value will be resolved as a path relative to
     * `${quarkus.http.non-application-root-path}`.
     */
    @WithDefault("/dashboards")
    String path();

    /**
     * Comma separated list of dashboards to be rendered by Dashbuilder.
     * If not used then Dashbuilder scan all `*.dash.(yaml|yml|json)` files
     */
    @WithDefault("")
    Optional<List<String>> dashboards();

    /**
     * If true samples will also be included in the final JAR
     */
    @WithDefault("false")
    boolean includeSamples();

    /**
     * Dashboards properties
     */
    Map<String, Map<String, String>> properties();

}