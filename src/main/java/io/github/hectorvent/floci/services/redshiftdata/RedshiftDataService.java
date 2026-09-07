package io.github.hectorvent.floci.services.redshiftdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.Resettable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class RedshiftDataService implements Resettable {

    private static final int PAGE_SIZE = 1000;

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

    // ── ExecuteStatement ────────────────────────────────────────────────────

    public ObjectNode executeStatement(JsonNode request, String region) {
        String sql = requiredText(request, "Sql");
        rejectMultiStatement(sql);
        Map<String, String> parameters = RedshiftDataSqlParameters.parseParameters(request, "Parameters");

        RedshiftDataResourceResolver.DatabaseTarget target = resolver.resolve(request, region);

        RedshiftDataStatementStore.StoredStatement stored = newStatement(request);
        stored.sql = sql;
        stored.sqls = List.of(sql);
        stored.clusterIdentifier = clusterIdentifierOrNull(request);
        stored.dbUser = textOrNull(request, "DbUser");
        stored.database = target.database();
        stored.resultFormat = request.path("ResultFormat").asText("JSON");

        runStatement(stored, target, sql, parameters);
        store.put(stored);
        return executeResponse(stored);
    }

    private void runStatement(RedshiftDataStatementStore.StoredStatement stored,
                              RedshiftDataResourceResolver.DatabaseTarget target,
                              String sql, Map<String, String> parameters) {
        try (Connection connection = connectionFactory.open(target)) {
            runOnConnection(stored, connection, sql, parameters);
        } catch (SQLException e) {
            stored.status = RedshiftDataStatementStore.Status.FAILED;
            stored.error = e.getMessage();
        }
        stored.updatedAt = Instant.now();
    }

    private void runOnConnection(RedshiftDataStatementStore.StoredStatement stored,
                                 Connection connection, String sql, Map<String, String> parameters)
            throws SQLException {
        RedshiftDataSqlParameters.ParsedSql parsed = RedshiftDataSqlParameters.parse(sql);
        long t0 = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(parsed.sql())) {
            RedshiftDataSqlParameters.bind(statement, parsed.parameterOrder(), parameters);
            boolean hasResultSet = statement.execute();
            if (hasResultSet) {
                try (ResultSet rs = statement.getResultSet()) {
                    stored.columnMetadata = RedshiftDataColumnMetadata.toColumnMetadata(objectMapper, rs.getMetaData());
                    stored.rows = RedshiftDataFieldMapper.rows(objectMapper, rs);
                    stored.hasResultSet = true;
                    stored.resultRows = stored.rows.size();
                    stored.resultSize = RedshiftDataFieldMapper.serializedSize(stored.rows);
                }
            } else {
                stored.hasResultSet = false;
                stored.resultRows = Math.max(statement.getUpdateCount(), 0);
            }
        }
        stored.durationNanos = System.nanoTime() - t0;
        stored.status = RedshiftDataStatementStore.Status.FINISHED;
    }

    private ObjectNode executeResponse(RedshiftDataStatementStore.StoredStatement stored) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("Id", stored.id);
        if (stored.clusterIdentifier != null) {
            response.put("ClusterIdentifier", stored.clusterIdentifier);
        } else {
            response.putNull("ClusterIdentifier");
        }
        response.put("CreatedAt", epochSeconds(stored.createdAt));
        response.put("Database", stored.database);
        if (stored.dbUser != null) {
            response.put("DbUser", stored.dbUser);
        }
        response.putArray("DbGroups");
        response.putNull("WorkgroupName");
        response.put("HasResultSet", stored.hasResultSet);
        response.putNull("SessionId");
        return response;
    }

    // ── DescribeStatement ───────────────────────────────────────────────────

    public ObjectNode describeStatement(JsonNode request) {
        RedshiftDataStatementStore.StoredStatement stored = require(request);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("Id", stored.id);
        response.put("Status", stored.status.name());
        response.put("CreatedAt", epochSeconds(stored.createdAt));
        response.put("UpdatedAt", epochSeconds(stored.updatedAt));
        response.put("Duration", stored.durationNanos);
        if (stored.error != null) {
            response.put("Error", stored.error);
        }
        response.put("QueryString", stored.sql);
        response.put("RedshiftPid", Math.abs(stored.id.hashCode()));
        response.put("RedshiftQueryId", (long) Math.abs(stored.id.hashCode()));
        response.put("ResultRows", stored.resultRows);
        response.put("ResultSize", stored.resultSize);
        response.put("HasResultSet", stored.hasResultSet);
        if (stored.clusterIdentifier != null) {
            response.put("ClusterIdentifier", stored.clusterIdentifier);
        }
        response.put("Database", stored.database);
        if (stored.dbUser != null) {
            response.put("DbUser", stored.dbUser);
        }
        response.putNull("WorkgroupName");
        response.put("ResultFormat", stored.resultFormat);
        if (stored.batch && stored.subStatements != null) {
            ArrayNode subs = response.putArray("SubStatements");
            for (RedshiftDataStatementStore.StoredStatement sub : stored.subStatements) {
                ObjectNode s = subs.addObject();
                s.put("Id", sub.id);
                s.put("Status", sub.status.name());
                s.put("QueryString", sub.sql);
                s.put("Duration", sub.durationNanos);
                s.put("ResultRows", sub.resultRows);
                s.put("ResultSize", sub.resultSize);
                s.put("HasResultSet", sub.hasResultSet);
                if (sub.error != null) {
                    s.put("Error", sub.error);
                }
            }
        }
        return response;
    }

    // ── GetStatementResult / GetStatementResultV2 ───────────────────────────

    public ObjectNode getStatementResult(JsonNode request) {
        RedshiftDataStatementStore.StoredStatement stored = resultBearing(request);
        int offset = decodeToken(textOrNull(request, "NextToken"));
        ObjectNode response = objectMapper.createObjectNode();
        response.set("ColumnMetadata", stored.columnMetadata);
        ArrayNode records = response.putArray("Records");
        int end = Math.min(offset + PAGE_SIZE, stored.rows.size());
        for (int i = offset; i < end; i++) {
            records.add(stored.rows.get(i));
        }
        response.put("TotalNumRows", (long) stored.rows.size());
        if (end < stored.rows.size()) {
            response.put("NextToken", encodeToken(end));
        }
        return response;
    }

    public ObjectNode getStatementResultV2(JsonNode request) {
        RedshiftDataStatementStore.StoredStatement stored = resultBearing(request);
        int offset = decodeToken(textOrNull(request, "NextToken"));
        ObjectNode response = objectMapper.createObjectNode();
        response.set("ColumnMetadata", stored.columnMetadata);
        int end = Math.min(offset + PAGE_SIZE, stored.rows.size());
        boolean csv = "CSV".equalsIgnoreCase(stored.resultFormat);
        ArrayNode records = response.putArray("Records");
        for (int i = offset; i < end; i++) {
            if (csv) {
                records.addObject().put("CSVRecords", toCsvLine(stored.rows.get(i)));
            } else {
                records.add(stored.rows.get(i));
            }
        }
        response.put("ResultFormat", csv ? "CSV" : "JSON");
        response.put("TotalNumRows", (long) stored.rows.size());
        if (end < stored.rows.size()) {
            response.put("NextToken", encodeToken(end));
        }
        return response;
    }

    private static String toCsvLine(ArrayNode row) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            JsonNode field = row.get(i);
            if (field.path("isNull").asBoolean(false)) {
                continue;
            }
            if (field.has("stringValue")) {
                line.append(field.get("stringValue").asText());
            } else if (field.has("longValue")) {
                line.append(field.get("longValue").asLong());
            } else if (field.has("doubleValue")) {
                line.append(field.get("doubleValue").asDouble());
            } else if (field.has("booleanValue")) {
                line.append(field.get("booleanValue").asBoolean());
            } else if (field.has("blobValue")) {
                line.append(field.get("blobValue").asText());
            }
        }
        return line.toString();
    }

    // ── Not yet implemented (Tasks 4 and 5) ────────────────────────────────

    public ObjectNode batchExecuteStatement(JsonNode request, String region) {
        throw notImplemented("BatchExecuteStatement");
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

    // ── Helpers ────────────────────────────────────────────────────────────

    private RedshiftDataStatementStore.StoredStatement newStatement(JsonNode request) {
        RedshiftDataStatementStore.StoredStatement stored = new RedshiftDataStatementStore.StoredStatement();
        stored.id = UUID.randomUUID().toString();
        stored.statementName = textOrNull(request, "StatementName");
        Instant now = Instant.now();
        stored.createdAt = now;
        stored.updatedAt = now;
        stored.status = RedshiftDataStatementStore.Status.STARTED;
        return stored;
    }

    private RedshiftDataStatementStore.StoredStatement require(JsonNode request) {
        String id = requiredText(request, "Id");
        RedshiftDataStatementStore.StoredStatement stored = store.get(id);
        if (stored == null) {
            throw new AwsException("ResourceNotFoundException", "Statement " + id + " was not found.", 400);
        }
        return stored;
    }

    private RedshiftDataStatementStore.StoredStatement resultBearing(JsonNode request) {
        RedshiftDataStatementStore.StoredStatement stored = require(request);
        if (!stored.hasResultSet || stored.rows == null) {
            throw new AwsException("ValidationException", "Statement has no result set", 400);
        }
        return stored;
    }

    private static void rejectMultiStatement(String sql) {
        String trimmed = sql.strip();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        }
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == ';' && !inSingle && !inDouble) {
                throw new AwsException("ValidationException",
                        "A single Sql statement is required; use BatchExecuteStatement for multiple.", 400);
            }
        }
    }

    private static String encodeToken(int offset) {
        return Base64.getEncoder().encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }

    private static int decodeToken(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new AwsException("ValidationException", "NextToken is not valid.", 400);
        }
    }

    private static double epochSeconds(Instant instant) {
        return instant.toEpochMilli() / 1000.0;
    }

    private String clusterIdentifierOrNull(JsonNode request) {
        return textOrNull(request, "ClusterIdentifier");
    }

    private static String requiredText(JsonNode request, String name) {
        String value = textOrNull(request, name);
        if (value == null || value.isBlank()) {
            throw new AwsException("ValidationException", name + " is required.", 400);
        }
        return value;
    }

    private static String textOrNull(JsonNode request, String name) {
        JsonNode node = request.get(name);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static AwsException notImplemented(String op) {
        return new AwsException("ValidationException", op + " is not implemented yet.", 400);
    }
}
