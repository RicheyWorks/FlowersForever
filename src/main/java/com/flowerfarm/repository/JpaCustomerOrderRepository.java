package com.flowerfarm.repository;

import com.flowerfarm.model.CustomerOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Spring Data adapter for the order persistence port. */
@Repository
public class JpaCustomerOrderRepository implements CustomerOrderRepository {

    private final CustomerOrderJpaRepository jpa;

    public JpaCustomerOrderRepository(CustomerOrderJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<CustomerOrder> findAllNewestFirst() {
        return jpa.findAllByOrderByOrderDateDescIdDesc();
    }

    @Override
    public List<CustomerOrder> findBetween(LocalDate from, LocalDate to) {
        return jpa.findByOrderDateBetweenOrderByOrderDateDescIdDesc(from, to);
    }

    @Override
    public List<CustomerOrder> findByCustomer(Long customerId) {
        return jpa.findByCustomerIdOrderByOrderDateDescIdDesc(customerId);
    }

    @Override
    public List<CustomerOrder> findConfirmedDueThrough(LocalDate throughDate, int limit) {
        return jpa.findByStatusIgnoreCaseAndOrderDateLessThanEqualOrderByOrderDateAscIdAsc(
                "CONFIRMED", throughDate, PageRequest.of(0, limit));
    }

    @Override
    public long countConfirmedDueThrough(LocalDate throughDate) {
        return jpa.countByStatusIgnoreCaseAndOrderDateLessThanEqual("CONFIRMED", throughDate);
    }

    @Override
    public Optional<CustomerOrder> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<CustomerOrder> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public CustomerOrder save(CustomerOrder order) {
        return jpa.save(order);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
