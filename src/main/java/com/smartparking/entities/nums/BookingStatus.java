package com.smartparking.entities.nums;

public enum BookingStatus {
    PENDING,    // Booked but payment not yet done
    PAID,       // Payment confirmed — user has NOT physically arrived yet
    ACTIVE,     // User has scanned their entry code and is physically parked
    COMPLETED,  // User has checked out
    CANCELLED
}