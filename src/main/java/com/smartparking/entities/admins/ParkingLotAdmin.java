package com.smartparking.entities.admins;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartparking.entities.parking.ParkingLot;
import com.smartparking.entities.users.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Entity
@Data
@EqualsAndHashCode(callSuper = true) // Keeps Lombok happy with inheritance
@Table(name = "parking_lot_admin")
public class ParkingLotAdmin extends User {

    @JsonIgnore
    @OneToMany(mappedBy = "parkingLotAdmin", cascade = CascadeType.ALL)
    private List<ParkingLot> parkingLots;

    private String businessRegistrationNumber;
    private String businessPhone;
    private boolean isVerified = false;   // ← ADD THIS LINE
}

