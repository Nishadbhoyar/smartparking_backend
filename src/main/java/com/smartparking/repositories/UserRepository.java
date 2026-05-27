package com.smartparking.repositories;


import com.smartparking.entities.users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Used for login and checking if an account already exists
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}