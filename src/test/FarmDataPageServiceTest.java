package com.flowerfarm.automation.farm;

import com.flowerfarm.model.Item;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.repository.CustomerJpaRepository;
import com.flowerfarm.repository.CustomerOrderJpaRepository;
import com.flowerfarm.repository.FloralRecipeJpaRepository;
import com.flowerfarm.repository.FieldAttachmentJpaRepository;
import com.flowerfarm.repository.GrowingTemplateJpaRepository;
import com.flowerfarm.repository.HarvestJpaRepository;
import com.flowerfarm.repository.ItemJpaRepository;
import com.flowerfarm.repository.KnowledgeJpaRepository;
import com.flowerfarm.repository.ProductionTaskJpaRepository;
import com.flowerfarm.repository.SeasonPlanJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Database-backed AI record paging")
class FarmDataPageServiceTest {

    @Mock ItemJpaRepository items;
    @Mock CustomerJpaRepository customers;
    @Mock HarvestJpaRepository harvests;
    @Mock CustomerOrderJpaRepository orders;
    @Mock KnowledgeJpaRepository knowledge;
    @Mock ProductionTaskJpaRepository tasks;
    @Mock GrowingTemplateJpaRepository templates;
    @Mock SeasonPlanJpaRepository plantings;
    @Mock FloralRecipeJpaRepository recipes;
    @Mock FieldAttachmentJpaRepository attachments;

    FarmDataPageService service;

    @BeforeEach
    void setUp() {
        service = new FarmDataPageService(
                items, customers, harvests, orders, knowledge,
                tasks, templates, plantings, recipes, attachments);
    }

    @Test
    @DisplayName("passes arbitrary offsets to the database instead of loading the table")
    void pagesAtDatabaseWithArbitraryOffset() {
        Item item = new Item("Page 138", "Flower", 2, "stem", 1, 5, "");
        when(items.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(items.count()).thenReturn(10_000L);

        FarmDataPageService.RecordPage<?> result = service.list(
                "inventory", 137, 25,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(items).findAll(pageable.capture());
        verify(items, never()).findAll();
        assertThat(pageable.getValue().getOffset()).isEqualTo(137);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageable.getValue().getSort().getOrderFor("id"))
                .isNotNull()
                .satisfies(order -> assertThat(order.isAscending()).isTrue());
        assertThat(result.records()).singleElement().isSameAs(item);
        assertThat(result.total()).isEqualTo(10_000);
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    @DisplayName("routes every unfiltered canonical type to a bounded repository query")
    void routesCanonicalTypes() {
        when(customers.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(harvests.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(orders.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(knowledge.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(templates.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(plantings.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(recipes.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(attachments.findAll(any(Pageable.class))).thenReturn(Page.empty());

        List.of(
                "customers", "harvests", "orders", "knowledge",
                "growing-templates", "planting-plans", "floral-recipes", "attachments")
                .forEach(type -> service.list(
                        type, 0, 50,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        verify(customers).findAll(any(Pageable.class));
        verify(harvests).findAll(any(Pageable.class));
        verify(orders).findAll(any(Pageable.class));
        verify(knowledge).findAll(any(Pageable.class));
        verify(templates).findAll(any(Pageable.class));
        verify(plantings).findAll(any(Pageable.class));
        verify(recipes).findAll(any(Pageable.class));
        verify(attachments).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("keeps production-task date filters inside the paged database query")
    void pagesProductionTasksWithinDateRange() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        ProductionTask task = org.mockito.Mockito.mock(ProductionTask.class);
        when(tasks.findByDueDateBetween(eq(from), eq(to), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(tasks.countByDueDateBetween(from, to)).thenReturn(81L);

        FarmDataPageService.RecordPage<?> result = service.list(
                "production-tasks", 80, 20, from, to);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tasks).findByDueDateBetween(eq(from), eq(to), pageable.capture());
        assertThat(pageable.getValue().getOffset()).isEqualTo(80);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(result.records()).singleElement().isSameAs(task);
        assertThat(result.total()).isEqualTo(81);
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    @DisplayName("rejects record types outside the published catalog")
    void rejectsUnknownType() {
        assertThatThrownBy(() -> service.list(
                "mystery", 0, 20,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported record type");
    }
}
