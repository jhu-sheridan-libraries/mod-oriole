package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Resource;
import org.folio.rest.jaxrs.resource.ResourcesResource;

import javax.ws.rs.core.Response;
import java.util.Map;

public class ResourcesResourceImpl implements ResourcesResource {

    @Override
    public void getResources(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {



    }

    @Override
    public void postResources(String lang, Resource entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    }

    @Override
    public void getResourcesByResourceId(String resourceId, String lang, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
            throws Exception {

    }

    @Override
    public void deleteResourcesByResourceId(String resourceId, String lang, Map<String, String> okapiHeaders, Handler
            <AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    }

    @Override
    public void putResourcesByResourceId(String resourceId, String lang, Resource entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    }
}
