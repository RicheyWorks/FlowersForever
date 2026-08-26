package com.flowerfarm.repository;

import com.flowerfarm.model.CustomerOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CustomerOrderJpaRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findAllByOrderByOrderDateDescIdDesc();

    List<CustomerOrder> findByOrderDateBetweenOrderByOrderDateDescIdDesc(LocalDate from, LocalDate to);

    List<CustomerOrder> findByCustomerIdOrderByOrderDateDescIdDesc(Long customerId);

    /** Serializes state transitions such as fulfillment for a single order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    java.util.Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);
}
