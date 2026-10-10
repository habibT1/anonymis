package com.inf5190.chat.status.model;

/**
 * Représente le statut d'un utilisateur.
 *
 * @param username Le nom d'utilisateur
 * @param status Le statut: "online", "offline", "away"
 * @param lastUpdate Timestamp de la dernière mise à jour (millisecondes)
 */
public record UserStatus(String username, String status, Long lastUpdate) {
}
