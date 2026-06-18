package com.autodispatch.notification.api;

/**
 * Outbound WhatsApp messaging port. The real adapter (WhatsApp Cloud API) is
 * intentionally NOT implemented yet; only a no-op stub exists. Message shapes,
 * templates, and delivery semantics are undesigned — do not extend this
 * interface until the design phase is approved.
 */
public interface WhatsAppGateway {

    /**
     * Sends a plain text message to a WhatsApp user.
     *
     * @param recipientPhoneE164 recipient phone number in E.164 format
     * @param text               message body
     */
    void sendText(String recipientPhoneE164, String text);

    /**
     * Sends a ride offer (interactive Accept button where the channel supports
     * it; the ride code is always included in the text for the reply fallback).
     */
    void sendRideOffer(String recipientPhoneE164, RideOfferNotification offer);
}
