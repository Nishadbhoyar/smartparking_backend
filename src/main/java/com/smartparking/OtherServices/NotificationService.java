package com.smartparking.OtherServices;

import com.smartparking.entities.NotificationHistory;
import com.smartparking.repositories.NotificationHistoryRepository;
import com.smartparking.websocket.NotificationWsMessage;
import jakarta.transaction.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * NotificationService — three responsibilities:
 *   1. Persist every notification to notification_history (powers in-app centre)
 *   2. Push over WebSocket to /topic/notifications/{userId} for instant delivery
 *   3. Named helpers called from service/controller layer
 *
 * Failure in any step is caught and logged — never throws, never breaks
 * the main business flow.
 */
@Service
public class NotificationService {

    private final NotificationHistoryRepository historyRepository;
    private final SimpMessagingTemplate         messagingTemplate;

    public NotificationService(NotificationHistoryRepository historyRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.historyRepository = historyRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void clearAllNotifications(Long userId) {
        historyRepository.deleteByUserId(userId);
    }

    // ── Core ────────────────────────────────────────────────────────────────
    public void notify(Long userId, String title, String body, String type) {
        try {
            // 1. Persist — notification centre reads from this table
            NotificationHistory record = new NotificationHistory();
            record.setUserId(userId);
            record.setTitle(title);
            record.setBody(body);
            record.setType(type);
            record.setRead(false);
            NotificationHistory saved = historyRepository.save(record);

            // 2. Push over WebSocket — instant delivery, no polling delay
            // Frontend subscribes to /topic/notifications/{userId}
            NotificationWsMessage wsMsg = new NotificationWsMessage(
                    saved.getId(),
                    saved.getTitle(),
                    saved.getBody(),
                    saved.getType(),
                    saved.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            messagingTemplate.convertAndSend("/topic/notifications/" + userId, wsMsg);

        } catch (Exception e) {
            System.err.println("[Notification] Failed for userId=" + userId + ": " + e.getMessage());
        }
    }

    // ── Valet flow ──────────────────────────────────────────────────────────

    public void notifyValetRequested(Long customerId) {
        notify(customerId,
                "Valet Request Submitted",
                "We're finding the nearest available valet for you. Hang tight!",
                "VALET");
    }

    public void notifyValetAccepted(Long customerId, String valetName) {
        notify(customerId,
                "Valet Assigned!",
                valetName + " has accepted your request and is on the way to your location.",
                "VALET");
    }

    public void notifyCarPickedUp(Long customerId, String valetName) {
        notify(customerId,
                "Keys Handed Over",
                valetName + " has collected your car and is driving to the parking lot.",
                "VALET");
    }

    public void notifyCarParked(Long customerId, String lotName, String slotNumber) {
        notify(customerId,
                "Car Safely Parked",
                "Your car is parked at " + lotName + ", Slot " + slotNumber
                        + ". Request it back whenever you are ready.",
                "VALET");
    }

    public void notifyReturnRequested(Long valetId, String customerName) {
        notify(valetId,
                "Return Requested",
                customerName + " wants their car back. Please head to the parking lot now.",
                "VALET");
    }

    /**
     * Sent to the customer when they tap "Request Car Back".
     * They receive a push notification showing the OTP they need to
     * confirm on screen (double safety in case they close the app).
     *
     * @param customerId  the customer's user ID
     * @param otp         the 4-digit return confirmation OTP
     * @param windowMins  time window in minutes before the OTP expires
     */
    public void notifyReturnConfirmOtp(Long customerId, String otp, int windowMins) {
        notify(customerId,
                "Confirm Your Return Request",
                "Your confirmation OTP is " + otp + ". Enter it in the app within "
                        + windowMins + " minutes to request your car back.",
                "VALET");
    }


    public void notifyJobCompleted(Long customerId, String valetName) {
        notify(customerId,
                "Car Returned!",
                valetName + " has returned your car. Thank you for using our valet service.",
                "VALET");
    }

    // ── Valet new job broadcasting ──────────────────────────────────────────

    public void notifyValetNewJobAvailable(Long valetId, String customerName) {
        notify(valetId,
                "New Job Available!",
                "A new valet request from " + customerName
                        + " is available. Open the app to accept it!",
                "VALET");
    }

    public void notifyValetJobTaken(Long valetId) {
        notify(valetId,
                "Job Taken",
                "The request you were viewing was accepted by another valet. Check for new jobs.",
                "VALET");
    }

    // ── Booking flow ────────────────────────────────────────────────────────

    public void notifyBookingConfirmed(Long customerId, String bookingCode, String lotName) {
        notify(customerId,
                "Booking Confirmed",
                "Your booking " + bookingCode + " at " + lotName
                        + " is confirmed. Show the code at entry.",
                "BOOKING");
    }

    public void notifyBookingCheckedIn(Long customerId, String lotName) {
        notify(customerId,
                "Checked In",
                "Welcome to " + lotName + "! Your booking is now active.",
                "BOOKING");
    }

    public void notifyBookingCompleted(Long customerId, double amount) {
        notify(customerId,
                "Checkout Complete",
                "Your parking session ended. Total charged: Rs."
                        + String.format("%.0f", amount) + ". Safe travels!",
                "BOOKING");
    }

    public void notifyBookingCancelled(Long customerId, String bookingCode) {
        notify(customerId,
                "Booking Cancelled",
                "Your booking " + bookingCode
                        + " has been cancelled. Any refund will be processed shortly.",
                "BOOKING");
    }

    // ── Parking Lot Admin ───────────────────────────────────────────────────

    public void notifyAdminNewBooking(Long adminId, String bookingCode, String slotNumber) {
        notify(adminId,
                "New Booking Received",
                "Booking " + bookingCode + " placed for Slot " + slotNumber + " at your lot.",
                "BOOKING");
    }

    public void notifyAdminBookingCancelled(Long adminId, String bookingCode, String slotNumber) {
        notify(adminId,
                "Booking Cancelled",
                "Booking " + bookingCode + " for Slot " + slotNumber
                        + " was cancelled. Slot is now free.",
                "BOOKING");
    }

    public void notifyAdminCheckout(Long adminId, String bookingCode, double amount) {
        notify(adminId,
                "Customer Checked Out",
                "Booking " + bookingCode + " completed. Revenue: Rs."
                        + String.format("%.0f", amount) + ".",
                "BOOKING");
    }

    // ── Car Owner ───────────────────────────────────────────────────────────

    public void notifyOwnerCarBooked(Long ownerId, String bookingCode,
                                     String carMake, String customerName) {
        notify(ownerId,
                "Your Car Was Booked!",
                customerName + " booked your " + carMake + ". Booking: " + bookingCode + ".",
                "RENTAL");
    }

    public void notifyOwnerCarPickedUp(Long ownerId, String carMake, String customerName) {
        notify(ownerId,
                "Car Picked Up",
                customerName + " collected your " + carMake + ". Rental is now active.",
                "RENTAL");
    }

    public void notifyOwnerCarReturned(Long ownerId, String carMake,
                                       String customerName, double amount) {
        notify(ownerId,
                "Car Returned",
                customerName + " returned your " + carMake
                        + ". Earned: Rs." + String.format("%.0f", amount) + ".",
                "RENTAL");
    }

    public void notifyOwnerCarOverdue(Long ownerId, String carMake, String customerName) {
        notify(ownerId,
                "Car Not Returned - Overdue",
                customerName + " has not returned your " + carMake + ". Please contact them.",
                "RENTAL");
    }

    // ── Fleet Admin ─────────────────────────────────────────────────────────

    public void notifyFleetCarBooked(Long fleetAdminId, String bookingCode,
                                     String carMake, String customerName) {
        notify(fleetAdminId,
                "Fleet Car Booked",
                customerName + " booked fleet car " + carMake + ". Code: " + bookingCode + ".",
                "RENTAL");
    }

    public void notifyFleetCarPickedUp(Long fleetAdminId, String carMake, String customerName) {
        notify(fleetAdminId,
                "Fleet Car Picked Up",
                customerName + " collected " + carMake + ". Rental active.",
                "RENTAL");
    }

    public void notifyFleetCarReturned(Long fleetAdminId, String carMake,
                                       String customerName, double amount) {
        notify(fleetAdminId,
                "Fleet Car Returned",
                customerName + " returned " + carMake
                        + ". Earned: Rs." + String.format("%.0f", amount) + ".",
                "RENTAL");
    }

    public void notifyFleetCarOverdue(Long fleetAdminId, String carMake, String customerName) {
        notify(fleetAdminId,
                "Fleet Car Overdue",
                customerName + " has not returned " + carMake + ". Follow up immediately.",
                "RENTAL");
    }
}