package com.flowerfarm.repository;

import com.flowerfarm.model.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerJpaRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where lower(c.name) = lower(:name)")
    Optional<Customer> findByNameIgnoreCaseForUpdate(@Param("name") String name);
}
