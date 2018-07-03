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
import static org.hamcrest.Matchers.is;

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
    private final Header JSON = new Header("Content-Type", "application/json");

    private final Header USER9 = new Header("X-Okapi-User-Id",
            "99999999-9999-4999-9999-999999999999");
    private final Header USER19 = new Header("X-Okapi-User-Id",
            "11999999-9999-4999-9999-999999999911");  // One that is not found in the mock data
    private final Header USER8 = new Header("X-Okapi-User-Id",
            "88888888-8888-4888-8888-888888888888");
    private final Header USER7 = new Header("X-Okapi-User-Id",
            "77777777-7777-4777-a777-777777777777");

    private final String resource = "{"
            + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
                + "\"title\" : \"PubMed\"," + LS
                + "\"link\" : \"https://www.ncbi.nlm.nih.gov/pubmed/\"," + LS
                + "\"description\" : \"PubMed is a free search engine accessing primarily the MEDLINE database of references and abstracts on life sciences and biomedical topics.\"}" + LS;


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

    @After
    public void tearDown(TestContext context) {
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
    public void testWithTenantBeforeInvoked() {
        // Simple GET request with a tenant, but before
        // we have invoked the tenant interface, so the
        // call will fail (with lots of traces in the log)
        given().header(TENANT_HEADER)
                .get("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(500);
    }

    @Test
    public void testInitializeDatabase() {
        String tenants = "{\"module_to\":\"" + moduleId + "\"}";
        LOGGER.info("About to call the tenant interface " + tenants);
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(tenants)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
    }

    @Test
    public void testEmptyList() {
        // initialize tenant first
        String tenants = "{\"module_to\":\"" + moduleId + "\"}";
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(tenants)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);

        // this should retrieve a blank list
        given().header(TENANT_HEADER)
                .get("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("\"resources\" : [ ]"));
    }

    @Test
    public void testPostMalformedResources() {
        String bad1 = "This is not json";
        given().header(TENANT_HEADER) // no content-type header
                .body(bad1)
                .post("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400)
                .body(containsString("Content-type"));

        given().header(TENANT_HEADER)
                .header(JSON)
                .body(bad1)
                .post("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400)
                .body(containsString("Json content error"));

        String bad2 = resource.replaceFirst("}", ")"); // make it invalid json
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(bad2)
                .post("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400)
                .body(containsString("Json content error"));

        String bad3 = resource.replaceFirst("link", "creatorUsername");
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(bad3)
                .post("/resources")
                .then()
                .log().ifValidationFails()
                .statusCode(422)
                // English error message for Locale.US, see @Before
                .body("errors[0].message", is("may not be null"))
                .body("errors[0].parameters[0].key", is("link"));

        String badfieldDoc = resource.replaceFirst("link", "UnknownFieldName");
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(badfieldDoc)
                .post("/resources")
                .then()
                .log().ifValidationFails()
                .statusCode(422)
                .body(containsString("Unrecognized field"));
    }

    @Test
    public void testInvalidUUID() {
        String tenants = "{\"module_to\":\"" + moduleId + "\"}";
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(tenants)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        String bad4 = resource.replaceAll("-1111-", "-2-");
        given().header(TENANT_HEADER)
                //.header(USER9)
                .header(JSON)
                .body(bad4)
                .post("/resources")
                .then()
                .log().ifValidationFails()
                .statusCode(422)
                .body(containsString("invalid input syntax for type uuid"));

    }

    @Test
    public void testPostAndFetch(TestContext context) {
        // initialize tenant
        String tenants = "{\"module_to\":\"" + moduleId + "\"}";
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(tenants)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // Post
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(resource)
                .post("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);

        // Fetch the posted resource
        given().header(TENANT_HEADER)
                .get("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("PubMed"))
                .body(containsString("\"totalRecords\" : 1"));
    }

    @Test
    public void testFetchById() {
        // initialize tenant
        String tenants = "{\"module_to\":\"" + moduleId + "\"}";
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(tenants)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // Post
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(resource)
                .post("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);

        given().header(TENANT_HEADER)
                .get("/resources/11111111-1111-1111-a111-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body(containsString("PubMed"));

        given().header(TENANT_HEADER)
                .get("/resources/99111111-1111-1111-a111-111111111199")
                .then()
                .log().ifValidationFails()
                .statusCode(404)
                .body(containsString("not found"));

        given().header(TENANT_HEADER)
                .get("/resources/777")
                .then()
                .log().ifValidationFails()
                .statusCode(400);

    }

    @Test
    public void testDelete(TestContext context) {
        // initialize tenant
        String tenants = "{\"module_to\":\"" + moduleId + "\"}";
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(tenants)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // Post
        given().header(TENANT_HEADER)
                .header(JSON)
                .body(resource)
                .post("/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);

        // A failed delete with bad UUID
        given().header(TENANT_HEADER)
                .delete("/resources/11111111-3-1111-333-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(400);

        // not found
        given().header(TENANT_HEADER)
                .delete("/resources/11111111-2222-3333-a444-555555555555")
                .then()
                .log().ifValidationFails()
                .statusCode(404);

        // delete it
        given().header(TENANT_HEADER)
                .delete("/resources/11111111-1111-1111-a111-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(204);

        // No longer there
        given().header(TENANT_HEADER)
                .delete("/resources/11111111-1111-1111-a111-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(404);
    }
}

