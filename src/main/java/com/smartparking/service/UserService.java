package com.smartparking.service;

import com.smartparking.dtos.request.UserRegistrationDTO;
import com.smartparking.dtos.response.UserResponseDTO;
import java.util.Map;

public interface UserService {

    /** Self-registration — blocks privileged roles (VALET, PARKING_LOT_ADMIN, SUPER_ADMIN). */
    UserResponseDTO registerUser(UserRegistrationDTO dto);

    /** Admin-initiated creation — allows all roles except SUPER_ADMIN. */
    UserResponseDTO registerUserByAdmin(UserRegistrationDTO dto);

    UserResponseDTO getUserByEmail(String email);

    UserResponseDTO updateProfile(Long userId, Map<String, String> body);


}