package com.smartparking.dtos.request;

import lombok.Data;
import java.time.OffsetDateTime;


@Data
public class BookingRequestDTO {
    private Long customerId;
    private Long parkingLotId;
    private Long slotId;
    private String slotType;
    private OffsetDateTime entryTime;
    private OffsetDateTime exitTime;
    private boolean isValetBooking;
    private String promoCode;
}