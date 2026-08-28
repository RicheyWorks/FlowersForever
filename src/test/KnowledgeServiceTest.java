package com.flowerfarm.service;

import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.repository.CustomerOrderRepository;
import com.flowerfarm.repository.CustomerRepository;
import com.flowerfarm.repository.HarvestRepository;
import com.flowerfarm.repository.InventoryRepository;
import com.flowerfarm.repository.KnowledgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Farm Knowledge Log service")
class KnowledgeServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-27T17:00:00Z"), ZoneOffset.UTC);

    @Mock KnowledgeRepository repository;
    @Mock HarvestRepository harvestRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock CustomerOrderRepository orderRepository;
    @Mock CustomerRepository customerRepository;
    @Mock SyncHistoryService syncHistoryService;

    KnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeService(repository, harvestRepository, inventoryRepository,
                orderRepository, customerRepository, syncHistoryService, CLOCK);
    }

    @Test
    @DisplayName("plain farm question finds the matching lesson from last August")
    void understandsPlainFarmQuestion() {
        KnowledgeEntry matching = mildewEntry(LocalDate.of(2025, 8, 18));
        KnowledgeEntry wrongMonth = mildewEntry(LocalDate.of(2026, 8, 18));
        KnowledgeEntry wrongCrop = entry(LocalDate.of(2025, 8, 20), "Dahlia", "Aphids",
                List.of("pests"));
        when(repository.findAllNewestFirst())
                .thenReturn(List.of(wrongMonth, wrongCrop, matching));

        List<KnowledgeEntry> result = service.search(new KnowledgeService.SearchRequest(
                "What worked for mildew on this rose last August?",
                "", "", "", null, null, false, 50));

        assertThat(result).containsExactly(matching);
    }

    @Test
    @DisplayName("filters due follow-ups and reusable tags")
    void filtersDueFollowUps() {
        KnowledgeEntry due = mildewEntry(LocalDate.of(2026, 8, 18));
        due.setFollowUpDate(LocalDate.of(2026, 8, 27));
        KnowledgeEntry later = mildewEntry(LocalDate.of(2026, 8, 19));
        later.setFollowUpDate(LocalDate.of(2026, 9, 2));
        when(repository.findAllNewestFirst()).thenReturn(List.of(later, due));

        List<KnowledgeEntry> result = service.search(new KnowledgeService.SearchRequest(
                "", "rose", "", "powdery mildew", null, null, true, 50));

        assertThat(result).containsExactly(due);
    }

    @Test
    @DisplayName("create validates linked records and writes an activity entry")
    void createsLinkedKnowledge() {
        KnowledgeEntry entry = mildewEntry(LocalDate.of(2026, 8, 18));
        entry.setHarvestId(44L);
        when(harvestRepository.findById(44L)).thenReturn(Optional.of(
                new com.flowerfarm.model.HarvestEntry(
                        LocalDate.of(2026, 8, 18), "Nootka Rose", 20,
                        "stems", "Bed A", "")));
        when(repository.save(any())).thenAnswer(invocation -> {
            KnowledgeEntry saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        KnowledgeEntry saved = service.create(entry);

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.now(CLOCK));
        verify(syncHistoryService).record(
                org.mockito.ArgumentMatchers.eq("knowledge"),
                org.mockito.ArgumentMatchers.eq("KNOWLEDGE_LOG"),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.contains("mildew"),
                org.mockito.ArgumentMatchers.contains("knowledgeId=7"),
                org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    @DisplayName("create refuses an operational link that does not exist")
    void refusesMissingLink() {
        KnowledgeEntry entry = mildewEntry(LocalDate.of(2026, 8, 18));
        entry.setOrderId(99L);
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> service.create(entry))
                .withMessageContaining("order #99");
    }

    @Test
    @DisplayName("future observations and unbounded result requests are rejected")
    void rejectsUnsafeInputs() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                        service.create(mildewEntry(LocalDate.of(2026, 8, 28))))
                .withMessageContaining("future");

        assertThatIllegalArgumentException().isThrownBy(() -> service.search(
                        new KnowledgeService.SearchRequest(
                                "", "", "", "", null, null, false, 501)))
                .withMessageContaining("500");
    }

    private static KnowledgeEntry mildewEntry(LocalDate date) {
        return entry(date, "Nootka Rose", "Powdery mildew spread after warm nights",
                List.of("powdery-mildew", "irrigation"));
    }

    private static KnowledgeEntry entry(LocalDate date, String crop, String observation,
                                        List<String> tags) {
        return new KnowledgeEntry(
                date,
                observation,
                KnowledgeKind.PROBLEM,
                crop,
                "",
                "Bed A",
                "Vegetative",
                observation,
                "Removed affected leaves and watered earlier.",
                "Richmond",
                "No new patches after seven days.",
                date.plusDays(7),
                KnowledgeConfidence.PROMISING,
                tags);
    }
}
