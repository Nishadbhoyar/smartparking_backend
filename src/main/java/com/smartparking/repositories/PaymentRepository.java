package com.smartparking.repositories;

import com.smartparking.entities.Payment;
import com.smartparking.entities.nums.PaymentStatus;
import com.smartparking.entities.nums.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Used by the webhook handler to resolve a payment from Cashfree's order ID. */
    Optional<Payment> findByCashfreeOrderId(String cashfreeOrderId);

    /**
     * All payments for a service record, newest first.
     * Used by getPaymentsByReference() to return full payment history.
     */
    List<Payment> findByServiceTypeAndReferenceIdOrderByInitiatedAtDesc(
            ServiceType serviceType, Long referenceId);

    /**
     * Most recent payment for a service record (any status).
     * Used by getPaymentStatus() for frontend polling.
     */
    Optional<Payment> findTopByServiceTypeAndReferenceIdOrderByInitiatedAtDesc(
            ServiceType serviceType, Long referenceId);

    /**
     * Most recent successful payment for a service record.
     * Used by initiateRefund() to find the payment to refund.
     */
    Optional<Payment> findTopByServiceTypeAndReferenceIdAndStatusOrderByInitiatedAtDesc(
            ServiceType serviceType, Long referenceId, PaymentStatus status);

    /** All payments made by a customer — used on the customer dashboard. */
    List<Payment> findByCustomerId(Long customerId);
}