package org.folio.rest.impl;

import io.vertx.core.Context;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;

import java.io.IOException;
import java.util.Map;
import java.util.Collections;

public class ApiUtil {
    public static CQLWrapper getCQL(String query, int limit, int offset, String table, String schema)
            throws IOException, FieldException, SchemaException {
        // Create CQL2PgJSON with just the field name
        CQL2PgJSON cql2pgJson = new CQL2PgJSON(Collections.singletonList(table + ".jsonb"));

        return new CQLWrapper(cql2pgJson, query)
                .setLimit(new Limit(limit))
                .setOffset(new Offset(offset));
    }

    public static PostgresClient getPostgresClient(Map<String, String> okapiHeaders, Context vertxContext) {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        return PostgresClient.getInstance(vertxContext.owner(), tenantId);
    }
}