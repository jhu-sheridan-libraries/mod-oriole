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
import org.junit.Before;
import org.junit.After;
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
public class EZProxyImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectsImplTest.class);
    private static Vertx vertx;
    private static Async async;
    private static String moduleId;

    private static final String TENANT = "test";
    private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
    private final Header CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
    private final Header ACCEPT_HEADER = new Header("Accept", "application/json");

    private final String sage1 = " {\n" +
            "    \"id\" : \"fef6d613-9d8a-41c5-873d-b6ad000dcafe\",\n" +
            "    \"altId\" : \"JHU03917\",\n" +
            "    \"url\" : \"https://journals.sagepub.com/\",\n" +
            "    \"title\" : \"SAGE Journals Online\",\n" +
            "    \"altTitle\" : \"\",\n" +
            "    \"publisher\" : \"SAGE Publications\",\n" +
            "    \"creator\" : \"SAGE Publications\",\n" +
            "    \"provider\" : \"SAGE Publications\",\n" +
            "    \"description\" : \"The SAGE Subject Collections are discipline-specific packages of the most popular peer-reviewed journals in Communication & Media Studies, Criminology, Education, Management & Organization Studies, Materials Science & Engineering, Nursing & Public Health, Political Science, Psychology, Sociology, and Urban Studies & Planning published by SAGE and participating societies.\",\n" +
            "    \"proxy\" : true,\n" +
            "    \"identifier\" : [ ],\n" +
            "    \"terms\" : [ ],\n" +
            "    \"accessRestrictions\" : [ ],\n" +
            "    \"availability\" : [ ],\n" +
            "    \"tags\" : {\n" +
            "      \"tagList\" : [ \"Biomedical Sciences -- Core Databases\", \"Communication + Journalism -- Core Databases\" ]\n" +
            "    }\n" +
            "  }";

    private final String sage2 = "{\n" +
            "    \"id\" : \"1f30d141-30de-4c04-b4a4-e07b4d358aef\",\n" +
            "    \"altId\" : \"JHU07025\",\n" +
            "    \"url\" : \"http://sk.sagepub.com/cases\",\n" +
            "    \"title\" : \"SAGE Business Cases\",\n" +
            "    \"altTitle\" : \"\",\n" +
            "    \"publisher\" : \"Sage Publications\",\n" +
            "    \"creator\" : \"\",\n" +
            "    \"provider\" : \"Sage Publications\",\n" +
            "    \"description\" : \"This global collection of 1,000 proprietary and commissioned business cases from Sage is intended to elicit discussion and inspire researchers to develop their own best practices and prepare for professional success.  Many of these contemporary and newsworthy cases include teaching notes and discussion questions to facilitate classroom use.  Formats range from short vignettes to narrative long form. Cases were written based on field research and publicly available sources.  Click <a href=\\\"http://sk.sagepub.com/business-cases-partners\\\">here</a> to learn more about the contributors.\",\n" +
            "    \"proxy\" : true,\n" +
            "    \"identifier\" : [ ],\n" +
            "    \"terms\" : [ ],\n" +
            "    \"accessRestrictions\" : [ ],\n" +
            "    \"availability\" : [ ],\n" +
            "    \"tags\" : {\n" +
            "      \"tagList\" : [ \"Business -- Cases\", \"My Saved Databases -- Databases\" ]\n" +
            "    }\n" +
            "  }";
    @Before
    public void setUp(TestContext context) throws Exception {
        Locale.setDefault(Locale.US);
        vertx = Vertx.vertx();
        String moduleName = PomReader.INSTANCE.getModuleName().replaceAll("_", "-");
        String moduleVersion = PomReader.INSTANCE.getVersion();
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

        int port = NetworkUtils.nextFreePort();
        DeploymentOptions options = new DeploymentOptions().setConfig(
                new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
        vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());
        RestAssured.port = port;
        LOGGER.info("EZProxyImplTest: setup done. Using port " + port);

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
    public void canCreateResourceAndGetEZProxyStanzas() {

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
        // add resource
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(sage1)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // get resources
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("\"totalRecords\" : 1"));
        // get ezproxy stanzas
        given().header(TENANT_HEADER)
                .header(new Header("Accept", "text/plain"))
                .get("/ezproxy")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("Title SAGE Journals Online (JHU03917)"))
                .body(containsString("# Complete list of IDs for included databases: JHU03917"))
                .body(containsString("URL https://journals.sagepub.com"))
                .body(containsString("DJ sagepub.com"))
                .body(containsString("HJ journals.sagepub.com"));
    }

    @Test
    public void cannotGetEZProxyStanzasWithWrongAcceptHeader() {
        // accept header must by text/plain
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
        // add resource
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(sage1)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // get resources
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("\"totalRecords\" : 1"));
        // get ezproxy stanzas
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/ezproxy")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400);
    }

    @Test
    public void getEZProxyStanzasWithoutResourcesReturnsEmptyBody() {
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
        // get ezproxy stanzas
        given().header(TENANT_HEADER)
                .get("/ezproxy")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString(""));
    }

    @Test
    public void canCreateResourcesWithSharedSubdomainAndGetEZProxyStanzas() {

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
        // add resource one
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(sage1)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // add resource two
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(sage2)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // get resources
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("\"totalRecords\" : 2"));
        // get ezproxy stanzas
        given().header(TENANT_HEADER)
                .header(new Header("Accept", "text/plain"))
                .get("/ezproxy")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("Title SAGE Journals Online (JHU03917)"))
                .body(containsString("# Complete list of IDs for included databases: JHU03917 JHU07025"))
                .body(containsString("DJ sagepub.com"))
                .body(containsString("HJ journals.sagepub.com"))
                .body(containsString("HJ sk.sagepub.com"));
    }
}