package com.flowerfarm.repository;

import com.flowerfarm.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerJpaRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    List<Customer> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
