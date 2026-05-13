package com.smartparking.dtos.response;

import com.smartparking.entities.nums.ValetStatus;
import com.smartparking.entities.nums.VehicleType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ValetResponseDTO {
    private Long id;
    private String customerName;
    private String carPlateNo;
    private String valetName;
    private Long valetId;
    private Long parkingLotId;
    private Integer customerRating;

    private ValetStatus status;

    // ── OTPs ────────────────────────────────────────────────────────────────
    private String pickupOtp;
    private String dropoffOtp;

    // Return confirmation OTP — only populated after customer initiates return.
    // The customer must submit this within the expiry window.
    private String returnConfirmOtp;
    private LocalDateTime returnConfirmOtpExpiry;

    // ── Parking ──────────────────────────────────────────────────────────────
    private String parkingLotName;
    private String slotNumber;
    private Double parkedLatitude;
    private Double parkedLongitude;

    // ── Images (split by phase) ───────────────────────────────────────────────
    // Photos taken by the valet BEFORE driving off (shows car condition at pickup)
    private List<Long> pickupImageIds;

    // Photos taken AFTER parking the car in the slot
    private List<Long> parkedImageIds;

    // Kept for backward-compat — all images regardless of phase
    private List<Long> carImageIds;

    // ── EV Bike ──────────────────────────────────────────────────────────────
    private VehicleType vehicleType;

    // Battery % recorded by valet at pickup (EV Bike only)
    private Integer batteryLevelAtPickup;

    // Battery % recorded by valet after parking (EV Bike only)
    private Integer batteryLevelAtParking;

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCarPlateNo() {
        return carPlateNo;
    }

    public void setCarPlateNo(String carPlateNo) {
        this.carPlateNo = carPlateNo;
    }

    public String getValetName() {
        return valetName;
    }

    public void setValetName(String valetName) {
        this.valetName = valetName;
    }

    public Long getValetId() {
        return valetId;
    }

    public void setValetId(Long valetId) {
        this.valetId = valetId;
    }

    public Long getParkingLotId() {
        return parkingLotId;
    }

    public void setParkingLotId(Long parkingLotId) {
        this.parkingLotId = parkingLotId;
    }

    public ValetStatus getStatus() {
        return status;
    }

    public void setStatus(ValetStatus status) {
        this.status = status;
    }

    public Integer getCustomerRating() {
        return customerRating;
    }

    public void setCustomerRating(Integer customerRating) {
        this.customerRating = customerRating;
    }

    public String getPickupOtp() {
        return pickupOtp;
    }

    public void setPickupOtp(String pickupOtp) {
        this.pickupOtp = pickupOtp;
    }

    public String getDropoffOtp() {
        return dropoffOtp;
    }

    public void setDropoffOtp(String dropoffOtp) {
        this.dropoffOtp = dropoffOtp;
    }

    public String getReturnConfirmOtp() {
        return returnConfirmOtp;
    }

    public void setReturnConfirmOtp(String returnConfirmOtp) {
        this.returnConfirmOtp = returnConfirmOtp;
    }

    public LocalDateTime getReturnConfirmOtpExpiry() {
        return returnConfirmOtpExpiry;
    }

    public void setReturnConfirmOtpExpiry(LocalDateTime returnConfirmOtpExpiry) {
        this.returnConfirmOtpExpiry = returnConfirmOtpExpiry;
    }

    public String getParkingLotName() {
        return parkingLotName;
    }

    public void setParkingLotName(String parkingLotName) {
        this.parkingLotName = parkingLotName;
    }

    public String getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(String slotNumber) {
        this.slotNumber = slotNumber;
    }

    public Double getParkedLongitude() {
        return parkedLongitude;
    }

    public void setParkedLongitude(Double parkedLongitude) {
        this.parkedLongitude = parkedLongitude;
    }

    public Double getParkedLatitude() {
        return parkedLatitude;
    }

    public void setParkedLatitude(Double parkedLatitude) {
        this.parkedLatitude = parkedLatitude;
    }

    public List<Long> getPickupImageIds() {
        return pickupImageIds;
    }

    public void setPickupImageIds(List<Long> pickupImageIds) {
        this.pickupImageIds = pickupImageIds;
    }

    public List<Long> getParkedImageIds() {
        return parkedImageIds;
    }

    public void setParkedImageIds(List<Long> parkedImageIds) {
        this.parkedImageIds = parkedImageIds;
    }

    public List<Long> getCarImageIds() {
        return carImageIds;
    }

    public void setCarImageIds(List<Long> carImageIds) {
        this.carImageIds = carImageIds;
    }

    public Integer getBatteryLevelAtPickup() {
        return batteryLevelAtPickup;
    }

    public void setBatteryLevelAtPickup(Integer batteryLevelAtPickup) {
        this.batteryLevelAtPickup = batteryLevelAtPickup;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public Integer getBatteryLevelAtParking() {
        return batteryLevelAtParking;
    }

    public void setBatteryLevelAtParking(Integer batteryLevelAtParking) {
        this.batteryLevelAtParking = batteryLevelAtParking;
    }
}
