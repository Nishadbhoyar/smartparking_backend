package com.smartparking.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() throws IOException {
        String json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");

        if (json == null || json.isBlank()) {
            // Firebase not configured — skip init (push notifications disabled)
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(
                            new ByteArrayInputStream(json.getBytes())))
                    .build();
            FirebaseApp.initializeApp(options);
        }
    }
}