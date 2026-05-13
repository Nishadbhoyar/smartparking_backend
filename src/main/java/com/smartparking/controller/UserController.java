package com.smartparking.controller;

import com.smartparking.dtos.response.UserResponseDTO;
import com.smartparking.entities.users.User;
import com.smartparking.exceptions.UnauthorizedAccessException;
import com.smartparking.repositories.UserRepository;
import com.smartparking.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/{email}")
    public ResponseEntity<UserResponseDTO> getUserByEmail(@PathVariable String email) {
        return new ResponseEntity<>(userService.getUserByEmail(email), HttpStatus.OK);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserResponseDTO> updateProfile(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        // FIX #3: Verify the caller is updating their own profile
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!auth.getName().equals(targetUser.getEmail())) {
            throw new UnauthorizedAccessException("You can only update your own profile.");
        }

        // FIX #3: Require current password when changing password
        if (body.containsKey("newPassword")) {
            String currentPassword = body.get("currentPassword");
            if (currentPassword == null || currentPassword.isBlank()) {
                throw new IllegalArgumentException("Current password required to change password.");
            }
            if (!passwordEncoder.matches(currentPassword, targetUser.getPassword())) {
                throw new IllegalArgumentException("Current password is incorrect.");
            }
        }

        return ResponseEntity.ok(userService.updateProfile(userId, body));
    }
}