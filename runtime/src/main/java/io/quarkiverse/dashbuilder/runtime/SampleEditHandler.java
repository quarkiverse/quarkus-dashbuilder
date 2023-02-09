/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates.
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
package io.quarkiverse.dashbuilder.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

public class SampleEditHandler implements Handler<RoutingContext> {

    private final String SAMPLE_ID_PARAM = "sampleId";

    private final String USER_SAMPLES_PATH = "src/main/resources/dashboards";

    private final String USER_SAMPLE_EXT = ".dash.yaml";

    private final static Logger log = Logger.getLogger(SampleEditHandler.class);

    private Map<String, String> samples;

    private String appDir;

    public SampleEditHandler() {
    }

    public SampleEditHandler(String appDir, Map<String, String> samples) {
        this.appDir = appDir;
        this.samples = samples;
    }

    @Override
    public void handle(RoutingContext event) {

        var request = event.request();
        var response = event.response();

        if (request.method() != HttpMethod.GET) {
            response.setStatusCode(405);
            response.end();
            return;
        }

        var sampleId = request.getParam(SAMPLE_ID_PARAM);
        if (sampleId != null && samples.containsKey(sampleId)) {
            var samplePathOp = storeOrGetExistingSample(sampleId);
            if (samplePathOp.isPresent()) {
                response.putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
                response.setStatusCode(200);
                response.end(samplePathOp.get());
            } else {
                response.setStatusCode(500);
                response.end();
            }

        } else {
            response.setStatusCode(404);
            response.end();
        }
    }

    private Optional<String> storeOrGetExistingSample(String sampleId) {
        var dir = Paths.get(appDir, USER_SAMPLES_PATH, sampleId + USER_SAMPLE_EXT);
        if (!dir.toFile().exists()) {
            try {
                Files.createDirectories(dir.getParent());
                Files.writeString(dir, samples.get(sampleId));
            } catch (IOException e) {
                log.warn("Not able to create user sample dashboard.", e);
                return Optional.empty();
            }
        }
        return Optional.of(dir.toAbsolutePath().toString());
    }

}
