package io.github.hectorvent.floci.services.redshiftdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.Resettable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RedshiftDataService implements Resettable {

    private final RedshiftDataResourceResolver resolver;
    private final RedshiftDataConnectionFactory connectionFactory;
    private final RedshiftDataStatementStore store;
    private final ObjectMapper objectMapper;

    @Inject
    public RedshiftDataService(RedshiftDataResourceResolver resolver,
                               RedshiftDataConnectionFactory connectionFactory,
                               RedshiftDataStatementStore store,
                               ObjectMapper objectMapper) {
        this.resolver = resolver;
        this.connectionFactory = connectionFactory;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    public void clear() {
        store.clear();
    }

    public ObjectNode executeStatement(JsonNode request, String region) {
        throw notImplemented("ExecuteStatement");
    }

    public ObjectNode batchExecuteStatement(JsonNode request, String region) {
        throw notImplemented("BatchExecuteStatement");
    }

    public ObjectNode describeStatement(JsonNode request) {
        throw notImplemented("DescribeStatement");
    }

    public ObjectNode getStatementResult(JsonNode request) {
        throw notImplemented("GetStatementResult");
    }

    public ObjectNode getStatementResultV2(JsonNode request) {
        throw notImplemented("GetStatementResultV2");
    }

    public ObjectNode listStatements(JsonNode request) {
        throw notImplemented("ListStatements");
    }

    public ObjectNode cancelStatement(JsonNode request) {
        throw notImplemented("CancelStatement");
    }

    public ObjectNode listDatabases(JsonNode request, String region) {
        throw notImplemented("ListDatabases");
    }

    public ObjectNode listSchemas(JsonNode request, String region) {
        throw notImplemented("ListSchemas");
    }

    public ObjectNode listTables(JsonNode request, String region) {
        throw notImplemented("ListTables");
    }

    public ObjectNode describeTable(JsonNode request, String region) {
        throw notImplemented("DescribeTable");
    }

    private static AwsException notImplemented(String op) {
        return new AwsException("ValidationException", op + " is not implemented yet.", 400);
    }
}
