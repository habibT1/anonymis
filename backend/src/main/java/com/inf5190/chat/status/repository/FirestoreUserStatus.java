package com.inf5190.chat.status.repository;

import com.google.cloud.Timestamp;

/**
 * Représentation Firestore du statut d'un utilisateur.
 */
public class FirestoreUserStatus {
    private String username;
    private String status;
    private Timestamp lastUpdate;

    public FirestoreUserStatus() {
    }

    public FirestoreUserStatus(String username, String status, Timestamp lastUpdate) {
        this.username = username;
        this.status = status;
        this.lastUpdate = lastUpdate;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Timestamp lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
