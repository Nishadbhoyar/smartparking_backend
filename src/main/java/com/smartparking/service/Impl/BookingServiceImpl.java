package com.smartparking.service.Impl;

import com.smartparking.OtherServices.SlotWebSocketService;
import com.smartparking.dtos.request.BookingRequestDTO;
import com.smartparking.dtos.response.BookingResponseDTO;
import com.smartparking.entities.Booking;
import com.smartparking.entities.nums.BookingStatus;
import com.smartparking.entities.nums.SlotStatus;
import com.smartparking.entities.nums.SlotType;
import com.smartparking.entities.parking.ParkingLot;
import com.smartparking.entities.parking.Slot;
import com.smartparking.entities.users.Customer;
import com.smartparking.exceptions.ResourceNotFoundException;
import com.smartparking.exceptions.UnauthorizedAccessException;
import com.smartparking.repositories.BookingRepository;
import com.smartparking.repositories.CustomerRepository;
import com.smartparking.repositories.ParkingLotRepository;
import com.smartparking.repositories.SlotRepository;
import com.smartparking.repositories.PromoUsageRepository;
import com.smartparking.repositories.PromoCodeRepository;
import com.smartparking.OtherServices.NotificationService;
import com.smartparking.service.BookingService;
import com.smartparking.service.PromoService;
import com.smartparking.dtos.request.ApplyPromoRequestDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingServiceImpl implements BookingService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private SlotWebSocketService slotWebSocketService;

    @Autowired
    private PromoService promoService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PromoUsageRepository promoUsageRepository;

    @Autowired
    private PromoCodeRepository promoCodeRepository;



    @Value("${smartparking.valet.base-fee:50.0}")
    private double valetBaseFee;

    @Override
    @Transactional(timeout = 10) // FIX C-1: 10s cap — prevents a stalled lock from hanging the thread forever
    public BookingResponseDTO createBooking(BookingRequestDTO requestDTO) {
        Customer customer = customerRepository.findById(requestDTO.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found!"));

        ParkingLot lot = parkingLotRepository.findById(requestDTO.getParkingLotId())
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found!"));

        // FIX #9: null/blank check before valueOf — prevents NPE
        if (requestDTO.getSlotType() == null || requestDTO.getSlotType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotType must not be null or empty.");
        }
        SlotType requestedType = SlotType.valueOf(requestDTO.getSlotType().toUpperCase());

        // Resolve which slot to book.
        Slot availableSlot;
        if (requestDTO.getSlotId() != null) {
            availableSlot = slotRepository.lockById(
                    requestDTO.getSlotId(),
                    lot.getId(),
                    SlotStatus.AVAILABLE
            ).orElseGet(() ->
                    // Slot was taken between the time the user saw it and now — fall back
                    slotRepository.lockFirstAvailable(
                            lot.getId(),
                            SlotStatus.AVAILABLE,
                            requestedType
                    ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Sorry, this lot is completely full for " + requestedType + " slots!"))
            );
        } else {
            availableSlot = slotRepository.lockFirstAvailable(
                    lot.getId(),
                    SlotStatus.AVAILABLE,
                    requestedType
            ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sorry, this lot is completely full for " + requestedType + " slots!"));
        }

        Booking booking = new Booking();
        String bookingCode;
        do {
            bookingCode = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (bookingRepository.existsByBookingCode(bookingCode));

        // C-3 FIX: Never trust the price sent by the client.
        long requestedHours = Math.max(1L, Duration.between(
                requestDTO.getEntryTime().toInstant(),
                requestDTO.getExitTime().toInstant()).toHours());
        double serverCalculatedAmount = availableSlot.getHourlyRate() * requestedHours;

        // C-4 FIX: Apply promo on the backend atomically
        if (requestDTO.getPromoCode() != null && !requestDTO.getPromoCode().isBlank()) {
            ApplyPromoRequestDTO promoReq = new ApplyPromoRequestDTO();
            promoReq.setCode(requestDTO.getPromoCode().trim().toUpperCase());
            promoReq.setBookingAmount(serverCalculatedAmount);
            promoReq.setCustomerId(requestDTO.getCustomerId());
            double discount = promoService.applyPromo(promoReq).getDiscountAmount();
            serverCalculatedAmount = Math.max(0, serverCalculatedAmount - discount);
            booking.setPromoCode(requestDTO.getPromoCode().trim().toUpperCase());
        }

        booking.setBookingCode(bookingCode);
        booking.setCustomer(customer);
        booking.setParkingLot(lot);
        booking.setSlot(availableSlot);
        booking.setEntryTime(LocalDateTime.ofInstant(requestDTO.getEntryTime().toInstant(), ZoneId.systemDefault()));
        booking.setExitTime(LocalDateTime.ofInstant(requestDTO.getExitTime().toInstant(), ZoneId.systemDefault()));
        booking.setTotalAmount(serverCalculatedAmount);   // server value, not client value
        booking.setStatus(BookingStatus.PENDING);

        if (requestDTO.isValetBooking()) {
            booking.setPickupOtp(String.format("%04d", SECURE_RANDOM.nextInt(10000)));
            booking.setDropoffOtp(String.format("%04d", SECURE_RANDOM.nextInt(10000)));
        }

        availableSlot.setStatus(SlotStatus.OCCUPIED);
        slotRepository.save(availableSlot);
        slotWebSocketService.broadcastSlotUpdate(availableSlot);

        Booking savedBooking = bookingRepository.save(booking);
        notificationService.notifyBookingConfirmed(
                customer.getId(),
                savedBooking.getBookingCode(),
                savedBooking.getParkingLot().getName());
        notificationService.notifyAdminNewBooking(
                savedBooking.getParkingLot().getParkingLotAdmin().getId(),
                savedBooking.getBookingCode(),
                savedBooking.getSlot().getSlotNumber());
        return mapToResponseDTO(savedBooking);
    }

    @Override
    @Transactional(readOnly = true) // FIX #2a: lazy customer/lot/slot need open session
    public List<BookingResponseDTO> getBookingsByCustomer(Long customerId) {
        return bookingRepository.findByCustomerIdOrderByEntryTimeDesc(customerId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BookingResponseDTO verifyEntryCode(String bookingCode, Long parkingLotId) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid Booking Code"));

        if (!booking.getParkingLot().getId().equals(parkingLotId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Security Alert: This ticket is for a different parking lot!");
        }

        if (booking.getStatus() == BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment not yet confirmed. Please complete payment before entering.");
        }

        if (booking.getStatus() != BookingStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This ticket has already been used or is invalid. Current status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.ACTIVE);
        Booking checkedIn = bookingRepository.save(booking);

        notificationService.notifyBookingCheckedIn(
                checkedIn.getCustomer().getId(),
                checkedIn.getParkingLot().getName());
        return mapToResponseDTO(checkedIn);
    }

    @Override
    @Transactional
    public BookingResponseDTO checkoutBooking(String bookingCode) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not Found!"));

        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vehicle is not currently checked in! Status is: " + booking.getStatus());
        }

        LocalDateTime exitTime  = LocalDateTime.now();
        LocalDateTime entryTime = booking.getEntryTime();

        long minutesParked = Duration.between(entryTime, exitTime).toMinutes();
        long hoursParked = Math.max(1L, (long) Math.ceil(minutesParked / 60.0));

        Slot slot = booking.getSlot();
        double baseCost = slot.getHourlyRate() * hoursParked;

        double valetFee = (booking.getPickupOtp() != null) ? valetBaseFee : 0.0;

        booking.setTotalAmount(baseCost + valetFee);
        booking.setExitTime(exitTime);
        booking.setStatus(BookingStatus.COMPLETED);

        slot.setStatus(SlotStatus.AVAILABLE);
        slotRepository.save(slot);
        slotWebSocketService.broadcastSlotUpdate(slot);

        Booking completedBooking = bookingRepository.save(booking);
        notificationService.notifyBookingCompleted(
                completedBooking.getCustomer().getId(),
                completedBooking.getTotalAmount());
        notificationService.notifyAdminCheckout(
                completedBooking.getParkingLot().getParkingLotAdmin().getId(),
                completedBooking.getBookingCode(),
                completedBooking.getTotalAmount());
        return mapToResponseDTO(completedBooking);
    }

    @Override
    @Transactional(readOnly = true) // FIX #2b: lazy customer/lot/slot need open session
    public List<BookingResponseDTO> getBookingsByLot(Long lotId) {
        return bookingRepository.findByParkingLotIdOrderByEntryTimeDesc(lotId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDTO> getBookingsByAdmin(Long adminId) {
        List<Long> lotIds = parkingLotRepository.findByParkingLotAdminId(adminId)
                .stream()
                .map(ParkingLot::getId)
                .collect(Collectors.toList());
        if (lotIds.isEmpty()) return List.of();
        return bookingRepository.findTop10ByParkingLotIdInOrderByEntryTimeDesc(lotIds)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private BookingResponseDTO mapToResponseDTO(Booking booking) {
        BookingResponseDTO dto = new BookingResponseDTO();
        dto.setId(booking.getId());
        dto.setBookingCode(booking.getBookingCode());
        dto.setCustomerName(booking.getCustomer().getName());
        dto.setParkingLotId(booking.getParkingLot().getId());
        dto.setParkingLotName(booking.getParkingLot().getName());
        dto.setSlotNumber(booking.getSlot().getSlotNumber());
        dto.setEntryTime(booking.getEntryTime());
        dto.setExitTime(booking.getExitTime());
        dto.setTotalAmount(booking.getTotalAmount());
        dto.setStatus(booking.getStatus());
        return dto;
    }

    @Override
    @Transactional
    public BookingResponseDTO cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel a completed booking.");
        }

        String callerEmail = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        if (!booking.getCustomer().getEmail().equals(callerEmail)) {
            throw new UnauthorizedAccessException("You are not authorized to cancel this booking.");
        }

        // Free the slot if active
        if (
                booking.getStatus() == BookingStatus.ACTIVE
                        || booking.getStatus() == BookingStatus.PENDING
                        || booking.getStatus() == BookingStatus.PAID
        ) {
            Slot slot = booking.getSlot();
            slot.setStatus(SlotStatus.AVAILABLE);
            slotRepository.save(slot);
            slotWebSocketService.broadcastSlotUpdate(slot);
        }

        // Revert promo code usage
        if (booking.getPromoCode() != null) {
            promoUsageRepository.deleteByPromoCodeCodeAndCustomerId(
                    booking.getPromoCode(),
                    booking.getCustomer().getId()
            );

            promoCodeRepository.findByCode(booking.getPromoCode())
                    .ifPresent(promo -> {
                        promo.setUsedCount(Math.max(0, promo.getUsedCount() - 1));
                        promoCodeRepository.save(promo);
                    });
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking cancelled = bookingRepository.save(booking);

        notificationService.notifyBookingCancelled(
                cancelled.getCustomer().getId(),
                cancelled.getBookingCode());

        notificationService.notifyAdminBookingCancelled(
                cancelled.getParkingLot().getParkingLotAdmin().getId(),
                cancelled.getBookingCode(),
                cancelled.getSlot().getSlotNumber());

        return mapToResponseDTO(cancelled);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponseDTO getBookingById(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return mapToResponseDTO(booking);
    }
}