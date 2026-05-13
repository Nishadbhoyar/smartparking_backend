package com.smartparking.repositories;

import com.smartparking.entities.featuresentites.PromoUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PromoUsageRepository extends JpaRepository<PromoUsage, Long> {

    boolean existsByPromoCodeIdAndCustomerId(Long promoCodeId, Long customerId);

    @Transactional
    void deleteByPromoCodeCodeAndCustomerId(String promoCode, Long customerId);
}