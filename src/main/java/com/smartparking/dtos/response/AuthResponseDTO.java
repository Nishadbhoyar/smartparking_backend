package com.smartparking.dtos.response;

import com.smartparking.entities.nums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponseDTO {
    private String token;
    private Long   userId;
    private String name;
    private String email;
    private Role   role;
    // Populated for FLEET_ADMIN only — null for all other roles
    private Long   companyId;
    private String companyName;

    // Keep original 5-arg constructor for non-FLEET_ADMIN logins
    public AuthResponseDTO(String token, Long userId, String name, String email, Role role) {
        this.token  = token;
        this.userId = userId;
        this.name   = name;
        this.email  = email;
        this.role   = role;
    }
}