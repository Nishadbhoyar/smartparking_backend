package com.smartparking.entities.users;

import com.smartparking.entities.nums.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED) // CRITICAL: Tells Spring to link subclass tables to this one
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;


    @Column(name = "phone_number", unique = true)
    private String phoneNumber;
}