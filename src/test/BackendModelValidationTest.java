package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Backend entity boundary validation")
class BackendModelValidationTest {

    @Test
    @DisplayName("rejects non-finite monetary and quantity values")
    void rejectsNonFiniteNumbers() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Item("Rose", "Flowers", Double.NaN, "stem", 1, 1, ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Item("Rose", "Flowers", 2, "stem", Double.POSITIVE_INFINITY, 1, ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Item("Rose", "Flowers", 1_000_000_001.0, "stem", 1, 1, ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new HarvestEntry(LocalDate.now(), "Rose", Double.NaN, "stem", "A", ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new HarvestEntry(LocalDate.now(), "Rose", 0, "stem", "A", ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new OrderLine("Rose", Double.POSITIVE_INFINITY, "stem", 2));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new OrderLine("Rose", 0, "stem", 2));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new OrderLine("Rose", 1, "stem", Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new HarvestEntry(LocalDate.now(), "Rose", (double) Integer.MAX_VALUE + 1,
                        "stem", "A", ""));
    }

    @Test
    @DisplayName("rejects values longer than their persistence columns")
    void rejectsOversizedText() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Item("x".repeat(256), "Flowers", 2, "stem", 1, 1, ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new HarvestEntry(LocalDate.now(), "Rose", 1, "x".repeat(65), "A", ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Customer("Farm", "", "", "x".repeat(65), "OTHER", ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new OrderLine("x".repeat(256), 1, "stem", 2));
    }

    @Test
    @DisplayName("normalizes supported order handoff types and preserves legacy orders")
    void validatesOrderHandoffType() {
        Customer customer = new Customer("Farm", "", "", "", "OTHER", "");

        assertThat(new CustomerOrder(customer, LocalDate.now(), "DRAFT", "")
                .getHandoffType()).isEqualTo("UNSPECIFIED");
        assertThat(new CustomerOrder(customer, LocalDate.now(), "DRAFT", "", "delivery")
                .getHandoffType()).isEqualTo("DELIVERY");
        assertThat(CustomerOrder.normalizeHandoffType(" market ")).isEqualTo("MARKET");
        assertThat(CustomerOrder.normalizeHandoffType(null)).isEqualTo("UNSPECIFIED");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CustomerOrder.normalizeHandoffType("courier"))
                .withMessageContainingAll("handoff", "PICKUP", "DELIVERY", "MARKET");
    }

    @Test
    @DisplayName("bounds structured production task actuals")
    void validatesProductionTaskActuals() {
        ProductionTask task = new ProductionTask(1L, 1L, LocalDate.now(),
                ProductionTaskType.SOW, ProductionTaskStatus.TODO,
                "Rose", "Bed A", 1, true, "");

        assertThatIllegalArgumentException().isThrownBy(() ->
                task.recordActuals(-1, 0, "", Instant.now()))
                .withMessageContaining("labor minutes");
        assertThatIllegalArgumentException().isThrownBy(() ->
                task.recordActuals(10, Double.NaN, "", Instant.now()))
                .withMessageContaining("Input cost");
        assertThatIllegalArgumentException().isThrownBy(() ->
                task.recordActuals(10, 5, "x".repeat(1001), Instant.now()))
                .withMessageContaining("Field observation");
    }
}
