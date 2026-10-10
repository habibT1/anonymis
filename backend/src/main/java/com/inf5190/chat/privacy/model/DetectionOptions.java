package com.inf5190.chat.privacy.model;

import java.util.List;

/**
 * Options de détection choisies par l'utilisateur.
 *
 * @param level     "standard" ou "strict"
 * @param watchlist noms métier à toujours masquer
 * @param allowlist termes à ne jamais masquer
 */
public record DetectionOptions(String level, List<WatchItem> watchlist, List<String> allowlist) {

    public record WatchItem(String term, String category) {
    }

    public static DetectionOptions standard() {
        return new DetectionOptions("standard", List.of(), List.of());
    }

    public DetectionOptions {
        level = level == null || level.isBlank() ? "standard" : level;
        watchlist = watchlist == null ? List.of() : watchlist;
        allowlist = allowlist == null ? List.of() : allowlist;
    }
}
