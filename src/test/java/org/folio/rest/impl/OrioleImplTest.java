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
import static org.hamcrest.Matchers.*;

@RunWith(VertxUnitRunner.class)
public class OrioleImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrioleImplTest.class);
    private static final String LS = System.lineSeparator();
    private static Vertx vertx;
    private static Async async;
    private static String moduleId;

    private static final String TENANT = "test";
    private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
    private final Header CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
    private final Header ACCEPT_HEADER = new Header("Accept", "application/json");
    private final String TENANT_BODY = "{\"module_to\":\"" + moduleId + "\"}";
    private final int CREATED = 201;

    private final String resource = "{"
            + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
            + "\"title\" : \"PubMed\"," + LS
            + "\"url\" : \"https://www.ncbi.nlm.nih.gov/pubmed/\"," + LS
            + "\"description\" : \"PubMed is a free search engine accessing primarily the MEDLINE database of references and abstracts on life sciences and biomedical topics.\"}" + LS;


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
            // PostgresClient.getInstance(vertx).execute("DELETE FROM test_mod_oriole.resource", context.asyncAssertSuccess());
        } catch (IOException e) {
            e.printStackTrace();
            context.fail(e);
            return;
        }
        int port = NetworkUtils.nextFreePort();
        DeploymentOptions options =
                new DeploymentOptions()
                        .setConfig(
                                new JsonObject()
                                        .put("http.port", port)
                                        .put(HttpClientMock2.MOCK_MODE, "true"));
        vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());

        RestAssured.port = port;
        LOGGER.info("resourcesTest: setup done. Using port " + port);
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
        // get resources without a tenant header
        given()
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources")
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
        // drop tenant if it exists
        // drop tenant if it exists
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(new Header("Accept", "text/plain"))
                .delete("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(anyOf(is(204), is(400)));
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(new Header("Accept", "text/plain"))
                .delete("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(anyOf(is(204), is(400)));
        given().header(new Header("X-Okapi-Tenant", "foobar"))
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(500);
    }

    @Test
    public void testInitializeDatabase() {
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);
    }

    @Test
    public void testEmptyList() {
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);
        // this should retrieve a blank list
        given().header(TENANT_HEADER)
                .get("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("\"resources\" : [ ]"));
    }

    @Test
    public void testPostMalformedResources() {

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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);

        String bad1 = "This is not json";
        given().header(TENANT_HEADER) // no content-type header
                .body(bad1)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400)
                .body(containsString("Content-type"));

        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(bad1)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400)
                .body(containsString("Json content error"));

        String bad2 = resource.replaceFirst("}", ")"); // make it invalid json
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(bad2)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(400)
                .body(containsString("Json content error"));

        String bad3 = resource.replaceFirst("url", "provider");
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(bad3)
                .post("/oriole/resources")
                .then()
                .log().ifValidationFails()
                .statusCode(422)
                // English error message for Locale.US, see @Before
                .body("errors[0].message", is("may not be null"))
                .body("errors[0].parameters[0].key", is("url"));

        String badfieldDoc = resource.replaceFirst("url", "UnknownFieldName");
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(badfieldDoc)
                .post("/oriole/resources")
                .then()
                .log().ifValidationFails()
                .statusCode(422)
                .body(containsString("Unrecognized field"));
    }

    @Test
    public void testInvalidUUID() {
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);
        String bad4 = resource.replaceAll("-1111-", "-2-");
        given().header(TENANT_HEADER)
                //.header(USER9)
                .header(CONTENT_TYPE_HEADER)
                .body(bad4)
                .post("/oriole/resources")
                .then()
                .log().ifValidationFails()
                .statusCode(422)
                .body(containsString("invalid input syntax for type uuid"));
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);
        // add resource
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(resource)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // get resources
        given().header(TENANT_HEADER)
                .get("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200)
                .body(containsString("PubMed"))
                .body(containsString("\"totalRecords\" : 1"));
    }

    @Test
    public void testFetchById() {
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);
        // add resource
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(resource)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // get a known resource by uuid
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources/11111111-1111-1111-a111-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body(containsString("PubMed"));
        // try to get resource by uuid that doesn't exist
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources/99111111-1111-1111-a111-111111111199")
                .then()
                .log().ifValidationFails()
                .statusCode(404)
                .body(containsString("not found"));
        // try to get resource using a non-uuid
        given().header(TENANT_HEADER)
                .get("/oriole/resources/777")
                .then()
                .log().ifValidationFails()
                .statusCode(400);
    }

    @Test
    public void testDelete() {
        // drop tenant if it exists
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(new Header("Accept", "text/plain"))
                .delete("/_/tenant")
                .then()
                .log().ifValidationFails()
                .statusCode(anyOf(is(204), is(400)));
        // add tenant
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log().ifValidationFails()
                .statusCode(CREATED);
        // add resource
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(resource)
                .post("/oriole/resources")
                .then()
                .log().ifValidationFails()
                .statusCode(201);
        // failed delete with bad UUID
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .delete("/oriole/resources/11111111-3-1111-333-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(400);
        // uuid not found
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .delete("/oriole/resources/11111111-2222-3333-a444-555555555555")
                .then()
                .log().ifValidationFails()
                .statusCode(404);
        // delete existing resource with a valid uuid
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .delete("/oriole/resources/11111111-1111-1111-a111-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(204);
        // try to delete again
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .delete("/oriole/resources/11111111-1111-1111-a111-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(404);
    }

    @Test
    public void testUpdate() {
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);
        // add resource
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(resource)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);

        // update resource
        //  no Creator fields, RMB should keep them, once we mark them as read-only
        final String updated1 = "{"
                + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
                + "\"title\" : \"PubMed\"," + LS
                + "\"url\" : \"https://www.ncbi.nlm.nih.gov/pubmed/\"," + LS
                + "\"description\" : \"PubMed lists journal articles and more back to 1947.\"}" + LS;

        // ID doesn't match
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(updated1)
                .put("/oriole/resources/22222222-2222-2222-a222-222222222222") // wrong one
                .then()
                .log().ifValidationFails()
                .statusCode(422)
                .body(containsString("Can not change Id"));

        // Invalid UUID
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(updated1)
                .put("/oriole/resources/11111111-222-1111-2-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(422);

        // ID not found
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(updated1.replaceAll("1", "3"))
                .put("/oriole/resources/33333333-3333-3333-a333-333333333333")
                .then()
                .log().ifValidationFails()
                .statusCode(404)
                .body(containsString("not found"));

        // This one should work
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(updated1)
                .put("/oriole/resources/11111111-1111-1111-a111-111111111111")
                .then()
                .log().ifValidationFails()
                .statusCode(204);
    }

    @Test
    public void testGoodQuery() {
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);
        // add resource
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(resource)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // title search
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources?query=title=PubMed")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body(containsString("free search engine"))
                .body(containsString("id"));
    }

    @Test
    public void testBadQuery() {
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);
        // add resource
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(resource)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // bad query
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources?query=VERYBADQUERY")
                .then()
                .log().ifValidationFails()
                .statusCode(422)
                .body(containsString("no serverChoiceIndexes defined"));
    }

    @Test
    public void testUnknownFieldQuery() {
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);
       // search title field
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources?query=UNKNOWNFIELD=foobar")
                .then()
                .log().ifValidationFails()
                .statusCode(422)
                .body(containsString("is not present in index"));

    }

    @Test
    public void testSubjectAndFacet() {
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
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .body(TENANT_BODY)
                .post("/_/tenant")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(CREATED);

        String resource = "{"
                + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
                + "\"title\" : \"PubMed\"," + LS
                + "\"url\" : \"https://www.ncbi.nlm.nih.gov/pubmed/\"," + LS
                + "\"description\" : \"PubMed is a free search engine accessing primarily the MEDLINE database of references and abstracts on life sciences and biomedical topics.\"," + LS
                + "\"terms\" : [{\"subject\": {\"id\": \"5711b80f-9e72-4d5c-9414-e98b454d61fa\", \"fastId\": \"fst01692913\", \"term\": \"Video recordings\", \"uri\": \"http://id.worldcat.org/fast/1692913\", \"facet\": \"Form\"}, \"category\": \"none\", \"score\": 1}]}" + LS;

        // add resource
        given().header(TENANT_HEADER)
                .header(CONTENT_TYPE_HEADER)
                .header(ACCEPT_HEADER)
                .body(resource)
                .post("/oriole/resources")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201);
        // title search
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources?query=title=PubMed")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body(containsString("free search engine"))
                .body(containsString("id"));
        // facets
        given().header(TENANT_HEADER)
                .header(ACCEPT_HEADER)
                .get("/oriole/resources?facets=active&facets=terms[].subject.term")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body(containsString("facetValues"))
                .body(containsString("\"count\" : 1"))
                .body(containsString("\"value\" : \"Video recordings\""));

        // Facet search
//        String query = "*\\\"facet\\\": \\\"Form\\\"*";
//        given().header(TENANT_HEADER)
//                .header(ACCEPT_HEADER)
//                .get("/oriole/resources?facets=active&facets=terms[].subject.term&query=terms==\"" + query + "\"")
//                .then()
//                .log().ifValidationFails()
//                .statusCode(200)
//                .body(containsString("facetValues"))
//                .body(containsString("\"totalRecords\" : 1"))
//                .body(containsString("\"value\" : \"Video recordings\""));
//        query = "*\\\"facet\\\": \\\"Topical\\\"*";
//        given().header(TENANT_HEADER)
//                .header(ACCEPT_HEADER)
//                .get("/oriole/resources?facets=active&facets=terms[].subject.term&query=terms==\"" + query + "\"")
//                .then()
//                .log().ifValidationFails()
//                .statusCode(200)
//                .body(containsString("\"totalRecords\" : 0"));
//        given().header(TENANT_HEADER)
//                .get("/oriole/resources?facets=active&facets=terms[].subject.term&query=terms.subject.term==\"*recordings*\"")
//                .then()
//                .log().ifValidationFails()
//                .statusCode(200)
//                .body(containsString("\"totalRecords\" : 1"));
    }
}

