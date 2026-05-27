package com.smartparking.dtos.response;

import com.smartparking.entities.nums.Role;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor

public class UserResponseDTO {
    private Long   id;
    private String name;
    private String email;
    private Role   role;

    private String defaultLicensePlate;
    private String drivingLicenseNumber;
    private String aadhaarNumber;
    private String businessPhone;
    private String businessRegistrationNumber;
    private boolean verified;
    // Add this inside UserResponseDTO.java (around line 14)

    private String phoneNumber;

// Optional: If you use the constructor at the bottom of that file,
// add it there too, though it's not strictly required if you use setters.

    /** Convenience constructor used by SuperAdminController */
    public UserResponseDTO(Long id, String name, String email, Role role, String defaultLicensePlate, String drivingLicenseNumber, String aadhaarNumber, String businessRegistrationNumber, String businessPhone, boolean verified, String phoneNumber) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.defaultLicensePlate = defaultLicensePlate;
        this.drivingLicenseNumber = drivingLicenseNumber;
        this.aadhaarNumber = aadhaarNumber;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.businessPhone = businessPhone;
        this.verified = verified;
        this.phoneNumber = phoneNumber;
    }
}