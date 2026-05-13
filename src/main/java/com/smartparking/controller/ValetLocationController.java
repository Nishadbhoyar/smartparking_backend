package com.smartparking.controller;

import com.smartparking.OtherServices.FareCalculationService;
import com.smartparking.OtherServices.NearestLotService;
import com.smartparking.OtherServices.NearestValetService;
import com.smartparking.dtos.response.FareResponseDTO;
import com.smartparking.entities.valet.ValetFare;
import com.smartparking.exceptions.ResourceNotFoundException;
import com.smartparking.repositories.ValetRequestRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Location: com/smartparking/controller/ValetLocationController.java
 *
 * Handles 5 things:
 *  1. Valet app sends GPS ping every 5 seconds  → POST /api/valet/location
 *  2. Customer checks ETA before booking        → GET  /api/valet/eta
 *  3. Customer sees fare estimate               → GET  /api/valet/fare-estimate
 *  4. Customer sees final bill after job        → GET  /api/valet/fare/{requestId}
 *  5. Customer map polls valet's live location  → GET  /api/valet/{requestId}/valet-location  ← NEW
 */
@RestController
@RequestMapping("/api/valet")
public class ValetLocationController {

    private final NearestValetService    nearestValetService;
    private final NearestLotService      nearestLotService;
    private final FareCalculationService fareCalculationService;
    private final ValetRequestRepository valetRequestRepository; // NEW

    public ValetLocationController(NearestValetService nearestValetService,
                                   NearestLotService nearestLotService,
                                   FareCalculationService fareCalculationService,
                                   ValetRequestRepository valetRequestRepository) { // NEW
        this.nearestValetService    = nearestValetService;
        this.nearestLotService      = nearestLotService;
        this.fareCalculationService = fareCalculationService;
        this.valetRequestRepository = valetRequestRepository; // NEW
    }

    // ─────────────────────────────────────────────────────────────────────
    //  1. Valet sends GPS location every 5 seconds
    //
    //  POST /api/valet/location
    //  Body: { "valetId": 1, "latitude": 18.5204, "longitude": 73.8567, "available": true }
    // ─────────────────────────────────────────────────────────────────────
    @PostMapping("/location")
    public ResponseEntity<Map<String, String>> updateLocation(
            @RequestBody Map<String, Object> body) {

        Long    valetId   = Long.valueOf(body.get("valetId").toString());
        double  lat       = Double.parseDouble(body.get("latitude").toString());
        double  lon       = Double.parseDouble(body.get("longitude").toString());
        boolean available = Boolean.parseBoolean(body.get("available").toString());

        nearestValetService.updateValetLocation(valetId, lat, lon, available);

        return ResponseEntity.ok(Map.of("status", "location updated"));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  2. Customer checks ETA before booking
    //
    //  GET /api/valet/eta?lat=18.5204&lon=73.8567
    //  Returns: nearest valet ETA + nearest lot info
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/eta")
    public ResponseEntity<Map<String, Object>> getETA(
            @RequestParam double lat,
            @RequestParam double lon) {

        NearestValetService.ValetETA       valetETA  = nearestValetService.getNearestValetETA(lat, lon);
        NearestLotService.NearestLotResult lotResult = nearestLotService.findNearestAvailableLot(lat, lon);

        return ResponseEntity.ok(Map.of(
                "valetEtaMinutes",  valetETA.etaMinutes(),
                "valetDistanceKm",  valetETA.distanceKm(),
                "nearestLotName",   lotResult.parkingLot().getName(),
                "nearestLotDistKm", lotResult.distanceKm(),
                "nearestLotEtaMin", lotResult.etaMinutes()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  3. Customer sees fare estimate BEFORE booking
    //
    //  GET /api/valet/fare-estimate?lat=18.5204&lon=73.8567
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/fare-estimate")
    public ResponseEntity<FareResponseDTO> getFareEstimate(
            @RequestParam double lat,
            @RequestParam double lon) {

        try {
            NearestValetService.ValetETA       valetETA  = nearestValetService.getNearestValetETA(lat, lon);
            NearestLotService.NearestLotResult lotResult = nearestLotService.findNearestAvailableLot(lat, lon);

            double pickupDist  = valetETA.distanceKm();
            double parkingDist = lotResult.distanceKm();
            double returnDist  = parkingDist;
            double totalDist   = pickupDist + parkingDist + returnDist;

            double distanceFare = totalDist * 12.0;
            double parkingFare  = 3.0 * 30.0;
            double total        = 50.0 + distanceFare + parkingFare;

            return ResponseEntity.ok(FareResponseDTO.builder()
                    .pickupDistanceKm(round(pickupDist))
                    .parkingDistanceKm(round(parkingDist))
                    .returnDistanceKm(round(returnDist))
                    .totalDistanceKm(round(totalDist))
                    .baseFare(50.0)
                    .distanceFare(round(distanceFare))
                    .parkingFare(parkingFare)
                    .totalFare(round(total))
                    .hoursParked(3.0)
                    .isEstimate(true)
                    .isSurge(false)
                    .surgeMultiplier(1.0)
                    .valetEtaMinutes(valetETA.etaMinutes())
                    .nearestLotName(lotResult.parkingLot().getName())
                    .nearestLotDistanceKm(round(parkingDist))
                    .paymentStatus("ESTIMATE")
                    .build());

        } catch (Exception e) {
            // No valets / lots in DB yet — return a placeholder estimate so the
            // booking form still renders. The user can still request a valet.
            return ResponseEntity.ok(FareResponseDTO.builder()
                    .baseFare(50.0)
                    .distanceFare(0.0)
                    .parkingFare(90.0)
                    .totalFare(140.0)
                    .totalDistanceKm(0.0)
                    .hoursParked(3.0)
                    .isEstimate(true)
                    .isSurge(false)
                    .surgeMultiplier(1.0)
                    .valetEtaMinutes(15)
                    .nearestLotName("Locating nearest lot...")
                    .nearestLotDistanceKm(0.0)
                    .paymentStatus("ESTIMATE")
                    .build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  4. Get final fare after job is complete
    //
    //  GET /api/valet/fare/{requestId}
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/fare/{requestId}")
    public ResponseEntity<FareResponseDTO> getFinalFare(
            @PathVariable Long requestId) {

        ValetFare fare = fareCalculationService.finalizeFare(requestId);

        FareResponseDTO response = FareResponseDTO.builder()
                .fareId(fare.getId())
                .valetRequestId(requestId)
                .pickupDistanceKm(fare.getPickupDistanceKm())
                .parkingDistanceKm(fare.getParkingDistanceKm())
                .returnDistanceKm(fare.getReturnDistanceKm())
                .totalDistanceKm(
                        fare.getPickupDistanceKm() +
                                fare.getParkingDistanceKm() +
                                fare.getReturnDistanceKm()
                )
                .baseFare(fare.getBaseFare())
                .distanceFare(fare.getDistanceFare())
                .parkingFare(fare.getParkingFare())
                .surgeFare(fare.getSurgeFare())
                .totalFare(fare.getTotalFare())
                .hoursParked(fare.getHoursParked())
                .isEstimate(false)
                .isSurge(fare.isSurge())
                .surgeMultiplier(fare.getSurgeMultiplier())
                .paymentStatus(fare.getPaymentStatus().name())
                .build();

        return ResponseEntity.ok(response);
    }

    /// ─────────────────────────────────────────────────────────────────────
    //  5. NEW — Customer map polls valet's live location during active job
    // ─────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true) // <-- ADD THIS LINE
    @GetMapping("/{requestId}/valet-location")
    public ResponseEntity<Map<String, Object>> getValetLiveLocation(
            @PathVariable Long requestId) {

        // 1. Load the booking — 404 if it doesn't exist
        var request = valetRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Valet request not found: " + requestId));

        // 2. A valet is only assigned once they accept the job.
        var valet = request.getValet();
        if (valet == null) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("message", "No valet assigned yet"));
        }

        // 3. Valet may have accepted but not sent a GPS ping yet.
        // Because of @Transactional, the database session is still open here!
        Double lat = valet.getCurrentLatitude();
        Double lon = valet.getCurrentLongitude();
        if (lat == null || lon == null) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("message", "Valet location not available yet"));
        }

        return ResponseEntity.ok(Map.of(
                "valetId",   valet.getId(),
                "latitude",  lat,
                "longitude", lon
        ));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────
    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}