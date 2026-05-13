package com.smartparking.repositories;

import com.smartparking.entities.featuresentites.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    Optional<PromoCode> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Returns all promos that pass global filters (active, not expired, not exhausted).
     * Per-customer filters (already used, newUsersOnly) are applied in the service layer.
     */
    @Query("SELECT p FROM PromoCode p WHERE p.isActive = true " +
            "AND (p.expiryDate IS NULL OR p.expiryDate > :now) " +
            "AND (p.maxUses IS NULL OR p.usedCount < p.maxUses)")
    List<PromoCode> findAllGloballyEligible(@Param("now") LocalDateTime now);
}