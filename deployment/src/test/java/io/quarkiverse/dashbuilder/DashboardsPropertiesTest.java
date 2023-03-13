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
package io.quarkiverse.dashbuilder;

import static org.hamcrest.Matchers.containsString;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class DashboardsPropertiesTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.dashbuilder.dashboards=db.dash.yaml\n" +
                                            "other.prop=replaced\n" +
                                            "quarkus.dashbuilder.properties.db.prop=replaced\n"),
                            "application.properties")
                    .addAsResource(
                            new StringAsset("properties:\n    prop: value\n    other.prop: value\npages:\n - components:"),
                            "db.dash.yaml"));

    @Test
    public void shouldReplacePropertyInDashboard() {
        RestAssured.when().get("/dashboards/__dashboard/db").then().statusCode(200)
                .body(containsString("prop: replaced"))
                .body(containsString("other.prop: replaced"));
    }

}
