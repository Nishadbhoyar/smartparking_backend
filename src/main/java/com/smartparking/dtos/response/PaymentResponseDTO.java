package com.smartparking.dtos.response;

import com.smartparking.entities.nums.PaymentStatus;
import com.smartparking.entities.nums.ServiceType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentResponseDTO {

    private Long paymentId;

    /** Cashfree order ID — frontend may need this for status polling. */
    private String cashfreeOrderId;

    /**
     * Pass this to the Cashfree Drop JS SDK to render the payment UI:
     *   cashfree.checkout({ paymentSessionId: response.paymentSessionId })
     */
    private String paymentSessionId;

    /** Which service this payment is for. */
    private ServiceType serviceType;

    /**
     * ID of the source record:
     *   PARKING_BOOKING → Booking.id
     *   CAR_RENTAL      → CarRentalBooking.id
     *   VALET           → ValetRequest.id
     */
    private Long referenceId;

    /** Human-readable reference code for display in the UI. */
    private String referenceCode;

    private Double amount;
    private String currency;
    private PaymentStatus status;
    private String paymentMethod;
    private String failureReason;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;

    /**
     * Cashfree hosted checkout URL.
     * Use this for a redirect-based flow instead of the Drop JS SDK.
     */
    private String cashfreeCheckoutUrl;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public String getCashfreeOrderId() { return cashfreeOrderId; }
    public void setCashfreeOrderId(String cashfreeOrderId) { this.cashfreeOrderId = cashfreeOrderId; }

    public String getPaymentSessionId() { return paymentSessionId; }
    public void setPaymentSessionId(String paymentSessionId) { this.paymentSessionId = paymentSessionId; }

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }

    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public LocalDateTime getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(LocalDateTime initiatedAt) { this.initiatedAt = initiatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getCashfreeCheckoutUrl() { return cashfreeCheckoutUrl; }
    public void setCashfreeCheckoutUrl(String cashfreeCheckoutUrl) { this.cashfreeCheckoutUrl = cashfreeCheckoutUrl; }
}