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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class LibrariesImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LibrariesImplTest.class);
    private static final String LS = System.lineSeparator();
    private static Vertx vertx;
    private static Async async;
    private static String moduleId;

    private static final String TENANT = "test";
    private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
    private final Header CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
    private final Header ACCEPT_HEADER = new Header("Accept", "application/json");

    private final String library = "{"
            + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
            + "\"name\" : \"welch\"" + LS
            + "}";

    @Before
    public void setUp(TestContext context) {
        Locale.setDefault(Locale.US);
        vertx = Vertx.vertx();
        String moduleName = PomReader.INSTANCE.getModuleName().replaceAll("_", "-");
        String moduleVersion = PomReader.INSTANCE.getVersion();
        moduleId = moduleName + "-" + moduleVersion;
        LOGGER.info("Test setup starting for " + moduleId);
        try {
            PostgresClient.setIsEmbedded(true);
            PostgresClient.getInstance(vertx).startEmbeddedPostgres();
            // no  longer needed since we are dropping the tenant in each unit test
            // PostgresClient.getInstance(vertx).execute("DELETE FROM test_mod_oriole.library", context.asyncAssertSuccess());
        } catch (IOException e) {
            e.printStackTrace();
            context.fail(e);
            return;
        }
        int port = NetworkUtils.nextFreePort();
        DeploymentOptions options = new DeploymentOptions().setConfig(
                new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
        vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());
        RestAssured.port = port;
        LOGGER.info("librariesTest: setup done. Using port " + port);
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
        // see if the module is running and we can talk to it
        given().get("/admin/health").then().log().all().statusCode(200);
    }

    @Test
    public void testGetWithoutTenant() {
        // get libraries without a tenant header
        given().header(ACCEPT_HEADER)
                .get("/oriole-libraries")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400)
                .body(containsString("Tenant"));
    }

    @Test
    public void testPostAndFetch() {
        // drop tenant if it exists
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(new Header("Accept", "text/plain"))
                .delete("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(anyOf(is(204), is(400)));
        // add tenant
        String tenants = "{\"module_to\":\"" + moduleId + "\"}";
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(tenants)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // add library
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(library)
                .post("/oriole-libraries")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // get library
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole-libraries")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("welch"))
                .body(containsString("\"totalRecords\" : 1"));
    }
}