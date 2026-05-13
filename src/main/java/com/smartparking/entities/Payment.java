package com.smartparking.entities;


import com.smartparking.entities.nums.PaymentStatus;
import com.smartparking.entities.nums.ServiceType;
import com.smartparking.entities.users.Customer;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    /**
     * The Cashfree order ID we generate. Format: CF-<bookingCode>-<timestamp>
     * Stored so we can look up the order on Cashfree if needed.
     */
    @Column(unique = true, nullable =false)
    private String cashfreeOrderId;

    /**
     * The session ID returned by Cashfree after order creation.
     * Frontend uses this with Cashfree Drop JS / SDK to render the payment UI.
     */

    @Column(nullable = false, length = 10000)
    private String paymentSessionId;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceType serviceType;

    @Column(nullable = false)
    private Long referenceId;  // ID of Booking / CarRentalBooking / ValetRequest

    @Column
    private String referenceCode; // bookingCode for display purposes



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id",nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private Double amount;


    @Column(nullable = false,length = 3)
    private String currency ="INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private PaymentStatus status = PaymentStatus.INITIATED;

    /** Cashfree's own transaction/payment reference (populated from webhook). */
    private String cashfreePaymentId;

    /** Cashfree payment method (UPI, CARD, NETBANKING, etc.) from webhook. */

    private String paymentMethod;

    /** Raw failure reason if status = FAILED, for support/debugging. */

    private String failureReason;

    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCashfreeOrderId() {
        return cashfreeOrderId;
    }

    public void setCashfreeOrderId(String cashfreeOrderId) {
        this.cashfreeOrderId = cashfreeOrderId;
    }

    public String getPaymentSessionId() {
        return paymentSessionId;
    }

    public void setPaymentSessionId(String paymentSessionId) {
        this.paymentSessionId = paymentSessionId;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public void setReferenceCode(String referenceCode) {
        this.referenceCode = referenceCode;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getCashfreePaymentId() {
        return cashfreePaymentId;
    }

    public void setCashfreePaymentId(String cashfreePaymentId) {
        this.cashfreePaymentId = cashfreePaymentId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDateTime getInitiatedAt() {
        return initiatedAt;
    }

    public void setInitiatedAt(LocalDateTime initiatedAt) {
        this.initiatedAt = initiatedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}

