package org.folio.rest.impl;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
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
public class ResourcesResourceImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesResourceImplTest.class);
    private final int port = Integer.parseInt(System.getProperty("port", "8081"));
    private static final String LS = System.lineSeparator();
    private final Header TENANT = new Header("X-Okapi-Tenant", "orioletest");
    private final Header ALL_PERM = new Header("X-Okapi-Permissions", "oriole.domain.all");
    private final Header JSON = new Header("Content-Type", "application/json");
    Vertx vertx;
    Async async;
    private String moduleName;
    private String moduleVersion;
    private String moduleId;

    @Before
    public void setUp(TestContext context) {
        Locale.setDefault(Locale.US);
        vertx = Vertx.vertx();
        moduleName = PomReader.INSTANCE.getModuleName()
                .replaceAll("_", "-");
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
        JsonObject conf = new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true");
        LOGGER.info("resouresTest: Deploying " + RestVerticle.class.getName() + " " + Json.encode(conf));
        DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
        vertx.deployVerticle(RestVerticle.class.getName(), opt, context.asyncAssertSuccess());
        RestAssured.port = port;
        LOGGER.info("resouresTest: setup done. Using port " + port);
    }

    @After
    public void tearDown(TestContext context) {
        LOGGER.info("Cleaning up after ModuleTest");
        async = context.async();
        vertx.close(context.asyncAssertSuccess(res -> {
            PostgresClient.stopEmbeddedPostgres();
            async.complete();
        }));
    }

    @Test
    public void test(TestContext context) {
        async = context.async();
        LOGGER.info("resourcesTest starting");

        given().get("/resources").then().log().ifValidationFails().statusCode(400).body(containsString("Tenant"));

        async.complete();
    }
}
