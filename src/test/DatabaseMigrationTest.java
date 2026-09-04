package com.flowerfarm.release;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("H2 database migrations")
class DatabaseMigrationTest {

    private static final String LOCATIONS = "classpath:db/migration/h2";

    @Test
    @DisplayName("a new database is created entirely by versioned migrations")
    void createsFreshSchema() throws Exception {
        String url = "jdbc:h2:mem:migration-fresh;DB_CLOSE_DELAY=-1";

        MigrateResult result = configuredFlyway(url).migrate();

        assertThat(result.migrationsExecuted).isEqualTo(21);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "INVENTORY_ITEMS")).isTrue();
            assertThat(tableExists(connection, "CUSTOMER_ORDERS")).isTrue();
            assertThat(columnExists(connection, "CUSTOMER_ORDERS", "HANDOFF_TYPE")).isTrue();
            assertThat(tableExists(connection, "KNOWLEDGE_ENTRIES")).isTrue();
            assertThat(tableExists(connection, "KNOWLEDGE_ENTRY_TAGS")).isTrue();
            assertThat(tableExists(connection, "SEASON_PLAN_ENTRIES")).isTrue();
            assertThat(tableExists(connection, "FARM_SEASON_SETTINGS")).isTrue();
            assertThat(columnExists(connection, "FARM_SEASON_SETTINGS",
                    "LOADED_LABOR_RATE_PER_HOUR")).isTrue();
            assertThat(tableExists(connection, "GROWING_TEMPLATES")).isTrue();
            assertThat(tableExists(connection, "PRODUCTION_TASKS")).isTrue();
            assertThat(columnExists(connection, "PRODUCTION_TASKS",
                    "ACTUAL_MINUTES")).isTrue();
            assertThat(columnExists(connection, "PRODUCTION_TASKS",
                    "INPUT_COST")).isTrue();
            assertThat(columnExists(connection, "PRODUCTION_TASKS",
                    "FIELD_OBSERVATION")).isTrue();
            assertThat(columnExists(connection, "PRODUCTION_TASKS",
                    "COMPLETED_AT")).isTrue();
            assertThat(tableExists(connection, "FLORAL_RECIPES")).isTrue();
            assertThat(tableExists(connection, "FLORAL_RECIPE_INGREDIENTS")).isTrue();
            assertThat(tableExists(connection, "INTEGRATION_HANDOFFS")).isTrue();
            assertThat(tableExists(connection, "ROBOT_OPERATION_EVENTS")).isTrue();
            assertThat(tableExists(connection, "ROBOT_CONTROL_LEASES")).isTrue();
            assertThat(tableExists(connection, "ROBOT_STAGED_PLANS")).isTrue();
            assertThat(tableExists(connection, "FARM_DATA_IMPORT_RECEIPTS")).isTrue();
            assertThat(tableExists(connection, "WORKER_ACTION_RECEIPTS")).isTrue();
            assertThat(tableExists(connection, "FIELD_ATTACHMENTS")).isTrue();
            assertThat(columnExists(connection, "HARVEST_LOG", "LOT_CODE")).isTrue();
            assertThat(columnExists(connection, "HARVEST_LOG", "HARVEST_GRADE")).isTrue();
            assertThat(columnExists(connection, "HARVEST_LOG", "LOSS_QUANTITY")).isTrue();
            assertThat(columnExists(connection, "HARVEST_LOG", "STORAGE_LOCATION")).isTrue();
            assertThat(columnExists(connection, "ROBOT_STAGED_PLANS",
                    "EXECUTION_REQUEST_ID")).isTrue();
            assertThat(columnExists(connection, "ROBOT_STAGED_PLANS",
                    "EXECUTION_REQUESTED_AT")).isTrue();
            assertThat(columnExists(connection, "ROBOT_OPERATION_EVENTS",
                    "REQUEST_ID")).isTrue();
            assertThat(columnExists(connection, "ROBOT_OPERATION_EVENTS",
                    "REQUEST_FINGERPRINT")).isTrue();
            assertThat(columnSize(connection, "SYNC_HISTORY", "OPERATION"))
                    .isGreaterThanOrEqualTo(64);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO sync_history(
                            success, occurred_at, operation, connector_name, message)
                        VALUES (
                            TRUE, CURRENT_TIMESTAMP, 'SEASON_PLAN_CREATE',
                            'season-planner', 'Planting block planned')
                        """);
            }
        }
    }

    @Test
    @DisplayName("an existing 1.0 database is baselined and upgraded without losing orders")
    void upgradesLegacySchemaInPlace() throws Exception {
        String url = "jdbc:h2:mem:migration-legacy;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE customers (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        customer_type VARCHAR(32) NOT NULL,
                        name VARCHAR(255) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE customer_orders (
                        order_date DATE NOT NULL,
                        customer_id BIGINT NOT NULL,
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        status VARCHAR(32) NOT NULL,
                        notes VARCHAR(2000)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE harvest_log (
                        harvest_date DATE NOT NULL,
                        quantity FLOAT(53) NOT NULL,
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        unit VARCHAR(64) NOT NULL,
                        bed_or_field VARCHAR(128),
                        notes VARCHAR(2000),
                        crop_name VARCHAR(255) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE inventory_items (
                        cost FLOAT(53) NOT NULL,
                        price FLOAT(53) NOT NULL,
                        quantity INTEGER NOT NULL,
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        unit VARCHAR(64) NOT NULL,
                        category VARCHAR(128) NOT NULL,
                        notes VARCHAR(2000),
                        name VARCHAR(255) NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO customers(customer_type, name) VALUES ('FLORIST', 'Legacy Farm')");
            statement.execute("""
                    INSERT INTO harvest_log(
                        harvest_date, quantity, unit, bed_or_field, notes, crop_name)
                    VALUES (DATE '2026-08-26', 24, 'stems', 'Legacy Bed', '', 'Legacy Dahlia')
                    """);
            statement.execute("""
                    INSERT INTO customer_orders(order_date, customer_id, status, notes)
                    VALUES (DATE '2026-08-27', 1, 'CONFIRMED', 'preserve me')
                    """);
        }

        MigrateResult result = configuredFlyway(url).migrate();

        assertThat(result.migrationsExecuted).isEqualTo(20);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT status, notes, handoff_type FROM customer_orders WHERE id = 1")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("status")).isEqualTo("CONFIRMED");
            assertThat(rows.getString("notes")).isEqualTo("preserve me");
            assertThat(rows.getString("handoff_type")).isEqualTo("UNSPECIFIED");
        }
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "KNOWLEDGE_ENTRIES")).isTrue();
            assertThat(tableExists(connection, "KNOWLEDGE_ENTRY_TAGS")).isTrue();
            assertThat(tableExists(connection, "SEASON_PLAN_ENTRIES")).isTrue();
            assertThat(columnExists(connection, "FARM_SEASON_SETTINGS",
                    "LOADED_LABOR_RATE_PER_HOUR")).isTrue();
            assertThat(tableExists(connection, "GROWING_TEMPLATES")).isTrue();
            assertThat(columnExists(connection, "PRODUCTION_TASKS",
                    "ACTUAL_MINUTES")).isTrue();
            assertThat(tableExists(connection, "FLORAL_RECIPES")).isTrue();
            assertThat(tableExists(connection, "ROBOT_OPERATION_EVENTS")).isTrue();
            assertThat(tableExists(connection, "ROBOT_CONTROL_LEASES")).isTrue();
            assertThat(tableExists(connection, "ROBOT_STAGED_PLANS")).isTrue();
            assertThat(tableExists(connection, "FARM_DATA_IMPORT_RECEIPTS")).isTrue();
            assertThat(tableExists(connection, "WORKER_ACTION_RECEIPTS")).isTrue();
            assertThat(tableExists(connection, "FIELD_ATTACHMENTS")).isTrue();
            assertThat(columnExists(connection, "ROBOT_STAGED_PLANS",
                    "EXECUTION_REQUEST_ID")).isTrue();
            assertThat(columnExists(connection, "ROBOT_OPERATION_EVENTS",
                    "REQUEST_ID")).isTrue();
            try (Statement statement = connection.createStatement();
                 ResultSet harvest = statement.executeQuery("""
                         SELECT lot_code, harvest_grade, loss_quantity, storage_location
                         FROM harvest_log WHERE id = 1
                         """)) {
                assertThat(harvest.next()).isTrue();
                assertThat(harvest.getString("lot_code"))
                        .isEqualTo("FFH-20260826-00000001");
                assertThat(harvest.getString("harvest_grade")).isEqualTo("UNGRADED");
                assertThat(harvest.getDouble("loss_quantity")).isZero();
                assertThat(harvest.getString("storage_location")).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("operational record deletion clears a link without erasing the farm lesson")
    void preservesKnowledgeWhenLinkedRecordIsDeleted() throws Exception {
        String url = "jdbc:h2:mem:migration-knowledge-links;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO harvest_log(
                        harvest_date, quantity, unit, bed_or_field, notes, crop_name,
                        lot_code, harvest_grade, loss_quantity, storage_location)
                    VALUES (DATE '2026-08-20', 20, 'stems', 'Bed A', '', 'Nootka Rose',
                        'MIGRATION-TEST-LOT', 'UNGRADED', 0, '')
                    """);
            statement.execute("""
                    INSERT INTO knowledge_entries(
                        observed_on, created_at, updated_at, harvest_id, kind, confidence,
                        summary, crop_name, observation)
                    VALUES (
                        DATE '2026-08-20', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1,
                        'PROBLEM', 'PROMISING', 'Mildew lesson', 'Nootka Rose',
                        'Mildew appeared after warm nights')
                    """);
            statement.execute("""
                    INSERT INTO knowledge_entry_tags(knowledge_entry_id, tag)
                    VALUES (1, 'mildew')
                    """);
            statement.execute("DELETE FROM harvest_log WHERE id = 1");

            try (ResultSet lesson = statement.executeQuery(
                    "SELECT harvest_id FROM knowledge_entries WHERE id = 1")) {
                assertThat(lesson.next()).isTrue();
                assertThat(lesson.getObject("harvest_id")).isNull();
            }

            statement.execute("DELETE FROM knowledge_entries WHERE id = 1");
            try (ResultSet tags = statement.executeQuery(
                    "SELECT COUNT(*) FROM knowledge_entry_tags")) {
                assertThat(tags.next()).isTrue();
                assertThat(tags.getInt(1)).isZero();
            }
        }
    }

    @Test
    @DisplayName("production integrity constraints reject invalid state and preserve handoff history")
    void enforcesProductionIntegrity() throws Exception {
        String url = "jdbc:h2:mem:migration-production-integrity;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO production_tasks(
                        due_date, sequence_number, generated, created_at, updated_at,
                        task_type, status, crop_name, bed_or_field)
                    VALUES (DATE '2026-09-01', 1, TRUE, CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP, 'SOW', 'INVALID', 'Zinnia', 'Bed A')
                    """)).isInstanceOf(java.sql.SQLException.class);

            statement.execute("""
                    INSERT INTO production_tasks(
                        due_date, sequence_number, generated, created_at, updated_at,
                        task_type, status, crop_name, bed_or_field,
                        actual_minutes, input_cost, field_observation)
                    VALUES (DATE '2026-09-01', 1, TRUE, CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP, 'SOIL_PREP', 'DONE', 'Zinnia',
                            'Bed A', 45, 12.50, 'Compost incorporated')
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE production_tasks SET actual_minutes = -1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE production_tasks SET input_cost = -1"))
                    .isInstanceOf(java.sql.SQLException.class);

            statement.execute("""
                    INSERT INTO integration_handoffs(
                        amount, created_at, updated_at, handoff_type, provider, status,
                        currency, external_reference, payload_summary)
                    VALUES (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MARKETPLACE',
                            'Florist portal', 'DRAFT', 'USD', 'listing-1', 'Reviewed')
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO integration_handoffs(
                        amount, created_at, updated_at, handoff_type, provider, status,
                        currency, external_reference, payload_summary)
                    VALUES (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MARKETPLACE',
                            'Florist portal', 'DRAFT', 'USD', 'listing-1', 'Duplicate')
                    """)).isInstanceOf(java.sql.SQLException.class);

            statement.execute("""
                    INSERT INTO floral_recipes(
                        sale_price, labor_minutes, labor_rate_hour, overhead_percent,
                        waste_percent, created_at, updated_at, name, design_type)
                    VALUES (35, 10, 25, 15, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                            'Market bouquet', 'Bouquet')
                    """);
            statement.execute("""
                    INSERT INTO integration_handoffs(
                        amount, related_recipe_id, created_at, updated_at, handoff_type,
                        provider, status, currency, external_reference, payload_summary)
                    VALUES (0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MARKETPLACE',
                            'Second portal', 'READY', 'USD', NULL, 'Recipe availability')
                    """);
            statement.execute("DELETE FROM floral_recipes WHERE id = 1");
            try (ResultSet handoff = statement.executeQuery(
                    "SELECT related_recipe_id FROM integration_handoffs WHERE provider = 'Second portal'")) {
                assertThat(handoff.next()).isTrue();
                assertThat(handoff.getObject("related_recipe_id")).isNull();
            }
        }
    }

    @Test
    @DisplayName("farm data receipts enforce one durable mapping per upstream record")
    void enforcesFarmDataImportIdentity() throws Exception {
        String url = "jdbc:h2:mem:migration-farm-data-receipts;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        String insert = """
                INSERT INTO farm_data_import_receipts(
                    target_record_id, imported_at, updated_at, source_system,
                    record_type, action, content_fingerprint, source_key)
                VALUES (7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'legacy-excel',
                    'harvests', 'import-harvest-history',
                    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                    'harvest-row-1')
                """;
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(insert);
            statement.execute("""
                    INSERT INTO worker_action_receipts(
                        target_record_id, created_at, action, operation_key_hash,
                        request_fingerprint, response_sha256, response_json)
                    VALUES (8, CURRENT_TIMESTAMP, 'COUNT_INVENTORY',
                        'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
                        'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
                        'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
                        '{"id":8}')
                    """);
            statement.execute("""
                    INSERT INTO worker_action_receipts(
                        target_record_id, created_at, action, operation_key_hash,
                        request_fingerprint, response_sha256, response_json)
                    VALUES (9, CURRENT_TIMESTAMP, 'ADD_ATTACHMENT',
                        '1111111111111111111111111111111111111111111111111111111111111111',
                        '2222222222222222222222222222222222222222222222222222222222222222',
                        '3333333333333333333333333333333333333333333333333333333333333333',
                        '{"id":9}')
                    """);
            assertThatThrownBy(() -> statement.execute(insert))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    @DisplayName("worker receipts enforce one durable result per operation key")
    void enforcesWorkerOperationIdentity() throws Exception {
        String url = "jdbc:h2:mem:migration-worker-receipts;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        String insert = """
                INSERT INTO worker_action_receipts(
                    target_record_id, created_at, action, operation_key_hash,
                    request_fingerprint, response_sha256, response_json)
                VALUES (7, CURRENT_TIMESTAMP, 'LOG_HARVEST',
                    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                    'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
                    '{"id":7}')
                """;
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(insert);
            statement.execute("""
                    INSERT INTO worker_action_receipts(
                        target_record_id, created_at, action, operation_key_hash,
                        request_fingerprint, response_sha256, response_json)
                    VALUES (8, CURRENT_TIMESTAMP, 'COMPLETE_ORDER',
                        'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
                        'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
                        'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
                        '{"id":8}')
                    """);
            assertThatThrownBy(() -> statement.execute(insert))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO worker_action_receipts(
                        target_record_id, created_at, action, operation_key_hash,
                        request_fingerprint, response_sha256, response_json)
                    VALUES (10, CURRENT_TIMESTAMP, 'UNKNOWN',
                        '4444444444444444444444444444444444444444444444444444444444444444',
                        '5555555555555555555555555555555555555555555555555555555555555555',
                        '6666666666666666666666666666666666666666666666666666666666666666',
                        '{"id":10}')
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    private static Flyway configuredFlyway(String url) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("1"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load();
    }

    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet result = connection.getMetaData()
                .getTables(null, null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private static boolean columnExists(Connection connection, String table, String column)
            throws Exception {
        try (ResultSet result = connection.getMetaData()
                .getColumns(null, null, table, column)) {
            return result.next();
        }
    }

    private static int columnSize(Connection connection, String table, String column)
            throws Exception {
        try (ResultSet result = connection.getMetaData()
                .getColumns(null, null, table, column)) {
            assertThat(result.next()).isTrue();
            return result.getInt("COLUMN_SIZE");
        }
    }
}
