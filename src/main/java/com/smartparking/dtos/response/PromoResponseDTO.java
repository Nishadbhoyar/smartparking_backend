package com.smartparking.dtos.response;

import com.smartparking.entities.nums.PromoType;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PromoResponseDTO {
    private Long          id;
    private String        code;
    private PromoType     type;
    private Double        discountValue;
    private Double        maxDiscountAmount;
    private Double        minBookingAmount;
    private Integer       maxUses;
    private Integer       usedCount;
    private LocalDateTime expiryDate;
    private boolean       isActive;
    private boolean       newUsersOnly;   // ADDED: frontend needs this for "New users only" badge

    // Returned when applying/validating promo
    private Double  originalAmount;
    private Double  discountAmount;
    private Double  finalAmount;
    private String  message;
}