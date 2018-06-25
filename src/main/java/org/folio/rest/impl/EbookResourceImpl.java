package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.commons.io.IOUtils;
import org.folio.rest.jaxrs.resource.EbooksResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.jaxrs.model.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.Map;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

public class EbookResourceImpl implements EbooksResource {
    private static final Logger LOGGER = LoggerFactory.getLogger("mod-oriole");
    private static final String ID_FIELD = "id";
    private String EBOOK_SCHEMA = null;
    private static final String EBOOK_SCHEMA_NAME = "ramls/ebook.json";
    private static final String EBOOK_TABLE = "ebook_data";

    public EbookResourceImpl(Vertx vertx, String tenantId) {
        if (EBOOK_SCHEMA == null) {
            initCQLValidation();
        }
        PostgresClient.getInstance(vertx, tenantId).setIdField(ID_FIELD);
    }

    private void initCQLValidation() {
        try {
            EBOOK_SCHEMA = IOUtils.toString(getClass().getClassLoader()
                    .getResourceAsStream(EBOOK_SCHEMA_NAME), "UTF-8");
        } catch (Exception e) {
            LOGGER.error("unable to load schema - " + EBOOK_SCHEMA_NAME
                    + ", validation of query fields will no be active");
        }
    }

    @Override
    public void getEbooksByBookTitle(String bookTitle, String author, BigDecimal publicationYear,
                                     BigDecimal rating, String isbn, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
            throws Exception {

    }

    @Override
    public void putEbooksByBookTitle(String bookTitle, String accessToken, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
            throws Exception {
        LOGGER.info("putEbooksByBookTitle");
    }

    private static PostgresClient getPosteresClient(Map<String, String> okapiHeaders, Context vertxContext) {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(OKAPI_HEADER_TENANT));
        return PostgresClient.getInstance(vertxContext.owner(), tenantId);
    }
}
