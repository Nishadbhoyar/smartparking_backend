package com.smartparking.controller;

import com.smartparking.entities.admins.FleetAdmin;
import com.smartparking.entities.nums.CarRentalStatus;
import com.smartparking.entities.nums.RentalCarStatus;
import com.smartparking.entities.nums.VehicleType;
import com.smartparking.entities.rental.CarRentalBooking;
import com.smartparking.entities.rental.RentalCar;
import com.smartparking.entities.rental.RentalCarImage;
import com.smartparking.entities.rental.RentalCompany;
import com.smartparking.entities.users.Customer;
import com.smartparking.OtherServices.NotificationService;
import com.smartparking.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.smartparking.repositories.UserRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class RentalCarController {

    @Autowired
    private RentalCompanyRepository rentalCompanyRepository;

    @Autowired
    private FleetAdminRepository fleetAdminRepository;

    @Autowired
    private RentalCarRepository rentalCarRepository;

    @Autowired
    private CarOwnerRepository carOwnerRepository;

    @Autowired
    private CarRentalBookingRepository carRentalBookingRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RentalCarImageRepository rentalCarImageRepository;

    @Autowired
    private UserRepository userRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ═════════════════════════════════════════════════════════
    // COMPANY REGISTRATION & VERIFICATION
    // ═════════════════════════════════════════════════════════

    @PostMapping("/api/rental-company/register")
    @Transactional
    public ResponseEntity<?> registerCompany(@RequestBody Map<String, String> body) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // FIX: Use repository method instead of loading all admins
        FleetAdmin fleetAdmin = fleetAdminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Fleet admin not found"));

        Optional<RentalCompany> existing = rentalCompanyRepository.findByFleetAdminId(fleetAdmin.getId());
        if (existing.isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Company already registered. Status: " +
                            (existing.get().isPlatformVerified() ? "VERIFIED" : "PENDING")));
        }

        String companyName = body.get("companyName");
        if (companyName == null || companyName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "companyName is required"));
        }

        RentalCompany company = new RentalCompany();
        company.setCompanyName(companyName.trim());
        company.setRegistrationNumber(body.getOrDefault("registrationNumber", ""));
        company.setAddress(body.getOrDefault("address", ""));
        company.setCity(body.getOrDefault("city", ""));
        company.setContactEmail(body.getOrDefault("contactEmail", email));
        company.setContactPhone(body.getOrDefault("contactPhone", ""));
        company.setPlatformVerified(false);
        company.setFleetAdmin(fleetAdmin);

        RentalCompany saved = rentalCompanyRepository.save(company);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCompanyMap(saved));
    }

    @GetMapping("/api/rental-company/my")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyCompany() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // FIX: Use repository method instead of loading all admins
        FleetAdmin fleetAdmin = fleetAdminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Fleet admin not found"));

        return rentalCompanyRepository.findByFleetAdminId(fleetAdmin.getId())
                .map(c -> ResponseEntity.ok(toCompanyMap(c)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No company registered yet")));
    }

    @GetMapping("/api/super-admin/rental-companies")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getAllCompanies(
            @RequestParam(required = false) Boolean verified) {

        List<RentalCompany> companies = rentalCompanyRepository.findAll();

        if (verified != null) {
            companies = companies.stream()
                    .filter(c -> c.isPlatformVerified() == verified)
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(
                companies.stream().map(this::toCompanyMap).collect(Collectors.toList())
        );
    }

    @PutMapping("/api/super-admin/rental-companies/{id}/verify")
    @Transactional
    public ResponseEntity<?> verifyCompany(@PathVariable Long id) {
        RentalCompany company = rentalCompanyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        company.setPlatformVerified(true);
        rentalCompanyRepository.save(company);

        return ResponseEntity.ok(Map.of(
                "message", "Company '" + company.getCompanyName() + "' verified successfully",
                "company", toCompanyMap(company)
        ));
    }

    @PutMapping("/api/super-admin/rental-companies/{id}/reject")
    @Transactional
    public ResponseEntity<?> rejectCompany(@PathVariable Long id) {
        RentalCompany company = rentalCompanyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        company.setPlatformVerified(false);
        rentalCompanyRepository.save(company);

        return ResponseEntity.ok(Map.of(
                "message", "Company '" + company.getCompanyName() + "' rejected",
                "company", toCompanyMap(company)
        ));
    }

    @GetMapping("/api/rental-company/by-admin/{adminId}")
    public ResponseEntity<?> getCompanyByAdmin(@PathVariable Long adminId) {
        return rentalCompanyRepository.findByFleetAdminId(adminId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ═════════════════════════════════════════════════════════
    // CAR LISTING
    // ═════════════════════════════════════════════════════════

    @PostMapping("/api/rental-cars/owner/{ownerId}/list")
    public ResponseEntity<RentalCar> listCar(@PathVariable Long ownerId,
                                             @RequestBody RentalCar car) {
        var owner = carOwnerRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Car owner not found"));
        car.setCarOwner(owner);
        car.setStatus(RentalCarStatus.AVAILABLE);
        return new ResponseEntity<>(rentalCarRepository.save(car), HttpStatus.CREATED);
    }

    @PostMapping("/api/rental-cars/company/{companyId}/list")
    public ResponseEntity<?> listCompanyCar(@PathVariable Long companyId,
                                            @RequestBody RentalCar car) {
        RentalCompany company = rentalCompanyRepository.findById(companyId)
                .or(() -> rentalCompanyRepository.findByFleetAdminId(companyId))
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // FIX: Check if company is verified before allowing car listing
        if (!company.isPlatformVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Company must be verified by super admin before listing cars"));
        }

        car.setRentalCompany(company);
        car.setStatus(RentalCarStatus.AVAILABLE);
        return new ResponseEntity<>(rentalCarRepository.save(car), HttpStatus.CREATED);
    }

    @GetMapping("/api/rental-cars/owner/{ownerId}")
    public ResponseEntity<List<RentalCar>> getOwnerCars(@PathVariable Long ownerId) {
        return ResponseEntity.ok(rentalCarRepository.findByCarOwnerId(ownerId));
    }

    @GetMapping("/api/rental-cars/company/{companyId}")
    public ResponseEntity<List<RentalCar>> getCompanyFleet(@PathVariable Long companyId) {
        return ResponseEntity.ok(rentalCarRepository.findByRentalCompanyId(companyId));
    }

    @GetMapping("/api/rental-cars/nearby")
    public ResponseEntity<List<RentalCar>> getNearbyCars(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) VehicleType type) {

        List<RentalCar> cars = (type != null)
                ? rentalCarRepository.findNearbyAvailableCarsByType(lat, lng, type.name(), limit)
                : rentalCarRepository.findNearbyAvailableCars(lat, lng, limit);

        return ResponseEntity.ok(cars);
    }

    @PutMapping("/api/rental-cars/{carId}/status")
    public ResponseEntity<RentalCar> updateStatus(@PathVariable Long carId,
                                                  @RequestParam RentalCarStatus status) {
        RentalCar car = rentalCarRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        car.setStatus(status);
        return ResponseEntity.ok(rentalCarRepository.save(car));
    }

    // ═════════════════════════════════════════════════════════
    // BOOKING
    // ═════════════════════════════════════════════════════════

    @PostMapping("/api/rental-cars/{carId}/book")
    @Transactional
    public ResponseEntity<?> bookCar(@PathVariable Long carId,
                                     @RequestBody Map<String, Object> body) {

        if (body.get("customerId") == null ||
                body.get("startTime") == null ||
                body.get("endTime") == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "customerId, startTime and endTime are required"));
        }

        RentalCar car = rentalCarRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        if (car.getStatus() != RentalCarStatus.AVAILABLE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Car is not available for booking"));
        }

        Long customerId = Long.valueOf(body.get("customerId").toString());
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        LocalDateTime startTime = LocalDateTime.parse(body.get("startTime").toString());
        LocalDateTime endTime = LocalDateTime.parse(body.get("endTime").toString());

        if (!endTime.isAfter(startTime)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "End time must be after start time"));
        }

        long hours = Duration.between(startTime, endTime).toHours();
        long days = Math.max(1L, (long) Math.ceil(hours / 24.0));
        double total = (car.getDailyRate() != null ? car.getDailyRate() : 0) * days;
        double deposit = car.getSecurityDeposit() != null ? car.getSecurityDeposit() : 0;

        String code;
        do {
            code = "CR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (carRentalBookingRepository.findByBookingCode(code).isPresent());

        CarRentalBooking booking = new CarRentalBooking();
        booking.setBookingCode(code);
        booking.setCustomer(customer);
        booking.setRentalCar(car);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setTotalAmount(total);
        booking.setDepositAmount(deposit);
        booking.setStatus(CarRentalStatus.CONFIRMED);
        booking.setPickupOtp(String.format("%04d", SECURE_RANDOM.nextInt(10000)));
        booking.setReturnOtp(String.format("%04d", SECURE_RANDOM.nextInt(10000)));
        booking.setOverdueAlerted(false);

        car.setStatus(RentalCarStatus.RENTED);
        rentalCarRepository.save(car);

        CarRentalBooking saved = carRentalBookingRepository.save(booking);

        notificationService.notify(customerId,
                "Rental Confirmed ✅",
                "Booking " + saved.getBookingCode() + " for " + car.getMake()
                        + " " + (car.getModel() != null ? car.getModel() : "")
                        + " confirmed. Show your pickup OTP when collecting the car.",
                "RENTAL");

        if (car.getCarOwner() != null) {
            notificationService.notifyOwnerCarBooked(
                    car.getCarOwner().getId(),
                    saved.getBookingCode(),
                    car.getMake(),
                    customer.getName());
        } else if (car.getRentalCompany() != null && car.getRentalCompany().getFleetAdmin() != null) {
            notificationService.notifyFleetCarBooked(
                    car.getRentalCompany().getFleetAdmin().getId(),
                    saved.getBookingCode(),
                    car.getMake(),
                    customer.getName());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.ofEntries(
                Map.entry("id", saved.getId()),
                Map.entry("bookingCode", saved.getBookingCode()),
                Map.entry("carMake", car.getMake()),
                Map.entry("carModel", car.getModel() != null ? car.getModel() : ""),
                Map.entry("vehicleType", car.getVehicleType().name()),
                Map.entry("licensePlate", car.getLicensePlate()),
                Map.entry("startTime", saved.getStartTime().toString()),
                Map.entry("endTime", saved.getEndTime().toString()),
                Map.entry("totalAmount", saved.getTotalAmount()),
                Map.entry("depositAmount", saved.getDepositAmount()),
                Map.entry("status", saved.getStatus().name()),
                Map.entry("pickupOtp", saved.getPickupOtp())
        ));
    }

    // ═════════════════════════════════════════════════════════
    // OTP VERIFICATION
    // ═════════════════════════════════════════════════════════

    @PostMapping("/api/rental-cars/bookings/{bookingId}/verify-pickup")
    @Transactional
    public ResponseEntity<?> verifyPickup(@PathVariable Long bookingId,
                                          @RequestBody Map<String, String> body) {
        String otp = body.get("otp");
        if (otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP is required"));
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        CarRentalBooking booking = carRentalBookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // FIX: Verify caller owns this car
        RentalCar car = booking.getRentalCar();
        boolean isOwner = false;

        if (car.getCarOwner() != null) {
            isOwner = car.getCarOwner().getEmail().equals(email);
        } else if (car.getRentalCompany() != null) {
            FleetAdmin admin = car.getRentalCompany().getFleetAdmin();
            isOwner = admin != null && admin.getEmail().equals(email);
        }

        if (!isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You don't own this car"));
        }

        if (booking.getStatus() != CarRentalStatus.CONFIRMED) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Booking is not in CONFIRMED state. Current: " + booking.getStatus()));
        }

        if (!otp.equals(booking.getPickupOtp())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid pickup OTP"));
        }

        booking.setStatus(CarRentalStatus.ACTIVE);
        booking.setActualPickupTime(LocalDateTime.now());
        carRentalBookingRepository.save(booking);

        notificationService.notify(booking.getCustomer().getId(),
                "Rental Active",
                "You have picked up " + booking.getRentalCar().getMake()
                        + ". Return by " + booking.getEndTime().toLocalDate() + ". Safe driving!",
                "RENTAL");

        RentalCar activeCar = booking.getRentalCar();
        if (activeCar.getCarOwner() != null) {
            notificationService.notifyOwnerCarPickedUp(
                    activeCar.getCarOwner().getId(),
                    activeCar.getMake(),
                    booking.getCustomer().getName());
        } else if (activeCar.getRentalCompany() != null && activeCar.getRentalCompany().getFleetAdmin() != null) {
            notificationService.notifyFleetCarPickedUp(
                    activeCar.getRentalCompany().getFleetAdmin().getId(),
                    activeCar.getMake(),
                    booking.getCustomer().getName());
        }

        return ResponseEntity.ok(Map.of(
                "message", "Pickup verified. Car is now ACTIVE.",
                "bookingId", bookingId,
                "actualPickup", booking.getActualPickupTime().toString(),
                "returnOtp", booking.getReturnOtp()
        ));
    }

    @PostMapping("/api/rental-cars/bookings/{bookingId}/verify-return")
    @Transactional
    public ResponseEntity<?> verifyReturn(@PathVariable Long bookingId,
                                          @RequestBody Map<String, String> body) {
        String otp = body.get("otp");
        if (otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP is required"));
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        CarRentalBooking booking = carRentalBookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // FIX: Verify caller owns this car
        RentalCar car = booking.getRentalCar();
        boolean isOwner = false;

        if (car.getCarOwner() != null) {
            isOwner = car.getCarOwner().getEmail().equals(email);
        } else if (car.getRentalCompany() != null) {
            FleetAdmin admin = car.getRentalCompany().getFleetAdmin();
            isOwner = admin != null && admin.getEmail().equals(email);
        }

        if (!isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You don't own this car"));
        }

        if (booking.getStatus() != CarRentalStatus.ACTIVE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Booking is not ACTIVE. Current: " + booking.getStatus()));
        }

        if (!otp.equals(booking.getReturnOtp())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid return OTP"));
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bookedEnd = booking.getEndTime();
        boolean isEarlyReturn = now.isBefore(bookedEnd);

        double refundAmount = 0.0;
        if (isEarlyReturn && booking.getRentalCar().getDailyRate() != null) {
            long savedHours = Duration.between(now, bookedEnd).toHours();
            long savedDays = savedHours / 24;
            if (savedDays > 0) {
                refundAmount = savedDays * booking.getRentalCar().getDailyRate();
            }
        }

        booking.setStatus(CarRentalStatus.COMPLETED);
        booking.setActualReturnTime(now);
        booking.setCompletedAt(now);

        car.setStatus(RentalCarStatus.AVAILABLE);
        rentalCarRepository.save(car);
        carRentalBookingRepository.save(booking);

        String refundMsg = refundAmount > 0
                ? " Early return credit of Rs." + String.format("%.0f", refundAmount) + " will be processed."
                : "";
        notificationService.notify(booking.getCustomer().getId(),
                "Rental Completed",
                "Thanks for returning " + car.getMake() + "! Total: Rs."
                        + String.format("%.0f", booking.getTotalAmount()) + "." + refundMsg,
                "RENTAL");

        if (car.getCarOwner() != null) {
            notificationService.notifyOwnerCarReturned(
                    car.getCarOwner().getId(),
                    car.getMake(),
                    booking.getCustomer().getName(),
                    booking.getTotalAmount());
        } else if (car.getRentalCompany() != null && car.getRentalCompany().getFleetAdmin() != null) {
            notificationService.notifyFleetCarReturned(
                    car.getRentalCompany().getFleetAdmin().getId(),
                    car.getMake(),
                    booking.getCustomer().getName(),
                    booking.getTotalAmount());
        }

        return ResponseEntity.ok(Map.of(
                "message", "Return verified. Booking COMPLETED.",
                "bookingId", bookingId,
                "actualReturn", now.toString(),
                "bookedEndTime", bookedEnd.toString(),
                "earlyReturn", isEarlyReturn,
                "refundAmount", refundAmount,
                "refundNote", refundAmount > 0
                        ? "Refund ₹" + refundAmount + " to customer for unused days"
                        : "No refund applicable"
        ));
    }

    // ═════════════════════════════════════════════════════════
    // BOOKING EXTENSION
    // ═════════════════════════════════════════════════════════

    @PutMapping("/api/rental-cars/bookings/{bookingId}/extend")
    @Transactional
    public ResponseEntity<?> extendBooking(@PathVariable Long bookingId,
                                           @RequestBody Map<String, String> body) {
        String newEndTimeStr = body.get("newEndTime");
        if (newEndTimeStr == null || newEndTimeStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "newEndTime is required"));
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        CarRentalBooking booking = carRentalBookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // FIX: Verify customer owns this booking
        if (!booking.getCustomer().getEmail().equals(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You can only extend your own bookings"));
        }

        if (booking.getStatus() != CarRentalStatus.ACTIVE &&
                booking.getStatus() != CarRentalStatus.CONFIRMED) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Can only extend ACTIVE or CONFIRMED bookings. Current: " + booking.getStatus()));
        }

        LocalDateTime newEndTime = LocalDateTime.parse(newEndTimeStr);
        LocalDateTime oldEndTime = booking.getEndTime();

        if (!newEndTime.isAfter(oldEndTime)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "New end time must be after current end time: " + oldEndTime));
        }

        boolean hasConflict = carRentalBookingRepository.hasOverlappingBooking(
                booking.getRentalCar().getId(),
                bookingId,
                oldEndTime,
                newEndTime
        );

        if (hasConflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Cannot extend — another booking exists in that time window"));
        }

        long extraHours = Duration.between(oldEndTime, newEndTime).toHours();
        long extraDays = (long) Math.ceil(extraHours / 24.0);
        double extraCost = extraDays * (booking.getRentalCar().getDailyRate() != null
                ? booking.getRentalCar().getDailyRate() : 0);

        booking.setEndTime(newEndTime);
        booking.setTotalAmount(booking.getTotalAmount() + extraCost);
        carRentalBookingRepository.save(booking);

        notificationService.notify(booking.getCustomer().getId(),
                "Rental Extended",
                "Your rental is extended to " + newEndTime.toLocalDate()
                        + ". Extra charge: Rs." + String.format("%.0f", extraCost) + ".",
                "RENTAL");

        RentalCar extendedCar = booking.getRentalCar();
        if (extendedCar.getCarOwner() != null) {
            notificationService.notify(extendedCar.getCarOwner().getId(),
                    "Rental Extended",
                    booking.getCustomer().getName() + " extended their rental of " + extendedCar.getMake()
                            + " to " + newEndTime.toLocalDate() + ".",
                    "RENTAL");
        } else if (extendedCar.getRentalCompany() != null && extendedCar.getRentalCompany().getFleetAdmin() != null) {
            notificationService.notify(extendedCar.getRentalCompany().getFleetAdmin().getId(),
                    "Fleet Rental Extended",
                    booking.getCustomer().getName() + " extended " + extendedCar.getMake()
                            + " to " + newEndTime.toLocalDate() + ".",
                    "RENTAL");
        }

        return ResponseEntity.ok(Map.of(
                "message", "Booking extended successfully",
                "bookingId", bookingId,
                "oldEndTime", oldEndTime.toString(),
                "newEndTime", newEndTime.toString(),
                "extraHours", extraHours,
                "extraCost", extraCost,
                "newTotalAmount", booking.getTotalAmount()
        ));
    }

    // ═════════════════════════════════════════════════════════
    // DASHBOARDS & HISTORY
    // ═════════════════════════════════════════════════════════

    @GetMapping("/api/rental-cars/owner/{ownerId}/active-rentals")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getOwnerActiveRentals(@PathVariable Long ownerId) {
        List<RentalCar> cars = rentalCarRepository.findByCarOwnerId(ownerId);
        LocalDateTime now = LocalDateTime.now();

        List<Map<String, Object>> result = cars.stream()
                .flatMap(car -> carRentalBookingRepository
                        .findByRentalCarIdAndStatus(car.getId(), CarRentalStatus.ACTIVE).stream())
                .map(b -> {
                    RentalCar c = b.getRentalCar();
                    boolean overdue = now.isAfter(b.getEndTime());
                    long minutesOverdue = overdue
                            ? Duration.between(b.getEndTime(), now).toMinutes() : 0;

                    Map<String, Object> entry = new HashMap<>();
                    entry.put("bookingId", b.getId());
                    entry.put("bookingCode", b.getBookingCode());
                    entry.put("carMake", c.getMake());
                    entry.put("carModel", c.getModel() != null ? c.getModel() : "");
                    entry.put("vehicleType", c.getVehicleType().name());
                    entry.put("licensePlate", c.getLicensePlate());
                    entry.put("customerName", b.getCustomer().getName());
                    entry.put("startTime", b.getStartTime() != null ? b.getStartTime().toString() : "");
                    entry.put("endTime", b.getEndTime() != null ? b.getEndTime().toString() : "");
                    entry.put("actualPickupTime", b.getActualPickupTime() != null ? b.getActualPickupTime().toString() : "");
                    entry.put("status", b.getStatus().name());
                    entry.put("isOverdue", overdue);
                    entry.put("minutesOverdue", minutesOverdue);
                    entry.put("totalAmount", b.getTotalAmount() != null ? b.getTotalAmount() : 0);
                    return entry;
                })
                .sorted((a, b) -> Boolean.compare((Boolean) b.get("isOverdue"), (Boolean) a.get("isOverdue")))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/rental-cars/owner/{ownerId}/bookings")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getOwnerBookings(@PathVariable Long ownerId) {
        List<RentalCar> cars = rentalCarRepository.findByCarOwnerId(ownerId);
        List<Map<String, Object>> result = cars.stream()
                .flatMap(car -> carRentalBookingRepository
                        .findByRentalCarIdOrderByCreatedAtDesc(car.getId()).stream())
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(b -> {
                    RentalCar c = b.getRentalCar();
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", b.getId());
                    entry.put("bookingCode", b.getBookingCode());
                    entry.put("carMake", c.getMake());
                    entry.put("carModel", c.getModel() != null ? c.getModel() : "");
                    entry.put("vehicleType", c.getVehicleType().name());
                    entry.put("licensePlate", c.getLicensePlate());
                    entry.put("customerName", b.getCustomer().getName());
                    entry.put("startTime", b.getStartTime() != null ? b.getStartTime().toString() : "");
                    entry.put("endTime", b.getEndTime() != null ? b.getEndTime().toString() : "");
                    entry.put("actualPickupTime", b.getActualPickupTime() != null ? b.getActualPickupTime().toString() : "");
                    entry.put("actualReturnTime", b.getActualReturnTime() != null ? b.getActualReturnTime().toString() : "");
                    entry.put("totalAmount", b.getTotalAmount() != null ? b.getTotalAmount() : 0);
                    entry.put("status", b.getStatus().name());
                    entry.put("isOverdue", b.getStatus() == CarRentalStatus.ACTIVE && LocalDateTime.now().isAfter(b.getEndTime()));
                    return entry;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/rental-cars/company/{companyId}/bookings")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getCompanyBookings(@PathVariable Long companyId) {
        List<RentalCar> cars = rentalCarRepository.findByRentalCompanyId(companyId);
        List<Map<String, Object>> result = cars.stream()
                .flatMap(car -> carRentalBookingRepository
                        .findByRentalCarIdOrderByCreatedAtDesc(car.getId()).stream())
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(b -> {
                    RentalCar c = b.getRentalCar();
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", b.getId());
                    entry.put("bookingCode", b.getBookingCode());
                    entry.put("carMake", c.getMake());
                    entry.put("carModel", c.getModel() != null ? c.getModel() : "");
                    entry.put("vehicleType", c.getVehicleType().name());
                    entry.put("licensePlate", c.getLicensePlate());
                    entry.put("customerName", b.getCustomer().getName());
                    entry.put("startTime", b.getStartTime() != null ? b.getStartTime().toString() : "");
                    entry.put("endTime", b.getEndTime() != null ? b.getEndTime().toString() : "");
                    entry.put("actualPickupTime", b.getActualPickupTime() != null ? b.getActualPickupTime().toString() : "");
                    entry.put("actualReturnTime", b.getActualReturnTime() != null ? b.getActualReturnTime().toString() : "");
                    entry.put("totalAmount", b.getTotalAmount() != null ? b.getTotalAmount() : 0);
                    entry.put("status", b.getStatus().name());
                    return entry;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/rental-cars/customer/{customerId}/bookings")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getCustomerRentalBookings(
            @PathVariable Long customerId) {

        List<Map<String, Object>> result = carRentalBookingRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(b -> {
                    RentalCar c = b.getRentalCar();
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", b.getId());
                    entry.put("bookingCode", b.getBookingCode());
                    entry.put("carMake", c.getMake());
                    entry.put("carModel", c.getModel() != null ? c.getModel() : "");
                    entry.put("vehicleType", c.getVehicleType().name());
                    entry.put("licensePlate", c.getLicensePlate());
                    entry.put("startTime", b.getStartTime() != null ? b.getStartTime().toString() : "");
                    entry.put("endTime", b.getEndTime() != null ? b.getEndTime().toString() : "");
                    entry.put("totalAmount", b.getTotalAmount() != null ? b.getTotalAmount() : 0);
                    entry.put("status", b.getStatus().name());
                    entry.put("pickupOtp", b.getPickupOtp() != null ? b.getPickupOtp() : "");
                    // FIX: Only expose returnOtp when booking is ACTIVE (after pickup)
                    entry.put("returnOtp",
                            b.getStatus() == CarRentalStatus.ACTIVE && b.getReturnOtp() != null
                                    ? b.getReturnOtp()
                                    : "");
                    return entry;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ═════════════════════════════════════════════════════════
    // SCHEDULED TASKS
    // ═════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 900_000) // every 15 minutes
    @Transactional
    public void alertOverdueRentals() {
        List<CarRentalBooking> overdueBookings = carRentalBookingRepository
                .findByStatusAndEndTimeBeforeAndOverdueAlerted(
                        CarRentalStatus.ACTIVE,
                        LocalDateTime.now(),
                        false
                );

        for (CarRentalBooking booking : overdueBookings) {
            booking.setOverdueAlerted(true);
            carRentalBookingRepository.save(booking);

            notificationService.notify(booking.getCustomer().getId(),
                    "Rental Overdue",
                    "Your rental of " + booking.getRentalCar().getMake()
                            + " was due on " + booking.getEndTime().toLocalDate()
                            + ". Please return the car to avoid extra charges.",
                    "RENTAL");

            RentalCar overdueCar = booking.getRentalCar();
            if (overdueCar.getCarOwner() != null) {
                notificationService.notifyOwnerCarOverdue(
                        overdueCar.getCarOwner().getId(),
                        overdueCar.getMake(),
                        booking.getCustomer().getName());
            } else if (overdueCar.getRentalCompany() != null && overdueCar.getRentalCompany().getFleetAdmin() != null) {
                notificationService.notifyFleetCarOverdue(
                        overdueCar.getRentalCompany().getFleetAdmin().getId(),
                        overdueCar.getMake(),
                        booking.getCustomer().getName());
            }
        }
    }

    // ═════════════════════════════════════════════════════════
    // CAR IMAGES
    // ═════════════════════════════════════════════════════════

    /**
     * GET /api/rental-cars/{carId}/images
     * Returns a list of image IDs for a car.
     * Frontend uses these IDs to fetch individual images via /api/rental-cars/images/{id}.
     * Accessible by any authenticated user (customers browsing listings need to see images too).
     */
    @GetMapping("/api/rental-cars/{carId}/images")
    public ResponseEntity<List<Long>> getCarImageIds(@PathVariable Long carId) {
        if (!rentalCarRepository.existsById(carId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(rentalCarImageRepository.findIdsByCarId(carId));
    }

    /**
     * GET /api/rental-cars/images/{imageId}
     * Serves raw image bytes for one car photo.
     * Accessible by any authenticated user — car photos are not private
     * (customers viewing a rental listing need to see them).
     */
    @GetMapping("/api/rental-cars/images/{imageId}")
    public ResponseEntity<byte[]> getCarImage(@PathVariable Long imageId) {
        RentalCarImage image = rentalCarImageRepository.findById(imageId)
                .orElse(null);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Content-Type", image.getContentType())
                .header("Cache-Control", "max-age=3600") // images are safe to cache — not private
                .body(image.getImageData());
    }

    /**
     * POST /api/rental-cars/{carId}/images
     * Uploads a new photo for a car.
     * Only the car's owner (CarOwner) can upload images for their car.
     * Max 6 images per car enforced server-side.
     * Max file size is enforced via Spring's multipart config (spring.servlet.multipart.max-file-size).
     */
    @PostMapping("/api/rental-cars/{carId}/images")
    @Transactional
    public ResponseEntity<?> uploadCarImage(
            @PathVariable Long carId,
            @RequestParam("image") MultipartFile file) {

        // 1. Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/jpeg") &&
                        !contentType.equals("image/png") &&
                        !contentType.equals("image/webp"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Only JPG, PNG or WebP images are allowed"));
        }

        // 2. Load and verify car exists
        RentalCar car = rentalCarRepository.findById(carId)
                .orElse(null);
        if (car == null) {
            return ResponseEntity.notFound().build();
        }

        // 3. Ownership check — accept CarOwner OR FleetAdmin whose company owns the car
        String requestEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean isCarOwner    = car.getCarOwner() != null && car.getCarOwner().getEmail().equals(requestEmail);
        boolean isFleetAdmin  = car.getRentalCompany() != null &&
                car.getRentalCompany().getFleetAdmin() != null &&
                car.getRentalCompany().getFleetAdmin().getEmail().equals(requestEmail);

        if (!isCarOwner && !isFleetAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You can only upload images for your own cars"));
        }

        // 4. Enforce max 6 images per car
        long existingCount = rentalCarImageRepository.countByRentalCarId(carId);
        if (existingCount >= 6) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Maximum 6 images per car. Delete an existing image first."));
        }

        // 5. Save image bytes to DB
        try {
            RentalCarImage image = new RentalCarImage();
            image.setRentalCar(car);
            image.setImageData(file.getBytes());
            image.setContentType(contentType);
            RentalCarImage saved = rentalCarImageRepository.save(image);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("imageId", saved.getId(), "message", "Image uploaded successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to save image: " + e.getMessage()));
        }
    }

    /**
     * DELETE /api/rental-cars/images/{imageId}
     * Deletes a car image. Only the car's owner can delete their own images.
     */
    @DeleteMapping("/api/rental-cars/images/{imageId}")
    @Transactional
    public ResponseEntity<?> deleteCarImage(@PathVariable Long imageId) {
        RentalCarImage image = rentalCarImageRepository.findById(imageId)
                .orElse(null);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }

        // Ownership check — accept CarOwner OR FleetAdmin whose company owns the car
        String requestEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        RentalCar car = image.getRentalCar();
        boolean isCarOwner   = car.getCarOwner() != null && car.getCarOwner().getEmail().equals(requestEmail);
        boolean isFleetAdmin = car.getRentalCompany() != null &&
                car.getRentalCompany().getFleetAdmin() != null &&
                car.getRentalCompany().getFleetAdmin().getEmail().equals(requestEmail);

        if (!isCarOwner && !isFleetAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You can only delete images for your own cars"));
        }

        rentalCarImageRepository.delete(image);
        return ResponseEntity.ok(Map.of("message", "Image deleted"));
    }

    // ═════════════════════════════════════════════════════════
    // HELPERS (original section below)
    // ═════════════════════════════════════════════════════════

    private Map<String, Object> toCompanyMap(RentalCompany c) {
        return Map.ofEntries(
                Map.entry("id", c.getId()),
                Map.entry("companyName", c.getCompanyName()),
                Map.entry("registrationNumber", c.getRegistrationNumber() != null ? c.getRegistrationNumber() : ""),
                Map.entry("address", c.getAddress() != null ? c.getAddress() : ""),
                Map.entry("city", c.getCity() != null ? c.getCity() : ""),
                Map.entry("contactEmail", c.getContactEmail() != null ? c.getContactEmail() : ""),
                Map.entry("contactPhone", c.getContactPhone() != null ? c.getContactPhone() : ""),
                Map.entry("platformVerified", c.isPlatformVerified()),
                Map.entry("fleetAdminId", c.getFleetAdmin().getId()),
                Map.entry("fleetAdminName", c.getFleetAdmin().getName())
        );
    }
}