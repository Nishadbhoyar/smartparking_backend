package com.smartparking.repositories;
import com.smartparking.entities.admins.FleetAdmin;
import com.smartparking.entities.rental.RentalCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;




public interface RentalCompanyRepository extends JpaRepository<RentalCompany, Long> {

    Optional<RentalCompany> findByFleetAdminId(Long fleetAdminId);

    RentalCompany findByFleetAdmin(FleetAdmin fleetAdmin);


}