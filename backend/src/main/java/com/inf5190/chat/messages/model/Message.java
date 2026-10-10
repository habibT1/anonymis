package com.inf5190.chat.messages.model;

/**
 * Représente un message.
 */
public record Message(String id, String text, String username, Long timestamp, String imageUrl) {
}
