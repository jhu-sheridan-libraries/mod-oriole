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
import org.folio.rest.jaxrs.resource.OrioleResources;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.facets.FacetField;
import org.folio.rest.persist.facets.FacetManager;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ResourcesImpl implements OrioleResources {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesImpl.class);
    public static final String RESOURCE_TABLE = "resource";
    private static final String ID_FIELD_NAME = "id";
    private static final String RESOURCE_SCHEMA_NAME = "ramls/schemas/resource.json";
    private static final String LOCATION_PREFIX = "/oriole-resources/";
    private String RESOURCE_SCHEMA = null;
    private final Messages messages = Messages.getInstance();

    public ResourcesImpl(Vertx vertx, String tennantId) {
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
    public void getOrioleResources(
            String query,
            int offset,
            int limit,
            List<String> facets,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        PostgresClient postgresClient = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
        CQLWrapper cql;
        try {
            cql = ApiUtil.getCQL(query, limit, offset, RESOURCE_TABLE, RESOURCE_SCHEMA);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            asyncResultHandler.handle(Future.failedFuture(e));
            return;
        }
        List<FacetField> facetList = FacetManager.convertFacetStrings2FacetFields(facets, "jsonb");
        postgresClient.get(RESOURCE_TABLE, Resource.class, new String[] {"*"}, cql, true, false,
                facetList, reply -> {
            if (reply.succeeded()) {
                ResourceCollection resources = new ResourceCollection();
                List<Resource> resourceList = reply.result().getResults();
                resources.setResources(resourceList);
                Integer total = reply.result().getResultInfo().getTotalRecords();
                resources.setTotalRecords(total);
                resources.setResultInfo(reply.result().getResultInfo());
                asyncResultHandler.handle(
                        Future.succeededFuture(GetOrioleResourcesResponse.respond200WithApplicationJson(resources)));
            } else {
                ValidationHelper.handleError(reply.cause(), asyncResultHandler);
            }
        });
    }

    @Override
    public void postOrioleResources(
            String lang,
            Resource entity,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
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
                        PostOrioleResourcesResponse.HeadersFor201 headers =
                                PostOrioleResourcesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret);
                        asyncResultHandler.handle(Future.succeededFuture(
                                PostOrioleResourcesResponse.respond201WithApplicationJson(stream, headers)));
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                }));
    }

    @Override
    public void getOrioleResourcesByResourceId(
            String resourceId,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        if (resourceId.equals("_self")) {
            return;
        }
        getOneResource(resourceId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                asyncResultHandler.handle(Future.succeededFuture(
                        GetOrioleResourcesByResourceIdResponse.respond200WithApplicationJson(res.result())));
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(
                                GetOrioleResourcesByResourceIdResponse.respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(
                                GetOrioleResourcesByResourceIdResponse.respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default:
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    @Override
    public void deleteOrioleResourcesByResourceId(
            String resourceId,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        getOneResource(resourceId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                getPostgresClient(okapiHeaders, vertxContext).delete(RESOURCE_TABLE, resourceId,
                        reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 1) {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    DeleteOrioleResourcesByResourceIdResponse.respond204()));
                        } else {
                            LOGGER.error(messages.getMessage(lang, MessageConsts.DeletedCountError,
                                    1, reply.result().getUpdated()));
                            asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleResourcesByResourceIdResponse
                                    .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.DeletedCountError,
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
                        asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleResourcesByResourceIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER: // bad request
                        asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleResourcesByResourceIdResponse
                                .respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default: // typically INTERNAL
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    @Override
    public void putOrioleResourcesByResourceId(
            String resourceId,
            String lang,
            Resource entity,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        if (entity.getId() == null) {
            entity.setId(resourceId);
            LOGGER.debug("No ID in the resource. Take the one from the link");
        }
        if (!entity.getId().equals(resourceId)) {
            Errors valErr = ValidationHelper.createValidationErrorMessage("id",
                    entity.getId(), "Can not change Id");
            asyncResultHandler.handle(Future.succeededFuture(
                    PutOrioleResourcesByResourceIdResponse.respond422WithApplicationJson(valErr)));
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
                                    PutOrioleResourcesByResourceIdResponse.respond500WithTextPlain(
                                            messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                        } else {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    PutOrioleResourcesByResourceIdResponse.respond204()));
                        }
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(PutOrioleResourcesByResourceIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER: // bad request
                        asyncResultHandler.handle(Future.succeededFuture(PutOrioleResourcesByResourceIdResponse
                                .respond400WithTextPlain(res.cause().getMessage())));
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


    private static PostgresClient getPostgresClient(Map<String, String> okapiHeaders, Context vertxContext) {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        return PostgresClient.getInstance(vertxContext.owner(), tenantId);
    }

}
