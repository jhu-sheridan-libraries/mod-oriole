package org.folio.rest.impl;

import io.vertx.core.*;
import org.apache.commons.io.IOUtils;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Resource;
import org.folio.rest.jaxrs.model.ResourceCollection;
import org.folio.rest.jaxrs.resource.ResourcesResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
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
    private final Messages messages = Messages.getInstance();

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
            LOGGER.error(e.getMessage());
            asyncResultHandler.handle(Future.failedFuture(e));
            return;
        }
        postgresClient.get(RESOURCE_TABLE, ResourceCollection.class, new String[] {"*"}, cql, true, false,
                reply -> {
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
            throws Exception {
        if (resourceId.equals("_self")) {
            return;
        }
        getOneResource(resourceId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                asyncResultHandler.handle(Future.succeededFuture(
                        GetResourcesByResourceIdResponse.withJsonOK(res.result())));
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(
                                GetResourcesByResourceIdResponse.withPlainNotFound(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(
                                GetResourcesByResourceIdResponse.withPlainBadRequest(res.cause().getMessage())));
                        break;
                    default:
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    @Override
    public void deleteResourcesByResourceId(
            String resourceId,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext)
            throws Exception {
        getOneResource(resourceId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                getPostgresClient(okapiHeaders, vertxContext).delete(RESOURCE_TABLE, resourceId,
                        reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 1) {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    DeleteResourcesByResourceIdResponse.withNoContent()));
                        } else {
                            LOGGER.error(messages.getMessage(lang, MessageConsts.DeletedCountError,
                                    1, reply.result().getUpdated()));
                            asyncResultHandler.handle(Future.succeededFuture(DeleteResourcesByResourceIdResponse
                                    .withPlainNotFound(messages.getMessage(lang, MessageConsts.DeletedCountError,
                                            1, reply.result().getUpdated()))));
                        }
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
            } else {
                switch (res.getType()) {
                    // ValidationHelper can not handle these error types
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(DeleteResourcesByResourceIdResponse
                                .withPlainNotFound(res.cause().getMessage())));
                        break;
                    case USER: // bad request
                        asyncResultHandler.handle(Future.succeededFuture(DeleteResourcesByResourceIdResponse
                                .withPlainBadRequest(res.cause().getMessage())));
                        break;
                    default: // typically INTERNAL
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    @Override
    public void putResourcesByResourceId(
            String resourceId,
            String lang,
            Resource entity,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext)
            throws Exception {
        if (entity.getId() == null) {
            entity.setId(resourceId);
            LOGGER.debug("No ID in the resource. Take the one from the link");
        }
        if (!entity.getId().equals(resourceId)) {
            Errors valErr = ValidationHelper.createValidationErrorMessage("id",
                    entity.getId(), "Can not change Id");
            asyncResultHandler.handle(Future.succeededFuture(
                    PutResourcesByResourceIdResponse.withJsonUnprocessableEntity(valErr)));
            return;
        }

        getOneResource(resourceId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                Resource oldRes = res.result();
                getPostgresClient(okapiHeaders, vertxContext).update(RESOURCE_TABLE, entity, resourceId,
                        reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 0) {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    PutResourcesByResourceIdResponse.withPlainInternalServerError(
                                            messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                        } else {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    PutResourcesByResourceIdResponse.withNoContent()));
                        }
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(PutResourcesByResourceIdResponse
                                .withPlainNotFound(res.cause().getMessage())));
                        break;
                    case USER: // bad request
                        asyncResultHandler.handle(Future.succeededFuture(PutResourcesByResourceIdResponse
                                .withPlainBadRequest(res.cause().getMessage())));
                        break;
                    default: // typically INTERNAL
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    /**
     * Helper to get a resource. Fetches the record from database.
     * @param resourceId
     * @param okapiHeaders
     * @param context
     * @param resp a callback that returns the resource, or an error
     */
    private void getOneResource(
            String resourceId,
            Map<String, String> okapiHeaders,
            Context context,
            Handler<ExtendedAsyncResult<Resource>> resp) {
        Criterion c = new Criterion(
                new Criteria().addField(ID_FIELD_NAME).setJSONB(false).setOperation("=").setValue("'"+resourceId+"'"));
        getPostgresClient(okapiHeaders, context).get(RESOURCE_TABLE, Resource.class, c, true,
                reply -> {
                    if (reply.succeeded()) {
                        List<Resource> resources = (List<Resource>)reply.result().getResults();
                        if (resources.isEmpty()) {
                            resp.handle(new Failure<>(
                                    ErrorType.NOT_FOUND, "Resource " + resourceId + " not found"));
                        } else {
                            Resource r = resources.get(0);
                            resp.handle(new Success<>(r));
                        }
                    } else {
                        String error = PgExceptionUtil.badRequestMessage(reply.cause());
                        if (error == null) {
                            resp.handle(new Failure<>(ErrorType.INTERNAL, ""));
                        } else {
                            resp.handle(new Failure<Resource>(ErrorType.USER, error));

                        }
                    }
                });
    }


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
            //cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
        } else {
            cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
        }
        return new CQLWrapper(cql2pgJson, query)
                .setLimit(new Limit(limit))
                .setOffset(new Offset(offset));
    }
}
