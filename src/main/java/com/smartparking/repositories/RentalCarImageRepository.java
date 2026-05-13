package com.smartparking.repositories;

import com.smartparking.entities.rental.RentalCarImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RentalCarImageRepository extends JpaRepository<RentalCarImage, Long> {

    /** Returns all image IDs for a given car (used to build the ID list for the frontend). */
    @Query("SELECT i.id FROM RentalCarImage i WHERE i.rentalCar.id = :carId")
    List<Long> findIdsByCarId(@Param("carId") Long carId);

    /** Count of images for a car — used to enforce the 6-image limit server-side. */
    long countByRentalCarId(Long carId);

    /**
     * Ownership check: returns the car owner's user ID for a given image ID.
     * Used to verify the delete request is coming from the actual owner.
     * Returns null if the car has no individual owner (fleet car).
     */
    @Query("SELECT i.rentalCar.carOwner.id FROM RentalCarImage i WHERE i.id = :imageId")
    Long findCarOwnerIdByImageId(@Param("imageId") Long imageId);
}