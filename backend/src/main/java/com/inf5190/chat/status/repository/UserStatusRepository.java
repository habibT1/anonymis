package com.inf5190.chat.status.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;

import com.inf5190.chat.status.model.UserStatus;

/**
 * Repository pour gérer les statuts des utilisateurs dans Firestore.
 */
@Repository
public class UserStatusRepository {
    private static final String COLLECTION_NAME = "userStatuses";
    private final Firestore firestore;

    public UserStatusRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Récupère le statut d'un utilisateur.
     */
    public UserStatus getUserStatus(String username)
            throws InterruptedException, ExecutionException {

        DocumentReference docRef = firestore
                .collection(COLLECTION_NAME)
                .document(username);

        DocumentSnapshot snapshot = docRef.get().get();

        if (!snapshot.exists()) {
            return null;
        }

        FirestoreUserStatus fsStatus = snapshot.toObject(FirestoreUserStatus.class);
        if (fsStatus == null) {
            return null;
        }

        Long timestamp = fsStatus.getLastUpdate() != null
                ? fsStatus.getLastUpdate().toDate().getTime()
                : System.currentTimeMillis();

        return new UserStatus(
                fsStatus.getUsername(),
                fsStatus.getStatus(),
                timestamp
        );
    }

    /**
     * Récupère tous les statuts des utilisateurs.
     */
    public List<UserStatus> getAllStatuses()
            throws InterruptedException, ExecutionException {

        ApiFuture<QuerySnapshot> future = firestore
                .collection(COLLECTION_NAME)
                .get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        List<UserStatus> statuses = new ArrayList<>();

        for (DocumentSnapshot doc : documents) {
            FirestoreUserStatus fsStatus = doc.toObject(FirestoreUserStatus.class);
            if (fsStatus != null) {
                Long timestamp = fsStatus.getLastUpdate() != null
                        ? fsStatus.getLastUpdate().toDate().getTime()
                        : System.currentTimeMillis();

                statuses.add(new UserStatus(
                        fsStatus.getUsername(),
                        fsStatus.getStatus(),
                        timestamp
                ));
            }
        }

        return statuses;
    }

    /**
     * Met à jour le statut d'un utilisateur.
     */
    public UserStatus updateUserStatus(String username, String status)
            throws InterruptedException, ExecutionException {

        long nowMillis = System.currentTimeMillis();
        Timestamp firestoreTs = Timestamp.ofTimeSecondsAndNanos(
                nowMillis / 1000,
                (int) ((nowMillis % 1000) * 1_000_000)
        );

        FirestoreUserStatus fsStatus = new FirestoreUserStatus(
                username,
                status,
                firestoreTs
        );

        firestore
                .collection(COLLECTION_NAME)
                .document(username)
                .set(fsStatus)
                .get();

        return new UserStatus(username, status, nowMillis);
    }
}
