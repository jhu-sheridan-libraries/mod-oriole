package org.folio.rest.impl;

import io.vertx.core.*;
import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Resource;
import org.folio.rest.jaxrs.model.ResourceCollection;
import org.folio.rest.jaxrs.resource.ResourcesResource;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ResourcesResourceImpl implements ResourcesResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesResourceImpl.class);
    public static final String RESOURCE_TABLE = "oriole_data";
    private static final String ID_FIELD_NAME = "id";
    private static final String RESOURCE_SCHEMA_NAME = "ramls/schemas/resource.json";
    private static final String LOCATION_PREFIX = "/resources/";
    private String RESOURCE_SCHEMA = null;

    public ResourcesResourceImpl(Vertx vertx, String tennantId) {
        if (RESOURCE_SCHEMA == null) {
            initCQLValidation();
        }
        PostgresClient.getInstance(vertx, tennantId).setIdField(ID_FIELD_NAME);
    }

    private void initCQLValidation() {
        String path = RESOURCE_SCHEMA_NAME;
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            RESOURCE_SCHEMA = IOUtils.toString(is, "UTF-8");
        } catch (Exception e) {
            LOGGER.error("Unable to load schema - " + path
                    + ", validation of query fields will not be active");
        }
    }

    @Override
    public void getResources(
            String query,
            int offset,
            int limit,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext)
            throws Exception {
        PostgresClient postgresClient = getPostgresClient(okapiHeaders, vertxContext);
        CQLWrapper cql = null;
        try {
            cql = getCQL(query, limit, offset, RESOURCE_SCHEMA);
        } catch (Exception e) {
            asyncResultHandler.handle(Future.failedFuture(e));
        }
        String perms = okapiHeaders.get(RestVerticle.OKAPI_HEADER_PERMISSIONS);
        if (perms == null || perms.isEmpty()) {
            LOGGER.error("No " + RestVerticle.OKAPI_HEADER_PERMISSIONS
                    + " - check oriole.domain.* permissions");
            asyncResultHandler.handle(Future.succeededFuture(
                    GetResourcesResponse.withPlainUnauthorized("No oriole.domain.* permissions")));
            return;
        }
        postgresClient.get(RESOURCE_TABLE, ResourceCollection.class, new String[] {"*"}, cql, true, false,
                reply -> {
            LOGGER.info("REPLY: " + reply.toString());
            if (reply.succeeded()) {
                ResourceCollection resources = new ResourceCollection();
                List<Resource> resourceList = (List<Resource>) reply.result().getResults();
                resources.setResources(resourceList);
                Integer total = reply.result().getResultInfo().getTotalRecords();
                resources.setTotalRecords(total);
                asyncResultHandler.handle(
                        Future.succeededFuture(GetResourcesResponse.withJsonOK(resources)));
            } else {
                ValidationHelper.handleError(reply.cause(), asyncResultHandler);
            }
        });
    }

    @Override
    public void postResources(
            String lang,
            Resource entity,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext)
            throws Exception {
        String id = entity.getId();
        if (id == null || id.isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }
        PostgresClient postgresClient = getPostgresClient(okapiHeaders, vertxContext);
        vertxContext.runOnContext(v ->
                postgresClient.save(RESOURCE_TABLE, id, entity, reply -> {
                    if (reply.succeeded()) {
                        Object ret = reply.result();
                        entity.setId((String) ret);
                        OutStream stream = new OutStream();
                        stream.setData(entity);
                        asyncResultHandler.handle(Future.succeededFuture(
                                PostResourcesResponse.withJsonCreated(LOCATION_PREFIX + ret, stream)));
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                }));
    }

    @Override
    public void getResourcesByResourceId(
            String resourceId,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext)
            throws Exception {}

    @Override
    public void deleteResourcesByResourceId(
            String resourceId,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext)
            throws Exception {}

    @Override
    public void putResourcesByResourceId(
            String resourceId,
            String lang,
            Resource entity,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext)
            throws Exception {}

    private static PostgresClient getPostgresClient(
            Map<String, String> okapiHeaders, Context vertxContext) {
        String tenantId =
                TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        return PostgresClient.getInstance(vertxContext.owner(), tenantId);
    }

    private CQLWrapper getCQL(String query, int limit, int offset, String schema)
            throws IOException, FieldException, SchemaException {
        CQL2PgJSON cql2pgJson = null;
        if (schema != null) {
            cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb", schema);
        } else {
            cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
        }
        return new CQLWrapper(cql2pgJson, query)
                .setLimit(new Limit(limit))
                .setOffset(new Offset(offset));
    }
}
