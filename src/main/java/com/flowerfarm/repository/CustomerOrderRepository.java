package com.flowerfarm.repository;

import com.flowerfarm.model.CustomerOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence boundary required by order application services. */
public interface CustomerOrderRepository {

    List<CustomerOrder> findAllNewestFirst();

    List<CustomerOrder> findBetween(LocalDate from, LocalDate to);

    List<CustomerOrder> findByCustomer(Long customerId);

    Optional<CustomerOrder> findById(Long id);

    Optional<CustomerOrder> findByIdForUpdate(Long id);

    CustomerOrder save(CustomerOrder order);

    void deleteById(Long id);
}
