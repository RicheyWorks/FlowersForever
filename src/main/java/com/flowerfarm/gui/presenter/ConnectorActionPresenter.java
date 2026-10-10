package com.flowerfarm.gui.presenter;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import java.util.List;

/**
 * Executes connector workflows and turns connector envelopes into view-ready feedback.
 */
public final class ConnectorActionPresenter {

    private final InventoryService inventoryService;
    private final ConnectorRegistry connectorRegistry;

    public ConnectorActionPresenter(InventoryService inventoryService,
                                    ConnectorRegistry connectorRegistry) {
        this.inventoryService = inventoryService;
        this.connectorRegistry = connectorRegistry;
    }

    public void exportInventory(String path) {
        inventoryService.exportToCsv(path);
    }

    public Feedback importFrom(String connectorName, String displayName) {
        ConnectorResult<List<Item>> result = connectorRegistry.runImport(connectorName);
        if (!result.isSuccess()) {
            return failure(displayName, "import", result);
        }
        int imported = result.getPayload() == null ? 0 : result.getPayload().size();
        return new Feedback(true, true,
                displayName + " import complete — imported " + imported + " item(s).",
                displayName + " Import Complete",
                displayName + " import complete.\nImported: " + imported);
    }

    public Feedback exportTo(String connectorName, String displayName) {
        ConnectorResult<Integer> result = connectorRegistry.runExport(connectorName);
        if (!result.isSuccess()) {
            return failure(displayName, "export", result);
        }
        int exported = result.getPayload() == null ? 0 : result.getPayload();
        return new Feedback(true, false,
                displayName + " export complete — exported " + exported + " item(s).",
                displayName + " Export Complete",
                displayName + " export complete.\nExported: " + exported);
    }

    public Feedback syncWith(String connectorName, String displayName) {
        ConnectorResult<SyncSummary> result = connectorRegistry.runSync(connectorName);
        if (!result.isSuccess()) {
            return failure(displayName, "sync", result);
        }
        SyncSummary summary = result.getPayload();
        String detail = summary == null ? result.getMessage() : summary.toString();
        return new Feedback(true, true,
                displayName + " sync complete — " + detail,
                displayName + " Sync Complete",
                displayName + " sync complete.\n" + detail);
    }

    private static Feedback failure(String displayName, String operation,
                                    ConnectorResult<?> result) {
        String capitalized = Character.toUpperCase(operation.charAt(0)) + operation.substring(1);
        return new Feedback(false, false,
                displayName + " " + operation + " failed: " + result.getMessage(),
                displayName + " " + capitalized + " Failed",
                result.getMessage() + "\n" + result.getErrorDetail());
    }

    public record Feedback(boolean success, boolean refreshRequired,
                           String status, String title, String message) {
    }
}
