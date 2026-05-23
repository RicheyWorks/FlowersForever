package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.ExternalConnector;
import com.flowerfarm.connector.SyncDirection;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ExcelConnector implements ExternalConnector<Row> {

    private final String filePath;

    public ExcelConnector(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public String getName() {
        return "excel";
    }

    @Override
    public String getDescription() {
        return "Excel XLSX file connector";
    }

    @Override
    public SyncDirection getSupportedDirection() {
        return SyncDirection.BIDIRECTIONAL;
    }

    @Override
    public boolean isAvailable() {
        return new File(filePath).exists();
    }

    @Override
    public ConnectorResult<List<Item>> importItems() {
        List<Item> items = new ArrayList<>();

        File file = new File(filePath);
        if (!file.exists()) {
            return ConnectorResult.fail("Excel import failed", "File not found: " + filePath, getName());
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            boolean firstRow = true;

            for (Row row : sheet) {
                if (firstRow) {
                    firstRow = false;
                    continue;
                }

                Item item = mapToItem(row);
                if (item != null) {
                    items.add(item);
                }
            }

            return ConnectorResult.ok(items, "Excel import successful", getName());

        } catch (Exception e) {
            return ConnectorResult.fail("Excel import failed", e, getName());
        }
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Inventory");

            // Header
            Row header = sheet.createRow(0);
            String[] headers = {"Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes"};

            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            int rowNum = 1;

            // Data rows
            for (Item item : items) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(item.getName());
                row.createCell(1).setCellValue(item.getCategory());
                row.createCell(2).setCellValue(item.getPrice());
                row.createCell(3).setCellValue(item.getUnit());
                row.createCell(4).setCellValue(item.getCost());
                row.createCell(5).setCellValue(item.getQuantity());
                row.createCell(6).setCellValue(item.getNotes());
            }

            // Auto-size columns AFTER writing data
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write file
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }

            return ConnectorResult.ok(items.size(),
                    "Excel export successful: " + filePath,
                    getName());

        } catch (Exception e) {
            return ConnectorResult.fail("Excel export failed", e, getName());
        }
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> exportResult = exportItems(localItems);

        if (!exportResult.isSuccess()) {
            return ConnectorResult.fail("Excel sync failed", exportResult.getErrorDetail(), getName());
        }

        SyncSummary summary = new SyncSummary(
                exportResult.getPayload() == null ? 0 : exportResult.getPayload(),
                0,
                0,
                0,
                0
        );

        return ConnectorResult.ok(summary, "Excel sync complete", getName());
    }

    @Override
    public Item mapToItem(Row row) {
        try {
            return new Item(
                    getString(row, 0),
                    getString(row, 1),
                    getDouble(row, 2),
                    getString(row, 3),
                    getDouble(row, 4),
                    getInt(row, 5),
                    getString(row, 6)
            );
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Row mapFromItem(Item item) {
        Workbook workbook = new XSSFWorkbook();
        Row row = workbook.createSheet("MappedItem").createRow(0);

        row.createCell(0).setCellValue(item.getName());
        row.createCell(1).setCellValue(item.getCategory());
        row.createCell(2).setCellValue(item.getPrice());
        row.createCell(3).setCellValue(item.getUnit());
        row.createCell(4).setCellValue(item.getCost());
        row.createCell(5).setCellValue(item.getQuantity());
        row.createCell(6).setCellValue(item.getNotes());

        return row;
    }

    // ---------- SAFE CELL READERS ----------

    private String getString(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield (v == Math.floor(v)) ? String.valueOf((int) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private double getDouble(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 0.0;

        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> cell.getNumericCellValue();
                case STRING -> Double.parseDouble(cell.getStringCellValue().trim());
                default -> 0.0;
            };
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int getInt(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 0;

        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (int) cell.getNumericCellValue();
                case STRING -> Integer.parseInt(cell.getStringCellValue().trim());
                default -> 0;
            };
        } catch (Exception e) {
            return 0;
        }
    }
}
