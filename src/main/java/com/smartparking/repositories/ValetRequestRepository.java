package com.smartparking.repositories;

import com.smartparking.entities.nums.ValetStatus;
import com.smartparking.entities.valet.ValetRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * REPLACE existing ValetRequestRepository.java
 * Location: com/smartparking/repositories/ValetRequestRepository.java
 *
 * FIX: Added countByStatusAndRequestedAtBetween() — returns Long, not long,
 *      so AnalyticsServiceImpl can assign result to Long variable (line 109)
 */
@Repository
public interface ValetRequestRepository extends JpaRepository<ValetRequest, Long> {

    // ── Existing ──────────────────────────────────────────────────────────
    List<ValetRequest> findByStatus(ValetStatus status);

    // ── FIX: line 109 error — method now exists ───────────────────────────
    // Returns Long (boxed) so it can be assigned to Long valetRequestsToday
    Long countByStatusAndRequestedAtBetween(ValetStatus status,
                                            LocalDateTime start,
                                            LocalDateTime end);

    // ── NEW: CustomerDashboardServiceImpl ─────────────────────────────────
    Optional<ValetRequest> findFirstByCustomerIdAndStatusIn(
            Long customerId, List<ValetStatus> statuses);

    long countByCustomerId(Long customerId);

    // ── NEW: ValetDashboardServiceImpl ────────────────────────────────────
    Optional<ValetRequest> findFirstByValetIdAndStatusIn(
            Long valetId, List<ValetStatus> statuses);

    long countByValetIdAndStatus(Long valetId, ValetStatus status);

    long countByValetIdAndStatusAndCompletedAtBetween(Long valetId,
                                                      ValetStatus status,
                                                      LocalDateTime start,
                                                      LocalDateTime end);

    List<ValetRequest> findTop5ByValetIdOrderByCompletedAtDesc(Long valetId);

    // ── NEW: LotAdminDashboardServiceImpl ─────────────────────────────────
    long countByParkingLotIdInAndStatusIn(List<Long> lotIds,
                                          List<ValetStatus> statuses);

    long countByParkingLotIdInAndStatusAndCompletedAtBetween(
            List<Long> lotIds, ValetStatus status,
            LocalDateTime start, LocalDateTime end);

    List<ValetRequest> findByCustomerId(Long customerId);;
}