package com.inf5190.chat.auth.repository;

import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;

@Repository
public class UserAccountRepository {
    private static final String COLLECTION_NAME = "userAccounts";
    private final Firestore firestore;

    public UserAccountRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public FirestoreUserAccount getUserAccount(String username)
            throws InterruptedException, ExecutionException {

        DocumentReference docRef = firestore
                .collection(COLLECTION_NAME)
                .document(username);

        DocumentSnapshot snapshot = docRef.get().get();

        if (!snapshot.exists()) {
            return null;
        }

        return snapshot.toObject(FirestoreUserAccount.class);
    }

    public void createUserAccount(FirestoreUserAccount userAccount)
            throws InterruptedException, ExecutionException {

        ApiFuture<WriteResult> writeFuture = firestore
                .collection(COLLECTION_NAME)
                .document(userAccount.getUsername())
                .set(userAccount);

        writeFuture.get();
    }
}