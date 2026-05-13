package com.smartparking.service.Impl;

import com.smartparking.service.PromoService;
import com.smartparking.dtos.request.ApplyPromoRequestDTO;
import com.smartparking.dtos.request.CreatePromoRequestDTO;
import com.smartparking.dtos.response.PromoResponseDTO;
import com.smartparking.entities.featuresentites.PromoCode;
import com.smartparking.entities.featuresentites.PromoUsage;
import com.smartparking.entities.nums.BookingStatus;
import com.smartparking.entities.nums.PromoType;
import com.smartparking.exceptions.ResourceNotFoundException;
import com.smartparking.repositories.BookingRepository;
import com.smartparking.repositories.PromoCodeRepository;
import com.smartparking.repositories.PromoUsageRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PromoServiceImpl implements PromoService {

    @Autowired private PromoCodeRepository  promoCodeRepository;
    @Autowired private PromoUsageRepository promoUsageRepository;
    @Autowired private BookingRepository    bookingRepository;

    @Override
    public PromoResponseDTO createPromo(CreatePromoRequestDTO dto) {
        if (promoCodeRepository.existsByCode(dto.getCode().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Promo code '" + dto.getCode() + "' already exists.");
        }

        PromoCode promo = PromoCode.builder()
                .code(dto.getCode().toUpperCase())
                .type(dto.getType())
                .discountValue(dto.getDiscountValue())
                .maxDiscountAmount(dto.getMaxDiscountAmount())
                .minBookingAmount(dto.getMinBookingAmount())
                .maxUses(dto.getMaxUses())
                .newUsersOnly(dto.isNewUsersOnly())
                .expiryDate(dto.getExpiryDate())
                .build();

        return mapToDTO(promoCodeRepository.save(promo));
    }

    @Override
    public PromoResponseDTO applyPromo(ApplyPromoRequestDTO dto) {
        PromoCode promo = validateAndGetPromo(
                dto.getCode(), dto.getBookingAmount(), dto.getCustomerId());

        if (promoUsageRepository.existsByPromoCodeIdAndCustomerId(
                promo.getId(), dto.getCustomerId())) {
            throw new IllegalArgumentException(
                    "You have already used promo code '" + dto.getCode() + "'.");
        }

        double discount    = calculateDiscount(promo, dto.getBookingAmount());
        double finalAmount = dto.getBookingAmount() - discount;

        try {
            promoUsageRepository.save(PromoUsage.builder()
                    .promoCode(promo).customerId(dto.getCustomerId()).build());
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "You have already used promo code '" + dto.getCode() + "'.");
        }

        try {
            promo.setUsedCount(promo.getUsedCount() + 1);
            promoCodeRepository.save(promo);
        } catch (OptimisticLockingFailureException e) {
            throw new IllegalArgumentException(
                    "Promo code usage limit reached. Please try again.");
        }

        PromoResponseDTO response = mapToDTO(promo);
        response.setOriginalAmount(dto.getBookingAmount());
        response.setDiscountAmount(round(discount));
        response.setFinalAmount(round(finalAmount));
        response.setMessage("Promo applied! You saved ₹" + round(discount));
        return response;
    }

    @Override
    public PromoResponseDTO validatePromo(String code, Double bookingAmount, Long customerId) {
        PromoCode promo = validateAndGetPromo(code, bookingAmount, customerId);

        if (customerId != null && promoUsageRepository.existsByPromoCodeIdAndCustomerId(
                promo.getId(), customerId)) {
            throw new IllegalArgumentException(
                    "You have already used promo code '" + code + "'.");
        }

        double discount = calculateDiscount(promo, bookingAmount);

        PromoResponseDTO response = mapToDTO(promo);
        response.setOriginalAmount(bookingAmount);
        response.setDiscountAmount(round(discount));
        response.setFinalAmount(round(bookingAmount - discount));
        response.setMessage(promo.isNewUsersOnly()
                ? "Valid for new users only! You will save ₹" + round(discount)
                : "Valid! You will save ₹" + round(discount));
        return response;
    }

    @Override
    public List<PromoResponseDTO> getAllPromos() {
        return promoCodeRepository.findAll().stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public PromoResponseDTO deactivatePromo(Long promoId) {
        PromoCode promo = promoCodeRepository.findById(promoId)
                .orElseThrow(() -> new ResourceNotFoundException("Promo not found"));
        promo.setActive(false);
        return mapToDTO(promoCodeRepository.save(promo));
    }

    @Override
    public List<PromoResponseDTO> getEligiblePromos(Long customerId, Double bookingAmount) {
        List<PromoCode> candidates = promoCodeRepository
                .findAllGloballyEligible(LocalDateTime.now());

        long completedBookings = bookingRepository
                .countByCustomerIdAndStatus(customerId, BookingStatus.COMPLETED);

        return candidates.stream()
                .filter(p -> !promoUsageRepository
                        .existsByPromoCodeIdAndCustomerId(p.getId(), customerId))
                .filter(p -> !p.isNewUsersOnly() || completedBookings == 0)
                .filter(p -> bookingAmount == null
                        || p.getMinBookingAmount() == null
                        || bookingAmount >= p.getMinBookingAmount())
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ─── private helpers ───────────────────────────────────────────────────

    private PromoCode validateAndGetPromo(String code, Double bookingAmount, Long customerId) {
        PromoCode promo = promoCodeRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Promo code '" + code + "' does not exist."));

        if (!promo.isActive())
            throw new IllegalArgumentException("This promo code is no longer active.");

        if (promo.getExpiryDate() != null &&
                promo.getExpiryDate().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("This promo code has expired.");

        if (promo.getMaxUses() != null &&
                promo.getUsedCount() >= promo.getMaxUses())
            throw new IllegalArgumentException(
                    "This promo code has reached its usage limit.");

        if (promo.getMinBookingAmount() != null &&
                bookingAmount < promo.getMinBookingAmount())
            throw new IllegalArgumentException(
                    "Minimum booking amount of ₹" + promo.getMinBookingAmount() +
                            " required to use this code.");

        if (promo.isNewUsersOnly() && customerId != null) {
            long previousBookings = bookingRepository
                    .countByCustomerIdAndStatus(customerId, BookingStatus.COMPLETED);
            if (previousBookings > 0) {
                throw new IllegalArgumentException(
                        "Sorry! This promo code '" + code +
                                "' is only for first-time customers.");
            }
        }

        return promo;
    }

    private double calculateDiscount(PromoCode promo, double amount) {
        if (promo.getType() == PromoType.FLAT) {
            return Math.min(promo.getDiscountValue(), amount);
        } else {
            double discount = amount * promo.getDiscountValue() / 100.0;
            if (promo.getMaxDiscountAmount() != null)
                discount = Math.min(discount, promo.getMaxDiscountAmount());
            return discount;
        }
    }

    private double round(double val) { return Math.round(val * 100.0) / 100.0; }

    private PromoResponseDTO mapToDTO(PromoCode p) {
        return PromoResponseDTO.builder()
                .id(p.getId())
                .code(p.getCode())
                .type(p.getType())
                .discountValue(p.getDiscountValue())
                .maxDiscountAmount(p.getMaxDiscountAmount())
                .minBookingAmount(p.getMinBookingAmount())
                .maxUses(p.getMaxUses())
                .usedCount(p.getUsedCount())       // was p.usageCount on frontend — root cause of "always 0"
                .newUsersOnly(p.isNewUsersOnly())
                .expiryDate(p.getExpiryDate())
                .isActive(p.isActive())
                .build();
    }
}