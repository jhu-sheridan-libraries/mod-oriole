package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import org.apache.commons.io.IOUtils;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.Oriole;
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
import java.util.*;

public class OrioleImpl implements Oriole {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrioleImpl.class);
    public static final String RESOURCE_TABLE = "resource";
    public static final String SUBJECT_TABLE = "subject";
    public static final String TAG_VIEW = "tag_view";
    private static final String ID_FIELD_NAME = "id";
    private static final String RESOURCE_SCHEMA_PATH = "ramls/schemas/resource.json";
    private static final String SUBJECT_SCHEMA_PATH = "ramls/schemas/subject.json";
    private static final String LOCATION_PREFIX = "/oriole/resources/";
    private static final String SUBJECT_PREFIX = "/oriole/subjects/";
    private String RESOURCE_SCHEMA = null;
    private String SUBJECT_SCHEMA = null;
    private final Messages messages = Messages.getInstance();

    public OrioleImpl(Vertx vertx, String tennantId) {
        if (RESOURCE_SCHEMA == null || SUBJECT_SCHEMA == null) {
            initCQLValidation();
        }
        PostgresClient.getInstance(vertx, tennantId).setIdField(ID_FIELD_NAME);
    }

    private void initCQLValidation() {
        try {
            InputStream ris = getClass().getClassLoader().getResourceAsStream(RESOURCE_SCHEMA_PATH);
            RESOURCE_SCHEMA = IOUtils.toString(ris, "UTF-8");
        } catch (Exception e) {
            LOGGER.error("Unable to load schema - " + RESOURCE_SCHEMA_PATH
                    + ", validation of query fields will not be active");
        }
        try {
            InputStream sis = getClass().getClassLoader().getResourceAsStream(SUBJECT_SCHEMA_PATH);
            SUBJECT_SCHEMA = IOUtils.toString(sis, "UTF-8");
        } catch (Exception e) {
            LOGGER.error("Unable to load schema - " + SUBJECT_SCHEMA_PATH
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
        postgresClient.get(
                RESOURCE_TABLE,
                Resource.class,
                new String[] {"*"},
                cql,
                true,
                false,
                facetList,
                reply -> {
                    if (reply.succeeded()) {
                        ResourceCollection resources = new ResourceCollection();
                        List<Resource> resourceList = reply.result().getResults();
                        while (resourceList.remove(null));
                        // There's a weird bug (possibly) in folio. When there is a URL
                        // param for "facets", it returns "Facet" objects
                        // in the resourceList. The following code doesn't resolve it. They're
                        // just left here for future reference.
                        // For example: /oriole/resources?facets=tags.tagList[]
                        Iterator<Resource> it = resourceList.iterator();
                        while (it.hasNext()) {
                            Object o = it.next();
                            if (o instanceof Facet) {
                                resourceList.remove(o);
                            } else {
                                ((Resource)o).setKeywords(null);
                            }

                        }

                        // Hide passwords unless it's from a logged in user

                        resources.setResources(resourceList);
                        Integer total = reply.result().getResultInfo().getTotalRecords();
                        resources.setTotalRecords(total);
                        resources.setResultInfo(reply.result().getResultInfo());
                        asyncResultHandler.handle(
                                Future.succeededFuture(
                                        GetOrioleResourcesResponse.respond200WithApplicationJson(
                                                resources)));
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
    }

    @Override
    public void getOrioleDatabases(String query, int offset, int limit, List<String> facets, String lang, Map<String,
            String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
        postgresClient.get(
                RESOURCE_TABLE,
                Resource.class,
                new String[] {"*"},
                cql,
                true,
                false,
                facetList,
                reply -> {
                    if (reply.succeeded()) {
                        ResourceCollection resources = new ResourceCollection();
                        List<Resource> resourceList = reply.result().getResults();
                        while (resourceList.remove(null));
                        // There's a weird bug (possibly) in folio. When there is a URL
                        // param for "facets", it returns "Facet" objects
                        // in the resourceList. The following code doesn't resolve it. They're
                        // just left here for future reference.
                        // For example: /oriole/resources?facets=tags.tagList[]
                        Iterator<Resource> it = resourceList.iterator();
                        while (it.hasNext()) {
                            Object o = it.next();
                            if (o instanceof Facet) {
                                resourceList.remove(o);
                            } else {
                                ((Resource)o).setKeywords(null);
                            }
                        }

                        // Hide passwords unless it's from a logged in user

                        resources.setResources(resourceList);
                        Integer total = reply.result().getResultInfo().getTotalRecords();
                        resources.setTotalRecords(total);
                        resources.setResultInfo(reply.result().getResultInfo());
                        asyncResultHandler.handle(
                                Future.succeededFuture(
                                        GetOrioleResourcesResponse.respond200WithApplicationJson(
                                                resources)));
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
    }

    @Override
    public void postOrioleDatabases(String lang, Resource entity, Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

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
        String altId = entity.getAltId();
        if (altId == null || altId.isEmpty()) {
            getLastAltId(okapiHeaders, vertxContext, res -> {
                if (res.succeeded()) {
                    String lastAltId = res.result();
                    entity.setAltId(getNextAltId(lastAltId));
                    saveResource(entity, okapiHeaders, asyncResultHandler, vertxContext);
                } else {

                }
            });
        } else {
            saveResource(entity, okapiHeaders, asyncResultHandler, vertxContext);
        }
    }

    private void saveResource(Resource entity,
                              Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler,
                              Context vertxContext) {
        // extract subjects and store it in the join table
        String id = entity.getId();
        vertxContext.runOnContext(
                v ->
                        getPostgresClient(okapiHeaders, vertxContext).save(
                                RESOURCE_TABLE,
                                id,
                                entity,
                                reply -> {
                                    if (reply.succeeded()) {
                                        Object ret = reply.result();
                                        entity.setId((String) ret);
                                        OutStream stream = new OutStream();
                                        stream.setData(entity);
                                        PostOrioleResourcesResponse.HeadersFor201 headers =
                                                PostOrioleResourcesResponse.headersFor201()
                                                        .withLocation(LOCATION_PREFIX + ret);
                                        asyncResultHandler.handle(
                                                Future.succeededFuture(
                                                        PostOrioleResourcesResponse
                                                                .respond201WithApplicationJson(
                                                                        stream, headers)));
                                    } else {
                                        ValidationHelper.handleError(
                                                reply.cause(), asyncResultHandler);
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

    @Override
    public void getOrioleTags(
            Map<String, String> okapiHeaders,
            Handler<AsyncResult<Response>> asyncResultHandler,
            Context vertxContext) {
        vertxContext.runOnContext(v -> {  // TODO: Is this necessary?
            PostgresClient client = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
            String sql = "SELECT tag FROM " + TAG_VIEW + " ORDER BY tag";
            client.select(sql, (reply) -> {
                if (reply.succeeded()) {
                    TagCollection tagCollection = new TagCollectionImpl();
                    List<JsonArray> results = reply.result().getResults();
                    List<String> tags = new ArrayList<>();
                    for (JsonArray result: results) {
                        tags.add(result.getString(0));
                    }
                    tagCollection.setTags(tags);
                    asyncResultHandler.handle(
                            Future.succeededFuture(GetOrioleTagsResponse.respond200WithApplicationJson(tagCollection)));
                } else {
                    ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                }
            });
        });
    }

    @Override
    public void getOrioleSubjects(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
        PostgresClient postgresClient = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
        CQLWrapper cql;
        try {
            cql = ApiUtil.getCQL(query, limit, offset, SUBJECT_TABLE, SUBJECT_SCHEMA_PATH);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            asyncResultHandler.handle(Future.failedFuture(e));
            return;
        }
        postgresClient.get(SUBJECT_TABLE, Subject.class, new String[] {"*"}, cql, true, false,
                reply -> {
                    if (reply.succeeded()) {
                        SubjectCollection subjects = new SubjectCollection();
                        List<Subject> subjectList = reply.result().getResults();
                        subjects.setSubjects(subjectList);
                        Integer total = reply.result().getResultInfo().getTotalRecords();
                        subjects.setTotalRecords(total);
                        asyncResultHandler.handle(
                                Future.succeededFuture(Oriole.GetOrioleSubjectsResponse.respond200WithApplicationJson(subjects)));
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
    }

    @Override
    public void postOrioleSubjects(String lang, Subject entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
        String id = entity.getId();
        if (id == null || id.isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }
        PostgresClient postgresClient = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
        vertxContext.runOnContext(v ->
                postgresClient.save(SUBJECT_TABLE, id, entity, reply -> {
                    if (reply.succeeded()) {
                        Object ret = reply.result();
                        entity.setId((String) ret);
                        OutStream stream = new OutStream();
                        stream.setData(entity);
                        Oriole.PostOrioleSubjectsResponse.HeadersFor201 headers =
                                Oriole.PostOrioleSubjectsResponse.headersFor201().withLocation(SUBJECT_PREFIX + ret);
                        asyncResultHandler.handle(Future.succeededFuture(
                                Oriole.PostOrioleSubjectsResponse.respond201WithApplicationJson(stream, headers)));
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                }));
    }

    @Override
    public void deleteOrioleSubjects(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
        String tennantId = TenantTool.tenantId(okapiHeaders);
        try {
            vertxContext.runOnContext(v -> {
                PostgresClient postgresClient = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
                postgresClient.mutate(String.format("DELETE FROM %s_%s.%s", tennantId, "mod_oriole", SUBJECT_TABLE),
                        reply -> {
                            if (reply.succeeded()) {
                                asyncResultHandler.handle(Future.succeededFuture(Oriole.DeleteOrioleSubjectsResponse.noContent().build()));
                            } else {
                                asyncResultHandler.handle(Future.succeededFuture(
                                        Oriole.DeleteOrioleSubjectsResponse.respond500WithTextPlain(reply.cause().getMessage())));
                            }
                        });
            });
        } catch (Exception e) {
            asyncResultHandler.handle(Future.failedFuture(e));
        }
    }

    @Override
    public void getOrioleSubjectsBySubjectId(String subjectId, String lang, Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
        if (subjectId.equals("_self")) {
            return;
        }
        getOneSubject(subjectId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                asyncResultHandler.handle(Future.succeededFuture(
                        Oriole.GetOrioleSubjectsBySubjectIdResponse.respond200WithApplicationJson(res.result())));
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(
                                Oriole.GetOrioleSubjectsBySubjectIdResponse.respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(
                                Oriole.GetOrioleSubjectsBySubjectIdResponse.respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default:
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    @Override
    public void putOrioleSubjectsBySubjectId(String subjectId, String lang, Subject entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
        if (entity.getId() == null) {
            entity.setId(subjectId);
            LOGGER.debug("No ID in the Subject. Take the one from the link");
        }
        if (!entity.getId().equals(subjectId)) {
            Errors valErr = ValidationHelper.createValidationErrorMessage("id", entity.getId(), "Can't change Id");
            asyncResultHandler.handle(Future.succeededFuture(Oriole.PutOrioleSubjectsBySubjectIdResponse.respond422WithApplicationJson(valErr)));
            return;
        }
        getOneSubject(subjectId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                Subject oldSubject = res.result();
                ApiUtil.getPostgresClient(okapiHeaders, vertxContext).update(SUBJECT_TABLE, entity, subjectId, reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 0) {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    Oriole.PutOrioleSubjectsBySubjectIdResponse.respond500WithTextPlain(
                                            messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                        } else {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    Oriole.PutOrioleSubjectsBySubjectIdResponse.respond204()));
                        }
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(Oriole.PutOrioleSubjectsBySubjectIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER: // bad request
                        asyncResultHandler.handle(Future.succeededFuture(Oriole.PutOrioleSubjectsBySubjectIdResponse
                                .respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default: // typically INTERNAL
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);
                }
            }
        });
    }

    @Override
    public void deleteOrioleSubjectsBySubjectId(String subjectId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
        getOneSubject(subjectId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                ApiUtil.getPostgresClient(okapiHeaders, vertxContext).delete(SUBJECT_TABLE, subjectId, reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 1) {
                            asyncResultHandler.handle(Future.succeededFuture(Oriole.DeleteOrioleSubjectsBySubjectIdResponse.respond204()));
                        } else {
                            LOGGER.error(messages.getMessage(lang, MessageConsts.DeletedCountError, 1,
                                    reply.result().getUpdated()));
                            asyncResultHandler.handle(Future.succeededFuture(Oriole.DeleteOrioleSubjectsBySubjectIdResponse.
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
                        asyncResultHandler.handle(Future.succeededFuture(Oriole.DeleteOrioleSubjectsBySubjectIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(Oriole.DeleteOrioleSubjectsBySubjectIdResponse
                                .respond400WithTextPlain(res.cause().getMessage())));
                        break;
                    default:
                        ValidationHelper.handleError(res.cause(), asyncResultHandler);

                }
            }
        });
    }

    /**
     * Helper to get a subject. Fetches the record from database.
     * @param subjectId
     * @param okapiHeaders
     * @param context
     * @param resp a callback that returns the subject, or an error
     */
    private void getOneSubject(
            String subjectId,
            Map<String, String> okapiHeaders,
            Context context,
            Handler<ExtendedAsyncResult<Subject>> resp) {
        Criterion c = new Criterion(
                new Criteria().addField(ID_FIELD_NAME).setJSONB(false).setOperation("=").setValue("'"+subjectId+"'"));
        ApiUtil.getPostgresClient(okapiHeaders, context).get(SUBJECT_TABLE, Subject.class, c, true,
                reply -> {
                    if (reply.succeeded()) {
                        List<Subject> subjects = (List<Subject>)reply.result().getResults();
                        if (subjects.isEmpty()) {
                            resp.handle(new Failure<>(
                                    ErrorType.NOT_FOUND, "Subject " + subjectId + " not found"));
                        } else {
                            Subject l = subjects.get(0);
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

    private void getLastAltId(Map<String, String> okapiHeaders, Context context, Handler<ExtendedAsyncResult<String>> resp) {
        String sql = "SELECT jsonb->>'altId' altId FROM " + RESOURCE_TABLE + " ORDER BY altId DESC LIMIT 1;";
        getPostgresClient(okapiHeaders, context).select(
                sql,
                (reply) -> {
                    if (reply.succeeded()) {
                        List<JsonArray> results = reply.result().getResults();
                        if (results.isEmpty()) {
                            resp.handle(new Success<>(""));
                        } else {
                            JsonArray jsonArray = results.get(0);
                            String value = jsonArray.getString(0);
                            resp.handle(new Success<>(value));
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




    private static PostgresClient getPostgresClient(Map<String, String> okapiHeaders, Context vertxContext) {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        return PostgresClient.getInstance(vertxContext.owner(), tenantId);
    }

    protected String getNextAltId(String lastAltId) {
        int current = Integer.parseInt(lastAltId.substring(3));
        return String.format("JHU%05d", current+1);
    }
}
