package com.smartparking.controller;

import com.smartparking.dtos.request.ValetBookingRequestDTO;
import com.smartparking.dtos.response.ValetResponseDTO;
import com.smartparking.entities.valet.ValetCarImage;
import com.smartparking.exceptions.ResourceNotFoundException;
import com.smartparking.repositories.UserRepository;
import com.smartparking.repositories.ValetCarImageRepository;
import com.smartparking.repositories.ValetRepository;
import com.smartparking.service.ValetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/valet")
public class ValetController {

    @Autowired
    private ValetService valetService;

    // NEW: needed to serve binary images and enforce ownership checks
    @Autowired
    private ValetCarImageRepository valetCarImageRepository;

    // NEW: needed to resolve the JWT email to a user ID for ownership check
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ValetRepository valetRepository ;

    // ── existing endpoints (unchanged) ─────────────────────────────────────

    @PostMapping("/request")
    public ResponseEntity<ValetResponseDTO> requestValet(
            @RequestBody ValetBookingRequestDTO requestDTO) {
        return new ResponseEntity<>(valetService.requestValet(requestDTO), HttpStatus.CREATED);
    }

    @GetMapping("/jobs/available")
    public ResponseEntity<List<ValetResponseDTO>> getAvailableJobs() {
        return new ResponseEntity<>(valetService.getAvailableJobs(), HttpStatus.OK);
    }

    @GetMapping("/jobs/active")
    public ResponseEntity<ValetResponseDTO> getActiveJob(@RequestParam Long valetId) {
        ValetResponseDTO job = valetService.getActiveJob(valetId);
        return job != null ? ResponseEntity.ok(job) : ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/accept")
    public ResponseEntity<ValetResponseDTO> acceptJob(
            @PathVariable Long requestId,
            @RequestParam Long valetId) {
        return new ResponseEntity<>(valetService.acceptJob(requestId, valetId), HttpStatus.OK);
    }

    @PostMapping("/{requestId}/verify-pickup")
    public ResponseEntity<ValetResponseDTO> verifyPickup(
            @PathVariable Long requestId,
            @RequestParam String otp) {
        return new ResponseEntity<>(valetService.verifyPickup(requestId, otp), HttpStatus.OK);
    }

    @PostMapping(value = "/{requestId}/park", consumes = "multipart/form-data")
    public ResponseEntity<ValetResponseDTO> parkVehicle(
            @PathVariable Long requestId,
            @RequestParam Long lotId,
            @RequestParam Long slotId,
            @RequestParam(value = "carImages", required = false) List<MultipartFile> carImages,
            @RequestParam(value = "batteryLevelAtParking", required = false) Integer batteryLevelAtParking) {
        return new ResponseEntity<>(
                valetService.parkVehicle(requestId, lotId, slotId, carImages, batteryLevelAtParking), HttpStatus.OK);
    }

    @PostMapping("/{requestId}/request-return")
    public ResponseEntity<ValetResponseDTO> requestVehicleBack(
            @PathVariable Long requestId) {
        return new ResponseEntity<>(valetService.initiateReturnConfirmation(requestId), HttpStatus.OK);
    }

    @PostMapping("/{requestId}/verify-dropoff")
    public ResponseEntity<ValetResponseDTO> verifyDropoff(
            @PathVariable Long requestId,
            @RequestParam String otp) {
        return new ResponseEntity<>(valetService.verifyDropoff(requestId, otp), HttpStatus.OK);
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<ValetResponseDTO> getRequestStatus(@PathVariable Long requestId) {
        return ResponseEntity.ok(valetService.getRequestById(requestId));
    }

    @GetMapping("/customer/{customerId}/active")
    public ResponseEntity<ValetResponseDTO> getActiveValetForCustomer(
            @PathVariable Long customerId) {
        ValetResponseDTO dto = valetService.getActiveValetForCustomer(customerId);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.noContent().build();
    }

    // ── NEW: serve a car image from MySQL ──────────────────────────────────
    /**
     * GET /api/valet/images/{imageId}
     *
     * Serves the raw image bytes of one car photo stored in MySQL.
     * Ownership is enforced: the JWT in the Authorization header must belong
     * to the customer who owns this image. Anyone else gets 403.
     *
     * The frontend displays images like this:
     *   <img src="/api/valet/images/101" />
     *   (with Authorization: Bearer <token> header set globally on the HTTP client)
     *
     * How the ownership check works:
     *   1. Spring Security already validated the JWT via JwtAuthFilter before
     *      this method runs. The email is in Authentication.getName().
     *   2. We look up the User by that email to get their DB id.
     *   3. We query valet_car_images to find whose customer owns image {imageId}.
     *   4. If those two IDs don't match → 403 Forbidden, no data returned.
     *
     * Note: this does NOT use a query param for the customer ID. The ID comes
     * from the validated JWT, so the caller cannot spoof it.
     */
    @GetMapping("/images/{imageId}")
    public ResponseEntity<byte[]> getCarImage(
            @PathVariable Long imageId,
            Authentication authentication) {

        // Step 1: who is making this request? (email from JWT, already validated)
        String email = authentication.getName();

        // Step 2: resolve email → user ID
        Long requestingUserId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();

        // Step 3: who actually owns this image?
        Long ownerCustomerId = valetCarImageRepository.findCustomerIdByImageId(imageId);

        // Step 4: ownership check — if IDs don't match, refuse to serve the image
        if (ownerCustomerId == null || !ownerCustomerId.equals(requestingUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Step 5: fetch and return the image bytes
        ValetCarImage image = valetCarImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        return ResponseEntity.ok()
                .header("Content-Type", image.getContentType())
                // Prevent browsers from caching car images (privacy)
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .body(image.getImageData());
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ValetResponseDTO>> getCustomerValetHistory(@PathVariable Long customerId) {
        return ResponseEntity.ok(valetService.getCustomerValetHistory(customerId));
    }

    @PostMapping("/{requestId}/confirm-return")
    public ResponseEntity<ValetResponseDTO> confirmReturn(
            @PathVariable Long requestId,
            @RequestParam String otp) {
        return new ResponseEntity<>(valetService.confirmReturn(requestId, otp), HttpStatus.OK);
    }

    @PutMapping("/{valetId}/status")
    public ResponseEntity<java.util.Map<String, Object>> updateValetStatus(
            @PathVariable Long valetId,
            @RequestParam boolean isAvailable) {

        if (isAvailable) {
            valetRepository.markAsFree(valetId);
        } else {
            valetRepository.markAsBusy(valetId);
        }

        return ResponseEntity.ok(java.util.Map.of(
                "message", "Status updated successfully",
                "isAvailable", isAvailable
        ));
    }
}