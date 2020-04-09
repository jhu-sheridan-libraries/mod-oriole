package org.folio.rest.impl;

import io.vertx.core.*;
import org.apache.commons.io.IOUtils;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Library;
import org.folio.rest.jaxrs.model.LibraryCollection;
import org.folio.rest.jaxrs.resource.OrioleLibraries;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
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
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LibrariesImpl implements OrioleLibraries {
    private static final Logger LOGGER = LoggerFactory.getLogger(LibrariesImpl.class);
    public static final String LIBRARY_TABLE = "library";
    private static final String ID_FIELD_NAME = "id";
    private static final String LIBRARY_SCHEMA_NAME = "ramls/schemas/library.json";
    private static final String LIBRARY_PREFIX = "/oriole-libraries/";
    private String LIBRARY_SCHEMA = null;
    private final Messages messages = Messages.getInstance();

    public LibrariesImpl(Vertx vertx, String tennantId) {
        if (LIBRARY_SCHEMA == null) {
            initCQLValidation();
        }
        PostgresClient.getInstance(vertx, tennantId).setIdField(ID_FIELD_NAME);
    }

    private void initCQLValidation() {
        String path = LIBRARY_SCHEMA_NAME;
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            assert is != null;
            LIBRARY_SCHEMA = IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Unable to load schema - " + path
                    + ", validation of query fields will not be active");
        }
    }

    @Override
    public void getOrioleLibraries(
            String query,
            int offset,
            int limit,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        PostgresClient postgresClient = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
        CQLWrapper cql;
        try {
            cql = ApiUtil.getCQL(query, limit, offset, LIBRARY_TABLE, LIBRARY_SCHEMA);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            asyncResultHandler.handle(Future.failedFuture(e));
            return;
        }
        postgresClient.get(LIBRARY_TABLE, Library.class, new String[] {"*"}, cql, true, false,
                reply -> {
            if (reply.succeeded()) {
                LibraryCollection libraries = new LibraryCollection();
                List<Library> libraryList = reply.result().getResults();
                libraries.setLibraries(libraryList);
                Integer total = reply.result().getResultInfo().getTotalRecords();
                libraries.setTotalRecords(total);
                asyncResultHandler.handle(
                        Future.succeededFuture(GetOrioleLibrariesResponse.respond200WithApplicationJson(libraries)));
            } else {
                ValidationHelper.handleError(reply.cause(), asyncResultHandler);
            }
        });
    }

    @Override
    public void postOrioleLibraries(
            String lang,
            Library entity,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        String id = entity.getId();
        if (id == null || id.isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }
        PostgresClient postgresClient = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
        vertxContext.runOnContext(v ->
                postgresClient.save(LIBRARY_TABLE, id, entity, reply -> {
                    if (reply.succeeded()) {
                        String ret = reply.result();
                        entity.setId(ret);
                        OutStream stream = new OutStream();
                        stream.setData(entity);
                        PostOrioleLibrariesResponse.HeadersFor201 headers =
                                PostOrioleLibrariesResponse.headersFor201().withLocation(LIBRARY_PREFIX + ret);
                        asyncResultHandler.handle(Future.succeededFuture(
                                PostOrioleLibrariesResponse.respond201WithApplicationJson(stream, headers)));
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                }));
    }

    @Override
    public void deleteOrioleLibraries(
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        String tennantId = TenantTool.tenantId(okapiHeaders);
        try {
            vertxContext.runOnContext(v -> {
                PostgresClient postgresClient = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
                postgresClient.mutate(String.format("DELETE FROM %s_%s.%s", tennantId, "mod_oriole", LIBRARY_TABLE),
                        reply -> {
                    if (reply.succeeded()) {
                        asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLibrariesResponse.noContent().build()));
                    } else {
                        asyncResultHandler.handle(Future.succeededFuture(
                                DeleteOrioleLibrariesResponse.respond500WithTextPlain(reply.cause().getMessage())));
                    }
                });
            });
        } catch (Exception e) {
            asyncResultHandler.handle(Future.failedFuture(e));
        }
    }

    @Override
    public void getOrioleLibrariesByLibraryId(
            String libraryId,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        if (libraryId.equals("_self")) {
            return;
        }
        getOneLibrary(libraryId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                asyncResultHandler.handle(Future.succeededFuture(
                        GetOrioleLibrariesByLibraryIdResponse.respond200WithApplicationJson(res.result())));
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(
                                GetOrioleLibrariesByLibraryIdResponse.respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(
                                GetOrioleLibrariesByLibraryIdResponse.respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default:
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    @Override
    public void deleteOrioleLibrariesByLibraryId(
            String libraryId,
            String lang,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        getOneLibrary(libraryId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                ApiUtil.getPostgresClient(okapiHeaders, vertxContext).delete(LIBRARY_TABLE, libraryId, reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 1) {
                            asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLibrariesByLibraryIdResponse.respond204()));
                        } else {
                            LOGGER.error(messages.getMessage(lang, MessageConsts.DeletedCountError, 1,
                                    reply.result().getUpdated()));
                            asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLibrariesByLibraryIdResponse.
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
                        asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLibrariesByLibraryIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(DeleteOrioleLibrariesByLibraryIdResponse
                                .respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default:
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);

                }
            }
        });
    }

    @Override
    public void putOrioleLibrariesByLibraryId(
            String libraryId,
            String lang,
            Library entity,
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        if (entity.getId() == null) {
            entity.setId(libraryId);
            LOGGER.debug("No ID in the library. Take the one from the link");
        }
        if (!entity.getId().equals(libraryId)) {
            Errors valErr = ValidationHelper.createValidationErrorMessage("id", entity.getId(), "Can't change Id");
            asyncResultHandler.handle(Future.succeededFuture(PutOrioleLibrariesByLibraryIdResponse.respond422WithApplicationJson(valErr)));
            return;
        }
        getOneLibrary(libraryId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                Library oldLibrary = res.result();
                ApiUtil.getPostgresClient(okapiHeaders, vertxContext).update(LIBRARY_TABLE, entity, libraryId, reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 0) {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    PutOrioleLibrariesByLibraryIdResponse.respond500WithTextPlain(
                                            messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                        } else {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    PutOrioleLibrariesByLibraryIdResponse.respond204()));
                        }
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(PutOrioleLibrariesByLibraryIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER: // bad request
                        asyncResultHandler.handle(Future.succeededFuture(PutOrioleLibrariesByLibraryIdResponse
                                .respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default: // typically INTERNAL
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    /**
     * Helper to get a library. Fetches the record from database.
     * @param libraryId
     * @param okapiHeaders
     * @param context
     * @param resp a callback that returns the library, or an error
     */
    private void getOneLibrary(
            String libraryId,
            Map<String, String> okapiHeaders,
            Context context,
            Handler<ExtendedAsyncResult<Library>> resp) {
        Criterion c = new Criterion(
                new Criteria().addField(ID_FIELD_NAME).setJSONB(false).setOperation("=").setValue("'"+libraryId+"'"));
        ApiUtil.getPostgresClient(okapiHeaders, context).get(LIBRARY_TABLE, Library.class, c, true,
                reply -> {
                    if (reply.succeeded()) {
                        List<Library> Libraries = reply.result().getResults();
                        if (Libraries.isEmpty()) {
                            resp.handle(new Failure<>(
                                    ErrorType.NOT_FOUND, "Library " + libraryId + " not found"));
                        } else {
                            Library l = Libraries.get(0);
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
