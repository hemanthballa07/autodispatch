package com.autodispatch.notification.internal;

import com.autodispatch.common.api.RideCodes;
import com.autodispatch.dispatch.api.DispatchQueries;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Maps a parsed inbound message to a DriverCommand. Button payloads use the
 * "ACTION:rideId" format; text falls back to the keyword set, with ride codes
 * (R&lt;short&gt;) resolved against the driver's open offers. No business
 * rules — only translation.
 */
@Component
class DriverCommandParser {

    private final DispatchQueries dispatchQueries;

    DriverCommandParser(DispatchQueries dispatchQueries) {
        this.dispatchQueries = dispatchQueries;
    }

    DriverCommand parse(UUID driverId, WhatsAppPayloadParser.InboundMessage message) {
        if (message.buttonPayload() != null) {
            return parseButton(message.buttonPayload());
        }
        return parseText(driverId, message.text());
    }

    private DriverCommand parseButton(String payload) {
        String trimmed = payload.trim();
        if (trimmed.toUpperCase(Locale.ROOT).startsWith("ACCEPT:")) {
            try {
                return new DriverCommand.AcceptRide(UUID.fromString(trimmed.substring("ACCEPT:".length())));
            } catch (IllegalArgumentException notAUuid) {
                return new DriverCommand.Unrecognized(payload);
            }
        }
        return new DriverCommand.Unrecognized(payload);
    }

    private DriverCommand parseText(UUID driverId, String text) {
        String raw = text == null ? "" : text.trim();
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "ON" -> new DriverCommand.GoOnline();
            case "OFF" -> new DriverCommand.GoOffline();
            case "ARRIVED" -> new DriverCommand.MarkArrived();
            case "START" -> new DriverCommand.StartTrip();
            case "DONE" -> new DriverCommand.CompleteTrip();
            case "CANCEL" -> new DriverCommand.CancelRide();
            default -> {
                if (RideCodes.looksLikeCode(raw)) {
                    yield dispatchQueries.resolveOpenOfferByCode(driverId, raw)
                            .<DriverCommand>map(DriverCommand.AcceptRide::new)
                            .orElse(new DriverCommand.Unrecognized(raw));
                }
                yield new DriverCommand.Unrecognized(raw);
            }
        };
    }
}
