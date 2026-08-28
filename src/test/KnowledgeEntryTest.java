package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Farm knowledge entry")
class KnowledgeEntryTest {

    @Test
    @DisplayName("normalizes reusable tags and preserves farm context")
    void normalizesTags() {
        KnowledgeEntry entry = entry(List.of(
                " Powdery Mildew ", "powdery   mildew", "Irrigation / Timing"));

        assertThat(entry.getTags())
                .containsExactlyInAnyOrder("powdery-mildew", "irrigation-timing");
        assertThat(entry.getCropName()).isEqualTo("Nootka Rose");
        assertThat(entry.getConfidence()).isEqualTo(KnowledgeConfidence.PROMISING);
    }

    @Test
    @DisplayName("requires a concise event and a factual observation")
    void requiresCoreFacts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KnowledgeEntry(
                LocalDate.of(2026, 8, 20), " ", KnowledgeKind.PROBLEM,
                "Rose", "", "A", "Vegetative", "Mildew appeared", "",
                "", "", null, KnowledgeConfidence.NEEDS_REVIEW, List.of()))
                .withMessageContaining("What happened");

        assertThatIllegalArgumentException().isThrownBy(() -> new KnowledgeEntry(
                LocalDate.of(2026, 8, 20), "Mildew appeared", KnowledgeKind.PROBLEM,
                "Rose", "", "A", "Vegetative", " ", "", "",
                "", null, KnowledgeConfidence.NEEDS_REVIEW, List.of()))
                .withMessageContaining("Observation");
    }

    @Test
    @DisplayName("rejects invalid operational record links")
    void rejectsInvalidLinks() {
        KnowledgeEntry entry = entry(List.of("mildew"));

        assertThatIllegalArgumentException().isThrownBy(() -> entry.setHarvestId(0L))
                .withMessageContaining("positive");
    }

    private static KnowledgeEntry entry(List<String> tags) {
        return new KnowledgeEntry(
                LocalDate.of(2026, 8, 20),
                "Mildew appeared after warm nights",
                KnowledgeKind.PROBLEM,
                "Nootka Rose",
                "",
                "Bed A",
                "Vegetative",
                "White patches appeared on lower leaves.",
                "Removed affected leaves and changed watering time.",
                "Richmond",
                "No new patches after seven days.",
                LocalDate.of(2026, 8, 28),
                KnowledgeConfidence.PROMISING,
                tags);
    }
}
