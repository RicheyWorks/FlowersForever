package com.flowerfarm.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/** Knowledge Log contracts separate editable business fields from managed identity and timestamps. */
public final class KnowledgeApiDtos {
    private KnowledgeApiDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KnowledgeRequest(
            LocalDate observedOn, String summary, KnowledgeKind kind,
            String cropName, String variety, String bedOrField, String productionStage,
            String observation, String actionTaken, String performedBy, String result,
            LocalDate followUpDate, KnowledgeConfidence confidence, Collection<String> tags,
            Long harvestId, Long inventoryItemId, Long orderId, Long customerId,
            Long fieldScienceExportId) {
        public KnowledgeEntry toEntry() {
            KnowledgeEntry entry = new KnowledgeEntry(observedOn, summary, kind, cropName,
                    variety, bedOrField, productionStage, observation, actionTaken,
                    performedBy, result, followUpDate, confidence, tags);
            entry.setHarvestId(harvestId);
            entry.setInventoryItemId(inventoryItemId);
            entry.setOrderId(orderId);
            entry.setCustomerId(customerId);
            entry.setFieldScienceExportId(fieldScienceExportId);
            return entry;
        }
    }

    public record KnowledgeResponse(
            Long id, LocalDate observedOn, String summary, KnowledgeKind kind,
            String cropName, String variety, String bedOrField, String productionStage,
            String observation, String actionTaken, String performedBy, String result,
            LocalDate followUpDate, KnowledgeConfidence confidence, Set<String> tags,
            Long harvestId, Long inventoryItemId, Long orderId, Long customerId,
            Long fieldScienceExportId, Instant createdAt, Instant updatedAt) {
        public static KnowledgeResponse from(KnowledgeEntry entry) {
            return new KnowledgeResponse(entry.getId(), entry.getObservedOn(), entry.getSummary(),
                    entry.getKind(), entry.getCropName(), entry.getVariety(), entry.getBedOrField(),
                    entry.getProductionStage(), entry.getObservation(), entry.getActionTaken(),
                    entry.getPerformedBy(), entry.getResult(), entry.getFollowUpDate(),
                    entry.getConfidence(), entry.getTags(), entry.getHarvestId(),
                    entry.getInventoryItemId(), entry.getOrderId(), entry.getCustomerId(),
                    entry.getFieldScienceExportId(), entry.getCreatedAt(), entry.getUpdatedAt());
        }
    }
}
