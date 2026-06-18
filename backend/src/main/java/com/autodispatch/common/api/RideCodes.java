package com.autodispatch.common.api;

import java.util.Locale;
import java.util.UUID;

/**
 * Short human-typable ride codes for WhatsApp text fallback
 * (e.g. {@code R1A2B3C4D}). One algorithm, shared by the module that renders
 * codes and the module that resolves them.
 */
public final class RideCodes {

    private RideCodes() {
    }

    public static String codeFor(UUID rideId) {
        return "R" + rideId.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public static boolean matches(String code, UUID rideId) {
        return code != null && codeFor(rideId).equalsIgnoreCase(code.trim());
    }

    /** Loose shape check: R followed by at least 4 hex chars. */
    public static boolean looksLikeCode(String text) {
        return text != null && text.trim().matches("(?i)R[0-9a-f]{4,}");
    }
}
