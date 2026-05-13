package com.smartparking.dtos.request;

import com.smartparking.entities.nums.ServiceType;
import lombok.Data;

@Data
public class PaymentInitiateRequestDTO {

    /**
     * Which service the customer is paying for.
     * Must be one of: PARKING_BOOKING, CAR_RENTAL, VALET
     */
    private ServiceType serviceType;

    /**
     * ID of the service record being paid for:
     *   PARKING_BOOKING → Booking.id
     *   CAR_RENTAL      → CarRentalBooking.id
     *   VALET           → ValetRequest.id
     */
    private Long referenceId;

    /** Customer's 10-digit mobile number. Required by Cashfree. */
    private String customerPhone;

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
}