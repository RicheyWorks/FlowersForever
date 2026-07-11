package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/**
 * Wholesale / market / florist customer for Kitsap farm sales.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String contactName;

    @Column(length = 255)
    private String email;

    @Column(length = 64)
    private String phone;

    /** WHOLESALE, MARKET, FLORIST, OTHER */
    @Column(nullable = false, length = 32)
    private String customerType;

    @Column(length = 2000)
    private String notes;

    protected Customer() {
    }

    public Customer(String name, String contactName, String email, String phone,
                    String customerType, String notes) {
        this(null, name, contactName, email, phone, customerType, notes);
    }

    @JsonCreator
    public Customer(
            @JsonProperty("id") Long id,
            @JsonProperty("name") String name,
            @JsonProperty("contactName") String contactName,
            @JsonProperty("email") String email,
            @JsonProperty("phone") String phone,
            @JsonProperty("customerType") String customerType,
            @JsonProperty("notes") String notes) {
        this.id = id;
        setName(name);
        setContactName(contactName);
        setEmail(email);
        setPhone(phone);
        setCustomerType(customerType);
        setNotes(notes);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getCustomerType() { return customerType; }
    public String getNotes() { return notes; }

    public void setId(Long id) { this.id = id; }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name is required.");
        }
        this.name = name.trim();
    }

    public void setContactName(String contactName) {
        this.contactName = contactName == null ? "" : contactName.trim();
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email.trim();
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? "" : phone.trim();
    }

    public void setCustomerType(String customerType) {
        this.customerType = (customerType == null || customerType.isBlank())
                ? "OTHER" : customerType.trim().toUpperCase();
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes.trim();
    }
}
