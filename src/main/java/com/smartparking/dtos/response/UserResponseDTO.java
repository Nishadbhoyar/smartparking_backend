package com.smartparking.dtos.response;

import com.smartparking.entities.nums.Role;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    /** Convenience constructor used by SuperAdminController */
    public UserResponseDTO(Long id, String name, String email, Role role ,boolean verified) {
        this.id    = id;
        this.name  = name;
        this.email = email;
        this.role  = role;
        this.verified = verified;
    }
}