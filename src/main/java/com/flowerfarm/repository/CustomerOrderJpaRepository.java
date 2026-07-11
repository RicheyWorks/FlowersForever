package com.flowerfarm.repository;

import com.flowerfarm.model.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CustomerOrderJpaRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findAllByOrderByOrderDateDescIdDesc();

    List<CustomerOrder> findByOrderDateBetweenOrderByOrderDateDescIdDesc(LocalDate from, LocalDate to);

    List<CustomerOrder> findByCustomerIdOrderByOrderDateDescIdDesc(Long customerId);
}
