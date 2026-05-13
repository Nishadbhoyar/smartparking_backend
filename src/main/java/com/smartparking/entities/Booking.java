package com.smartparking.entities;

import com.smartparking.entities.featuresentites.PromoCode;
import com.smartparking.entities.nums.BookingStatus;
import com.smartparking.entities.parking.ParkingLot;
import com.smartparking.entities.parking.Slot;
import com.smartparking.entities.users.Customer;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@ToString(of = {"id", "bookingCode", "status"})
@EqualsAndHashCode(of = "id")
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private BookingStatus status = BookingStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_lot_id", nullable = false)
    private ParkingLot parkingLot;

    private String dropoffOtp;
    private String pickupOtp;

    /**
     * Stores the promo code string used at booking time.
     * Used by cancelBooking() to reverse the promo usage.
     * Kept as String to avoid cascading changes through the service layer.
     */
    private String promoCode;

    /**
     * FK to PromoCode — the full entity reference.
     * Available for richer queries if needed later.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code_id", nullable = true)
    private PromoCode appliedPromo;

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBookingCode() { return bookingCode; }
    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public Slot getSlot() { return slot; }
    public void setSlot(Slot slot) { this.slot = slot; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public ParkingLot getParkingLot() { return parkingLot; }
    public void setParkingLot(ParkingLot parkingLot) { this.parkingLot = parkingLot; }

    public String getDropoffOtp() { return dropoffOtp; }
    public void setDropoffOtp(String dropoffOtp) { this.dropoffOtp = dropoffOtp; }

    public String getPickupOtp() { return pickupOtp; }
    public void setPickupOtp(String pickupOtp) { this.pickupOtp = pickupOtp; }

    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }

    public PromoCode getAppliedPromo() { return appliedPromo; }
    public void setAppliedPromo(PromoCode appliedPromo) { this.appliedPromo = appliedPromo; }
}