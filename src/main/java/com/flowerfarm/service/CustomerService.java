package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.repository.CustomerJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerJpaRepository repository;

    public CustomerService(CustomerJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Customer> getAll() {
        return new ArrayList<>(repository.findAllByOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Customer> search(String query) {
        if (query == null || query.isBlank()) {
            return getAll();
        }
        String q = query.trim().toLowerCase();
        // Broader UI search: name, contact, email, phone, type, notes
        return getAll().stream()
                .filter(c -> contains(c.getName(), q)
                        || contains(c.getContactName(), q)
                        || contains(c.getEmail(), q)
                        || contains(c.getPhone(), q)
                        || contains(c.getCustomerType(), q)
                        || contains(c.getNotes(), q))
                .toList();
    }

    private static boolean contains(String field, String q) {
        return field != null && field.toLowerCase().contains(q);
    }

    @Transactional
    public Customer add(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer must not be null.");
        }
        customer.setId(null);
        return repository.save(customer);
    }

    @Transactional
    public Customer update(Long id, Customer incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("Customer must not be null.");
        }
        Customer existing = repository.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No customer with id=" + id));
        existing.setName(incoming.getName());
        existing.setContactName(incoming.getContactName());
        existing.setEmail(incoming.getEmail());
        existing.setPhone(incoming.getPhone());
        existing.setCustomerType(incoming.getCustomerType());
        existing.setNotes(incoming.getNotes());
        return repository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new IndexOutOfBoundsException("No customer with id=" + id);
        }
        repository.deleteById(id);
    }
}
