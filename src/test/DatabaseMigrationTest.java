package com.flowerfarm.release;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

        assertThat(result.migrationsExecuted).isEqualTo(49);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertFarmOperationDefaults(connection);
            assertThat(tableExists(connection, "INVENTORY_ITEMS")).isTrue();
            assertThat(tableExists(connection, "CUSTOMER_ORDERS")).isTrue();
            assertThat(columnExists(connection, "CUSTOMER_ORDERS", "HANDOFF_TYPE")).isTrue();
            assertThat(tableExists(connection, "KNOWLEDGE_ENTRIES")).isTrue();
            assertThat(tableExists(connection, "KNOWLEDGE_ENTRY_TAGS")).isTrue();
            assertThat(columnExists(connection, "KNOWLEDGE_ENTRIES",
                    "FIELD_SCIENCE_EXPORT_ID")).isTrue();
            assertThat(tableExists(connection, "FIELD_SCIENCE_EXPORTS")).isTrue();
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
            assertThat(tableExists(connection, "CANNABIS_CULTIVATION_BATCHES")).isTrue();
            assertThat(tableExists(connection, "CROP_AREA_PLANS")).isTrue();
            assertThat(tableExists(connection, "BERRY_BLOCK_PLANS")).isTrue();
            assertThat(tableExists(connection, "NURSERY_BATCHES")).isTrue();
            assertThat(tableExists(connection, "HERD_GROUPS")).isTrue();
            assertThat(tableExists(connection, "EGG_COLLECTIONS")).isTrue();
            assertThat(tableExists(connection, "MILK_COLLECTIONS")).isTrue();
            assertThat(tableExists(connection, "VINEYARD_BLOCK_PLANS")).isTrue();
            assertThat(tableExists(connection, "HAY_CUTTINGS")).isTrue();
            assertThat(tableExists(connection, "HOP_YARD_PLANS")).isTrue();
            assertThat(tableExists(connection, "GRAIN_FIELD_PLANS")).isTrue();
            assertThat(tableExists(connection, "SEED_FIELD_PLANS")).isTrue();
            assertThat(tableExists(connection, "NUT_BLOCK_PLANS")).isTrue();
            assertThat(tableExists(connection, "VEGETABLE_PLANTING_PLANS")).isTrue();
            assertThat(tableExists(connection, "AQUATIC_COHORT_PLANS")).isTrue();
            assertThat(tableExists(connection, "CHRISTMAS_TREE_BLOCK_PLANS")).isTrue();
            assertThat(tableExists(connection, "HIVE_CHECKS")).isTrue();
            assertThat(tableExists(connection, "MUSHROOM_BATCH_PLANS")).isTrue();
            assertThat(tableExists(connection, "CRANBERRY_BED_PLANS")).isTrue();
            assertThat(tableExists(connection, "PUMPKIN_PATCH_PLANS")).isTrue();
            assertThat(tableExists(connection, "SCOUTING_FINDINGS")).isTrue();
            assertThat(columnExists(connection, "ORDER_LINES", "RECIPE_ID")).isTrue();
            assertThat(tableExists(connection, "SCOUTING_FINDING_PHOTOS")).isTrue();
            assertThat(tableExists(connection, "SCOUTING_LAB_SAMPLES")).isTrue();
            assertThat(tableExists(connection, "SCOUTING_SETTINGS")).isTrue();
            assertThat(columnExists(connection, "SCOUTING_SETTINGS", "JSON_IMPORTED_AT")).isTrue();
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
                    CREATE TABLE order_lines (
                        quantity FLOAT(53) NOT NULL,
                        unit_price FLOAT(53) NOT NULL,
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        order_id BIGINT NOT NULL,
                        unit VARCHAR(64) NOT NULL,
                        product_name VARCHAR(255) NOT NULL
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
            statement.execute("""
                    INSERT INTO order_lines(quantity, unit_price, order_id, unit, product_name)
                    VALUES (40, 2.5, 1, 'stems', 'Legacy Dahlia')
                    """);
        }

        MigrateResult result = configuredFlyway(url).migrate();

        assertThat(result.migrationsExecuted).isEqualTo(48);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT status, notes, handoff_type FROM customer_orders WHERE id = 1")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("status")).isEqualTo("CONFIRMED");
            assertThat(rows.getString("notes")).isEqualTo("preserve me");
            assertThat(rows.getString("handoff_type")).isEqualTo("UNSPECIFIED");
        }
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT product_name, quantity, recipe_id FROM order_lines WHERE order_id = 1")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("product_name")).isEqualTo("Legacy Dahlia");
            assertThat(rows.getDouble("quantity")).isEqualTo(40);
            assertThat(rows.getObject("recipe_id")).isNull();
        }
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertFarmOperationDefaults(connection);
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
            assertThat(tableExists(connection, "CANNABIS_CULTIVATION_BATCHES")).isTrue();
            assertThat(tableExists(connection, "CROP_AREA_PLANS")).isTrue();
            assertThat(tableExists(connection, "BERRY_BLOCK_PLANS")).isTrue();
            assertThat(tableExists(connection, "VINEYARD_BLOCK_PLANS")).isTrue();
            assertThat(tableExists(connection, "NURSERY_BATCHES")).isTrue();
            assertThat(tableExists(connection, "HERD_GROUPS")).isTrue();
            assertThat(tableExists(connection, "EGG_COLLECTIONS")).isTrue();
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

    @ParameterizedTest
    @CsvSource({"ORCHARDS,CUSTOM_FARM", "CANNABIS,CANNABIS_FARM"})
    @DisplayName("version 22 nonflower farm choices gain a compatible workspace")
    void addsWorkspaceToExistingFarmSettings(String enterprise, String expectedWorkspace)
            throws Exception {
        String url = "jdbc:h2:mem:migration-farm-workspace-" + enterprise
                + ";DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("22"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM farm_operation_flower_offerings WHERE settings_id = 1");
            statement.execute("DELETE FROM farm_operation_enterprises WHERE settings_id = 1");
            statement.execute("UPDATE farm_operation_settings SET state_code = 'OR', "
                    + "primary_enterprise = '" + enterprise + "' WHERE id = 1");
            statement.execute("INSERT INTO farm_operation_enterprises"
                    + "(settings_id, enterprise_code) VALUES (1, '" + enterprise + "')");
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(27);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet settings = statement.executeQuery("""
                     SELECT state_code, primary_enterprise, workspace_code
                     FROM farm_operation_settings WHERE id = 1
                     """)) {
            assertThat(settings.next()).isTrue();
            assertThat(settings.getString("state_code")).isEqualTo("OR");
            assertThat(settings.getString("primary_enterprise")).isEqualTo(enterprise);
            assertThat(settings.getString("workspace_code")).isEqualTo(expectedWorkspace);
            assertThat(settings.next()).isFalse();
            try (ResultSet enterprises = statement.executeQuery("""
                    SELECT enterprise_code FROM farm_operation_enterprises
                    WHERE settings_id = 1
                    """)) {
                assertThat(enterprises.next()).isTrue();
                assertThat(enterprises.getString(1)).isEqualTo(enterprise);
                assertThat(enterprises.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("version 27 farm setups gain only their saved primary farm type")
    void existingFarmSetupStartsWithOneEnabledWorkspace() throws Exception {
        String url = "jdbc:h2:mem:migration-combined-workspaces;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("27"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM farm_operation_flower_offerings WHERE settings_id = 1");
            statement.execute("DELETE FROM farm_operation_enterprises WHERE settings_id = 1");
            statement.execute("INSERT INTO farm_operation_enterprises"
                    + "(settings_id, enterprise_code) VALUES (1, 'ORCHARDS')");
            statement.execute("""
                    UPDATE farm_operation_settings SET primary_enterprise = 'ORCHARDS',
                        workspace_code = 'APPLE_ORCHARD' WHERE id = 1
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(22);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet workspaces = statement.executeQuery("""
                     SELECT display_order, workspace_code
                     FROM farm_operation_workspaces WHERE settings_id = 1
                     """)) {
            assertThat(workspaces.next()).isTrue();
            assertThat(workspaces.getInt("display_order")).isZero();
            assertThat(workspaces.getString("workspace_code"))
                    .isEqualTo("APPLE_ORCHARD");
            assertThat(workspaces.next()).isFalse();
        }
    }

    @Test
    @DisplayName("cultivation batches store valid plans and reject invalid count, dates, and stage")
    void enforcesCannabisCultivationBatchConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-cannabis-cultivation;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO cannabis_cultivation_batches(
                        batch_name, cultivar, area, plant_count, start_date,
                        planned_harvest_date, stage, notes, created_at, updated_at)
                    VALUES ('Autumn A', 'Cultivar A', 'Room 1', 20,
                        DATE '2026-09-01', DATE '2026-12-01', 'PLANNED', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE cannabis_cultivation_batches SET plant_count = -1 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE cannabis_cultivation_batches
                    SET planned_harvest_date = DATE '2026-08-31' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE cannabis_cultivation_batches SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT plant_count, planned_harvest_date, stage
                    FROM cannabis_cultivation_batches WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt("plant_count")).isEqualTo(20);
                assertThat(row.getDate("planned_harvest_date").toLocalDate())
                        .isEqualTo(java.time.LocalDate.of(2026, 12, 1));
                assertThat(row.getString("stage")).isEqualTo("PLANNED");
            }
        }
    }

    @Test
    @DisplayName("potato and apple workspaces persist valid area plans with guarded fields")
    void enforcesCropAreaPlanConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-crop-area-plans;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'POTATO_FARM' WHERE id = 1
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'APPLE_ORCHARD' WHERE id = 1
                    """);
            statement.execute("""
                    INSERT INTO crop_area_plans(
                        workspace_code, area_name, variety, acres, planting_date,
                        target_harvest_date, stage, notes, created_at, updated_at)
                    VALUES ('POTATO_FARM', 'North pivot', 'Russet', 18.25,
                        DATE '2026-04-01', DATE '2026-10-01', 'GROWING', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE crop_area_plans SET acres = -1 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE crop_area_plans
                    SET target_harvest_date = DATE '2026-03-31' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE crop_area_plans SET workspace_code = 'CUSTOM_FARM' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE crop_area_plans SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT workspace_code, acres, stage
                    FROM crop_area_plans WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("workspace_code")).isEqualTo("POTATO_FARM");
                assertThat(row.getBigDecimal("acres")).isEqualByComparingTo("18.25");
                assertThat(row.getString("stage")).isEqualTo("GROWING");
            }
        }
    }

    @Test
    @DisplayName("berry workspace persists block plans with guarded pick windows and channels")
    void enforcesBerryBlockPlanConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-berry-blocks;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'BERRY_FARM' WHERE id = 1
                    """);
            statement.execute("""
                    INSERT INTO berry_block_plans(
                        block_name, crop, cultivar, acres, planting_date,
                        first_pick_date, last_pick_date, sales_channel,
                        stage, notes, created_at, updated_at)
                    VALUES ('North rows', 'Blueberries', 'Duke', 2.50,
                        DATE '2026-03-01', DATE '2026-06-01', DATE '2026-08-01',
                        'FRESH', 'GROWING', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE berry_block_plans SET acres = -1 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE berry_block_plans
                    SET first_pick_date = DATE '2026-02-28' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE berry_block_plans
                    SET last_pick_date = DATE '2026-05-31' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE berry_block_plans SET sales_channel = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE berry_block_plans SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE berry_block_plans SET crop = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT crop, acres, sales_channel, stage
                    FROM berry_block_plans WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("crop")).isEqualTo("Blueberries");
                assertThat(row.getBigDecimal("acres")).isEqualByComparingTo("2.50");
                assertThat(row.getString("sales_channel")).isEqualTo("FRESH");
                assertThat(row.getString("stage")).isEqualTo("GROWING");
            }
        }
    }

    @Test
    @DisplayName("nursery workspace stores live-plant plans with guarded dates and counts")
    void enforcesNurseryBatchConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-nursery-batches;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'NURSERY_FARM' WHERE id = 1
                    """);
            statement.execute("""
                    INSERT INTO nursery_batches(
                        batch_name, plant_name, cultivar, plant_form, growing_area,
                        started_date, planned_ready_date, starting_count,
                        sales_channel, stage, notes, created_at, updated_at)
                    VALUES ('Spring starts', 'Dahlias', 'Cafe au Lait',
                        'STARTS_OR_PLUGS', 'Greenhouse 2', DATE '2026-03-01',
                        DATE '2026-05-01', 120, 'RETAIL', 'GROWING', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nursery_batches SET starting_count = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE nursery_batches
                    SET planned_ready_date = DATE '2026-02-28' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nursery_batches SET plant_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nursery_batches SET plant_form = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nursery_batches SET sales_channel = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nursery_batches SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT plant_name, starting_count, plant_form, sales_channel, stage
                    FROM nursery_batches WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("plant_name")).isEqualTo("Dahlias");
                assertThat(row.getInt("starting_count")).isEqualTo(120);
                assertThat(row.getString("plant_form")).isEqualTo("STARTS_OR_PLUGS");
                assertThat(row.getString("sales_channel")).isEqualTo("RETAIL");
                assertThat(row.getString("stage")).isEqualTo("GROWING");
            }
        }
    }

    @Test
    @DisplayName("livestock workspace stores herd groups and guards head counts")
    void enforcesHerdGroupConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-herd-groups;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'LIVESTOCK_FARM' WHERE id = 1
                    """);
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'LIVESTOCK_FARM')
                    """);
            statement.execute("""
                    INSERT INTO herd_groups(
                        group_name, species, head_count, location, purpose,
                        next_check_date, notes, created_at, updated_at)
                    VALUES ('Spring calves', 'Cattle', 0, 'West pasture', 'Growing',
                        DATE '2026-10-03', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE herd_groups SET head_count = -1 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE herd_groups SET head_count = 1000001 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE herd_groups SET head_count = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE herd_groups SET group_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE herd_groups SET species = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.execute("UPDATE herd_groups SET head_count = 18 WHERE id = 1");
            try (ResultSet row = statement.executeQuery("""
                    SELECT species, head_count, next_check_date
                    FROM herd_groups WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("species")).isEqualTo("Cattle");
                assertThat(row.getInt("head_count")).isEqualTo(18);
                assertThat(row.getDate("next_check_date").toLocalDate())
                        .isEqualTo(java.time.LocalDate.of(2026, 10, 3));
            }
        }
    }

    @Test
    @DisplayName("poultry migration keeps an existing combined farm and accepts the new workspace")
    void poultryMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-poultry-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("29"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'POTATO_FARM'), (1, 2, 'LIVESTOCK_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(20);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("display_order")).isZero();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("display_order")).isEqualTo(1);
                assertThat(rows.getString("workspace_code")).isEqualTo("POTATO_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("display_order")).isEqualTo(2);
                assertThat(rows.getString("workspace_code")).isEqualTo("LIVESTOCK_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'POULTRY_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'POULTRY_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery("""
                    SELECT workspace_code FROM farm_operation_settings WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("POULTRY_FARM");
            }
        }
    }

    @Test
    @DisplayName("egg collections allow repeat days and guard required names and counts")
    void enforcesEggCollectionConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-egg-collections;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            String insert = """
                    INSERT INTO egg_collections(
                        collection_date, flock_name, location, collected_count,
                        unusable_count, notes, created_at, updated_at)
                    VALUES (DATE '2026-10-01', 'Layers', 'Coop A', 84, 3, '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """;
            statement.execute(insert);
            statement.execute(insert);
            try (ResultSet rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM egg_collections
                    WHERE collection_date = DATE '2026-10-01' AND flock_name = 'Layers'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE egg_collections SET collection_date = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE egg_collections SET flock_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE egg_collections SET collected_count = -1 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE egg_collections SET collected_count = 1000001 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE egg_collections SET unusable_count = -1 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE egg_collections SET unusable_count = 85 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.execute("UPDATE egg_collections SET unusable_count = 0 WHERE id = 1");
        }
    }

    @Test
    @DisplayName("dairy migration keeps an existing combined farm and accepts the new workspace")
    void dairyMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-dairy-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("30"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'POTATO_FARM'), (1, 2, 'POULTRY_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(19);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("display_order")).isZero();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("display_order")).isEqualTo(1);
                assertThat(rows.getString("workspace_code")).isEqualTo("POTATO_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("display_order")).isEqualTo(2);
                assertThat(rows.getString("workspace_code")).isEqualTo("POULTRY_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'DAIRY_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'DAIRY_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery("""
                    SELECT workspace_code FROM farm_operation_settings WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("DAIRY_FARM");
            }
        }
    }

    @Test
    @DisplayName("milk collections allow repeat sessions and guard names, amounts, and units")
    void enforcesMilkCollectionConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-milk-collections;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            String insert = """
                    INSERT INTO milk_collections(
                        collection_date, group_name, session_name, tank_location,
                        amount, unit_code, notes, created_at, updated_at)
                    VALUES (DATE '2026-10-01', 'Milking herd', 'Morning', 'Tank A',
                        125.50, 'POUNDS', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """;
            statement.execute(insert);
            statement.execute(insert);
            try (ResultSet rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM milk_collections
                    WHERE collection_date = DATE '2026-10-01'
                        AND group_name = 'Milking herd'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE milk_collections SET collection_date = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE milk_collections SET group_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE milk_collections SET session_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE milk_collections SET amount = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE milk_collections SET amount = 1000000.01 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE milk_collections SET unit_code = 'BARRELS' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.execute("""
                    UPDATE milk_collections
                    SET amount = 1000000.00, unit_code = 'GALLONS' WHERE id = 1
                    """);
        }
    }

    private static void assertFarmOperationDefaults(Connection connection) throws Exception {
        assertThat(tableExists(connection, "FARM_OPERATION_SETTINGS")).isTrue();
        assertThat(tableExists(connection, "FARM_OPERATION_ENTERPRISES")).isTrue();
        assertThat(tableExists(connection, "FARM_OPERATION_SPECIALTY_CROPS")).isTrue();
        assertThat(tableExists(connection, "FARM_OPERATION_FLOWER_OFFERINGS")).isTrue();
        assertThat(tableExists(connection, "FARM_OPERATION_WORKSPACES")).isTrue();
        try (Statement statement = connection.createStatement();
             ResultSet settings = statement.executeQuery("""
                     SELECT state_code, primary_enterprise, workspace_code
                     FROM farm_operation_settings WHERE id = 1
                     """)) {
            assertThat(settings.next()).isTrue();
            assertThat(settings.getString("state_code")).isEqualTo("WA");
            assertThat(settings.getString("primary_enterprise"))
                    .isEqualTo("CUT_FLOWERS");
            assertThat(settings.getString("workspace_code"))
                    .isEqualTo("ROSE_AND_EVENTS");
            assertThat(settings.next()).isFalse();
        }
        try (Statement statement = connection.createStatement();
             ResultSet enterprises = statement.executeQuery("""
                     SELECT enterprise_code FROM farm_operation_enterprises
                     WHERE settings_id = 1
                     """)) {
            assertThat(enterprises.next()).isTrue();
            assertThat(enterprises.getString("enterprise_code"))
                    .isEqualTo("CUT_FLOWERS");
            assertThat(enterprises.next()).isFalse();
        }
        try (Statement statement = connection.createStatement();
             ResultSet workspaces = statement.executeQuery("""
                     SELECT display_order, workspace_code
                     FROM farm_operation_workspaces WHERE settings_id = 1
                     """)) {
            assertThat(workspaces.next()).isTrue();
            assertThat(workspaces.getInt("display_order")).isZero();
            assertThat(workspaces.getString("workspace_code"))
                    .isEqualTo("ROSE_AND_EVENTS");
            assertThat(workspaces.next()).isFalse();
        }
        try (Statement statement = connection.createStatement();
             ResultSet offerings = statement.executeQuery("""
                     SELECT offering_code FROM farm_operation_flower_offerings
                     WHERE settings_id = 1 ORDER BY offering_code
                     """)) {
            assertThat(offerings.next()).isTrue();
            assertThat(offerings.getString("offering_code")).isEqualTo("BOUQUETS");
            assertThat(offerings.next()).isTrue();
            assertThat(offerings.getString("offering_code")).isEqualTo("STEMS");
            assertThat(offerings.next()).isTrue();
            assertThat(offerings.getString("offering_code")).isEqualTo("WEDDINGS");
            assertThat(offerings.next()).isFalse();
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

    @Test
    @DisplayName("vineyard migration keeps an existing combined farm and accepts the new workspace")
    void vineyardMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-vineyard-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("31"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'POTATO_FARM'), (1, 2, 'DAIRY_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(18);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("POTATO_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("DAIRY_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'VINEYARD_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'VINEYARD_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("VINEYARD_FARM");
            }
            assertThat(tableExists(connection, "VINEYARD_BLOCK_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("vineyard block plans guard acreage, harvest windows, and status values")
    void enforcesVineyardBlockPlanConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-vineyard-blocks;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO vineyard_block_plans(
                        block_name, location, grape_variety, acres,
                        planned_harvest_start, planned_harvest_end,
                        destination, stage, notes, created_at, updated_at)
                    VALUES ('North slope', 'West bench', 'Pinot noir', 2.50,
                        DATE '2026-09-01', DATE '2026-10-15',
                        'WINERY_BUYER', 'GROWING', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vineyard_block_plans SET block_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vineyard_block_plans SET grape_variety = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vineyard_block_plans SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vineyard_block_plans SET acres = 1000000.01 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE vineyard_block_plans
                    SET planned_harvest_end = DATE '2026-08-31' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vineyard_block_plans SET destination = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vineyard_block_plans SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT block_name, grape_variety, acres, destination, stage
                    FROM vineyard_block_plans WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("block_name")).isEqualTo("North slope");
                assertThat(row.getString("grape_variety")).isEqualTo("Pinot noir");
                assertThat(row.getBigDecimal("acres")).isEqualByComparingTo("2.50");
                assertThat(row.getString("destination")).isEqualTo("WINERY_BUYER");
                assertThat(row.getString("stage")).isEqualTo("GROWING");
            }
        }
    }

    @Test
    @DisplayName("hay migration keeps an existing combined farm and accepts the new workspace")
    void hayMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-hay-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("32"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'DAIRY_FARM'), (1, 2, 'VINEYARD_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(17);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("DAIRY_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("VINEYARD_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'HAY_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'HAY_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("HAY_FARM");
            }
            assertThat(tableExists(connection, "HAY_CUTTINGS")).isTrue();
        }
    }

    @Test
    @DisplayName("hay cuttings guard required details, acreage, dates, and stage")
    void enforcesHayCuttingConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-hay-cuttings;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO hay_cuttings(
                        field_name, forage_crop, cutting_number, acres,
                        planned_cut_date, planned_bale_date, stage, notes,
                        created_at, updated_at)
                    VALUES ('North field', 'Alfalfa', 2, 12.50,
                        DATE '2026-06-01', DATE '2026-06-05', 'CUT', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hay_cuttings SET field_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hay_cuttings SET forage_crop = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hay_cuttings SET cutting_number = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hay_cuttings SET cutting_number = 100 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hay_cuttings SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hay_cuttings SET acres = 1000000.01 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hay_cuttings SET planned_cut_date = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE hay_cuttings
                    SET planned_bale_date = DATE '2026-05-31' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hay_cuttings SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT field_name, forage_crop, cutting_number, acres, stage
                    FROM hay_cuttings WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("field_name")).isEqualTo("North field");
                assertThat(row.getString("forage_crop")).isEqualTo("Alfalfa");
                assertThat(row.getInt("cutting_number")).isEqualTo(2);
                assertThat(row.getBigDecimal("acres")).isEqualByComparingTo("12.50");
                assertThat(row.getString("stage")).isEqualTo("CUT");
            }
        }
    }

    @Test
    @DisplayName("hops migration keeps an existing combined farm and accepts the new workspace")
    void hopsMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-hops-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("33"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'DAIRY_FARM'), (1, 2, 'HAY_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(16);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("DAIRY_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("HAY_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'HOPS_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'HOPS_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("HOPS_FARM");
            }
            assertThat(tableExists(connection, "HOP_YARD_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("hop yard plans guard required details, acreage, dates, and stage")
    void enforcesHopYardConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-hop-yards;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO hop_yard_plans(
                        yard_name, hop_variety, acres, planned_stringing_date,
                        planned_harvest_start, planned_harvest_end, stage, notes,
                        created_at, updated_at)
                    VALUES ('North yard', 'Cascade', 12.50, DATE '2026-03-01',
                        DATE '2026-08-20', DATE '2026-08-30', 'STRINGING', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO hop_yard_plans(
                        yard_name, hop_variety, planned_harvest_start,
                        stage, notes, created_at, updated_at)
                    VALUES ('North yard', 'Cascade', DATE '2027-08-20',
                        'PLANNED', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hop_yard_plans SET yard_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hop_yard_plans SET hop_variety = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hop_yard_plans SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hop_yard_plans SET acres = 1000000.01 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hop_yard_plans SET planned_harvest_start = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE hop_yard_plans
                    SET planned_stringing_date = DATE '2026-08-21' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE hop_yard_plans
                    SET planned_harvest_end = DATE '2026-08-19' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hop_yard_plans SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT yard_name, hop_variety, acres, stage
                    FROM hop_yard_plans WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("yard_name")).isEqualTo("North yard");
                assertThat(row.getString("hop_variety")).isEqualTo("Cascade");
                assertThat(row.getBigDecimal("acres")).isEqualByComparingTo("12.50");
                assertThat(row.getString("stage")).isEqualTo("STRINGING");
            }
        }
    }

    @Test
    @DisplayName("grain migration keeps an existing combined farm and accepts the new workspace")
    void grainMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-grain-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("34"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'DAIRY_FARM'), (1, 2, 'HOPS_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(15);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("DAIRY_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("HOPS_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'GRAIN_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'GRAIN_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("GRAIN_FARM");
            }
            assertThat(tableExists(connection, "GRAIN_FIELD_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("grain field plans guard required details, acreage, dates, and stage")
    void enforcesGrainFieldConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-grain-fields;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO grain_field_plans(
                        field_name, grain_crop, variety, acres, previous_crop,
                        planned_seeding_date, planned_harvest_start, planned_harvest_end,
                        stage, notes, created_at, updated_at)
                    VALUES ('North field', 'Wheat', 'Soft white', 12.50, 'Canola',
                        DATE '2026-03-01', DATE '2026-08-20', DATE '2026-08-30',
                        'SEEDED', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO grain_field_plans(
                        field_name, grain_crop, variety, previous_crop,
                        planned_harvest_start, stage, notes, created_at, updated_at)
                    VALUES ('West field', 'Oats', '', '', DATE '2027-08-20',
                        'PLANNED', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE grain_field_plans SET field_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE grain_field_plans SET grain_crop = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE grain_field_plans SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE grain_field_plans SET acres = 1000000.01 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE grain_field_plans SET planned_harvest_start = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE grain_field_plans
                    SET planned_seeding_date = DATE '2026-08-21' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE grain_field_plans
                    SET planned_harvest_end = DATE '2026-08-19' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE grain_field_plans SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT field_name, grain_crop, variety, acres, previous_crop, stage
                    FROM grain_field_plans WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("field_name")).isEqualTo("North field");
                assertThat(row.getString("grain_crop")).isEqualTo("Wheat");
                assertThat(row.getString("variety")).isEqualTo("Soft white");
                assertThat(row.getBigDecimal("acres")).isEqualByComparingTo("12.50");
                assertThat(row.getString("previous_crop")).isEqualTo("Canola");
                assertThat(row.getString("stage")).isEqualTo("SEEDED");
            }
        }
    }

    @Test
    @DisplayName("seed migration keeps an existing combined farm and accepts the new workspace")
    void seedMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-seed-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("35"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'DAIRY_FARM'), (1, 2, 'GRAIN_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(14);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("DAIRY_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("GRAIN_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'SEED_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'SEED_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("SEED_FARM");
            }
            assertThat(tableExists(connection, "SEED_FIELD_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("seed field plans guard required details, acreage, dates, and stage")
    void enforcesSeedFieldConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-seed-fields;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO seed_field_plans(
                        field_name, seed_crop, variety, acres, previous_crop,
                        planned_sowing_date, planned_flowering_start,
                        planned_flowering_end, planned_harvest_start, planned_harvest_end,
                        intended_use, isolation_notes, stage, notes, created_at, updated_at)
                    VALUES ('West stand', 'Ryegrass', 'Perennial', 12.50, 'Wheat',
                        DATE '2026-03-01', DATE '2026-06-01', DATE '2026-06-10',
                        DATE '2026-08-20', DATE '2026-08-30', 'Turf', 'North boundary',
                        'SOWN', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO seed_field_plans(
                        field_name, seed_crop, variety, previous_crop,
                        planned_harvest_start, intended_use, isolation_notes,
                        stage, notes, created_at, updated_at)
                    VALUES ('East stand', 'Clover', '', '', DATE '2027-08-20',
                        '', '', 'PLANNED', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE seed_field_plans SET field_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE seed_field_plans SET seed_crop = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE seed_field_plans SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE seed_field_plans SET planned_harvest_start = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE seed_field_plans
                    SET planned_sowing_date = DATE '2026-08-21' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE seed_field_plans
                    SET planned_flowering_end = DATE '2026-05-31' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE seed_field_plans
                    SET planned_harvest_end = DATE '2026-08-19' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE seed_field_plans SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT field_name, seed_crop, variety, acres, intended_use, stage
                    FROM seed_field_plans WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("field_name")).isEqualTo("West stand");
                assertThat(row.getString("seed_crop")).isEqualTo("Ryegrass");
                assertThat(row.getBigDecimal("acres")).isEqualByComparingTo("12.50");
                assertThat(row.getString("intended_use")).isEqualTo("Turf");
                assertThat(row.getString("stage")).isEqualTo("SOWN");
            }
        }
    }

    @Test
    @DisplayName("tree nut migration keeps an existing combined farm and accepts the orchard workspace")
    void nutMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-nut-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("36"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'APPLE_ORCHARD'), (1, 2, 'SEED_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(13);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("APPLE_ORCHARD");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("SEED_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'NUT_ORCHARD')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'NUT_ORCHARD' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("NUT_ORCHARD");
            }
            assertThat(tableExists(connection, "NUT_BLOCK_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("tree nut blocks guard required details, acreage, trees, dates, and stage")
    void enforcesNutBlockConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-nut-blocks;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO nut_block_plans(
                        block_name, nut_crop, variety, pollinizer_variety,
                        acres, tree_count, planned_bloom_start, planned_floor_prep_date,
                        planned_harvest_start, planned_harvest_end, intended_destination,
                        stage, notes, created_at, updated_at)
                    VALUES ('North block', 'Hazelnut', 'Jefferson', 'Eta',
                        12.50, 500, DATE '2026-02-01', DATE '2026-08-01',
                        DATE '2026-09-20', DATE '2026-09-30', 'Processor',
                        'NUT_SET', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO nut_block_plans(
                        block_name, nut_crop, variety, pollinizer_variety,
                        planned_harvest_start, intended_destination, stage, notes,
                        created_at, updated_at)
                    VALUES ('East block', 'Walnut', '', '', DATE '2027-10-01',
                        '', 'PLANNED', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nut_block_plans SET block_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nut_block_plans SET nut_crop = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nut_block_plans SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nut_block_plans SET tree_count = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE nut_block_plans
                    SET planned_bloom_start = DATE '2026-09-21' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE nut_block_plans
                    SET planned_floor_prep_date = DATE '2026-09-21' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE nut_block_plans
                    SET planned_harvest_end = DATE '2026-09-19' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE nut_block_plans SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT block_name, nut_crop, acres, tree_count, stage
                    FROM nut_block_plans WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("block_name")).isEqualTo("North block");
                assertThat(row.getString("nut_crop")).isEqualTo("Hazelnut");
                assertThat(row.getBigDecimal("acres")).isEqualByComparingTo("12.50");
                assertThat(row.getInt("tree_count")).isEqualTo(500);
                assertThat(row.getString("stage")).isEqualTo("NUT_SET");
            }
        }
    }

    @Test
    @DisplayName("vegetable migration keeps an existing combined farm and accepts the new workspace")
    void vegetableMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-vegetable-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("37"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'POTATO_FARM'), (1, 2, 'NUT_ORCHARD')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(12);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("POTATO_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("NUT_ORCHARD");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'VEGETABLE_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'VEGETABLE_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("VEGETABLE_FARM");
            }
            assertThat(tableExists(connection, "VEGETABLE_PLANTING_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("vegetable plans guard required details, succession, acreage, dates, and stage")
    void enforcesVegetablePlantingConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-vegetable-plantings;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO vegetable_planting_plans(
                        field_or_bed, vegetable_crop, variety, previous_crop,
                        acres, succession_number, planned_sowing_date,
                        planned_transplant_date, planned_harvest_start,
                        planned_harvest_end, intended_channel, stage, notes,
                        created_at, updated_at)
                    VALUES ('Bed 3', 'Lettuce', 'Buttercrunch', 'Beans',
                        0.50, 1, DATE '2026-07-20', DATE '2026-08-20',
                        DATE '2026-09-20', DATE '2026-09-27', 'CSA', 'GROWING', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO vegetable_planting_plans(
                        field_or_bed, vegetable_crop, variety, previous_crop,
                        succession_number, planned_harvest_start, intended_channel,
                        stage, notes, created_at, updated_at)
                    VALUES ('Field 2', 'Squash', '', '', 2, DATE '2027-09-01',
                        '', 'PLANNED', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vegetable_planting_plans SET field_or_bed = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vegetable_planting_plans SET vegetable_crop = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vegetable_planting_plans SET succession_number = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vegetable_planting_plans SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vegetable_planting_plans SET planned_harvest_start = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE vegetable_planting_plans
                    SET planned_sowing_date = DATE '2026-09-21' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE vegetable_planting_plans
                    SET planned_transplant_date = DATE '2026-09-21' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE vegetable_planting_plans
                    SET planned_transplant_date = DATE '2026-07-19' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE vegetable_planting_plans
                    SET planned_harvest_end = DATE '2026-09-19' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE vegetable_planting_plans SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT field_or_bed, vegetable_crop, succession_number, acres, stage
                    FROM vegetable_planting_plans WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("field_or_bed")).isEqualTo("Bed 3");
                assertThat(row.getString("vegetable_crop")).isEqualTo("Lettuce");
                assertThat(row.getInt("succession_number")).isEqualTo(1);
                assertThat(row.getBigDecimal("acres")).isEqualByComparingTo("0.50");
                assertThat(row.getString("stage")).isEqualTo("GROWING");
            }
        }
    }

    @Test
    @DisplayName("aquaculture migration keeps an existing combined farm and accepts the new workspace")
    void aquacultureMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-aquaculture-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("38"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'POTATO_FARM'), (1, 2, 'VEGETABLE_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(11);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT display_order, workspace_code
                    FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("POTATO_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workspace_code")).isEqualTo("VEGETABLE_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'AQUACULTURE_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'AQUACULTURE_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("AQUACULTURE_FARM");
            }
            assertThat(tableExists(connection, "AQUATIC_COHORT_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("aquaculture plans guard required details, date order, and stage")
    void enforcesAquaticCohortConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-aquaculture-cohorts;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO aquatic_cohort_plans(
                        cohort_name, species, growing_site, culture_method,
                        planned_stocking_date, planned_harvest_start,
                        planned_harvest_end, intended_destination, stage, notes,
                        created_at, updated_at)
                    VALUES ('Bay 3', 'Pacific oyster', 'South lease', 'Bottom culture',
                        DATE '2026-10-20', DATE '2027-07-20', DATE '2027-08-03',
                        'Wholesale', 'GROWING_OUT', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO aquatic_cohort_plans(
                        cohort_name, species, growing_site, culture_method,
                        planned_harvest_start, intended_destination, stage, notes,
                        created_at, updated_at)
                    VALUES ('Line 2', 'Sugar kelp', 'West line', '',
                        DATE '2027-06-01', '', 'PLANNED', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE aquatic_cohort_plans SET cohort_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE aquatic_cohort_plans SET species = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE aquatic_cohort_plans SET growing_site = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE aquatic_cohort_plans SET planned_harvest_start = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE aquatic_cohort_plans
                    SET planned_stocking_date = DATE '2027-07-21' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE aquatic_cohort_plans
                    SET planned_harvest_end = DATE '2027-07-19' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE aquatic_cohort_plans SET stage = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            try (ResultSet row = statement.executeQuery("""
                    SELECT cohort_name, species, growing_site, stage
                    FROM aquatic_cohort_plans WHERE id = 1
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("cohort_name")).isEqualTo("Bay 3");
                assertThat(row.getString("species")).isEqualTo("Pacific oyster");
                assertThat(row.getString("growing_site")).isEqualTo("South lease");
                assertThat(row.getString("stage")).isEqualTo("GROWING_OUT");
            }
        }
    }

    @Test
    @DisplayName("Christmas tree migration preserves a combined farm and accepts the new workspace")
    void christmasTreeMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-christmas-tree-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("39"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'NURSERY_FARM'), (1, 2, 'AQUACULTURE_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(10);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT workspace_code FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("NURSERY_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("AQUACULTURE_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'CHRISTMAS_TREE_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'CHRISTMAS_TREE_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("CHRISTMAS_TREE_FARM");
            }
            assertThat(tableExists(connection, "CHRISTMAS_TREE_BLOCK_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("Christmas tree plans guard years, acreage, counts, cut dates, and stage")
    void enforcesChristmasTreeBlockConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-christmas-tree-blocks;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO christmas_tree_block_plans(
                        block_name, tree_species, planting_year, acres,
                        planted_tree_count, target_height, next_shearing_date,
                        target_harvest_year, planned_cut_start, planned_cut_end,
                        intended_channel, stage, notes, created_at, updated_at)
                    VALUES ('North slope', 'Noble fir', 2025, 2.50, 3000, '6-7 ft',
                        DATE '2033-09-01', 2033, DATE '2033-11-20',
                        DATE '2033-12-20', 'Choose and cut', 'GROWING', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO christmas_tree_block_plans(
                        block_name, tree_species, target_height, target_harvest_year,
                        intended_channel, stage, notes, created_at, updated_at)
                    VALUES ('East block', 'Douglas fir', '', 2035, '', 'PLANNED', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE christmas_tree_block_plans SET block_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE christmas_tree_block_plans SET tree_species = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE christmas_tree_block_plans SET target_harvest_year = 1800 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE christmas_tree_block_plans SET planting_year = 2034 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE christmas_tree_block_plans SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE christmas_tree_block_plans SET planted_tree_count = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE christmas_tree_block_plans
                    SET planned_cut_start = DATE '2034-01-01' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE christmas_tree_block_plans
                    SET planned_cut_end = DATE '2033-11-01' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE christmas_tree_block_plans
                    SET next_shearing_date = DATE '2033-12-01' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE christmas_tree_block_plans
                    SET next_shearing_date = DATE '2036-01-01' WHERE id = 2
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE christmas_tree_block_plans SET stage = 'CUT' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    @DisplayName("apiary migration preserves a combined farm and accepts the new workspace")
    void apiaryMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-apiary-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("40"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'NURSERY_FARM'), (1, 2, 'CHRISTMAS_TREE_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(9);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT workspace_code FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("NURSERY_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("CHRISTMAS_TREE_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'APIARY_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'APIARY_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("APIARY_FARM");
            }
            assertThat(tableExists(connection, "HIVE_CHECKS")).isTrue();
        }
    }

    @Test
    @DisplayName("hive checks allow repeat visits and guard required details, dates, and status")
    void enforcesHiveCheckConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-hive-checks;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO hive_checks(
                        apiary_name, hive_label, check_date, purpose, status,
                        next_check_date, observations, actions, created_at, updated_at)
                    VALUES ('East apiary', 'Hive 4', DATE '2027-04-12', 'Spring check',
                        'ACTIVE', DATE '2027-04-22', 'Adult bees at entrance',
                        'Observe again', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO hive_checks(
                        apiary_name, hive_label, check_date, purpose, status,
                        observations, actions, created_at, updated_at)
                    VALUES ('East apiary', 'Hive 4', DATE '2027-04-22', '',
                        'NOT_ASSESSED', '', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            try (ResultSet count = statement.executeQuery("""
                    SELECT COUNT(*) FROM hive_checks
                    WHERE apiary_name = 'East apiary' AND hive_label = 'Hive 4'
                    """)) {
                assertThat(count.next()).isTrue();
                assertThat(count.getInt(1)).isEqualTo(2);
            }
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hive_checks SET apiary_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hive_checks SET hive_label = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hive_checks SET check_date = NULL WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE hive_checks
                    SET next_check_date = DATE '2027-04-11' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE hive_checks SET status = 'DIAGNOSED' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    @DisplayName("mushroom migration preserves a combined farm and accepts the new workspace")
    void mushroomMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-mushroom-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("41"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'APIARY_FARM'), (1, 2, 'CHRISTMAS_TREE_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(8);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT workspace_code FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("APIARY_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("CHRISTMAS_TREE_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'MUSHROOM_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'MUSHROOM_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("MUSHROOM_FARM");
            }
            assertThat(tableExists(connection, "MUSHROOM_BATCH_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("mushroom plans guard names, fruiting dates, and stage")
    void enforcesMushroomBatchConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-mushroom-batches;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO mushroom_batch_plans(
                        batch_name, mushroom_species, growing_method, substrate,
                        growing_site, planned_inoculation_date,
                        planned_fruiting_start, planned_fruiting_end,
                        intended_channel, stage, notes, created_at, updated_at)
                    VALUES ('Log set A', 'Shiitake', 'Logs', 'Hardwood', 'North grove',
                        DATE '2027-02-10', DATE '2027-08-10', DATE '2027-09-10',
                        'Farm stand', 'INCUBATING', '', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO mushroom_batch_plans(
                        batch_name, mushroom_species, growing_method, substrate,
                        growing_site, intended_channel, stage, notes,
                        created_at, updated_at)
                    VALUES ('Room 2', 'Oyster', '', '', '', '', 'PLANNED', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE mushroom_batch_plans SET batch_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE mushroom_batch_plans SET mushroom_species = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE mushroom_batch_plans
                    SET planned_fruiting_start = DATE '2027-02-09' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE mushroom_batch_plans
                    SET planned_fruiting_end = DATE '2027-08-09' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE mushroom_batch_plans
                    SET planned_fruiting_end = DATE '2027-09-10' WHERE id = 2
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE mushroom_batch_plans SET stage = 'HARVESTED' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    @DisplayName("cranberry migration preserves a combined farm and accepts the new workspace")
    void cranberryMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-cranberry-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("42"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'MUSHROOM_FARM'), (1, 2, 'APIARY_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(7);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT workspace_code FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("MUSHROOM_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("APIARY_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'CRANBERRY_FARM')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'CRANBERRY_FARM' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("CRANBERRY_FARM");
            }
            assertThat(tableExists(connection, "CRANBERRY_BED_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("cranberry plans guard bed name, acreage, method, harvest dates, and stage")
    void enforcesCranberryBedConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-cranberry-beds;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO cranberry_bed_plans(
                        bed_name, variety, acres, harvest_method,
                        planned_harvest_start, planned_harvest_end,
                        intended_channel, stage, notes, created_at, updated_at)
                    VALUES ('North bed', 'Stevens', 2.50, 'WET',
                        DATE '2027-10-01', DATE '2027-10-20',
                        'Processor', 'HARVEST_PREP', '', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO cranberry_bed_plans(
                        bed_name, variety, harvest_method, intended_channel,
                        stage, notes, created_at, updated_at)
                    VALUES ('South bed', '', 'UNDECIDED', '', 'PLANNED', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE cranberry_bed_plans SET bed_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE cranberry_bed_plans SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE cranberry_bed_plans SET harvest_method = 'UNKNOWN' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE cranberry_bed_plans
                    SET planned_harvest_end = DATE '2027-09-30' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE cranberry_bed_plans
                    SET planned_harvest_end = DATE '2027-10-20' WHERE id = 2
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE cranberry_bed_plans SET stage = 'HARVESTED' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    @DisplayName("pumpkin migration preserves a combined farm and accepts the new workspace")
    void pumpkinMigrationPreservesExistingMix() throws Exception {
        String url = "jdbc:h2:mem:migration-pumpkin-mix;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("43"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 1, 'CRANBERRY_FARM'), (1, 2, 'MUSHROOM_FARM')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(6);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT workspace_code FROM farm_operation_workspaces
                    WHERE settings_id = 1 ORDER BY display_order
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("ROSE_AND_EVENTS");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("CRANBERRY_FARM");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("MUSHROOM_FARM");
                assertThat(rows.next()).isFalse();
            }
            statement.execute("""
                    INSERT INTO farm_operation_workspaces(
                        settings_id, display_order, workspace_code)
                    VALUES (1, 3, 'PUMPKIN_PATCH')
                    """);
            statement.execute("""
                    UPDATE farm_operation_settings
                    SET workspace_code = 'PUMPKIN_PATCH' WHERE id = 1
                    """);
            try (ResultSet row = statement.executeQuery(
                    "SELECT workspace_code FROM farm_operation_settings WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("PUMPKIN_PATCH");
            }
            assertThat(tableExists(connection, "PUMPKIN_PATCH_PLANS")).isTrue();
        }
    }

    @Test
    @DisplayName("pumpkin plans guard patch name, acreage, dates, sales mode, and stage")
    void enforcesPumpkinPatchConstraints() throws Exception {
        String url = "jdbc:h2:mem:migration-pumpkin-patches;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO pumpkin_patch_plans(
                        patch_name, variety, acres, planned_planting_date,
                        planned_picking_start, planned_picking_end, sales_mode,
                        visitor_area_notes, stage, notes, created_at, updated_at)
                    VALUES ('North patch', 'Jack O Lantern', 2.50,
                        DATE '2027-05-15', DATE '2027-10-01', DATE '2027-10-20',
                        'PICK_YOUR_OWN', 'East entrance', 'GROWING', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO pumpkin_patch_plans(
                        patch_name, variety, sales_mode, visitor_area_notes,
                        stage, notes, created_at, updated_at)
                    VALUES ('South patch', '', 'UNDECIDED', '', 'PLANNED', '',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE pumpkin_patch_plans SET patch_name = ' ' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE pumpkin_patch_plans SET acres = 0 WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE pumpkin_patch_plans
                    SET planned_picking_start = DATE '2027-05-14' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE pumpkin_patch_plans
                    SET planned_picking_end = DATE '2027-09-30' WHERE id = 1
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE pumpkin_patch_plans
                    SET planned_picking_end = DATE '2027-10-20' WHERE id = 2
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE pumpkin_patch_plans SET sales_mode = 'TICKETS' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE pumpkin_patch_plans SET stage = 'HARVESTED' WHERE id = 1"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    @DisplayName("field science link migration preserves old lessons and enforces an existing export")
    void linksKnowledgeToReviewedFieldScienceExports() throws Exception {
        String url = "jdbc:h2:mem:migration-knowledge-field-science;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("45"))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO knowledge_entries(
                        observed_on, created_at, updated_at, kind, confidence,
                        summary, observation)
                    VALUES (DATE '2026-08-20', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, 'PRACTICE', 'NEEDS_REVIEW',
                        'Earlier finding', 'Observed before this upgrade')
                    """);
        }

        assertThat(configuredFlyway(url).migrate().migrationsExecuted).isEqualTo(4);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(columnExists(connection, "KNOWLEDGE_ENTRIES",
                    "FIELD_SCIENCE_EXPORT_ID")).isTrue();
            try (ResultSet old = statement.executeQuery("""
                    SELECT field_science_export_id FROM knowledge_entries
                    WHERE summary = 'Earlier finding'
                    """)) {
                assertThat(old.next()).isTrue();
                assertThat(old.getObject(1)).isNull();
            }
            statement.execute("""
                    INSERT INTO field_science_exports(
                        created_at, instrument_id, instrument_title, source_url,
                        source_commit, title, filename, note, byte_length,
                        sha256, content)
                    VALUES (CURRENT_TIMESTAMP, 'farm-scout', 'Farm Scout',
                        'https://example.org/farm-scout', '1234567890123456789012345678901234567890',
                        'Reviewed walk', 'walk.txt', '', 4,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'data')
                    """);
            statement.execute("""
                    UPDATE knowledge_entries SET field_science_export_id = 1
                    WHERE summary = 'Earlier finding'
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    UPDATE knowledge_entries SET field_science_export_id = 999
                    WHERE summary = 'Earlier finding'
                    """)).isInstanceOf(java.sql.SQLException.class);
            statement.execute("DELETE FROM field_science_exports WHERE id = 1");
            try (ResultSet remaining = statement.executeQuery("""
                    SELECT field_science_export_id FROM knowledge_entries
                    WHERE summary = 'Earlier finding'
                    """)) {
                assertThat(remaining.next()).isTrue();
                assertThat(remaining.getObject(1)).isNull();
            }
        }
    }

    @Test
    @DisplayName("an order line can link to a recipe; deleting the recipe clears the link and keeps the line")
    void orderLineRecipeLinkSurvivesRecipeDeletion() throws Exception {
        String url = "jdbc:h2:mem:migration-recipe-link;DB_CLOSE_DELAY=-1";
        configuredFlyway(url).migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO customers(customer_type, name) VALUES ('FLORIST', 'Event Florist')");
            statement.execute("""
                    INSERT INTO customer_orders(order_date, customer_id, status, notes, handoff_type)
                    VALUES (DATE '2026-10-17', 1, 'CONFIRMED', 'wedding', 'DELIVERY')
                    """);
            statement.execute("""
                    INSERT INTO floral_recipes(name, design_type, sale_price, labor_minutes, labor_rate_hour,
                        overhead_percent, waste_percent, created_at, updated_at)
                    VALUES ('Centerpiece', 'Centerpiece', 65, 20, 25, 10, 10,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO order_lines(order_id, product_name, quantity, unit, unit_price, recipe_id)
                    VALUES (1, 'Centerpiece', 12, 'designs', 65, 1)
                    """);

            statement.execute("DELETE FROM floral_recipes WHERE id = 1");

            try (ResultSet rows = statement.executeQuery(
                    "SELECT product_name, recipe_id FROM order_lines WHERE order_id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Centerpiece");
                assertThat(rows.getObject(2)).isNull();
            }
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE order_lines SET recipe_id = 999 WHERE order_id = 1"))
                    .hasMessageContaining("FK_ORDER_LINE_RECIPE");
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
