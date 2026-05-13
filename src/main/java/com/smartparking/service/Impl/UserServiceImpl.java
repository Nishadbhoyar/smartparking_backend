package com.smartparking.service.Impl;

import com.smartparking.dtos.request.UserRegistrationDTO;
import com.smartparking.dtos.response.UserResponseDTO;
import com.smartparking.entities.admins.CarOwner;
import com.smartparking.entities.admins.FleetAdmin;
import com.smartparking.entities.admins.ParkingLotAdmin;
import com.smartparking.entities.admins.SuperAdmin;
import com.smartparking.entities.nums.Role;
import com.smartparking.entities.users.Customer;
import com.smartparking.entities.users.User;
import com.smartparking.entities.valet.Valet;
import com.smartparking.exceptions.DuplicateResourceException;
import com.smartparking.exceptions.ResourceNotFoundException;
import com.smartparking.repositories.*;
import com.smartparking.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private PasswordEncoder            passwordEncoder;

    @Autowired
    private UserRepository             userRepository;

    @Autowired
    private ParkingLotAdminRepository  parkingLotAdminRepository;

    @Autowired
    private CustomerRepository         customerRepository;

    @Autowired
    private ValetRepository            valetRepository;

    @Autowired
    private SuperAdminRepository       superAdminRepository;

    @Autowired
    private CarOwnerRepository         carOwnerRepository;

    @Autowired
    FleetAdminRepository       fleetAdminRepository;

    // ── Public self-registration (only SUPER_ADMIN is blocked — created via SQL) ──
    @Override
    public UserResponseDTO registerUser(UserRegistrationDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException(
                    "Registration failed: Email " + dto.getEmail() + " is already in use.");
        }

        if (dto.getRole() == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException(
                    "Super Admin accounts are created by the platform administrator.");
        }


        return mapToResponseDTO(createUserByRole(dto));
    }

    // ── Admin-initiated creation (all roles allowed except SUPER_ADMIN) ──────
    @Override
    public UserResponseDTO registerUserByAdmin(UserRegistrationDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException(
                    "Email " + dto.getEmail() + " is already in use.");
        }
        return mapToResponseDTO(createUserByRole(dto));
    }

    // ── Shared core: build and save the correct subtype ─────────────────────
    private User createUserByRole(UserRegistrationDTO dto) {
        switch (dto.getRole()) {
            case PARKING_LOT_ADMIN: {
                ParkingLotAdmin admin = new ParkingLotAdmin();
                mapCommonFields(admin, dto);
                admin.setBusinessRegistrationNumber(dto.getBusinessRegistrationNumber());
                return parkingLotAdminRepository.save(admin);
            }
            case SUPER_ADMIN: {
                SuperAdmin superAdmin = new SuperAdmin();
                mapCommonFields(superAdmin, dto);
                return superAdminRepository.save(superAdmin);
            }
            case CAR_OWNER: {
                CarOwner carOwner = new CarOwner();
                mapCommonFields(carOwner, dto);
                carOwner.setAadhaarNumber(dto.getAadhaarNumber());
                return carOwnerRepository.save(carOwner);
            }
            case FLEET_ADMIN: {
                FleetAdmin fleetAdmin = new FleetAdmin();
                mapCommonFields(fleetAdmin, dto);
                fleetAdmin.setBusinessPhone(dto.getBusinessPhone());
                return fleetAdminRepository.save(fleetAdmin);
            }
            case CUSTOMER: {
                Customer customer = new Customer();
                mapCommonFields(customer, dto);
                customer.setDefaultLicensePlate(dto.getDefaultLicensePlate());
                return customerRepository.save(customer);
            }
            case VALET: {
                Valet valet = new Valet();
                mapCommonFields(valet, dto);
                valet.setDrivingLicenseNumber(dto.getDrivingLicenseNumber());
                valet.setAvailableNow(true);
                return valetRepository.save(valet);
            }
            default:
                throw new IllegalArgumentException("Invalid Role provided!");
        }
    }

    @Override
    public UserResponseDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user found with email: " + email));
        return mapToResponseDTO(user);
    }

    @Override
    public UserResponseDTO updateProfile(Long userId, Map<String, String> body) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (body.containsKey("name") && !body.get("name").isBlank()) {
            user.setName(body.get("name").trim());
        }
        if (body.containsKey("password") && body.get("password").length() >= 6) {
            user.setPassword(passwordEncoder.encode(body.get("password")));
        }
        return mapToResponseDTO(userRepository.save(user));
    }

    private void mapCommonFields(User user, UserRegistrationDTO dto) {
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(dto.getRole());
    }

    private UserResponseDTO mapToResponseDTO(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());

        if (user instanceof Customer) {
            dto.setDefaultLicensePlate(((Customer) user).getDefaultLicensePlate());
        } else if (user instanceof Valet) {
            dto.setDrivingLicenseNumber(((Valet) user).getDrivingLicenseNumber());
        }
        return dto;
    }
}