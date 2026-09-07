package io.github.hectorvent.floci.services.redshiftdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedshiftDataServiceTest {

    static {
        Driver.load();
    }

    private static final String REGION = "us-east-1";

    private final ObjectMapper om = new ObjectMapper();
    private String jdbcUrl;
    private RedshiftDataService service;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";

        RedshiftDataResourceResolver resolver = mock(RedshiftDataResourceResolver.class);
        when(resolver.resolve(any(), any())).thenReturn(new RedshiftDataResourceResolver.DatabaseTarget(
                "arn:aws:redshift:us-east-1:000000000000:cluster:wh", "127.0.0.1", 5439, "dev", "admin", "x"));

        RedshiftDataConnectionFactory factory = mock(RedshiftDataConnectionFactory.class);
        when(factory.open(any())).thenAnswer(inv -> DriverManager.getConnection(jdbcUrl, "sa", ""));

        service = new RedshiftDataService(resolver, factory,
                new RedshiftDataStatementStore(24, Clock.systemUTC()), om);
    }

    private ObjectNode req(String sql) {
        ObjectNode r = om.createObjectNode();
        r.put("Sql", sql);
        r.put("ClusterIdentifier", "wh");
        r.put("DbUser", "admin");
        r.put("Database", "dev");
        return r;
    }

    private ObjectNode idOf(String id) {
        return om.createObjectNode().put("Id", id);
    }

    @Test
    void executeStoresFinishedResultAndDescribeReportsIt() {
        String createId = service.executeStatement(req("create table t (id int, name varchar(20))"), REGION)
                .get("Id").asText();
        ObjectNode createDescribe = service.describeStatement(idOf(createId));
        assertEquals("FINISHED", createDescribe.get("Status").asText());
        assertFalse(createDescribe.get("HasResultSet").asBoolean());

        service.executeStatement(req("insert into t values (1, 'a'), (2, 'b')"), REGION);

        String selectId = service.executeStatement(req("select id, name from t order by id"), REGION)
                .get("Id").asText();
        ObjectNode selectDescribe = service.describeStatement(idOf(selectId));
        assertEquals("FINISHED", selectDescribe.get("Status").asText());
        assertTrue(selectDescribe.get("HasResultSet").asBoolean());
        assertEquals(2, selectDescribe.get("ResultRows").asInt());

        ObjectNode result = service.getStatementResult(idOf(selectId));
        assertEquals(2, result.get("Records").size());
        assertEquals(1L, result.get("Records").get(0).get(0).get("longValue").asLong());
        assertEquals("a", result.get("Records").get(0).get(1).get("stringValue").asText());
        assertTrue(result.get("ColumnMetadata").get(0).get("name").asText().equalsIgnoreCase("id"));
    }

    @Test
    void executionErrorIsStoredAsFailedButExecuteStillReturnsAnId() {
        ObjectNode response = service.executeStatement(req("select * from does_not_exist"), REGION);
        String id = response.get("Id").asText();
        ObjectNode describe = service.describeStatement(idOf(id));
        assertEquals("FAILED", describe.get("Status").asText());
        assertFalse(describe.get("Error").asText().isBlank());
    }

    @Test
    void getStatementResultOnUpdateStatementIsRejected() {
        service.executeStatement(req("create table u (id int)"), REGION);
        String id = service.executeStatement(req("insert into u values (1)"), REGION).get("Id").asText();
        AwsException e = assertThrows(AwsException.class, () -> service.getStatementResult(idOf(id)));
        assertEquals("ValidationException", e.getErrorCode());
    }

    @Test
    void unknownIdIsResourceNotFound() {
        AwsException e = assertThrows(AwsException.class, () -> service.describeStatement(idOf("nope")));
        assertEquals("ResourceNotFoundException", e.getErrorCode());
    }

    @Test
    void parameterisedSelectBindsValues() {
        service.executeStatement(req("create table p (id int)"), REGION);
        service.executeStatement(req("insert into p values (1), (2), (3)"), REGION);
        ObjectNode r = req("select id from p where id = :id");
        r.putArray("Parameters").addObject().put("name", "id").put("value", "2");
        String id = service.executeStatement(r, REGION).get("Id").asText();
        ObjectNode result = service.getStatementResult(idOf(id));
        assertEquals(1, result.get("Records").size());
        assertEquals(2L, result.get("Records").get(0).get(0).get("longValue").asLong());
    }

    @Test
    void rejectsMultiStatementSql() {
        AwsException e = assertThrows(AwsException.class,
                () -> service.executeStatement(req("select 1; select 2"), REGION));
        assertEquals("ValidationException", e.getErrorCode());
    }

    private ObjectNode batchReq(String... sqls) {
        ObjectNode r = om.createObjectNode();
        r.put("ClusterIdentifier", "wh");
        r.put("DbUser", "admin");
        r.put("Database", "dev");
        var array = r.putArray("Sqls");
        for (String sql : sqls) {
            array.add(sql);
        }
        return r;
    }

    @Test
    void batchExecutesInOneTransactionAndDescribeShowsSubStatements() {
        String id = service.batchExecuteStatement(batchReq(
                "create table b (id int)",
                "insert into b values (1), (2)",
                "select count(*) as c from b"), REGION).get("Id").asText();

        ObjectNode describe = service.describeStatement(idOf(id));
        assertEquals("FINISHED", describe.get("Status").asText());
        assertEquals(3, describe.get("SubStatements").size());

        ObjectNode result = service.getStatementResult(idOf(id));
        assertEquals(1, result.get("Records").size());
        assertEquals(2L, result.get("Records").get(0).get(0).get("longValue").asLong());
    }

    @Test
    void batchRollsBackWhenASubStatementFails() {
        String id = service.batchExecuteStatement(batchReq(
                "create table r1 (id int)",
                "insert into r1 values (1)",
                "insert into r1 (nope) values (2)"), REGION).get("Id").asText();

        ObjectNode describe = service.describeStatement(idOf(id));
        assertEquals("FAILED", describe.get("Status").asText());
        assertFalse(describe.get("Error").asText().isBlank());
    }

    @Test
    void listStatementsIsNewestFirstAndFiltersByName() {
        ObjectNode a = req("select 1");
        a.put("StatementName", "alpha");
        service.executeStatement(a, REGION);
        ObjectNode b = req("select 2");
        b.put("StatementName", "beta");
        service.executeStatement(b, REGION);

        ObjectNode all = service.listStatements(om.createObjectNode());
        assertTrue(all.get("Statements").size() >= 2);

        ObjectNode filtered = service.listStatements(om.createObjectNode().put("StatementName", "alpha"));
        assertEquals(1, filtered.get("Statements").size());
        assertEquals("alpha", filtered.get("Statements").get(0).get("StatementName").asText());
    }

    @Test
    void cancelOnFinishedStatementReturnsTrueWithoutChangingIt() {
        service.executeStatement(req("create table cf (id int)"), REGION);
        String id = service.executeStatement(req("select id from cf"), REGION).get("Id").asText();
        assertTrue(service.cancelStatement(idOf(id)).get("Status").asBoolean());
        assertEquals("FINISHED", service.describeStatement(idOf(id)).get("Status").asText());
    }

    @Test
    void cancelUnknownIdIsResourceNotFound() {
        AwsException e = assertThrows(AwsException.class, () -> service.cancelStatement(idOf("missing")));
        assertEquals("ResourceNotFoundException", e.getErrorCode());
    }
}
