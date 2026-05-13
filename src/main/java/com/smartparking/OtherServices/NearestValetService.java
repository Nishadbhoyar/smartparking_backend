package com.smartparking.OtherServices;

import com.smartparking.entities.valet.Valet;
import com.smartparking.exceptions.ResourceNotFoundException;
import com.smartparking.repositories.ValetRepository;
import com.smartparking.utils.GeoUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class NearestValetService {

    // Only look for valets within 20 km of customer's location
    private static final double MAX_SEARCH_RADIUS_KM = 20.0;

    private final ValetRepository valetRepository;

    public NearestValetService(ValetRepository valetRepository) {
        this.valetRepository = valetRepository;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  CORE: Find nearest available valet to customer's pickup location
    // ─────────────────────────────────────────────────────────────────────

    /**
     * How it works:
     * 1. Get all valets where isAvailableNow=true AND lat/lon is set
     * 2. For each valet, calculate distance to customer using Haversine
     * 3. Filter to only valets within 20 km
     * 4. Return the closest one
     *
     * Uses your existing Valet entity fields:
     *   - valet.isAvailableNow()
     *   - valet.getCurrentLatitude()
     *   - valet.getCurrentLongitude()
     */
    public Valet findNearestValet(double pickupLat, double pickupLon) {

        List<Valet> availableValets = valetRepository.findAllAvailableValets();

        if (availableValets.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No valets are available right now. Please try again shortly."
            );
        }

        Optional<Valet> nearest = availableValets.stream()
                .filter(v ->
                        // Only valets within max search radius
                        GeoUtils.isWithinRadius(
                                pickupLat, pickupLon,
                                v.getCurrentLatitude(),
                                v.getCurrentLongitude(),
                                MAX_SEARCH_RADIUS_KM
                        )
                )
                .min(Comparator.comparingDouble(v ->
                        // Sort by distance — closest first
                        GeoUtils.calculateDistanceKm(
                                pickupLat, pickupLon,
                                v.getCurrentLatitude(),
                                v.getCurrentLongitude()
                        )
                ));

        return nearest.orElseThrow(() ->
                new ResourceNotFoundException(
                        "No valets available within " + (int) MAX_SEARCH_RADIUS_KM
                                + " km of your location. Try again later."
                )
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Get ETA for nearest valet (shown to customer before they book)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns: distance in km + estimated arrival time in minutes.
     * Shown to user as: "Valet is 2.3 km away, arrives in ~6 mins"
     */
    public ValetETA getNearestValetETA(double pickupLat, double pickupLon) {

        List<Valet> availableValets = valetRepository.findAllAvailableValets();

        return availableValets.stream()
                .filter(v -> v.getCurrentLatitude() != null && v.getCurrentLongitude() != null)
                .map(v -> {
                    double dist = GeoUtils.calculateDistanceKm(
                            pickupLat, pickupLon,
                            v.getCurrentLatitude(),
                            v.getCurrentLongitude()
                    );
                    return new ValetETA(v.getId(), dist, GeoUtils.estimatedMinutes(dist));
                })
                .filter(eta -> eta.distanceKm() <= MAX_SEARCH_RADIUS_KM)
                .min(Comparator.comparingDouble(ValetETA::distanceKm))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No valets available near your location."
                ));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Mark valet BUSY when assigned to a job
    //  Mark valet FREE when job is completed
    // ─────────────────────────────────────────────────────────────────────

    public void markValetBusy(Long valetId) {
        valetRepository.markAsBusy(valetId);
    }

    public void markValetFree(Long valetId) {
        valetRepository.markAsFree(valetId);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Valet app sends GPS ping every 5 seconds → update their location
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Called from ValetLocationController.
     * Uses the updateLocation() query in ValetRepository
     * to update the Valet's currentLatitude, currentLongitude, isAvailableNow.
     */
    // ─────────────────────────────────────────────────────────────────────
    //  Valet app sends GPS ping every 5 seconds → update their location
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void updateValetLocation(Long valetId, double lat, double lon, boolean available) {

        Valet valet = valetRepository.findById(valetId)
                .orElseThrow(() -> new ResourceNotFoundException("Valet not found"));

        // 1. ALWAYS update the coordinates, whether the valet is busy or free!
        valet.setCurrentLatitude(lat);
        valet.setCurrentLongitude(lon);

        // 2. DO NOT call valetRepository.updateLocation(...) or update isAvailableNow here.
        // The ping should only update GPS coordinates. Availability is handled by acceptJob/completeJob.

        valetRepository.save(valet);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helper: get a specific valet's distance to a point
    // ─────────────────────────────────────────────────────────────────────

    public double getValetDistanceTo(Valet valet, double lat, double lon) {
        if (valet.getCurrentLatitude() == null || valet.getCurrentLongitude() == null) {
            return Double.MAX_VALUE;
        }
        return GeoUtils.calculateDistanceKm(
                lat, lon,
                valet.getCurrentLatitude(),
                valet.getCurrentLongitude()
        );
    }

    // Simple record to return ETA data
    public record ValetETA(Long valetId, double distanceKm, int etaMinutes) {}
}