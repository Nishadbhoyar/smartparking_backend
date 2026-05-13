package com.smartparking.dtos.request;

import com.smartparking.entities.nums.SlotType;
import lombok.Data;

@Data
public class BulkSlotRequestDTO {

    // ── New frontend format ──────────────────────────────────────────────
    private Long lotId;
    private Integer count;
    private SlotType vehicleType;
    private String floor;
    private String namePrefix;

    // ── Legacy Postman / API format ──────────────────────────────────────
    private Long parkingLotId;

    private Integer regularCount;
    private Integer evCount;
    private Integer heavyVehicleCount;
    private Integer bikeCount;

    private double  defaultHourlyRate;

    // 🚨 ADD THESE NEW FIELDS for specific rates:
    private Double  regularRate;
    private Double  evRate;
    private Double  heavyVehicleRate;
    private Double  bikeRate;

    private Integer rows;
    private Integer cols;

    public Long getEffectiveLotId() {
        if (lotId != null)        return lotId;
        if (parkingLotId != null) return parkingLotId;
        return null;
    }
}