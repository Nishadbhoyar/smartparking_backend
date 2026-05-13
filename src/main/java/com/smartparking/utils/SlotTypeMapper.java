package com.smartparking.utils;

import com.smartparking.entities.nums.SlotType;
import com.smartparking.entities.nums.VehicleType;

import java.util.Set;

/**
 * Explicit mapping between a customer's VehicleType and the SlotTypes
 * that are compatible with that vehicle.
 *
 * Rules:
 *  - BIKE / SCOOTER  → BIKE slots only
 *  - TRUCK / VAN     → HEAVY_VEHICLE slots only
 *  - SUV             → SUV or REGULAR slots
 *  - AUTO            → REGULAR slots
 *  - CAR             → REGULAR slots (and SUV as a fallback)
 *
 * Used when filtering available slots for a booking search.
 */
public class SlotTypeMapper {

    private SlotTypeMapper() {}

    /**
     * Returns the set of SlotTypes that are compatible with the given VehicleType.
     * A slot query should use: WHERE slot_type IN (:compatibleTypes).
     */
    public static Set<SlotType> compatibleSlots(VehicleType vehicleType) {
        return switch (vehicleType) {
            case BIKE, SCOOTER, EV_BIKE -> Set.of(SlotType.BIKE, SlotType.EV_CHARGING);
            case TRUCK         -> Set.of(SlotType.HEAVY_VEHICLE, SlotType.TRUCK);
            case VAN           -> Set.of(SlotType.HEAVY_VEHICLE, SlotType.TRUCK, SlotType.BUS);
            case SUV           -> Set.of(SlotType.SUV, SlotType.REGULAR);
            case AUTO, CAR     -> Set.of(SlotType.REGULAR, SlotType.SUV);
        };
    }

    /**
     * Returns the best default SlotType to create when generating slots
     * for a given VehicleType. Used by the bulk slot generator.
     */
    public static SlotType defaultSlotType(VehicleType vehicleType) {
        return switch (vehicleType) {
            case BIKE, SCOOTER, EV_BIKE -> SlotType.BIKE;
            case TRUCK, VAN    -> SlotType.HEAVY_VEHICLE;
            case SUV           -> SlotType.SUV;
            case AUTO, CAR     -> SlotType.REGULAR;
        };
    }
}