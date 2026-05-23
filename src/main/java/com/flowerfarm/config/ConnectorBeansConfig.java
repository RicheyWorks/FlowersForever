package com.flowerfarm.config;

import com.flowerfarm.connector.impl.AirtableConnector;
import com.flowerfarm.connector.impl.ExcelConnector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers connectors whose constructors require property injection but are
 * not themselves {@code @Component} classes (AirtableConnector, ExcelConnector).
 *
 * <p>All other connectors ({@code CsvInventoryConnector}, the stub connectors)
 * are {@code @Component} beans and are picked up automatically by Spring.
 */
@Configuration
public class ConnectorBeansConfig {

    // ── Excel ─────────────────────────────────────────────────────────────────

    @Bean
    public ExcelConnector excelConnector(
            @Value("${connector.excel.file:flower_inventory.xlsx}") String filePath) {
        return new ExcelConnector(filePath);
    }

    // ── Airtable ──────────────────────────────────────────────────────────────

    @Bean
    public AirtableConnector airtableConnector(
            @Value("${connector.airtable.api-key:}")      String apiKey,
            @Value("${connector.airtable.base-id:}")      String baseId,
            @Value("${connector.airtable.table-name:Table 1}") String tableName) {
        return new AirtableConnector(apiKey, baseId, tableName);
    }
}
