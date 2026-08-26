package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService")
class CustomerServiceTest {

    @Mock CustomerRepository repository;
    CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(repository);
    }

    @Test
    @DisplayName("add() clears id and saves")
    void add() {
        when(repository.save(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        Customer saved = service.add(new Customer("Kitsap Blooms", "Sam", "", "360-555-0100", "WHOLESALE", ""));
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("Kitsap Blooms");
    }

    @Test
    @DisplayName("add() rejects null")
    void addNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.add(null));
    }

    @Test
    @DisplayName("delete() throws when missing")
    void deleteMissing() {
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> service.delete(9L));
    }

    @Test
    @DisplayName("search() matches name/contact/email (blank → all)")
    void search() {
        when(repository.findAllByName()).thenReturn(java.util.List.of(
                new Customer("Kitsap Blooms", "Sam", "sam@x.com", "360", "WHOLESALE", "florist"),
                new Customer("Market Stall", "Lee", "", "", "MARKET", "")
        ));
        assertThat(service.search("kitsap")).hasSize(1);
        assertThat(service.search("sam@")).hasSize(1);
        assertThat(service.search("market")).hasSize(1);
        assertThat(service.search("")).hasSize(2);
        assertThat(service.search("zzz")).isEmpty();
    }

    @Test
    @DisplayName("update() applies fields")
    void update() {
        Customer existing = new Customer("Old", "", "", "", "OTHER", "");
        existing.setId(2L);
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Customer saved = service.update(2L, new Customer("New", "Pat", "p@x.com", "1", "FLORIST", "n"));
        assertThat(saved.getName()).isEqualTo("New");
        assertThat(saved.getCustomerType()).isEqualTo("FLORIST");
    }

    @Test
    @DisplayName("add and rename reject an existing customer name without saving")
    void duplicateCustomerNamesAreRejected() {
        Customer existing = new Customer(
                "Kitsap Blooms", "Sam", "", "", "WHOLESALE", "");
        existing.setId(8L);
        when(repository.findByNameIgnoreCaseForUpdate(any()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.add(new Customer(
                "kitsap blooms", "Pat", "", "", "WHOLESALE", "")))
                .isInstanceOfSatisfying(CustomerService.DuplicateCustomerNameException.class,
                        duplicate -> {
                            assertThat(duplicate.existingId()).isEqualTo(8L);
                            assertThat(duplicate.customerName()).isEqualTo("Kitsap Blooms");
                        });

        Customer renamed = new Customer("Other", "", "", "", "OTHER", "");
        renamed.setId(9L);
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(renamed));
        assertThatThrownBy(() -> service.update(9L, new Customer(
                "KITSAP BLOOMS", "", "", "", "OTHER", "")))
                .isInstanceOf(CustomerService.DuplicateCustomerNameException.class);

        verify(repository, never()).save(any());
    }
}
