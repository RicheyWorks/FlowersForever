package com.flowerfarm.repository;

import com.flowerfarm.model.Customer;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for the customer persistence port. */
@Repository
public class JpaCustomerRepository implements CustomerRepository {

    private final CustomerJpaRepository jpa;

    public JpaCustomerRepository(CustomerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Customer> findAllByName() {
        return jpa.findAllByOrderByNameAsc();
    }

    @Override
    public Optional<Customer> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Customer> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public Optional<Customer> findByNameIgnoreCaseForUpdate(String name) {
        return jpa.findByNameIgnoreCaseForUpdate(name);
    }

    @Override
    public Customer save(Customer customer) {
        return jpa.save(customer);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
