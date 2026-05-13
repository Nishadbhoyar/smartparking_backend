package com.smartparking.entities.valet;

import com.smartparking.entities.nums.ValetStatus;
import com.smartparking.entities.nums.VehicleType;
import com.smartparking.entities.parking.ParkingLot;
import com.smartparking.entities.parking.Slot;
import com.smartparking.entities.users.Customer;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@ToString(of = {"id", "status", "carPlateNo"})
@EqualsAndHashCode(of = "id")
@Table(name = "valet_requests")
public class ValetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    private String customerName;
    private String mobileNo;
    private String carPlateNo;

    // Where the customer is standing (for valet pickup)
    private double pickupLatitude;
    private double pickupLongitude;

    // NEW: Where the valet actually parked the car.
    // Populated when parkVehicle() is called. Used by the customer to
    // see their car's location on the map while it is in PARKED state.
    @Column(name = "parked_latitude")
    private Double parkedLatitude;

    @Column(name = "parked_longitude")
    private Double parkedLongitude;

    @Column(nullable = false)
    private String pickupOtp;

    @Column(nullable = false)
    private String dropoffOtp;

    @Enumerated(EnumType.STRING)
    private ValetStatus status = ValetStatus.REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valet_id")
    private Valet valet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_lot_id")
    private ParkingLot parkingLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private Slot slot;

    @OneToMany(mappedBy = "valetRequest", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<ValetCarImage> carImages = new java.util.ArrayList<>();

    // EV Bike support
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    private Integer batteryLevelAtPickup;
    private Integer batteryLevelAtParking;

    // Return confirmation OTP (2-step return flow)
    private String returnConfirmOtp;
    private LocalDateTime returnConfirmOtpExpiry;

    private LocalDateTime requestedAt;
    private LocalDateTime parkedAt;
    private LocalDateTime completedAt;
}