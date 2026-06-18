package com.autodispatch.common.api;

/**
 * The single phone/wa_id normalization utility — used everywhere a phone
 * number or WhatsApp id enters the system. Campus deployment default country
 * code is +91.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String toE164(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Phone number must not be empty");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 10) {
            digits = "91" + digits;
        }
        if (digits.length() < 8 || digits.length() > 15) {
            throw new IllegalArgumentException("Not a valid phone number: " + raw);
        }
        return "+" + digits;
    }
}
