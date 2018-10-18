package org.folio.rest.impl;

import io.vertx.core.*;
import org.apache.commons.io.IOUtils;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.LocationCollection;
import org.folio.rest.jaxrs.resource.OrioleLocations;
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

public class LocationsImpl implements OrioleLocations {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationsImpl.class);
    public static final String LOCATIONS_TABLE = "location";
    private static final String ID_FIELD_NAME = "id";
    private static final String LOCATION_SCHEMA_NAME = "ramls/schemas/location.json";
    private static final String LOCATION_PREFIX = "/oriole-locations/";
    private String LOCATION_SCHEMA = null;
    private final Messages messages = Messages.getInstance();

    public LocationsImpl(Vertx vertx, String tennantId) {
        if (LOCATION_SCHEMA == null) {
            initCQLValidation();
        }
        PostgresClient.getInstance(vertx, tennantId).setIdField(ID_FIELD_NAME);
    }

    private void initCQLValidation() {
        String path = LOCATION_SCHEMA_NAME;
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            LOCATION_SCHEMA = IOUtils.toString(is, "UTF-8");
        } catch (Exception e) {
            LOGGER.error("Unable to load schema - " + path
                    + ", validation of query fields will not be active");
        }
    }

    @Override
    public void getOrioleLocations(
            String query,
            int offset,
            int limit,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        PostgresClient postgresClient = getPostgresClient(okapiHeaders, vertxContext);
        CQLWrapper cql = null;
        try {
            cql = getCQL(query, limit, offset, LOCATION_SCHEMA);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            asyncResultHandler.handle(Future.failedFuture(e));
            return;
        }
        postgresClient.get(LOCATIONS_TABLE, Location.class, new String[] {"*"}, cql, true, false,
                reply -> {
            if (reply.succeeded()) {
                LocationCollection locations = new LocationCollection();
                List<Location> locationList = reply.result().getResults();
                locations.setLocations(locationList);
                Integer total = reply.result().getResultInfo().getTotalRecords();
                locations.setTotalRecords(total);
                asyncResultHandler.handle(
                        Future.succeededFuture(GetOrioleLocationsResponse.respond200WithApplicationJson(locations)));
            } else {
                ValidationHelper.handleError(reply.cause(), asyncResultHandler);
            }
        });
    }

    @Override
    public void postOrioleLocations(
            String lang,
            Location entity,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        String id = entity.getId();
        if (id == null || id.isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }
        PostgresClient postgresClient = getPostgresClient(okapiHeaders, vertxContext);
        vertxContext.runOnContext(v ->
                postgresClient.save(LOCATIONS_TABLE, id, entity, reply -> {
                    if (reply.succeeded()) {
                        Object ret = reply.result();
                        entity.setId((String) ret);
                        OutStream stream = new OutStream();
                        stream.setData(entity);
                        PostOrioleLocationsResponse.HeadersFor201 headers =
                                PostOrioleLocationsResponse.headersFor201().withLocation(LOCATION_PREFIX + ret);
                        asyncResultHandler.handle(Future.succeededFuture(
                                PostOrioleLocationsResponse.respond201WithApplicationJson(stream, headers)));
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                }));
    }

    @Override
    public void deleteOrioleLocations(
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        String tennantId = TenantTool.tenantId(okapiHeaders);
        try {
            vertxContext.runOnContext(v -> {
                PostgresClient postgresClient = getPostgresClient(okapiHeaders, vertxContext);
                postgresClient.mutate(String.format("DELETE FROM %s_%s.%s", tennantId, "mod_oriole", LOCATIONS_TABLE),
                        reply -> {
                    if (reply.succeeded()) {
                        asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLocationsResponse.noContent().build()));
                    } else {
                        asyncResultHandler.handle(Future.succeededFuture(
                                DeleteOrioleLocationsResponse.respond500WithTextPlain(reply.cause().getMessage())));
                    }
                });
            });
        } catch (Exception e) {
            asyncResultHandler.handle(Future.failedFuture(e));
        }
    }

    @Override
    public void getOrioleLocationsByLocationId(
            String locationId,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        if (locationId.equals("_self")) {
            return;
        }
        getOneLocation(locationId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                asyncResultHandler.handle(Future.succeededFuture(
                        GetOrioleLocationsByLocationIdResponse.respond200WithApplicationJson(res.result())));
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(
                                GetOrioleLocationsByLocationIdResponse.respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(
                                GetOrioleLocationsByLocationIdResponse.respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default:
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    @Override
    public void deleteOrioleLocationsByLocationId(
            String locationId,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        getOneLocation(locationId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                getPostgresClient(okapiHeaders, vertxContext).delete(LOCATIONS_TABLE, locationId, reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 1) {
                            asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLocationsByLocationIdResponse.respond204()));
                        } else {
                            LOGGER.error(messages.getMessage(lang, MessageConsts.DeletedCountError, 1,
                                    reply.result().getUpdated()));
                            asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLocationsByLocationIdResponse.
                                    respond404WithTextPlain(messages.getMessage(lang, MessageConsts.DeletedCountError,
                                            1, reply.result().getUpdated()))));
                        }
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLocationsByLocationIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLocationsByLocationIdResponse
                                .respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default:
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);

                }
            }
        });
    }

    @Override
    public void putOrioleLocationsByLocationId(
            String locationId,
            String lang,
            Location entity,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        if (entity.getId() == null) {
            entity.setId(locationId);
            LOGGER.debug("No ID in the location. Take the one from the link");
        }
        if (!entity.getId().equals(locationId)) {
            Errors valErr = ValidationHelper.createValidationErrorMessage("id", entity.getId(), "Can't change Id");
            asyncResultHandler.handle(Future.succeededFuture(PutOrioleLocationsByLocationIdResponse.respond422WithApplicationJson(valErr)));
            return;
        }
        getOneLocation(locationId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                Location oldLocation = res.result();
                getPostgresClient(okapiHeaders, vertxContext).update(LOCATIONS_TABLE, entity, locationId, reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 0) {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    PutOrioleLocationsByLocationIdResponse.respond500WithTextPlain(
                                            messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                        } else {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    PutOrioleLocationsByLocationIdResponse.respond204()));
                        }
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(PutOrioleLocationsByLocationIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER: // bad request
                        asyncResultHandler.handle(Future.succeededFuture(PutOrioleLocationsByLocationIdResponse
                                .respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default: // typically INTERNAL
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    private static PostgresClient getPostgresClient(Map<String, String> okapiHeaders, Context vertxContext) {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        return PostgresClient.getInstance(vertxContext.owner(), tenantId);
    }

    private CQLWrapper getCQL(String query, int limit, int offset, String schema)
            throws IOException, FieldException, SchemaException {
        CQL2PgJSON cql2pgJson = null;
        if (schema != null) {
            cql2pgJson = new CQL2PgJSON(LOCATIONS_TABLE + ".jsonb", schema);
            //cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
        } else {
            cql2pgJson = new CQL2PgJSON(LOCATIONS_TABLE + ".jsonb");
        }
        return new CQLWrapper(cql2pgJson, query)
                .setLimit(new Limit(limit))
                .setOffset(new Offset(offset));
    }

    /**
     * Helper to get a location. Fetches the record from database.
     * @param locationId
     * @param okapiHeaders
     * @param context
     * @param resp a callback that returns the location, or an error
     */
    private void getOneLocation(
            String locationId,
            Map<String, String> okapiHeaders,
            Context context,
            Handler<ExtendedAsyncResult<Location>> resp) {
        Criterion c = new Criterion(
                new Criteria().addField(ID_FIELD_NAME).setJSONB(false).setOperation("=").setValue("'"+locationId+"'"));
        getPostgresClient(okapiHeaders, context).get(LOCATIONS_TABLE, Location.class, c, true,
                reply -> {
                    if (reply.succeeded()) {
                        List<Location> Locations = (List<Location>)reply.result().getResults();
                        if (Locations.isEmpty()) {
                            resp.handle(new Failure<>(
                                    ErrorType.NOT_FOUND, "Location " + locationId + " not found"));
                        } else {
                            Location l = Locations.get(0);
                            resp.handle(new Success<>(l));
                        }
                    } else {
                        String error = PgExceptionUtil.badRequestMessage(reply.cause());
                        if (error == null) {
                            resp.handle(new Failure<>(ErrorType.INTERNAL, ""));
                        } else {
                            resp.handle(new Failure<>(ErrorType.USER, error));

                        }
                    }
                });
    }
}
