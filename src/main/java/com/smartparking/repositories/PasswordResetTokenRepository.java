package com.smartparking.repositories;


import com.smartparking.entities.users.PasswordResetToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // Find the latest unused token for a user by OTP
    @Query("SELECT t FROM PasswordResetToken t JOIN FETCH t.user WHERE t.otp = :otp AND t.used = false")
    Optional<PasswordResetToken> findByOtpAndUsedFalse(@Param("otp") String otp);

    // Delete all previous tokens for a user before issuing a new one
    @Modifying
    @Transactional
    void deleteByUserId(Long userId);
}