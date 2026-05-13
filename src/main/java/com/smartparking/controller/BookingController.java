package com.smartparking.controller;

import com.smartparking.dtos.request.BookingRequestDTO;
import com.smartparking.dtos.response.BookingResponseDTO;
import com.smartparking.entities.users.Customer;
import com.smartparking.exceptions.UnauthorizedAccessException;
import com.smartparking.repositories.CustomerRepository;
import com.smartparking.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private CustomerRepository customerRepository;

    @PostMapping("/reserve")
    public ResponseEntity<BookingResponseDTO> reserveSlot(@RequestBody BookingRequestDTO requestDTO) {
        return new ResponseEntity<>(bookingService.createBooking(requestDTO), HttpStatus.CREATED);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponseDTO> getBookingById(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.getBookingById(bookingId));
    }

    // FIX #4: ownership check — customer can only view own bookings
    // OLD getCustomerBookings() removed — duplicate mapping on same URL
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<BookingResponseDTO>> getBookingsByCustomer(
            @PathVariable Long customerId,
            Authentication auth) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        if (!auth.getName().equals(customer.getEmail())) {
            throw new UnauthorizedAccessException("You can only view your own bookings.");
        }

        return ResponseEntity.ok(bookingService.getBookingsByCustomer(customerId));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<BookingResponseDTO> verifyEntryCode(
            @RequestParam String code,
            @RequestParam Long lotId) {
        return new ResponseEntity<>(bookingService.verifyEntryCode(code, lotId), HttpStatus.OK);
    }

    @PostMapping("/checkout")
    public ResponseEntity<BookingResponseDTO> checkoutBooking(@RequestParam String code) {
        return new ResponseEntity<>(bookingService.checkoutBooking(code), HttpStatus.OK);
    }

    @GetMapping("/lot/{lotId}")
    public ResponseEntity<List<BookingResponseDTO>> getLotBookings(@PathVariable Long lotId) {
        return new ResponseEntity<>(bookingService.getBookingsByLot(lotId), HttpStatus.OK);
    }

    @GetMapping("/lot-admin/{adminId}")
    public ResponseEntity<List<BookingResponseDTO>> getBookingsByAdmin(@PathVariable Long adminId) {
        return new ResponseEntity<>(bookingService.getBookingsByAdmin(adminId), HttpStatus.OK);
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponseDTO> cancelBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.cancelBooking(bookingId));
    }

}