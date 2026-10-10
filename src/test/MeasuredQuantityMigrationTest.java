package com.flowerfarm.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MeasuredQuantityMigrationTest {
    @Test void upgradingPreservesOldStockAndTheAmountEachOldHarvestActuallyCredited() throws Exception {
        String url = "jdbc:h2:mem:quantity-upgrade-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2")
                .target("48").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO inventory_items(name,category,price,unit,cost,quantity,notes) "
                    + "VALUES('Legacy Blue Dream','Cannabis',5,'grams',1,4,'old credited stock')");
            statement.executeUpdate("INSERT INTO harvest_log(harvest_date,crop_name,quantity,unit,lot_code) "
                    + "VALUES(DATE '2026-10-01','Legacy Blue Dream',3.5,'grams','LEGACY-WEIGHT')");
            statement.executeUpdate("INSERT INTO harvest_log(harvest_date,crop_name,quantity,unit,lot_code) "
                    + "VALUES(DATE '2026-09-01','Imported history',7.125,'grams','IMPORTED-WEIGHT')");
            statement.executeUpdate("INSERT INTO farm_data_import_receipts(target_record_id,imported_at,updated_at,"
                    + "source_system,record_type,action,content_fingerprint,source_key) "
                    + "SELECT id,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'legacy','harvests','import-harvest-history',"
                    + "'" + "a".repeat(64) + "','history-1' FROM harvest_log WHERE lot_code='IMPORTED-WEIGHT'");
        }
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT quantity FROM inventory_items")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("4");
            }
            try (var rows = statement.executeQuery("SELECT quantity,inventory_contribution FROM harvest_log ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getDouble(1)).isEqualTo(3.5);
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("4");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getDouble(1)).isEqualTo(7.125);
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("0");
            }
            statement.executeUpdate("UPDATE inventory_items SET quantity=0.123456");
            try (var rows = statement.executeQuery("SELECT quantity FROM inventory_items")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("0.123456");
            }
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE inventory_items SET quantity=2147483648"))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.execute("SHUTDOWN");
        }
    }
}
