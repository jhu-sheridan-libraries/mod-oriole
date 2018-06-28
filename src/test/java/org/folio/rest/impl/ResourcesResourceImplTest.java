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
import org.folio.rest.client.TenantClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@RunWith(VertxUnitRunner.class)
public class ResourcesResourceImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesResourceImplTest.class);
    private static final String LS = System.lineSeparator();
    private static Vertx vertx;
    private static Async async;
    private static String moduleName;
    private static String moduleVersion;
    private static String moduleId;

    private static int port;
    private static final String TENANT = "test";
    private static final String TOKEN = "test";
    private static final String HOST = "localhost";

    private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
    private final Header ALL_PERM = new Header("X-Okapi-Permissions", "oriole.domain.all");
    private final Header JSON = new Header("Content-Type", "application/json");

    @BeforeClass
    public static void setUpBeforeClass(TestContext context) {
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
        DeploymentOptions options =
                new DeploymentOptions()
                        .setConfig(
                                new JsonObject()
                                        .put("http.port", port)
                                        .put(HttpClientMock2.MOCK_MODE, "true"));
        vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());

        RestAssured.port = port;
        LOGGER.info("resouresTest: setup done. Using port " + port);
    }

    @AfterClass
    public static void tearDownAfterClass(TestContext context) {
        LOGGER.info("Cleaning up after ModuleTest");
        async = context.async();
        vertx.close(
                context.asyncAssertSuccess(
                        res -> {
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
        given().get("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400)
                .body(containsString("Tenant"));
    }

    @Test
    public void testGetWitoutPermissions() {
        // Simple GET without oriole.domain.* permissions
        given().header(TENANT_HEADER)
                .get("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(401)
                .body(containsString("oriole.domain"));
    }

    @Test
    public void testWithTenantBeforeInvoked() {
        // Simple GET request with a tenant, but before
        // we have invoked the tenant interface, so the
        // call will fail (with lots of traces in the log)
        given().header(TENANT_HEADER)
                .header(ALL_PERM)
                .get("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(500);
    }
}

