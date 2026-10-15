package ru.universitybooking.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirestoreConfig {

    @Bean
    public FirebaseApp firebaseApp(
            @Value("${firebase.project-id}") String projectId,
            @Value("${firebase.credentials-path}") String credentialsPath)  {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        GoogleCredentials credentials;
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            try (var in = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalStateException("Задайте FIREBASE_CREDENTIALS");
        }
        return FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build());
    }

    @Bean
    public Firestore firestore(FirebaseApp app) {
        return FirestoreClient.getFirestore(app);
    }
}