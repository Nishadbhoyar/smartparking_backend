package com.smartparking.service.Impl;

import com.smartparking.OtherServices.FareCalculationService;
import com.smartparking.OtherServices.SlotWebSocketService;
import com.smartparking.dtos.request.ValetBookingRequestDTO;
import com.smartparking.dtos.response.ValetResponseDTO;
import com.smartparking.entities.nums.SlotStatus;
import com.smartparking.entities.nums.ValetStatus;
import com.smartparking.entities.nums.VehicleType;
import com.smartparking.entities.parking.Feedback;
import com.smartparking.entities.parking.ParkingLot;
import com.smartparking.entities.parking.Slot;
import com.smartparking.entities.users.Customer;
import com.smartparking.entities.valet.Valet;
import com.smartparking.entities.valet.ValetCarImage;
import com.smartparking.entities.valet.ValetFare;
import com.smartparking.entities.valet.ValetRequest;
import com.smartparking.exceptions.ResourceNotFoundException;
import com.smartparking.repositories.*;
import com.smartparking.OtherServices.NotificationService;
import com.smartparking.service.ValetEarningsService;
import com.smartparking.service.ValetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ValetServiceImpl implements ValetService {

    @Autowired private ValetRequestRepository valetRequestRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ValetRepository valetRepository;
    @Autowired private ParkingLotRepository parkingLotRepository;
    @Autowired private SlotRepository slotRepository;
    @Autowired private SlotWebSocketService slotWebSocketService;
    @Autowired private ValetEarningsService valetEarningsService;
    @Autowired private ValetFareRepository valetFareRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private FareCalculationService fareCalculationService;
    @Autowired private ValetCarImageRepository valetCarImageRepository;
    @Autowired private FeedbackRepository feedbackRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RETURN_CONFIRM_WINDOW_MINUTES = 5;
    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024L; // 5 MB
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private String generateOTP() {
        return String.format("%04d", SECURE_RANDOM.nextInt(10000));
    }

    // ── requestValet ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ValetResponseDTO requestValet(ValetBookingRequestDTO requestDTO) {
        Customer customer = customerRepository.findById(requestDTO.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found!"));

        ValetRequest request = new ValetRequest();
        request.setCustomer(customer);
        request.setCustomerName(customer.getName());
        request.setMobileNo(requestDTO.getMobileNo());
        request.setCarPlateNo(requestDTO.getCarPlateNo());
        request.setPickupLatitude(requestDTO.getPickupLatitude());
        request.setPickupLongitude(requestDTO.getPickupLongitude());
        request.setPickupOtp(generateOTP());
        request.setDropoffOtp(generateOTP());
        request.setStatus(ValetStatus.REQUESTED);
        request.setRequestedAt(LocalDateTime.now());

        // EV Bike support
        if (requestDTO.getVehicleType() != null) {
            request.setVehicleType(requestDTO.getVehicleType());
        }
        // Store customer-reported battery level at pickup time
        if (requestDTO.getVehicleType() == VehicleType.EV_BIKE && requestDTO.getBatteryLevel() != null) {
            request.setBatteryLevelAtPickup(requestDTO.getBatteryLevel());
        }

        ValetRequest saved = valetRequestRepository.save(request);

        notificationService.notifyValetRequested(customer.getId());
        valetRepository.findAllAvailableValets().forEach(v ->
                notificationService.notifyValetNewJobAvailable(v.getId(), customer.getName()));

        return mapToResponseDTO(saved);
    }

    // ── getAvailableJobs ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ValetResponseDTO> getAvailableJobs() {
        return valetRequestRepository.findByStatus(ValetStatus.REQUESTED)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    // ── getActiveJob ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ValetResponseDTO getActiveJob(Long valetId) {
        return valetRequestRepository.findFirstByValetIdAndStatusIn(
                valetId,
                List.of(ValetStatus.ACCEPTED, ValetStatus.PICKED_UP,
                        ValetStatus.PARKED,
                        ValetStatus.RETURN_CONFIRM_PENDING,
                        ValetStatus.RETURN_REQUESTED)
        ).map(this::mapToResponseDTO).orElse(null);
    }

    // ── acceptJob ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ValetResponseDTO acceptJob(Long requestId, Long valetId) {
        ValetRequest request = getRequest(requestId);

        if (request.getStatus() != ValetStatus.REQUESTED) {
            throw new RuntimeException("This job is no longer available.");
        }

        Valet valet = valetRepository.findById(valetId)
                .orElseThrow(() -> new ResourceNotFoundException("Valet not found!"));

        request.setValet(valet);
        request.setStatus(ValetStatus.ACCEPTED);
        ValetRequest saved = valetRequestRepository.save(request);

        notificationService.notifyValetAccepted(request.getCustomer().getId(), valet.getName());
        valetRepository.findAllAvailableValets().stream()
                .filter(v -> !v.getId().equals(valetId))
                .forEach(v -> notificationService.notifyValetJobTaken(v.getId()));

        return mapToResponseDTO(saved);
    }

    // ── verifyPickup ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ValetResponseDTO verifyPickup(Long requestId, String enteredOtp) {
        ValetRequest request = getRequest(requestId);

        if (request.getStatus() != ValetStatus.ACCEPTED) {
            throw new RuntimeException(
                    "Cannot verify pickup — job must be ACCEPTED first. Current status: "
                            + request.getStatus());
        }

        if (!request.getPickupOtp().equals(enteredOtp)) {
            throw new RuntimeException("Invalid Pickup OTP! Do not hand over the keys.");
        }

        request.setStatus(ValetStatus.PICKED_UP);
        ValetRequest pickedUp = valetRequestRepository.save(request);

        notificationService.notifyCarPickedUp(
                request.getCustomer().getId(),
                request.getValet().getName());

        return mapToResponseDTO(pickedUp);
    }

    // ── uploadPickupImages ────────────────────────────────────────────────────
    // Valet uploads photos of the vehicle BEFORE driving off.
    // Allowed in ACCEPTED or PICKED_UP state (flexible — some valets photo before OTP, some after).

    @Override
    @Transactional
    public ValetResponseDTO uploadPickupImages(Long requestId, List<MultipartFile> images,
                                               Integer batteryLevelAtPickup) {
        ValetRequest request = getRequest(requestId);

        if (request.getStatus() != ValetStatus.ACCEPTED && request.getStatus() != ValetStatus.PICKED_UP) {
            throw new RuntimeException(
                    "Pickup photos can only be uploaded when status is ACCEPTED or PICKED_UP. " +
                            "Current status: " + request.getStatus());
        }

        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("At least one pickup image is required.");
        }

        saveImages(request, images, "PICKUP");

        // EV Bike: update battery level recorded by the valet
        if (request.getVehicleType() == VehicleType.EV_BIKE && batteryLevelAtPickup != null) {
            request.setBatteryLevelAtPickup(batteryLevelAtPickup);
        }

        ValetRequest saved = valetRequestRepository.save(request);
        return mapToResponseDTO(saved);
    }

    // ── parkVehicle ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ValetResponseDTO parkVehicle(Long requestId, Long lotId, Long slotId,
                                        List<MultipartFile> carImages,
                                        Integer batteryLevelAtParking) {

        ValetRequest request = valetRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Valet Request not found!"));

        if (request.getStatus() != ValetStatus.PICKED_UP) {
            throw new RuntimeException(
                    "Cannot park vehicle — pickup OTP must be verified first. Current status: "
                            + request.getStatus());
        }

        ParkingLot lot = parkingLotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found!"));
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found!"));

        if (!slot.getParkingLot().getId().equals(lotId)) {
            throw new IllegalArgumentException("Slot " + slotId + " does not belong to lot " + lotId + ".");
        }

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new RuntimeException("Slot " + slot.getSlotNumber() + " is already "
                    + slot.getStatus() + ". Choose a different slot.");
        }

        request.setParkingLot(lot);
        request.setSlot(slot);
        slot.setStatus(SlotStatus.OCCUPIED);
        slotRepository.save(slot);
        slotWebSocketService.broadcastSlotUpdate(slot);

        // Parking photos — tagged "PARKED"
        if (carImages != null && !carImages.isEmpty()) {
            if (carImages.size() > 5) {
                throw new IllegalArgumentException("Maximum 5 parking images allowed.");
            }
            saveImages(request, carImages, "PARKED");
        }

        request.setParkedLatitude(lot.getLatitude());
        request.setParkedLongitude(lot.getLongitude());
        request.setStatus(ValetStatus.PARKED);
        request.setParkedAt(LocalDateTime.now());

        // EV Bike: record battery after parking
        if (request.getVehicleType() == VehicleType.EV_BIKE && batteryLevelAtParking != null) {
            request.setBatteryLevelAtParking(batteryLevelAtParking);
        }

        ValetRequest parkedReq = valetRequestRepository.save(request);

        notificationService.notifyCarParked(
                request.getCustomer().getId(),
                lot.getName(),
                slot.getSlotNumber());

        return mapToResponseDTO(parkedReq);
    }

    // ── initiateReturnConfirmation ─────────────────────────────────────────────
    // Step 1: customer taps "Request Car Back".
    // We generate a confirmation OTP and give them 5 minutes to verify it.
    // This prevents accidental taps.

    @Override
    @Transactional
    public ValetResponseDTO initiateReturnConfirmation(Long requestId) {
        ValetRequest request = getRequest(requestId);

        if (request.getStatus() != ValetStatus.PARKED) {
            throw new RuntimeException(
                    "Can only initiate return from PARKED state. Current: " + request.getStatus());
        }

        String confirmOtp = generateOTP();
        request.setReturnConfirmOtp(confirmOtp);
        request.setReturnConfirmOtpExpiry(
                LocalDateTime.now().plusMinutes(RETURN_CONFIRM_WINDOW_MINUTES));
        request.setStatus(ValetStatus.RETURN_CONFIRM_PENDING);

        ValetRequest saved = valetRequestRepository.save(request);

        // Notify the customer with the OTP (they'll see it on screen too,
        // but a push notification is an extra safety net)
        notificationService.notifyReturnConfirmOtp(
                request.getCustomer().getId(), confirmOtp, RETURN_CONFIRM_WINDOW_MINUTES);

        return mapToResponseDTO(saved);
    }

    // ── confirmReturn ─────────────────────────────────────────────────────────
    // Step 2: customer enters the confirmation OTP.
    // Only then does the actual return request get sent to the valet.

    @Override
    @Transactional
    public ValetResponseDTO confirmReturn(Long requestId, String otp) {
        ValetRequest request = getRequest(requestId);

        if (request.getStatus() != ValetStatus.RETURN_CONFIRM_PENDING) {
            throw new RuntimeException(
                    "No return confirmation pending. Initiate return first.");
        }

        // Check if OTP has expired
        if (LocalDateTime.now().isAfter(request.getReturnConfirmOtpExpiry())) {
            // Expire: revert back to PARKED so the customer can try again
            request.setStatus(ValetStatus.PARKED);
            request.setReturnConfirmOtp(null);
            request.setReturnConfirmOtpExpiry(null);
            valetRequestRepository.save(request);
            throw new RuntimeException(
                    "Return confirmation OTP has expired. Please request your car again.");
        }

        if (!request.getReturnConfirmOtp().equals(otp)) {
            throw new RuntimeException("Invalid confirmation OTP.");
        }

        // OTP correct and not expired — officially request the return
        request.setStatus(ValetStatus.RETURN_REQUESTED);
        request.setReturnConfirmOtp(null);
        request.setReturnConfirmOtpExpiry(null);
        ValetRequest saved = valetRequestRepository.save(request);

        if (request.getValet() != null) {
            notificationService.notifyReturnRequested(
                    request.getValet().getId(),
                    request.getCustomerName());
        }

        return mapToResponseDTO(saved);
    }

    // ── verifyDropoff ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ValetResponseDTO verifyDropoff(Long requestId, String enteredOtp) {
        ValetRequest request = getRequest(requestId);

        if (request.getStatus() != ValetStatus.RETURN_REQUESTED) {
            throw new RuntimeException("Cannot verify dropoff — customer must confirm return first.");
        }

        if (!request.getDropoffOtp().equals(enteredOtp)) {
            throw new RuntimeException("Invalid Dropoff OTP! Transaction not completed.");
        }

        Slot slot = request.getSlot();
        if (slot != null) {
            slot.setStatus(SlotStatus.AVAILABLE);
            slotRepository.save(slot);
            slotWebSocketService.broadcastSlotUpdate(slot);
        }

        request.setStatus(ValetStatus.COMPLETED);
        request.setCompletedAt(LocalDateTime.now());
        ValetRequest saved = valetRequestRepository.save(request);

        notificationService.notifyJobCompleted(
                request.getCustomer().getId(),
                request.getValet() != null ? request.getValet().getName() : "Your valet"
        );

        if (request.getValet() != null) {
            if (valetFareRepository.findByValetRequestId(requestId).isEmpty()) {
                double customerLat = request.getPickupLatitude();
                double customerLon = request.getPickupLongitude();
                double parkLat = request.getParkedLatitude() != null ? request.getParkedLatitude() : customerLat;
                double parkLon = request.getParkedLongitude() != null ? request.getParkedLongitude() : customerLon;
                fareCalculationService.createFareEstimate(request, customerLat, customerLon, parkLat, parkLon);
            }

            ValetFare finalFare = fareCalculationService.finalizeFare(requestId);
            valetEarningsService.recordEarning(
                    request.getValet().getId(),
                    requestId,
                    finalFare.getTotalFare()
            );
        }

        return mapToResponseDTO(saved);
    }

    // ── getRequestById ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ValetResponseDTO getRequestById(Long requestId) {
        ValetRequest req = valetRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Valet request not found"));
        return mapToResponseDTO(req);
    }

    // ── getActiveValetForCustomer ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ValetResponseDTO getActiveValetForCustomer(Long customerId) {
        return valetRequestRepository.findFirstByCustomerIdAndStatusIn(
                customerId,
                List.of(ValetStatus.REQUESTED, ValetStatus.ACCEPTED,
                        ValetStatus.PICKED_UP, ValetStatus.PARKED,
                        ValetStatus.RETURN_CONFIRM_PENDING,
                        ValetStatus.RETURN_REQUESTED)
        ).map(this::mapToResponseDTO).orElse(null);
    }

    // ── getCustomerValetHistory ───────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ValetResponseDTO> getCustomerValetHistory(Long customerId) {
        return valetRequestRepository.findByCustomerId(customerId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ValetRequest getRequest(Long id) {
        return valetRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Valet Request not found!"));
    }

    /**
     * Validates and persists image files, tagging each with the given phase ("PICKUP" or "PARKED").
     */
    private void saveImages(ValetRequest request, List<MultipartFile> files, String phase) {
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
                throw new IllegalArgumentException(
                        "Image '" + file.getOriginalFilename() + "' exceeds 5 MB limit.");
            }

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                throw new IllegalArgumentException(
                        "Unsupported file type: " + contentType + ". Only JPEG, PNG, WEBP allowed.");
            }

            try {
                ValetCarImage image = new ValetCarImage();
                image.setValetRequest(request);
                image.setImageData(file.getBytes());
                image.setContentType(contentType);
                image.setImagePhase(phase);
                request.getCarImages().add(image);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read image: " + file.getOriginalFilename(), e);
            }
        }
    }

    private ValetResponseDTO mapToResponseDTO(ValetRequest request) {
        ValetResponseDTO dto = new ValetResponseDTO();
        dto.setId(request.getId());
        dto.setCustomerName(request.getCustomerName());
        dto.setCarPlateNo(request.getCarPlateNo());
        dto.setStatus(request.getStatus());
        dto.setPickupOtp(request.getPickupOtp());
        dto.setDropoffOtp(request.getDropoffOtp());
        dto.setReturnConfirmOtp(request.getReturnConfirmOtp());
        dto.setReturnConfirmOtpExpiry(request.getReturnConfirmOtpExpiry());
        dto.setVehicleType(request.getVehicleType());
        dto.setBatteryLevelAtPickup(request.getBatteryLevelAtPickup());
        dto.setBatteryLevelAtParking(request.getBatteryLevelAtParking());

        if (request.getValet() != null) {
            dto.setValetName(request.getValet().getName());
            dto.setValetId(request.getValet().getId());

            if (request.getCustomer() != null) {
                Feedback feedback = feedbackRepository.findFirstByCustomerIdAndValetIdOrderByCreatedAtDesc(
                        request.getCustomer().getId(),
                        request.getValet().getId()
                );
                if (feedback != null) {
                    dto.setCustomerRating(feedback.getRating());
                }
            }
        }

        // Split images by phase
        if (request.getCarImages() != null && !request.getCarImages().isEmpty()) {
            List<Long> pickupIds = request.getCarImages().stream()
                    .filter(img -> "PICKUP".equals(img.getImagePhase()))
                    .map(ValetCarImage::getId)
                    .collect(Collectors.toList());

            List<Long> parkedIds = request.getCarImages().stream()
                    .filter(img -> "PARKED".equals(img.getImagePhase()))
                    .map(ValetCarImage::getId)
                    .collect(Collectors.toList());

            List<Long> allIds = request.getCarImages().stream()
                    .map(ValetCarImage::getId)
                    .collect(Collectors.toList());

            dto.setPickupImageIds(pickupIds);
            dto.setParkedImageIds(parkedIds);
            dto.setCarImageIds(allIds); // backward-compat
        }

        if (request.getParkingLot() != null) {
            dto.setParkingLotName(request.getParkingLot().getName());
            dto.setParkingLotId(request.getParkingLot().getId());

            if (request.getSlot() != null) {
                dto.setSlotNumber(request.getSlot().getSlotNumber());
            }
        }

        dto.setParkedLatitude(request.getParkedLatitude());
        dto.setParkedLongitude(request.getParkedLongitude());

        return dto;
    }
}
