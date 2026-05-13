package com.smartparking.entities.nums;

public enum PaymentStatus {
    INITIATED,   // Order created at Cashfree, awaiting user action
    SUCCESS,     // Payment confirmed via webhook
    FAILED,      // Payment failed or expired
    REFUNDED     // Refund processed
}