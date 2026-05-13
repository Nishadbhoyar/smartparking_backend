package com.smartparking.repositories;

import com.smartparking.entities.admins.FleetAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FleetAdminRepository extends JpaRepository<FleetAdmin, Long> {

    // FIX: Add this method to avoid loading all admins and filtering in Java
    Optional<FleetAdmin> findByEmail(String email);

}