package com.flowerfarm.reference;

import com.flowerfarm.model.EventServiceProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Wedding and memorial event service catalog")
class EventServiceCatalogTest {

    @Test
    @DisplayName("covers the full event scope with complete operational prompts")
    void coversCompleteEventScope() {
        assertThat(EventServiceCatalog.all()).hasSizeGreaterThanOrEqualTo(24);
        assertThat(EventServiceCatalog.occasions()).contains(
                "Wedding", "Memorial / funeral", "Any event");
        assertThat(EventServiceCatalog.categories()).contains(
                "Floral design", "Catering", "Décor & rentals",
                "Photography & media", "Logistics", "Planning & coordination");
        assertThat(EventServiceCatalog.all()).allSatisfy(service -> {
            assertThat(service.serviceName()).isNotBlank();
            assertThat(service.deliverables()).isNotBlank();
            assertThat(service.intakeQuestions()).isNotBlank();
            assertThat(service.operationsChecklist()).isNotBlank();
            assertThat(service.privacyAndSafety()).isNotBlank();
            assertThat(service.source().url()).startsWith("https://");
        });
    }

    @Test
    @DisplayName("keeps official event and memorial workflow provenance")
    void keepsWorkflowProvenance() {
        assertThat(EventServiceCatalog.sources()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(EventServiceCatalog.sources())
                .extracting(EventServiceProfile.Source::organization)
                .contains("Curate", "Floranext", "Tripleseat", "HoneyBook",
                        "Tribute Technology", "National Funeral Directors Association");
        assertThat(new HashSet<>(EventServiceCatalog.sources()))
                .hasSameSizeAs(EventServiceCatalog.sources());
    }

    @Test
    @DisplayName("memorial media search retains permission and approval safeguards")
    void memorialMediaSearchIncludesSafeguards() {
        EventServiceProfile photography = EventServiceCatalog.all().stream()
                .filter(service -> "Memorial photography".equals(service.serviceName()))
                .findFirst().orElseThrow();
        EventServiceProfile tribute = EventServiceCatalog.all().stream()
                .filter(service -> "Tribute photo and video coordination"
                        .equals(service.serviceName()))
                .findFirst().orElseThrow();

        assertThat(photography.searchableText())
                .contains("permission", "no portfolio use", "funeral");
        assertThat(tribute.searchableText())
                .contains("family approver", "preview", "licensed media");
    }
}
