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
            @Value("${firebase.credentials-path}") String credentialsPath) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        boolean emulatorMode = System.getenv("FIRESTORE_EMULATOR_HOST") != null;

        GoogleCredentials credentials;
        if (emulatorMode) {
            try {
                credentials = GoogleCredentials.getApplicationDefault();
            } catch (IOException e) {
                credentials = GoogleCredentials.newBuilder().build();
            }
        } else if (credentialsPath != null && !credentialsPath.isBlank()) {
            try (var in = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(in);
            } catch (IOException e) {
                throw new RuntimeException("Не удалось прочитать firebase credentials: " + credentialsPath, e);
            }
        } else {
            throw new IllegalStateException(
                "Задайте FIREBASE_CREDENTIALS или FIRESTORE_EMULATOR_HOST для локального эмулятора");
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