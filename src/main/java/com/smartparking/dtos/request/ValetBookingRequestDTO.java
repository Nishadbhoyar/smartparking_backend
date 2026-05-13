package com.smartparking.dtos.request;

import com.smartparking.entities.nums.VehicleType;
import lombok.Data;

@Data
public class ValetBookingRequestDTO {
    private Long customerId;
    private String mobileNo;
    private String carPlateNo;
    private double pickupLatitude;
    private double pickupLongitude;

    // NEW: defaults to CAR if not sent by older frontend versions
    private VehicleType vehicleType = VehicleType.CAR;

    // NEW: EV Bike only — initial battery % the customer reports (0-100).
    // Null for CAR requests.
    private Integer batteryLevel;
}