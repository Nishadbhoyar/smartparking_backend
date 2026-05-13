package com.smartparking.service;

import com.smartparking.dtos.request.ApplyPromoRequestDTO;
import com.smartparking.dtos.request.CreatePromoRequestDTO;
import com.smartparking.dtos.response.PromoResponseDTO;
import java.util.List;

public interface PromoService {

    PromoResponseDTO createPromo(CreatePromoRequestDTO dto);

    PromoResponseDTO applyPromo(ApplyPromoRequestDTO dto);

    PromoResponseDTO validatePromo(String code, Double bookingAmount, Long customerId);

    List<PromoResponseDTO> getAllPromos();

    PromoResponseDTO deactivatePromo(Long promoId);

    /**
     * Returns only promos the given customer is actually eligible to use.
     * Filters: active, not expired, not exhausted, not already used by this customer,
     * newUsersOnly check, and optional minBookingAmount check.
     */
    List<PromoResponseDTO> getEligiblePromos(Long customerId, Double bookingAmount);
}