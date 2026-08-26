package com.flowerfarm.repository;

import com.flowerfarm.model.Customer;

import java.util.List;
import java.util.Optional;

/** Persistence boundary required by customer application services. */
public interface CustomerRepository {

    List<Customer> findAllByName();

    Optional<Customer> findById(Long id);

    Optional<Customer> findByIdForUpdate(Long id);

    Optional<Customer> findByNameIgnoreCaseForUpdate(String name);

    Customer save(Customer customer);

    void deleteById(Long id);
}
