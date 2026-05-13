package com.smartparking.service.Impl;

import com.smartparking.dtos.request.BulkSlotRequestDTO;
import com.smartparking.dtos.request.SlotRequestDTO;
import com.smartparking.dtos.response.SlotResponseDTO;
import com.smartparking.entities.nums.SlotStatus;
import com.smartparking.entities.nums.SlotType;
import com.smartparking.entities.parking.ParkingLot;
import com.smartparking.entities.parking.Slot;
import com.smartparking.exceptions.DuplicateResourceException;
import com.smartparking.exceptions.ResourceNotFoundException;
import com.smartparking.repositories.ParkingLotRepository;
import com.smartparking.repositories.SlotRepository;
import com.smartparking.service.SlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SlotServiceImpl implements SlotService {

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Override
    @Transactional
    public SlotResponseDTO createSlot(SlotRequestDTO requestDTO) {
        Long effectiveLotId = requestDTO.getEffectiveLotId();
        if (effectiveLotId == null) {
            throw new IllegalArgumentException("lotId (or parkingLotId) must not be null in request body.");
        }

        ParkingLot lot = parkingLotRepository.findById(effectiveLotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cannot create slot: Parking Lot with ID " + effectiveLotId + " not found."));

        String slotName = requestDTO.getEffectiveSlotNumber();
        if (slotName == null || slotName.isBlank()) {
            long count = slotRepository.countByParkingLotId(lot.getId());
            slotName = requestDTO.getEffectiveZone() + "-" + (count + 1);
        }

        if (slotRepository.existsBySlotNumberAndParkingLotId(slotName, lot.getId())) {
            throw new DuplicateResourceException(
                    "A slot named '" + slotName + "' already exists in this parking lot!");
        }

        Slot slot = new Slot();
        slot.setSlotNumber(slotName);
        slot.setZone(requestDTO.getEffectiveZone());
        slot.setSlotType(requestDTO.getEffectiveSlotType());
        slot.setStatus(SlotStatus.AVAILABLE);
        slot.setParkingLot(lot);
        slot.setHourlyRate(requestDTO.getHourlyRate());

        return mapToResponseDTO(slotRepository.save(slot));
    }

    @Override
    @Transactional(readOnly = true) // FIX #6: lazy parkingLot.getId() needs open session
    public List<SlotResponseDTO> getSlotsByLot(Long lotId) {
        return slotRepository.findByParkingLotId(lotId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SlotResponseDTO updateSlotStatus(Long slotId, SlotStatus newStatus) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + slotId));
        slot.setStatus(newStatus);
        return mapToResponseDTO(slotRepository.save(slot));
    }

    @Override
    @Transactional
    public void deleteSlot(Long slotId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + slotId));
        slotRepository.delete(slot);
    }

    @Override
    @Transactional
    public String bulkGenerateSlots(BulkSlotRequestDTO request) {
        Long lotId = request.getEffectiveLotId();
        if (lotId == null) {
            throw new IllegalArgumentException(
                    "lotId (or parkingLotId) must not be null. " +
                            "Send { \"lotId\": <id>, ... } in the request body.");
        }

        ParkingLot lot = parkingLotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parking lot not found with ID: " + lotId));

        List<Slot> slotsToSave = new ArrayList<>();

        // --- NEW FRONTEND FORMAT (Single Type) ---
        if (request.getVehicleType() != null && request.getCount() != null && request.getCount() > 0) {
            SlotType  type   = request.getVehicleType();
            int       count  = request.getCount();
            String    floor  = request.getFloor() != null ? request.getFloor() : "G";
            String    prefix = request.getNamePrefix() != null ? request.getNamePrefix() : typePrefix(type);
            double    rate   = request.getDefaultHourlyRate();

            long existing = slotRepository.countByParkingLotIdAndSlotType(lot.getId(), type);

            for (long i = existing + 1; i <= existing + count; i++) {
                String slotName = prefix + i;
                if (!slotRepository.existsBySlotNumberAndParkingLotId(slotName, lot.getId())) {
                    slotsToSave.add(buildSlot(lot, slotName, type, floor, rate));
                }
            }

            slotRepository.saveAll(slotsToSave);
            return String.format(
                    "Successfully generated %d %s slots on floor %s for lot: %s",
                    slotsToSave.size(), type, floor, lot.getName());
        }

        // --- LEGACY/MULTI-TYPE FORMAT ---
        int regularCount = nvl(request.getRegularCount());
        int evCount      = nvl(request.getEvCount());
        int hvCount      = nvl(request.getHeavyVehicleCount());
        int bikeCount    = nvl(request.getBikeCount());

        // 🚨 GET SPECIFIC RATES (Fallback to defaultHourlyRate if null)
        double defRate   = request.getDefaultHourlyRate();
        double regRate   = request.getRegularRate() != null ? request.getRegularRate() : defRate;
        double evRate    = request.getEvRate() != null ? request.getEvRate() : defRate;
        double hvRate    = request.getHeavyVehicleRate() != null ? request.getHeavyVehicleRate() : defRate;
        double bikeRate  = request.getBikeRate() != null ? request.getBikeRate() : defRate;

        long existingReg  = slotRepository.countByParkingLotIdAndSlotType(lot.getId(), SlotType.REGULAR);
        long existingEv   = slotRepository.countByParkingLotIdAndSlotType(lot.getId(), SlotType.EV_CHARGING);
        long existingHv   = slotRepository.countByParkingLotIdAndSlotType(lot.getId(), SlotType.HEAVY_VEHICLE);
        long existingBike = slotRepository.countByParkingLotIdAndSlotType(lot.getId(), SlotType.BIKE);

        // 🚨 PASS THE SPECIFIC RATES INTO buildSlot()
        for (long i = existingReg + 1;  i <= existingReg  + regularCount; i++)
            slotsToSave.add(buildSlot(lot, "REG-"  + i, SlotType.REGULAR,       "G", regRate));

        for (long i = existingEv + 1;   i <= existingEv   + evCount;      i++)
            slotsToSave.add(buildSlot(lot, "EV-"   + i, SlotType.EV_CHARGING,   "G", evRate));

        for (long i = existingHv + 1;   i <= existingHv   + hvCount;      i++)
            slotsToSave.add(buildSlot(lot, "HV-"   + i, SlotType.HEAVY_VEHICLE, "G", hvRate));

        for (long i = existingBike + 1; i <= existingBike + bikeCount;    i++)
            slotsToSave.add(buildSlot(lot, "BIKE-" + i, SlotType.BIKE,          "G", bikeRate));

        slotRepository.saveAll(slotsToSave);
        return String.format(
                "Successfully generated %d slots for lot: %s [REG:%d | EV:%d | HV:%d | BIKE:%d]",
                slotsToSave.size(), lot.getName(), regularCount, evCount, hvCount, bikeCount);
    }

    private int nvl(Integer val) { return val != null ? val : 0; }

    private String typePrefix(SlotType type) {
        return switch (type) {
            case REGULAR         -> "C";
            case BIKE            -> "B";
            case EV_CHARGING     -> "EV";
            case TRUCK,
                 HEAVY_VEHICLE   -> "HV";
            case BUS             -> "BUS";
            case SUV             -> "SUV";
            case DISABLED        -> "HC";
            case VIP             -> "VIP";
        };
    }

    private Slot buildSlot(ParkingLot lot, String slotNumber,
                           SlotType type, String floor, double hourlyRate) {
        Slot slot = new Slot();
        slot.setParkingLot(lot);
        slot.setSlotNumber(slotNumber);
        slot.setSlotType(type);
        slot.setZone(floor);
        slot.setStatus(SlotStatus.AVAILABLE);
        slot.setHourlyRate(hourlyRate);
        return slot;
    }

    private SlotResponseDTO mapToResponseDTO(Slot slot) {
        SlotResponseDTO dto = new SlotResponseDTO();
        dto.setId(slot.getId());
        dto.setSlotNumber(slot.getSlotNumber());
        dto.setName(slot.getSlotNumber());
        dto.setZone(slot.getZone());
        dto.setFloor(slot.getZone());
        dto.setSlotType(slot.getSlotType());
        dto.setType(slot.getSlotType());
        dto.setStatus(slot.getStatus());
        dto.setParkingLotId(slot.getParkingLot().getId());
        dto.setHourlyRate(slot.getHourlyRate());
        return dto;
    }
}