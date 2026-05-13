package com.smartparking.controller;

import com.smartparking.OtherServices.CashfreePaymentService;
import com.smartparking.dtos.request.PaymentInitiateRequestDTO;
import com.smartparking.dtos.request.RefundRequestDTO;
import com.smartparking.dtos.response.PaymentResponseDTO;
import com.smartparking.entities.nums.ServiceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private CashfreePaymentService paymentService;

    /**
     * Initiate payment for any service.
     *
     * POST /api/payments/initiate
     * Body: {
     *   "serviceType": "PARKING_BOOKING" | "CAR_RENTAL" | "VALET",
     *   "referenceId": 42,
     *   "customerPhone": "9876543210"
     * }
     *
     * Returns paymentSessionId — pass to Cashfree Drop JS SDK:
     *   cashfree.checkout({ paymentSessionId: response.paymentSessionId })
     */
    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponseDTO> initiatePayment(
            @RequestBody PaymentInitiateRequestDTO request) {
        try {
            PaymentResponseDTO response = paymentService.initiatePayment(request);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("Payment initiation rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Payment initiation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Cashfree webhook — called after every payment event.
     *
     * POST /api/payments/webhook
     *
     * IMPORTANT: Must be public (no JWT). Add to permitAll in SecurityConfig:
     *   "/api/payments/webhook"
     *
     * Always returns 200 OK on success so Cashfree does not retry.
     * Returns 400 only on invalid signature.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "x-webhook-signature-sha256", required = false) String signature,
            @RequestHeader(value = "x-webhook-timestamp",        required = false) String timestamp) {

        try {
            paymentService.handleWebhook(rawBody, timestamp, signature);
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (SecurityException e) {
            log.warn("Webhook rejected — invalid signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid signature"));
        } catch (Exception e) {
            log.error("Webhook processing error", e);
            return ResponseEntity.ok(Map.of("status", "error_logged"));
        }
    }

    /**
     * Poll for latest payment status (frontend fallback when webhook is delayed).
     *
     * GET /api/payments/status?serviceType=PARKING_BOOKING&referenceId=42
     */
    @GetMapping("/status")
    public ResponseEntity<PaymentResponseDTO> getPaymentStatus(
            @RequestParam ServiceType serviceType,
            @RequestParam Long referenceId) {
        try {
            return ResponseEntity.ok(paymentService.getPaymentStatus(serviceType, referenceId));
        } catch (Exception e) {
            log.error("Error fetching payment status: serviceType={}, referenceId={}",
                    serviceType, referenceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Full payment history for a service record.
     *
     * GET /api/payments/history?serviceType=CAR_RENTAL&referenceId=15
     */
    @GetMapping("/history")
    public ResponseEntity<List<PaymentResponseDTO>> getPaymentHistory(
            @RequestParam ServiceType serviceType,
            @RequestParam Long referenceId) {
        return ResponseEntity.ok(paymentService.getPaymentsByReference(serviceType, referenceId));
    }

    /**
     * Initiate a refund.
     *
     * POST /api/payments/refund
     * Body: {
     *   "serviceType": "CAR_RENTAL",
     *   "referenceId": 15,
     *   "refundAmount": 500.0,       ← optional; omit to refund full amount
     *   "refundReason": "Cancelled by customer"
     * }
     */
    @PostMapping("/refund")
    public ResponseEntity<PaymentResponseDTO> initiateRefund(
            @RequestBody RefundRequestDTO request) {
        try {
            return ResponseEntity.ok(paymentService.initiateRefund(request));
        } catch (IllegalArgumentException e) {
            log.warn("Refund rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Refund failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}