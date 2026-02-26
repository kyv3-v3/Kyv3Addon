package com.kyv3.addon.modules.flow.v2.domain;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FlowNameSanitizer {
    private FlowNameSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) return "";

        return value.toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("[^a-z0-9-_]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-|-$", "");
    }

    public static String nextUniqueName(String preferredBaseName, Iterable<String> existingNames) {
        String root = sanitize(preferredBaseName);
        if (root.isBlank()) root = "custom-module";

        Set<String> names = new HashSet<>();
        for (String existingName : existingNames) {
            String sanitized = sanitize(existingName);
            if (!sanitized.isBlank()) names.add(sanitized);
        }

        if (!names.contains(root)) return root;

        for (int i = 2; i < 10_000; i++) {
            String candidate = root + "-" + i;
            if (!names.contains(candidate)) return candidate;
        }

        return root + "-" + System.currentTimeMillis();
    }
}
