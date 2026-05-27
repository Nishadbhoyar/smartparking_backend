package com.smartparking.dtos;

import com.smartparking.entities.nums.PaymentStatus;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Returned by GET /api/payments/receipt?bookingId={id}
 *
 * Combines Booking + Payment data for the receipt page.
 * Field names match exactly what PaymentReceiptPage.jsx reads.
 */
@Data
@Transactional(readOnly = true)
public class ReceiptResponseDTO {

    // ── Payment fields ──────────────────────────────────────────
    private String      paymentStatus;       // PaymentStatus enum name  e.g. "SUCCESS"
    private String      cashfreeOrderId;
    private String      cashfreePaymentId;   // nullable; only set after webhook
    private String      paymentMethod;       // nullable
    private String      failureReason;       // nullable
    private LocalDateTime paymentInitiatedAt;
    private LocalDateTime paymentCompletedAt; // nullable
    private Double      amountPaid;

    // ── Booking fields ───────────────────────────────────────────
    private String      bookingCode;
    private String      customerName;        // Customer's full name
    private String      parkingLotName;
    private String      slotNumber;
    private LocalDateTime scheduledEntryTime;
    private LocalDateTime scheduledExitTime;
    private String      bookingStatus;       // BookingStatus enum name  e.g. "PAID"
}