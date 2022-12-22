
package io.quarkiverse.dashbuilder.it;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class DashbuilderExtensionTest {

    @Test
    public void shouldRegisterScannedDashboard() {
        RestAssured.when().get("/dashboards/setup.js").then().statusCode(200)
                .body(containsString("\"dashboards\": ['sample']"));
        RestAssured.when().get("/dashboards/index.html").then().body(containsString("org.dashbuilder.DashbuilderRuntime"));
    }

    @Test
    public void shouldLoadScannedDashboard() {
        RestAssured.when().get("/dashboards/?import=sample").then().statusCode(200)
                .body(containsString("DashBuilder Runtime: Dashboards"));
    }

}
