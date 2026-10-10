package com.flowerfarm.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.HarvestGrade;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.SyncHistoryEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Explicit REST contracts. Persistence identities and relationships are never
 * accepted from request bodies; the existing services still own all mutations.
 */
public final class FarmApiDtos {
    private FarmApiDtos() {}

    public record ItemRequest(String name, String category, double price, String unit,
                              double cost, int quantity, String notes) {
        public Item toItem() {
            return new Item(name, category, price, unit, cost, quantity, notes);
        }
    }

    public record ItemResponse(Long id, String name, String category, double price,
                               String unit, double cost, int quantity, String notes) {
        public static ItemResponse from(Item item) {
            return new ItemResponse(item.getId(), item.getName(), item.getCategory(),
                    item.getPrice(), item.getUnit(), item.getCost(), item.getQuantity(), item.getNotes());
        }
    }

    public record CustomerRequest(String name, String contactName, String email, String phone,
                                  String customerType, String notes) {
        public Customer toCustomer() {
            return new Customer(name, contactName, email, phone, customerType, notes);
        }
    }

    public record CustomerResponse(Long id, String name, String contactName, String email,
                                   String phone, String customerType, String notes) {
        public static CustomerResponse from(Customer customer) {
            return new CustomerResponse(customer.getId(), customer.getName(), customer.getContactName(),
                    customer.getEmail(), customer.getPhone(), customer.getCustomerType(), customer.getNotes());
        }
    }

    public record HarvestRequest(LocalDate harvestDate, String cropName, double quantity,
                                 String unit, String bedOrField, String notes, String lotCode, HarvestGrade grade,
                                 double lossQuantity, String storageLocation) {
        public HarvestEntry toHarvestEntry() {
            return new HarvestEntry(harvestDate, cropName, quantity, unit, bedOrField, notes,
                    lotCode, grade, lossQuantity, storageLocation);
        }
    }

    public record HarvestResponse(Long id, LocalDate harvestDate, String cropName, double quantity,
                                  String unit, String bedOrField, String notes, String lotCode, HarvestGrade grade,
                                  double lossQuantity, double grossQuantity, String storageLocation) {
        public static HarvestResponse from(HarvestEntry entry) {
            return new HarvestResponse(entry.getId(), entry.getHarvestDate(), entry.getCropName(),
                    entry.getQuantity(), entry.getUnit(), entry.getBedOrField(), entry.getNotes(),
                    entry.getLotCode(), entry.getGrade(), entry.getLossQuantity(), entry.getGrossQuantity(),
                    entry.getStorageLocation());
        }
    }

    public record OrderLineRequest(String productName, double quantity, String unit, double unitPrice) {
        public OrderLine toOrderLine() {
            return new OrderLine(productName, quantity, unit, unitPrice);
        }
    }

    public record OrderLineResponse(Long id, String productName, double quantity, String unit,
                                    double unitPrice, @JsonInclude(JsonInclude.Include.NON_NULL) Long recipeId) {
        public static OrderLineResponse from(OrderLine line) {
            return new OrderLineResponse(line.getId(), line.getProductName(), line.getQuantity(),
                    line.getUnit(), line.getUnitPrice(), line.getRecipeId());
        }
    }

    public record OrderResponse(Long id, CustomerResponse customer, LocalDate orderDate,
                                String status, String notes, String handoffType, List<OrderLineResponse> lines) {
        public static OrderResponse from(CustomerOrder order) {
            return new OrderResponse(order.getId(), CustomerResponse.from(order.getCustomer()),
                    order.getOrderDate(), order.getStatus(), order.getNotes(), order.getHandoffType(),
                    order.getLines().stream().map(OrderLineResponse::from).toList());
        }
    }

    public record HistoryResponse(Long id, String connectorName, String operation, boolean success,
                                  String message, String detail, Integer recordCount, Instant occurredAt) {
        public static HistoryResponse from(SyncHistoryEntry entry) {
            return new HistoryResponse(entry.getId(), entry.getConnectorName(), entry.getOperation(),
                    entry.isSuccess(), entry.getMessage(), entry.getDetail(), entry.getRecordCount(),
                    entry.getOccurredAt());
        }
    }
}
