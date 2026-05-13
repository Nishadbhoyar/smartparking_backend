package com.smartparking.controller;

import com.smartparking.dtos.request.UserRegistrationDTO;
import com.smartparking.dtos.response.PlatformDashboardResponseDTO;
import com.smartparking.dtos.response.UserResponseDTO;
import com.smartparking.entities.admins.CarOwner;
import com.smartparking.entities.admins.FleetAdmin;
import com.smartparking.entities.admins.ParkingLotAdmin;
import com.smartparking.entities.nums.Role;
import com.smartparking.entities.rental.RentalCompany;
import com.smartparking.entities.users.User;
import com.smartparking.repositories.*;
import com.smartparking.service.AnalyticsService;
import com.smartparking.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminController {

    @Autowired private ParkingLotAdminRepository parkingLotAdminRepository;
    @Autowired private CarOwnerRepository         carOwnerRepository;
    @Autowired private FleetAdminRepository        fleetAdminRepository;
    @Autowired private RentalCompanyRepository     rentalCompanyRepository;
    @Autowired private UserRepository              userRepository;
    @Autowired private AnalyticsService            analyticsService;
    @Autowired private UserService                 userService;  // ← ADD THIS

    // ── Platform dashboard ──────────────────────────────────────────────────
    @GetMapping("/platform-stats")
    public ResponseEntity<PlatformDashboardResponseDTO> getPlatformStats() {
        return ResponseEntity.ok(analyticsService.getPlatformDashboard());
    }

    // ── All users ───────────────────────────────────────────────────────────
    @GetMapping("/all-users")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(
                userRepository.findAll().stream()
                        .map(u -> {
                            UserResponseDTO dto = new UserResponseDTO();
                            dto.setId(u.getId());
                            dto.setName(u.getName());
                            dto.setEmail(u.getEmail());
                            dto.setRole(u.getRole());
                            return dto;
                        })
                        .toList()
        );
    }

    // ── Create any user (VALET, PARKING_LOT_ADMIN, etc.) ───────────────────
    // POST /api/super-admin/users/create
    // Body: { name, email, password, role, drivingLicenseNumber? (for VALET),
    //         businessRegistrationNumber? (for PARKING_LOT_ADMIN) }
    @PostMapping("/users/create")
    public ResponseEntity<?> createUser(@RequestBody UserRegistrationDTO dto) {
        // Reject SUPER_ADMIN creation even by super admin — must be DB-seeded
        if (dto.getRole() == Role.SUPER_ADMIN) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "SUPER_ADMIN accounts must be created directly in the database."));
        }
        try {
            UserResponseDTO created = userService.registerUserByAdmin(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    // ── Parking lot admins ──────────────────────────────────────────────────
    @GetMapping("/parking-lot-admins")
    public ResponseEntity<List<UserResponseDTO>> getAllParkingLotAdmins() {
        return ResponseEntity.ok(
                parkingLotAdminRepository.findAll().stream()
                        .map(a -> {
                            UserResponseDTO dto = new UserResponseDTO();
                            dto.setId(a.getId());
                            dto.setName(a.getName());
                            dto.setEmail(a.getEmail());
                            dto.setRole(a.getRole());
                            dto.setVerified(a.isVerified());   // ← ADD THIS LINE
                            return dto;
                        })
                        .toList()
        );
    }

    @PutMapping("/parking-lot-admins/{id}/verify")
    public ResponseEntity<UserResponseDTO> verifyParkingLotAdmin(@PathVariable Long id) {
        ParkingLotAdmin admin = parkingLotAdminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parking lot admin not found"));
        admin.setVerified(true);
        ParkingLotAdmin saved = parkingLotAdminRepository.save(admin);

        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(saved.getId());
        dto.setName(saved.getName());
        dto.setEmail(saved.getEmail());
        dto.setRole(saved.getRole());
        dto.setVerified(saved.isVerified());
        return ResponseEntity.ok(dto);
    }

    // ── Car owners ──────────────────────────────────────────────────────────
    @GetMapping("/car-owners")
    public ResponseEntity<List<UserResponseDTO>> getAllCarOwners() {
        return ResponseEntity.ok(
                carOwnerRepository.findAll().stream()
                        .map(a -> {
                            UserResponseDTO dto = new UserResponseDTO();
                            dto.setId(a.getId());
                            dto.setName(a.getName());
                            dto.setEmail(a.getEmail());
                            dto.setRole(a.getRole());
                            dto.setVerified(a.isVerified());
                            return dto;
                        })
                        .toList()
        );
    }

    @PutMapping("/car-owners/{id}/verify")
    public ResponseEntity<CarOwner> verifyCarOwner(@PathVariable Long id) {
        CarOwner owner = carOwnerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Car owner not found"));
        owner.setVerified(true);
        return ResponseEntity.ok(carOwnerRepository.save(owner));
    }

    // ── Fleet admins ────────────────────────────────────────────────────────
    @GetMapping("/fleet-admins")
    public ResponseEntity<List<UserResponseDTO>> getAllFleetAdmins() {
        return ResponseEntity.ok(
                fleetAdminRepository.findAll().stream()
                        .map(a -> {
                            UserResponseDTO dto = new UserResponseDTO();
                            dto.setId(a.getId());
                            dto.setName(a.getName());
                            dto.setEmail(a.getEmail());
                            dto.setRole(a.getRole());
                            dto.setVerified(a.isVerified());
                            return dto;
                        })
                        .toList()
        );
    }

    @PutMapping("/fleet-admins/{id}/verify")
    public ResponseEntity<FleetAdmin> verifyFleetAdmin(@PathVariable Long id) {
        FleetAdmin fleetAdmin = fleetAdminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fleet admin not found"));

        // FIXED
        fleetAdmin.setVerified(true);
        FleetAdmin savedAdmin = fleetAdminRepository.save(fleetAdmin);

        // Also verify the associated company
        RentalCompany company = rentalCompanyRepository.findByFleetAdmin(fleetAdmin);
        if (company != null) {
            company.setPlatformVerified(true);
            rentalCompanyRepository.save(company);
        }

        return ResponseEntity.ok(savedAdmin);
    }
}