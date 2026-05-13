package com.smartparking.dtos.request;

import com.smartparking.entities.nums.ServiceType;
import lombok.Data;

@Data
public class RefundRequestDTO {

    /**
     * Which service the refund is for.
     * Must be one of: PARKING_BOOKING, CAR_RENTAL, VALET
     */
    private ServiceType serviceType;

    /**
     * ID of the service record being refunded:
     *   PARKING_BOOKING → Booking.id
     *   CAR_RENTAL      → CarRentalBooking.id
     *   VALET           → ValetRequest.id
     */
    private Long referenceId;

    /**
     * Amount to refund in INR.
     * Must be <= original payment amount.
     * If null, the full original amount is refunded.
     */
    private Double refundAmount;

    /** Reason stored in Cashfree and your DB for audit trail. */
    private String refundReason;

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }

    public Double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Double refundAmount) { this.refundAmount = refundAmount; }

    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
}