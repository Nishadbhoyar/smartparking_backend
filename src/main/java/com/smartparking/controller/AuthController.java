package com.smartparking.controller;

import com.smartparking.dtos.request.LoginRequestDTO;
import com.smartparking.dtos.request.UserRegistrationDTO;
import com.smartparking.dtos.response.AuthResponseDTO;
import com.smartparking.dtos.response.UserResponseDTO;

import com.smartparking.entities.users.PasswordResetToken;
import com.smartparking.entities.users.User;
import com.smartparking.repositories.PasswordResetTokenRepository;
import com.smartparking.repositories.UserRepository;
import com.smartparking.security.JwtUtil;
import com.smartparking.OtherServices.EmailService;
import com.smartparking.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.smartparking.security.TokenBlacklistService;
import com.smartparking.repositories.RentalCompanyRepository;
import com.smartparking.entities.nums.Role;

import java.time.LocalDateTime;
import java.util.Map;
import java.security.SecureRandom;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private  AuthenticationManager  authenticationManager;

    @Autowired
    private UserRepository  userRepository;

    @Autowired
    private PasswordResetTokenRepository   resetTokenRepository;

    @Autowired
    private JwtUtil                        jwtUtil;

    @Autowired
    private UserService                    userService;

    @Autowired
    private EmailService                   emailService;

    @Autowired
    private PasswordEncoder                passwordEncoder;

    @Autowired
    private TokenBlacklistService          tokenBlacklistService;

    @Autowired
    private RentalCompanyRepository         rentalCompanyRepository;

    // ── Register ───────────────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserRegistrationDTO dto) {
        UserResponseDTO saved = userService.registerUser(dto);
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole().name());
        AuthResponseDTO response = new AuthResponseDTO(
                token, saved.getId(), saved.getName(), saved.getEmail(), saved.getRole()
        );
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ── Login ──────────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO dto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
        );
        User user  = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        AuthResponseDTO response = new AuthResponseDTO(
                token, user.getId(), user.getName(), user.getEmail(), user.getRole()
        );
        // Populate companyId for FLEET_ADMIN so frontend can call /company/{companyId}/list
        if (user.getRole() == Role.FLEET_ADMIN) {
            rentalCompanyRepository.findByFleetAdminId(user.getId())
                    .ifPresent(company -> response.setCompanyId(company.getId()));
        }
        return ResponseEntity.ok(response);
    }
    // ── Forgot Password — Step 1: Send OTP ────────────────────────────────
    // POST /api/auth/forgot-password
    // Body: { "email": "user@example.com" }
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email is required."));
        }

        // Always return 200 — never reveal whether an account exists (security best practice)
        userRepository.findByEmail(email).ifPresent(user -> {
            // Delete any old unused tokens for this user
            resetTokenRepository.deleteByUserId(user.getId());

            // Generate a 6-digit OTP
            // H-2 FIX: SecureRandom is cryptographically strong — new Random() is predictable.
            String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));

            // Save token with 15-minute expiry
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setOtp(otp);
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            resetTokenRepository.save(resetToken);

            // Send email
            emailService.sendPasswordResetOtp(user.getEmail(), user.getName(), otp);
        });

        return ResponseEntity.ok(Map.of(
                "message", "If an account exists with that email, an OTP has been sent."
        ));
    }

    // ── Reset Password — Step 2: Verify OTP + Set New Password ────────────
    // POST /api/auth/reset-password
    // Body: { "email": "user@example.com", "otp": "123456", "newPassword": "secret" }
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        String email       = body.get("email");
        String otp         = body.get("otp");
        String newPassword = body.get("newPassword");

        if (email == null || otp == null || newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email, OTP and a password of at least 6 characters are required."));
        }

        // Find the token
        PasswordResetToken token = resetTokenRepository.findByOtpAndUsedFalse(otp)
                .orElse(null);

        if (token == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or already used OTP."));
        }

        // Check it belongs to the right user
        if (!token.getUser().getEmail().equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "OTP does not match the provided email."));
        }

        // Check expiry
        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "OTP has expired. Please request a new one."));
        }

        // Update password
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token as used
        token.setUsed(true);
        resetTokenRepository.save(token);

        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now log in."));
    }

    // ── Logout — M-2 FIX: blacklist the token so it cannot be reused ──────
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenBlacklistService.blacklist(authHeader.substring(7));
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }
}