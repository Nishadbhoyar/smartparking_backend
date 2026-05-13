package com.smartparking.repositories;

import com.smartparking.entities.parking.Slot;
import com.smartparking.entities.nums.SlotStatus;
import com.smartparking.entities.nums.SlotType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {

    // Lock a specific slot by ID — used when the client sends an explicit slotId
    @Query(value = "SELECT * FROM slots WHERE id = :slotId " +
            "AND parking_lot_id = :lotId " +
            "AND status = :#{#status.name()} FOR UPDATE",
            nativeQuery = true)
    Optional<Slot> lockById(
            @Param("slotId") Long slotId,
            @Param("lotId") Long lotId,
            @Param("status") SlotStatus status);

    // Fallback: pick the cheapest available slot of the requested type
    @Query(value = "SELECT * FROM slots WHERE parking_lot_id = :lotId " +
            "AND status = :#{#status.name()} AND slot_type = :#{#type.name()} " +
            "ORDER BY id ASC LIMIT 1 FOR UPDATE",
            nativeQuery = true)
    Optional<Slot> lockFirstAvailable(
            @Param("lotId") Long lotId,
            @Param("status") SlotStatus status,
            @Param("type") SlotType type);

    List<Slot> findByParkingLotId(Long parkingLotId);

    // NEW: Checks if a slot name already exists in a specific lot!
    boolean existsBySlotNumberAndParkingLotId(String slotNumber, Long parkingLotId);

    // UPDATED: Now uses the Enum
    long countByParkingLotIdAndStatus(Long parkingLotId, SlotStatus status);

    // UPDATED: Now uses the Enum
    Optional<Slot> findFirstByParkingLotIdAndStatusAndSlotType(Long parkingLotId, SlotStatus status, SlotType slotType);

    // Counts all slots regardless of status
    long countByParkingLotId(Long parkingLotId);

    Optional<Slot> findFirstByParkingLotIdAndStatus(Long parkingLotId, SlotStatus status);

    Long countByStatus(SlotStatus status);

    long countByParkingLotIdAndSlotType(Long parkingLotId, com.smartparking.entities.nums.SlotType slotType);
}