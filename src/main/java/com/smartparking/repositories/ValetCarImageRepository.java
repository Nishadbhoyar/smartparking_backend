package com.smartparking.repositories;

import com.smartparking.entities.valet.ValetCarImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValetCarImageRepository extends JpaRepository<ValetCarImage, Long> {

    // Used for ownership check: get the customer ID who owns this image
    @Query("SELECT v.valetRequest.customer.id FROM ValetCarImage v WHERE v.id = :imageId")
    Long findCustomerIdByImageId(@Param("imageId") Long imageId);
}