package com.smartparking.entities.nums;

public enum ValetStatus {
    REQUESTED,       // ← ADD THIS
    PENDING,
    ACCEPTED,
    PICKED_UP,       // ← ADD THIS
    IN_PROGRESS,
    PARKED,
    RETURN_CONFIRM_PENDING,
    RETURN_REQUESTED,
    COMPLETED,
    CANCELLED
}