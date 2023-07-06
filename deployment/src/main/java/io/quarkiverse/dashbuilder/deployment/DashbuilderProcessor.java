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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.dashbuilder.runtime.DashbuilderRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.GACT;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.webjar.WebJarBuildItem;
import io.quarkus.vertx.http.deployment.webjar.WebJarResourcesFilter.FilterResult;
import io.quarkus.vertx.http.deployment.webjar.WebJarResultsBuildItem;

public class DashbuilderProcessor {

    private final static Logger log = Logger.getLogger(DashbuilderProcessor.class);

    private static final GACT DASHBUILDER_UI_WEBJAR_ARTIFACT_KEY = new GACT("io.quarkiverse.dashbuilder",
            "quarkus-dashbuilder-ui", null,
            "jar");

    private static final String FEATURE = "dashbuilder";

    private static final String SETUP_FILE = "setup.js";

    private static final String SAMPLES_FILE = "samples.json";

    private static final String SAMPLES_URL = "samples";

    private static final String SAMPLES_EXT = ".dash.yaml";

    private static final String DASHBUILDER_STATIC_PATH = "@kie-tools/dashbuilder-client/dist/";

    private static final String DASHBOARDS_WEB_CONTEXT = "__dashboard";

    private static final String SAMPLES_EDIT_URL = "getSamplePath";

    private static final String PROPERTIES_KEY = "properties";

    @BuildStep
    public FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep()
    public void scanDashboardsAndSamples(ApplicationArchivesBuildItem applicationArchives,
            BuildProducer<HotDeploymentWatchedFileBuildItem> hotDeploymentWatchedFiles,
            DashbuilderConfig dashbuilderConfig,
            BuildProducer<DashboardsBuildItem> dashboardsProducer) {

        var dashboardsBuildItem = new DashboardsBuildItem();
        var watchList = new ArrayList<String>();
        var dashboards = dashbuilderConfig.dashboards;

        BiConsumer<String, String> register = (name, content) -> {
            if (dashbuilderConfig.properties.containsKey(name)) {
                var dashboardProperties = dashbuilderConfig.properties.get(name);
                var newContent = replaceProperties(dashboardProperties, name, content);
                dashboardsBuildItem.registerDashboard(name, newContent);
            } else {
                dashboardsBuildItem.registerDashboard(name, content);
            }
        };

        if (dashboards.isEmpty()) {
            applicationArchives.getRootArchive().accept(t -> {
                t.walk(visit -> {
                    var path = visit.getPath();
                    if (isDashboard(path.toString())) {
                        var relativePath = visit.getRelativePath("/");
                        watchList.add(relativePath);
                        try {
                            var name = getDashboardName(path);
                            var content = Files.readString(path);
                            register.accept(name, content);
                        } catch (IOException e) {
                            log.errorv("Not able to load dashboard file {}: {}", relativePath, e.getMessage());
                            log.debug(e);
                        }
                    }
                });
            });
            int n = dashboardsBuildItem.listDashboards().size();
            log.info("Found " + n + " dashboard" + (n == 1 ? "" : "s"));
        } else {
            for (var db : dashboards.get()) {
                var name = getDashboardName(Paths.get(db));
                var content = readDashboardFromClasspath(db);
                if (content != null) {
                    watchList.add(db);
                    register.accept(name, content);
                    log.info("Registered " + db);
                } else {
                    log.warn("Not able to load " + db);
                }
            }
        }

        dashboardsProducer.produce(dashboardsBuildItem);
        watchList.forEach(
                db -> hotDeploymentWatchedFiles.produce(new HotDeploymentWatchedFileBuildItem(db)));
    }

    @BuildStep
    public void buildDashbuilderWebApp(
            DashboardsBuildItem dashboardsBuildItem,
            CurateOutcomeBuildItem curateOutcomeBuildItem,
            DashbuilderConfig dashbuilderConfig,
            BuildProducer<WebJarBuildItem> webJarBuildProducer)
            throws Exception {

        var dashboards = dashboardsBuildItem.listDashboards();
        var buildSetupJs = buildSetupJs(dashboards, dashbuilderConfig).getBytes();
        if (isDev()) {
            var appPath = curateOutcomeBuildItem.getApplicationModel().getApplicationModule().getModuleDir().toString();
            dashboardsBuildItem.setApplicationDirectory(appPath);
        }
        webJarBuildProducer.produce(
                WebJarBuildItem.builder().artifactKey(DASHBUILDER_UI_WEBJAR_ARTIFACT_KEY)
                        .root(DASHBUILDER_STATIC_PATH)
                        .filter((fileName, file) -> {

                            if (fileName.startsWith(SAMPLES_URL) && !includeSamples(dashbuilderConfig)) {
                                return new FilterResult(null, true);
                            }

                            if (fileName.equals(SETUP_FILE)) {
                                return new FilterResult(new ByteArrayInputStream(buildSetupJs), true);
                            }

                            if (fileName.equals(SAMPLES_FILE)) {
                                var filteredSamples = filterSamples(curateOutcomeBuildItem, file);
                                return new FilterResult(filteredSamples, true);
                            }

                            if (fileName.startsWith(SAMPLES_URL + "/") && isDashboard(fileName)) {
                                var sampleId = retrieveSampleId(fileName);
                                var samplesBytes = file.readAllBytes();
                                var content = new String(samplesBytes, StandardCharsets.UTF_8);
                                dashboardsBuildItem.registerSample(sampleId, content);
                                return new FilterResult(new ByteArrayInputStream(samplesBytes), false);
                            }
                            return new FilterResult(file, false);
                        })
                        .build());
    }

    private String retrieveSampleId(String fileName) {
        return Paths.get(fileName).toFile().getName().replaceAll(SAMPLES_EXT, "");

    }

    @BuildStep
    @Record(value = ExecutionTime.RUNTIME_INIT)
    public void registerDashbuilderHandler(
            DashbuilderRecorder dashbuilderRecorder,
            DashboardsBuildItem dashboardsBuildItem,
            BuildProducer<RouteBuildItem> routes,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            WebJarResultsBuildItem webJarResultsBuildItem,
            LaunchModeBuildItem launchMode,
            DashbuilderConfig dashbuilderConfig,
            ShutdownContextBuildItem shutdownContext) {

        var result = webJarResultsBuildItem
                .byArtifactKey(DASHBUILDER_UI_WEBJAR_ARTIFACT_KEY);
        if (result == null) {
            return;
        }

        var dashbuilderWebAppPath = nonApplicationRootPathBuildItem.resolvePath(dashbuilderConfig.path);

        var webAppHandler = dashbuilderRecorder.dashbuilderWebAppHandler(result.getFinalDestination(),
                dashbuilderWebAppPath,
                result.getWebRootConfigurations(), shutdownContext);

        var dashboardsHandler = dashbuilderRecorder.dashboardsHandler(DASHBOARDS_WEB_CONTEXT,
                dashboardsBuildItem.getDashboards());

        var dashboardsContext = dashbuilderConfig.path + "/" + DASHBOARDS_WEB_CONTEXT;

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .route(dashbuilderConfig.path)
                .displayOnNotFoundPage("Dashbuilder Web App")
                .routeConfigKey("quarkus.dashbuilder.path")
                .handler(webAppHandler)
                .build());

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .route(dashbuilderConfig.path + "*")
                .handler(webAppHandler)
                .build());

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .route(dashboardsContext)
                .handler(dashboardsHandler)
                .build());

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .route(dashboardsContext + "*")
                .handler(dashboardsHandler)
                .build());

        if (isDev()) {
            var samplesHandler = dashbuilderRecorder.samplesHandler(dashboardsBuildItem.getAppDir(),
                    dashboardsBuildItem.getSamples());
            routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                    .route(SAMPLES_EDIT_URL)
                    .handler(samplesHandler)
                    .build());

            routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                    .route(SAMPLES_EDIT_URL + "*")
                    .handler(samplesHandler)
                    .build());
        }
    }

    boolean isDashboard(String path) {
        return path.endsWith(".dash.yaml") ||
                path.endsWith(".dash.yml") ||
                path.endsWith(".dash.json");
    }

    ByteArrayInputStream filterSamples(CurateOutcomeBuildItem curateOutcomeBuildItem, InputStream file) {
        try {
            var mapper = new ObjectMapper();
            var jsonObj = mapper.readTree(file);
            var resultNode = mapper.createObjectNode();
            curateOutcomeBuildItem.getApplicationModel()
                    .getDependencies()
                    .stream()
                    .filter(ResolvedDependency::isRuntimeExtensionArtifact)
                    .map(ResolvedDependency::getArtifactId)
                    .filter(jsonObj::has)
                    .forEach(artifact -> resultNode.set(artifact, jsonObj.get(artifact)));
            return new ByteArrayInputStream(mapper.writeValueAsBytes(resultNode));
        } catch (IOException e) {
            throw new RuntimeException("Error generating samples file", e);
        }
    }

    String buildSetupJs(Set<String> dashboardsSet, DashbuilderConfig config) {
        var dashboardsJsArray = dashboardsSet.stream()
                .map(p -> "'" + p + "'")
                .collect(Collectors.joining(",", "[", "]"));
        var setupJsSb = new StringBuffer();
        setupJsSb.append("dashbuilder = {\n");
        setupJsSb.append("   \"mode\": \"CLIENT\"\n");
        if (!dashboardsSet.isEmpty()) {
            setupJsSb.append(",   \"path\": \"" + DASHBOARDS_WEB_CONTEXT + "\"\n");
            setupJsSb.append(",   \"dashboards\": " + dashboardsJsArray + "\n");
        }

        if (includeSamples(config)) {
            setupJsSb.append(",  \"samplesUrl\": \"" + SAMPLES_URL + "\"\n");
        }
        // edit is only available in DEV mode
        if (isDev()) {
            setupJsSb.append(",  \"samplesEditService\": \"/q/" + SAMPLES_EDIT_URL + "\"\n");
        }

        setupJsSb.append("}");
        return setupJsSb.toString();
    }

    String getDashboardName(Path dashboard) {
        var fileName = dashboard.getFileName().toString();
        var dotIndex = fileName.indexOf(".");
        return dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);
    }

    String readDashboardFromClasspath(String db) {
        var resource = db;
        if (!db.startsWith("/")) {
            resource = "/" + resource;
        }
        var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (is != null) {
            try {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.errorv("Not able to read {}: {}", db, e.getMessage());
                log.debug(e);
            }
        }
        return null;
    }

    private boolean isDev() {
        return LaunchMode.current() == LaunchMode.DEVELOPMENT;
    }

    /**
     *
     * Samples are included only in dev or if users wants to include it
     *
     * @param config
     *        Dashbuilder config
     * @return
     *         true if samples should be included in the build, false otherwise
     */
    private boolean includeSamples(DashbuilderConfig config) {
        return isDev() || config.includeSamples;
    }

    private String replaceProperties(Map<String, String> dashboardProperties, String name, String content) {
        try {
            var yaml = new Yaml();
            Map<String, Object> loadedYaml = yaml.load(content);
            @SuppressWarnings("unchecked")
            var props = (Map<String, String>) loadedYaml.get(PROPERTIES_KEY);

            var config = ConfigProvider.getConfig();

            if (props != null && !props.isEmpty()) {

                // getting quarkus properties
                props.keySet().stream()
                        .map(config::getConfigValue)
                        .filter(prop -> prop.getValue() != null)
                        .forEach(prop -> props.put(prop.getName(), prop.getValue()));

                // properties set by user
                props.keySet().stream()
                        .filter(dashboardProperties::containsKey)
                        .forEach(key -> props.put(key, dashboardProperties.get(key)));

                return yaml.dumpAsMap(loadedYaml);
            }
        } catch (Exception e) {
            log.errorv("Error replacing properties for dashboard {0}: {1}", name, e.getMessage());
            log.debug(e);
        }
        return content;
    }
}
