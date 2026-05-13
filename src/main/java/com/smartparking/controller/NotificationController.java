package com.smartparking.controller;

import com.smartparking.OtherServices.NotificationService;
import com.smartparking.entities.DeviceToken;
import com.smartparking.entities.NotificationHistory;
import com.smartparking.repositories.DeviceTokenRepository;
import com.smartparking.repositories.NotificationHistoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService           notificationService;
    private final DeviceTokenRepository         deviceTokenRepository;
    private final NotificationHistoryRepository historyRepository;

    public NotificationController(NotificationService notificationService,
                                  DeviceTokenRepository deviceTokenRepository,
                                  NotificationHistoryRepository historyRepository) {
        this.notificationService   = notificationService;
        this.deviceTokenRepository = deviceTokenRepository;
        this.historyRepository     = historyRepository;
    }

    // POST /api/notifications/register-token
    @PostMapping("/register-token")
    public ResponseEntity<String> registerToken(@RequestBody Map<String, String> body) {
        Long userId   = Long.valueOf(body.get("userId"));
        String token  = body.get("token");
        String device = body.getOrDefault("deviceType", "UNKNOWN");

        if (!deviceTokenRepository.existsByUserIdAndToken(userId, token)) {
            DeviceToken dt = new DeviceToken();
            dt.setUserId(userId);
            dt.setToken(token);
            dt.setDeviceType(device);
            deviceTokenRepository.save(dt);
        }
        return ResponseEntity.ok("Device token registered for user: " + userId);
    }

    // POST /api/notifications/test
    // FIX: old endpoint called sendPushNotification() which no longer exists.
    // NotificationService was rewritten to use notify(userId, title, body, type)
    // which saves to DB and pushes over WebSocket. Updated to match.
    @PostMapping("/test")
    public ResponseEntity<String> testNotification(@RequestBody Map<String, String> body) {
        Long userId = Long.valueOf(body.getOrDefault("userId", "0"));
        notificationService.notify(
                userId,
                body.getOrDefault("title", "Test"),
                body.getOrDefault("body", "Test notification"),
                "SYSTEM"
        );
        return ResponseEntity.ok("Notification sent!");
    }

    // GET /api/notifications/history/{userId}
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<NotificationHistory>> getHistory(@PathVariable Long userId) {
        return ResponseEntity.ok(historyRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    // GET /api/notifications/unread-count/{userId}
    @GetMapping("/unread-count/{userId}")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable Long userId) {
        long count = historyRepository.countByUserIdAndReadFalse(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // PUT /api/notifications/mark-all-read/{userId}
    @PutMapping("/mark-all-read/{userId}")
    public ResponseEntity<String> markAllRead(@PathVariable Long userId) {
        List<NotificationHistory> unread = historyRepository
                .findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> !n.isRead())
                .toList();
        unread.forEach(n -> n.setRead(true));
        historyRepository.saveAll(unread);
        return ResponseEntity.ok("Marked " + unread.size() + " notifications as read");
    }

    // PUT /api/notifications/{notifId}/read
    @PutMapping("/{notifId}/read")
    public ResponseEntity<String> markOneRead(@PathVariable Long notifId) {
        historyRepository.findById(notifId).ifPresent(n -> {
            n.setRead(true);
            historyRepository.save(n);
        });
        return ResponseEntity.ok("Marked as read");
    }

    // DELETE /api/notifications/clear/{userId}
    @DeleteMapping("/clear/{userId}")
    public ResponseEntity<String> clearAll(@PathVariable Long userId) {
        // Change this line to use the service instead of the repository
        notificationService.clearAllNotifications(userId);
        return ResponseEntity.ok("Cleared");
    }
}