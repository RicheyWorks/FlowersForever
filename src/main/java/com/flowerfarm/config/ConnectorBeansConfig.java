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
 * <p>All other connectors ({@code CsvInventoryConnector}, Shopify, Square,
 * Farmbrite, Floranext, etc.) are {@code @Component} beans and are picked up
 * automatically by Spring. Non-essential POS stubs were removed from the tree
 * so the registry only exposes maintained connectors.
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
            @Value("${connector.airtable.table-name:Table 1}") String tableName,
            @Value("${connector.airtable.local-file:}") String localFile) {
        return new AirtableConnector(apiKey, baseId, tableName, localFile);
    }
}
