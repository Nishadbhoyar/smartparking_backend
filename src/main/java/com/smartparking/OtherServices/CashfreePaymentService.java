package com.smartparking.OtherServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.smartparking.dtos.request.PaymentInitiateRequestDTO;
import com.smartparking.dtos.request.RefundRequestDTO;
import com.smartparking.dtos.response.PaymentResponseDTO;
import com.smartparking.dtos.ReceiptResponseDTO;

import com.smartparking.entities.Booking;
import com.smartparking.entities.Payment;
import com.smartparking.entities.rental.CarRentalBooking;
import com.smartparking.entities.valet.ValetFare;
import com.smartparking.entities.valet.ValetRequest;
import com.smartparking.entities.users.Customer;

import com.smartparking.entities.nums.BookingStatus;
import com.smartparking.entities.nums.CarRentalStatus;
import com.smartparking.entities.nums.PaymentStatus;
import com.smartparking.entities.nums.ServiceType;

import com.smartparking.exceptions.ResourceNotFoundException;

import com.smartparking.repositories.BookingRepository;
import com.smartparking.repositories.CarRentalBookingRepository;
import com.smartparking.repositories.PaymentRepository;
import com.smartparking.repositories.ValetFareRepository;
import com.smartparking.repositories.ValetRequestRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CashfreePaymentService {

    private static final Logger log = LoggerFactory.getLogger(CashfreePaymentService.class);
    private static final String CASHFREE_API_VERSION = "2023-08-01";

    @Value("${cashfree.app-id}")
    private String appId;

    @Value("${cashfree.secret-key}")
    private String secretKey;

    @Value("${cashfree.base-url}")
    private String baseUrl;

    @Value("${cashfree.webhook-secret}")
    private String webhookSecret;

    @Autowired
    private BookingRepository         bookingRepository;

    @Autowired
    private CarRentalBookingRepository carRentalBookingRepository;

    @Autowired
    private ValetRequestRepository     valetRequestRepository;

    @Autowired
    private ValetFareRepository        valetFareRepository;

    @Autowired
    private PaymentRepository          paymentRepository;


    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient   httpClient   = HttpClient.newHttpClient();

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Initiate Payment
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Unified entry point for all service payments.
     *
     * Request must include:
     *   serviceType  → PARKING_BOOKING | CAR_RENTAL | VALET
     *   referenceId  → ID of Booking / CarRentalBooking / ValetRequest
     *   customerPhone
     *
     * Returns a paymentSessionId — pass this to the Cashfree Drop JS SDK:
     *   cashfree.checkout({ paymentSessionId: response.paymentSessionId })
     */
    @Transactional
    public PaymentResponseDTO initiatePayment(PaymentInitiateRequestDTO request) throws Exception {

        if (request.getServiceType() == null) {
            throw new IllegalArgumentException("serviceType is required.");
        }
        if (request.getReferenceId() == null) {
            throw new IllegalArgumentException("referenceId is required.");
        }

        // ── Resolve service-specific data ────────────────────────────────────
        ResolvedPaymentContext ctx = resolvePaymentContext(request.getServiceType(), request.getReferenceId());

        // ── Build a unique Cashfree order ID (max 50 chars) ──────────────────
        String cashfreeOrderId = "CF-" + ctx.referenceCode + "-" + System.currentTimeMillis();
        if (cashfreeOrderId.length() > 50) {
            cashfreeOrderId = cashfreeOrderId.substring(0, 50);
        }

        // ── Build Cashfree create-order request body ─────────────────────────
        ObjectNode body = objectMapper.createObjectNode();
        body.put("order_id",       cashfreeOrderId);
        body.put("order_amount",   ctx.amount);
        body.put("order_currency", "INR");

        ObjectNode customerDetails = objectMapper.createObjectNode();
        customerDetails.put("customer_id",    "CUST-" + ctx.customer.getId());
        customerDetails.put("customer_name",  ctx.customer.getName());
        customerDetails.put("customer_email", ctx.customer.getEmail());
        String phone = (request.getCustomerPhone() != null && !request.getCustomerPhone().isBlank())
                ? request.getCustomerPhone() : "9999999999";
        customerDetails.put("customer_phone", phone);
        body.set("customer_details", customerDetails);

        ObjectNode orderMeta = objectMapper.createObjectNode();
        orderMeta.put("return_url", "https://your-frontend.com/payment/result?order_id={order_id}");
        body.set("order_meta", orderMeta);

        // ── Call Cashfree API ─────────────────────────────────────────────────
        JsonNode cashfreeResponse = callCashfreeApi("POST", "/pg/orders", body.toString());

        String paymentSessionId = cashfreeResponse.path("payment_session_id").asText();
        if (paymentSessionId == null || paymentSessionId.isBlank()) {
            String message = cashfreeResponse.path("message").asText("Unknown Cashfree error");
            throw new RuntimeException("Cashfree order creation failed: " + message);
        }

        // ── Persist payment record ────────────────────────────────────────────
        Payment payment = new Payment();
        payment.setCashfreeOrderId(cashfreeOrderId);
        payment.setPaymentSessionId(paymentSessionId);
        payment.setServiceType(request.getServiceType());
        payment.setReferenceId(request.getReferenceId());
        payment.setReferenceCode(ctx.referenceCode);
        payment.setCustomer(ctx.customer);
        payment.setAmount(ctx.amount);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setInitiatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        log.info("Payment initiated: orderId={}, serviceType={}, referenceId={}, amount={}",
                cashfreeOrderId, request.getServiceType(), request.getReferenceId(), ctx.amount);

        return toDTO(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Handle Webhook
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cashfree calls this after every payment event.
     * MUST be public (no JWT). Verify signature before trusting payload.
     *
     * On SUCCESS: marks the originating service record as paid/active.
     * On FAILED / USER_DROPPED: marks payment as FAILED.
     */
    @Transactional
    public void handleWebhook(String rawBody, String timestamp, String signature) throws Exception {

        if (!isValidWebhookSignature(rawBody, timestamp, signature)) {
            log.warn("Webhook signature verification failed — rejecting payload");
            throw new SecurityException("Invalid webhook signature");
        }

        JsonNode payload      = objectMapper.readTree(rawBody);
        JsonNode dataNode     = payload.path("data");
        JsonNode orderNode    = dataNode.path("order");
        JsonNode paymentNode  = dataNode.path("payment");

        String cashfreeOrderId  = orderNode.path("order_id").asText();
        String cashfreePaymentId = paymentNode.path("cf_payment_id").asText();
        String paymentStatus    = paymentNode.path("payment_status").asText();
        String paymentMethod    = paymentNode.path("payment_method").asText();
        String failureMessage   = paymentNode.path("payment_message").asText();

        Payment payment = paymentRepository.findByCashfreeOrderId(cashfreeOrderId)
                .orElseGet(() -> {
                    log.warn("Webhook received for unknown orderId: {}", cashfreeOrderId);
                    return null;
                });

        if (payment == null) return;

        payment.setCashfreePaymentId(cashfreePaymentId);
        payment.setPaymentMethod(paymentMethod);
        payment.setCompletedAt(LocalDateTime.now());

        if ("SUCCESS".equalsIgnoreCase(paymentStatus)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            markServiceAsPaid(payment.getServiceType(), payment.getReferenceId());
            log.info("Payment SUCCESS: orderId={}, serviceType={}, referenceId={}",
                    cashfreeOrderId, payment.getServiceType(), payment.getReferenceId());

        } else if ("FAILED".equalsIgnoreCase(paymentStatus) || "USER_DROPPED".equalsIgnoreCase(paymentStatus)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureMessage);
            log.info("Payment FAILED: orderId={}, reason={}", cashfreeOrderId, failureMessage);

        } else {
            // PENDING or other intermediate states — wait for next webhook
            log.info("Payment not terminal yet: orderId={}, status={}", cashfreeOrderId, paymentStatus);
        }

        paymentRepository.save(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Get Payment Status (frontend polling fallback)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the latest payment status for a given service record.
     * Refreshes from Cashfree if our local status is still INITIATED.
     *
     * @param serviceType  Which service (PARKING_BOOKING, CAR_RENTAL, VALET)
     * @param referenceId  ID of that service's booking/request
     */
    public PaymentResponseDTO getPaymentStatus(ServiceType serviceType, Long referenceId) throws Exception {

        Payment payment = paymentRepository
                .findTopByServiceTypeAndReferenceIdOrderByInitiatedAtDesc(serviceType, referenceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment found for " + serviceType + " id=" + referenceId));

        if (payment.getStatus() == PaymentStatus.INITIATED) {
            try {
                JsonNode cashfreeData = callCashfreeApi(
                        "GET", "/pg/orders/" + payment.getCashfreeOrderId(), null);
                String orderStatus = cashfreeData.path("order_status").asText();

                if ("PAID".equalsIgnoreCase(orderStatus)) {
                    payment.setStatus(PaymentStatus.SUCCESS);
                    payment.setCompletedAt(LocalDateTime.now());
                    markServiceAsPaid(serviceType, referenceId);
                    paymentRepository.save(payment);

                } else if ("EXPIRED".equalsIgnoreCase(orderStatus)) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setFailureReason("Order expired on Cashfree");
                    paymentRepository.save(payment);
                }
            } catch (Exception e) {
                log.warn("Could not refresh payment status from Cashfree: {}", e.getMessage());
            }
        }

        return toDTO(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Get All Payments for a Service Reference
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns full payment history for a service record.
     * E.g. multiple attempts if first attempt failed.
     */
    public List<PaymentResponseDTO> getPaymentsByReference(ServiceType serviceType, Long referenceId) {
        return paymentRepository
                .findByServiceTypeAndReferenceIdOrderByInitiatedAtDesc(serviceType, referenceId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Refund
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initiates a refund on Cashfree.
     * Only works when the most recent payment for the reference has status=SUCCESS.
     *
     * POST /pg/orders/{order_id}/refunds
     */
    @Transactional
    public PaymentResponseDTO initiateRefund(RefundRequestDTO request) throws Exception {

        Payment payment = paymentRepository
                .findTopByServiceTypeAndReferenceIdAndStatusOrderByInitiatedAtDesc(
                        request.getServiceType(), request.getReferenceId(), PaymentStatus.SUCCESS)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No successful payment found for " + request.getServiceType()
                                + " id=" + request.getReferenceId()));

        double refundAmount = (request.getRefundAmount() != null)
                ? request.getRefundAmount()
                : payment.getAmount();

        if (refundAmount > payment.getAmount()) {
            throw new IllegalArgumentException(
                    "Refund amount (" + refundAmount + ") exceeds original payment (" + payment.getAmount() + ")");
        }

        String refundId = "RF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("refund_amount", refundAmount);
        body.put("refund_id",     refundId);
        body.put("refund_note",
                request.getRefundReason() != null ? request.getRefundReason() : "Service cancellation refund");

        callCashfreeApi("POST", "/pg/orders/" + payment.getCashfreeOrderId() + "/refunds", body.toString());

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setCompletedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        log.info("Refund initiated: refundId={}, orderId={}, serviceType={}, referenceId={}, amount={}",
                refundId, payment.getCashfreeOrderId(),
                payment.getServiceType(), payment.getReferenceId(), refundAmount);

        return toDTO(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private — Service Resolution
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves amount, customer, and reference code for any service type.
     * All validation (cancelled status, missing fare, etc.) happens here
     * so initiatePayment() stays clean.
     */
    private ResolvedPaymentContext resolvePaymentContext(ServiceType serviceType, Long referenceId) {

        switch (serviceType) {

            case PARKING_BOOKING: {
                Booking b = bookingRepository.findById(referenceId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Parking booking not found: " + referenceId));
                if (b.getStatus() == BookingStatus.CANCELLED) {
                    throw new IllegalStateException("Cannot pay for a cancelled parking booking.");
                }
                if (b.getTotalAmount() == null || b.getTotalAmount() <= 0) {
                    throw new IllegalStateException("Parking booking has no valid amount.");
                }
                return new ResolvedPaymentContext(b.getTotalAmount(), b.getCustomer(), b.getBookingCode());
            }

            case CAR_RENTAL: {
                CarRentalBooking r = carRentalBookingRepository.findById(referenceId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Car rental booking not found: " + referenceId));
                if (r.getStatus() == CarRentalStatus.CANCELLED) {
                    throw new IllegalStateException("Cannot pay for a cancelled car rental.");
                }
                if (r.getTotalAmount() == null || r.getTotalAmount() <= 0) {
                    throw new IllegalStateException("Car rental booking has no valid amount.");
                }
                return new ResolvedPaymentContext(r.getTotalAmount(), r.getCustomer(), r.getBookingCode());
            }

            case VALET: {
                ValetRequest v = valetRequestRepository.findById(referenceId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Valet request not found: " + referenceId));
                ValetFare fare = valetFareRepository.findByValetRequestId(referenceId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Fare not yet calculated for valet request: " + referenceId
                                        + ". Payment can only be initiated after the job is complete."));
                if (fare.getPaymentStatus() == ValetFare.PaymentStatus.PAID) {
                    throw new IllegalStateException("Valet request " + referenceId + " is already paid.");
                }
                String refCode = "VLT-" + v.getId();
                return new ResolvedPaymentContext(fare.getTotalFare(), v.getCustomer(), refCode);
            }

            default:
                throw new IllegalArgumentException("Unsupported service type: " + serviceType);
        }
    }

    /**
     * After a successful payment, updates the source service record's status.
     * Called from both the webhook handler and the polling-based status refresh.
     */
    private void markServiceAsPaid(ServiceType serviceType, Long referenceId) {

        switch (serviceType) {

            case PARKING_BOOKING: {
                Booking b = bookingRepository.findById(referenceId).orElse(null);
                if (b != null && b.getStatus() == BookingStatus.PENDING) {
                    b.setStatus(BookingStatus.PAID);
                    bookingRepository.save(b);
                }
                break;
            }

            case CAR_RENTAL: {
                CarRentalBooking r = carRentalBookingRepository.findById(referenceId).orElse(null);
                if (r != null && r.getStatus() == CarRentalStatus.PENDING) {
                    r.setStatus(CarRentalStatus.CONFIRMED);
                    carRentalBookingRepository.save(r);
                }
                break;
            }

            case VALET: {
                ValetFare fare = valetFareRepository.findByValetRequestId(referenceId).orElse(null);
                if (fare != null && fare.getPaymentStatus() != ValetFare.PaymentStatus.PAID) {
                    fare.setPaymentStatus(ValetFare.PaymentStatus.PAID);
                    valetFareRepository.save(fare);
                }
                break;
            }

            default:
                log.warn("markServiceAsPaid: unhandled serviceType={}", serviceType);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private — HTTP & Signature Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private JsonNode callCashfreeApi(String method, String path, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("x-api-version",   CASHFREE_API_VERSION)
                .header("x-client-id",     appId)
                .header("x-client-secret", secretKey)
                .header("Content-Type",    "application/json")
                .header("Accept",          "application/json");

        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        } else if ("GET".equals(method)) {
            builder.GET();
        } else if ("PATCH".equals(method)) {
            builder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody));
        }

        HttpResponse<String> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());

        log.debug("Cashfree API {} {}: status={}, body={}",
                method, path, response.statusCode(), response.body());

        JsonNode jsonResponse = objectMapper.readTree(response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = jsonResponse.path("message").asText("Cashfree API error");
            throw new RuntimeException("Cashfree API error [" + response.statusCode() + "]: " + message);
        }

        return jsonResponse;
    }

    private boolean isValidWebhookSignature(String rawBody, String timestamp, String receivedSignature) {
        try {
            String signedPayload = timestamp + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = Base64.getEncoder().encodeToString(hash);
            return computedSignature.equals(receivedSignature);
        } catch (Exception e) {
            log.error("Error computing webhook signature", e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private — DTO Mapper
    // ─────────────────────────────────────────────────────────────────────────

    private PaymentResponseDTO toDTO(Payment payment) {
        PaymentResponseDTO dto = new PaymentResponseDTO();
        dto.setPaymentId(payment.getId());
        dto.setCashfreeOrderId(payment.getCashfreeOrderId());
        dto.setPaymentSessionId(payment.getPaymentSessionId());
        dto.setServiceType(payment.getServiceType());
        dto.setReferenceId(payment.getReferenceId());
        dto.setReferenceCode(payment.getReferenceCode());
        dto.setAmount(payment.getAmount());
        dto.setCurrency(payment.getCurrency());
        dto.setStatus(payment.getStatus());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setFailureReason(payment.getFailureReason());
        dto.setInitiatedAt(payment.getInitiatedAt());
        dto.setCompletedAt(payment.getCompletedAt());
        dto.setCashfreeCheckoutUrl(baseUrl + "/pg/view/order/" + payment.getCashfreeOrderId());
        return dto;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private — Inner Helper Record
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Carries the resolved data needed to create a Cashfree order,
     * regardless of which service type triggered the payment.
     */
    private static class ResolvedPaymentContext {
        final double   amount;
        final Customer customer;
        final String   referenceCode;

        ResolvedPaymentContext(double amount, Customer customer, String referenceCode) {
            this.amount        = amount;
            this.customer      = customer;
            this.referenceCode = referenceCode;
        }
    }

    public ReceiptResponseDTO getReceiptForBooking(Long bookingId) {

        // 1. Load booking — throws if not found
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found: " + bookingId));

        // 2. Most recent payment for this booking (any status)
        Payment payment = paymentRepository
                .findTopByServiceTypeAndReferenceIdOrderByInitiatedAtDesc(
                        ServiceType.PARKING_BOOKING, bookingId)
                .orElseThrow(() -> new IllegalStateException(
                        "No payment record found for bookingId: " + bookingId));

        // 3. Map to receipt DTO
        ReceiptResponseDTO dto = new ReceiptResponseDTO();

        // Payment side
        dto.setPaymentStatus(payment.getStatus() != null
                ? payment.getStatus().name() : null);
        dto.setCashfreeOrderId(payment.getCashfreeOrderId());
        dto.setCashfreePaymentId(payment.getCashfreePaymentId());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setFailureReason(payment.getFailureReason());
        dto.setPaymentInitiatedAt(payment.getInitiatedAt());
        dto.setPaymentCompletedAt(payment.getCompletedAt());
        dto.setAmountPaid(payment.getAmount());

        // Booking side
        dto.setBookingCode(booking.getBookingCode());
        dto.setScheduledEntryTime(booking.getEntryTime());
        dto.setScheduledExitTime(booking.getExitTime());
        dto.setBookingStatus(booking.getStatus() != null
                ? booking.getStatus().name() : null);

        // Customer name — guard against null
        if (booking.getCustomer() != null) {
            dto.setCustomerName(booking.getCustomer().getName());
        }

        // Parking lot name — guard against null
        if (booking.getParkingLot() != null) {
            dto.setParkingLotName(booking.getParkingLot().getName());
        }

        // Slot number — guard against null
        if (booking.getSlot() != null) {
            dto.setSlotNumber(booking.getSlot().getSlotNumber());
        }

        return dto;
    }
}