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
        this.name = requireMaxLength(name.trim(), 255, "Customer name");
    }

    public void setContactName(String contactName) {
        this.contactName = requireMaxLength(
                contactName == null ? "" : contactName.trim(), 255, "Contact name");
    }

    public void setEmail(String email) {
        this.email = requireMaxLength(email == null ? "" : email.trim(), 255, "Email");
    }

    public void setPhone(String phone) {
        this.phone = requireMaxLength(phone == null ? "" : phone.trim(), 64, "Phone");
    }

    public void setCustomerType(String customerType) {
        String normalized = (customerType == null || customerType.isBlank())
                ? "OTHER" : customerType.trim().toUpperCase(java.util.Locale.ROOT);
        this.customerType = requireMaxLength(normalized, 32, "Customer type");
    }

    public void setNotes(String notes) {
        this.notes = requireMaxLength(notes == null ? "" : notes.trim(), 2000, "Notes");
    }

    private static String requireMaxLength(String value, int max, String field) {
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return value;
    }
}
