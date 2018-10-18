package org.folio.rest.impl;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@RunWith(VertxUnitRunner.class)
public class LocationsImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationsImplTest.class);
    private static final String LS = System.lineSeparator();
    private static Vertx vertx;
    private static Async async;
    private static String moduleName;
    private static String moduleVersion;
    private static String moduleId;

    private static int port;
    private static final String TENANT = "test";
    private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
    private final Header JSON = new Header("Content-Type", "application/json");

    private final String location = "{"
            + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
            + "\"name\" : \"welch\"" + LS
            + "}";

    @Before
    public void setUp(TestContext context) {
        Locale.setDefault(Locale.US);
        vertx = Vertx.vertx();
        moduleName = PomReader.INSTANCE.getModuleName().replaceAll("_", "-");
        moduleVersion = PomReader.INSTANCE.getVersion();
        moduleId = moduleName + "-" + moduleVersion;
        LOGGER.info("Test setup starting for " + moduleId);
        try {
            PostgresClient.setIsEmbedded(true);
            PostgresClient.getInstance(vertx).startEmbeddedPostgres();
        } catch (IOException e) {
            e.printStackTrace();
            context.fail(e);
            return;
        }
        port = NetworkUtils.nextFreePort();
        DeploymentOptions options = new DeploymentOptions().setConfig(
                new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
        vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());
        RestAssured.port = port;
        LOGGER.info("locationsTest: setup done. Using port " + port);
    }

    @After
    public void tearDown(TestContext context) {
        async = context.async();
        vertx.close(context.asyncAssertSuccess(res -> {
            PostgresClient.stopEmbeddedPostgres();
            async.complete();
        }));
    }

    @Test
    public void testModuleRunning() {
        // Simple GET request to see the module is running and we can talk to it.
        given().get("/admin/health").then().log().all().statusCode(200);
    }

    @Test
    public void testGetWithoutTenant() {
        // Simple GET request without a tanant
        given().get("/oriole-locations")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400)
                .body(containsString("Tenant"));
    }
}