package org.folio.rest.impl;

import io.vertx.core.*;
import org.apache.commons.io.IOUtils;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.jaxrs.model.SubjectCollection;
import org.folio.rest.jaxrs.resource.OrioleSubjects;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SubjectsImpl implements OrioleSubjects {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectsImpl.class);
    public static final String SUBJECT_TABLE = "subject";
    private static final String ID_FIELD_NAME = "id";
    private static final String SUBJECT_SCHEMA_NAME = "ramls/schemas/subject.json";
    private static final String SUBJECT_PREFIX = "/oriole-subjects/";
    private String SUBJECT_SCHEMA = null;
    private final Messages messages = Messages.getInstance();

    public SubjectsImpl(Vertx vertx, String tennantId) {
        if (SUBJECT_SCHEMA == null) {
            initCQLValidation();
        }
        PostgresClient.getInstance(vertx, tennantId).setIdField(ID_FIELD_NAME);
    }

    private void initCQLValidation() {
        String path = SUBJECT_SCHEMA_NAME;
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            SUBJECT_SCHEMA = IOUtils.toString(is, "UTF-8");
        } catch (Exception e) {
            LOGGER.error("Unable to load schema - " + path
                    + ", validation of query fields will not be active");
        }
    }

    @Override
    public void getOrioleSubjects(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
        PostgresClient postgresClient = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
        CQLWrapper cql;
        try {
            cql = ApiUtil.getCQL(query, limit, offset, SUBJECT_TABLE, SUBJECT_SCHEMA);
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
                                Future.succeededFuture(OrioleSubjects.GetOrioleSubjectsResponse.respond200WithApplicationJson(subjects)));
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
                        OrioleSubjects.PostOrioleSubjectsResponse.HeadersFor201 headers =
                                OrioleSubjects.PostOrioleSubjectsResponse.headersFor201().withLocation(SUBJECT_PREFIX + ret);
                        asyncResultHandler.handle(Future.succeededFuture(
                                OrioleSubjects.PostOrioleSubjectsResponse.respond201WithApplicationJson(stream, headers)));
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
                                asyncResultHandler.handle(Future.succeededFuture(OrioleSubjects.DeleteOrioleSubjectsResponse.noContent().build()));
                            } else {
                                asyncResultHandler.handle(Future.succeededFuture(
                                        OrioleSubjects.DeleteOrioleSubjectsResponse.respond500WithTextPlain(reply.cause().getMessage())));
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
                        OrioleSubjects.GetOrioleSubjectsBySubjectIdResponse.respond200WithApplicationJson(res.result())));
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(
                                OrioleSubjects.GetOrioleSubjectsBySubjectIdResponse.respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(
                                OrioleSubjects.GetOrioleSubjectsBySubjectIdResponse.respond400WithTextPlain(res.cause().getMessage())));
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
            asyncResultHandler.handle(Future.succeededFuture(OrioleSubjects.PutOrioleSubjectsBySubjectIdResponse.respond422WithApplicationJson(valErr)));
            return;
        }
        getOneSubject(subjectId, okapiHeaders, vertxContext, res -> {
            if (res.succeeded()) {
                Subject oldSubject = res.result();
                ApiUtil.getPostgresClient(okapiHeaders, vertxContext).update(SUBJECT_TABLE, entity, subjectId, reply -> {
                    if (reply.succeeded()) {
                        if (reply.result().getUpdated() == 0) {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    OrioleSubjects.PutOrioleSubjectsBySubjectIdResponse.respond500WithTextPlain(
                                            messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                        } else {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    OrioleSubjects.PutOrioleSubjectsBySubjectIdResponse.respond204()));
                        }
                    } else {
                        ValidationHelper.handleError(reply.cause(), asyncResultHandler);
                    }
                });
            } else {
                switch (res.getType()) {
                    case NOT_FOUND:
                        asyncResultHandler.handle(Future.succeededFuture(OrioleSubjects.PutOrioleSubjectsBySubjectIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER: // bad request
                        asyncResultHandler.handle(Future.succeededFuture(OrioleSubjects.PutOrioleSubjectsBySubjectIdResponse
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
                            asyncResultHandler.handle(Future.succeededFuture(OrioleSubjects.DeleteOrioleSubjectsBySubjectIdResponse.respond204()));
                        } else {
                            LOGGER.error(messages.getMessage(lang, MessageConsts.DeletedCountError, 1,
                                    reply.result().getUpdated()));
                            asyncResultHandler.handle(Future.succeededFuture(OrioleSubjects.DeleteOrioleSubjectsBySubjectIdResponse.
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
                        asyncResultHandler.handle(Future.succeededFuture(OrioleSubjects.DeleteOrioleSubjectsBySubjectIdResponse
                                .respond404WithTextPlain(res.cause().getMessage())));
                        break;
                    case USER:
                        asyncResultHandler.handle(Future.succeededFuture(OrioleSubjects.DeleteOrioleSubjectsBySubjectIdResponse
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

}
