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

}