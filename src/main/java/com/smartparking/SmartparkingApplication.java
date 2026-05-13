package com.smartparking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching  // ✅ Required for @Cacheable in CustomUserDetailsService to work
@EnableScheduling
public class SmartparkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartparkingApplication.class, args);
    }
}