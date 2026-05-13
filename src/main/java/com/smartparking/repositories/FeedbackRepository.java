package com.smartparking.repositories;

import com.smartparking.entities.parking.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // 1. Get all reviews for a specific parking lot (to display in a list)
    List<Feedback> findByParkingLotIdOrderByCreatedAtDesc(Long parkingLotId);

    // 2. Get all reviews for a specific valet
    List<Feedback> findByValetIdOrderByCreatedAtDesc(Long valetId);

    // 3. THE HEAVY LIFTING: Automatically calculates the average star rating for a parking lot!
    @Query("SELECT AVG(f.rating) FROM Feedback f WHERE f.parkingLot.id = :lotId")
    Double getAverageRatingForParkingLot(@Param("lotId") Long lotId);

    // 4. Automatically calculates the average star rating for a valet!
    @Query("SELECT AVG(f.rating) FROM Feedback f WHERE f.valet.id = :valetId")
    Double getAverageRatingForValet(@Param("valetId") Long valetId);

    Feedback findFirstByCustomerIdAndValetIdOrderByCreatedAtDesc(Long customerId, Long valetId);

    Feedback findFirstByCustomerIdAndParkingLotIdOrderByCreatedAtDesc(Long customerId, Long parkingLotId);
}
