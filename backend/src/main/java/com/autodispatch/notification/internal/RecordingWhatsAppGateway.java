package com.autodispatch.notification.internal;

import com.autodispatch.notification.api.MessageCatalog;
import com.autodispatch.notification.api.RideOfferNotification;
import com.autodispatch.notification.api.WhatsAppGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * No-op stand-in until the WhatsApp Cloud API adapter is designed and built.
 * Never calls any external API: messages are logged and recorded in memory so
 * tests can assert on outbound traffic.
 */
@Component
@ConditionalOnProperty(name = "whatsapp.mode", havingValue = "STUB", matchIfMissing = true)
public class RecordingWhatsAppGateway implements WhatsAppGateway {

    public record RecordedMessage(String recipient, String text) {
    }

    private static final Logger log = LoggerFactory.getLogger(RecordingWhatsAppGateway.class);

    private final List<RecordedMessage> recorded = new CopyOnWriteArrayList<>();

    @Override
    public void sendText(String recipientPhoneE164, String text) {
        log.info("[stub] WhatsApp message recorded (recipient={}, {} chars)",
                recipientPhoneE164, text == null ? 0 : text.length());
        recorded.add(new RecordedMessage(recipientPhoneE164, text));
    }

    @Override
    public void sendRideOffer(String recipientPhoneE164, RideOfferNotification offer) {
        sendText(recipientPhoneE164, MessageCatalog.rideOffer(offer));
    }

    public List<RecordedMessage> recorded() {
        return List.copyOf(recorded);
    }

    public void clear() {
        recorded.clear();
    }
}
