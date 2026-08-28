package com.flowerfarm.repository;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.model.SyncHistoryEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("JPA persistence adapters")
class RepositoryAdapterTest {

    @Test
    @DisplayName("customer adapter translates the application port")
    void customerAdapter() {
        CustomerJpaRepository jpa = mock(CustomerJpaRepository.class);
        Customer customer = new Customer("Farm", "", "", "", "OTHER", "");
        when(jpa.findAllByOrderByNameAsc()).thenReturn(List.of(customer));

        CustomerRepository repository = new JpaCustomerRepository(jpa);

        assertThat(repository.findAllByName()).containsExactly(customer);
        repository.findByIdForUpdate(7L);
        verify(jpa).findByIdForUpdate(7L);
    }

    @Test
    @DisplayName("harvest adapter translates date-range queries")
    void harvestAdapter() {
        HarvestJpaRepository jpa = mock(HarvestJpaRepository.class);
        LocalDate day = LocalDate.of(2026, 8, 23);
        HarvestEntry entry = new HarvestEntry(day, "Rose", 2, "stems", "A", "");
        when(jpa.findByHarvestDateBetweenOrderByHarvestDateDescIdDesc(day, day))
                .thenReturn(List.of(entry));

        HarvestRepository repository = new JpaHarvestRepository(jpa);

        assertThat(repository.findBetween(day, day)).containsExactly(entry);
    }

    @Test
    @DisplayName("order adapter translates customer queries")
    void orderAdapter() {
        CustomerOrderJpaRepository jpa = mock(CustomerOrderJpaRepository.class);
        CustomerOrder order = new CustomerOrder(
                new Customer("Farm", "", "", "", "OTHER", ""),
                LocalDate.now(), "DRAFT", "");
        when(jpa.findByCustomerIdOrderByOrderDateDescIdDesc(4L)).thenReturn(List.of(order));

        CustomerOrderRepository repository = new JpaCustomerOrderRepository(jpa);

        assertThat(repository.findByCustomer(4L)).containsExactly(order);
    }

    @Test
    @DisplayName("sync-history adapter owns Spring Data pagination")
    void syncHistoryAdapter() {
        SyncHistoryJpaRepository jpa = mock(SyncHistoryJpaRepository.class);
        SyncHistoryEntry entry = new SyncHistoryEntry("csv", "IMPORT", true, "ok", "", 1);
        when(jpa.findAllByOrderByOccurredAtDesc(any(Pageable.class))).thenReturn(List.of(entry));

        SyncHistoryRepository repository = new JpaSyncHistoryRepository(jpa);

        assertThat(repository.findRecent(25)).containsExactly(entry);
        verify(jpa).findAllByOrderByOccurredAtDesc(
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == 25));
    }

    @Test
    @DisplayName("knowledge adapter owns newest-first and locking queries")
    void knowledgeAdapter() {
        KnowledgeJpaRepository jpa = mock(KnowledgeJpaRepository.class);
        KnowledgeEntry entry = new KnowledgeEntry(
                LocalDate.of(2026, 8, 20), "Mildew lesson", KnowledgeKind.PROBLEM,
                "Rose", "", "Bed A", "Vegetative", "Mildew appeared", "",
                "", "", null, KnowledgeConfidence.NEEDS_REVIEW, List.of("mildew"));
        when(jpa.findAllByOrderByObservedOnDescIdDesc()).thenReturn(List.of(entry));

        KnowledgeRepository repository = new JpaKnowledgeRepository(jpa);

        assertThat(repository.findAllNewestFirst()).containsExactly(entry);
        repository.findByIdForUpdate(9L);
        verify(jpa).findByIdForUpdate(9L);
    }
}
