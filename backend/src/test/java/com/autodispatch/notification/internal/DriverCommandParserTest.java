package com.autodispatch.notification.internal;

import com.autodispatch.dispatch.api.DispatchQueries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Phase 4 gate 4: real-shaped Meta payload fixtures → correct DriverCommand
 * mapping (pure unit; DispatchQueries mocked for ride-code resolution).
 */
class DriverCommandParserTest {

    private static final UUID FIXTURE_RIDE_ID = UUID.fromString("6f1d2c3b-4a5e-4f60-9b7a-1c2d3e4f5a6b");
    private static final UUID DRIVER_ID = UUID.randomUUID();

    private final WhatsAppPayloadParser payloadParser = new WhatsAppPayloadParser(new ObjectMapper());
    private DispatchQueries dispatchQueries;
    private DriverCommandParser commandParser;

    @BeforeEach
    void setUp() {
        dispatchQueries = Mockito.mock(DispatchQueries.class);
        commandParser = new DriverCommandParser(dispatchQueries);
    }

    @Test
    void button_reply_maps_to_accept_ride_with_uuid() {
        var messages = payloadParser.parse(fixture("button_reply"));
        assertEquals(1, messages.size());
        assertEquals("wamid.button-reply-001", messages.get(0).messageId());
        assertEquals("919876543210", messages.get(0).fromWaId());

        DriverCommand command = commandParser.parse(DRIVER_ID, messages.get(0));
        DriverCommand.AcceptRide accept = assertInstanceOf(DriverCommand.AcceptRide.class, command);
        assertEquals(FIXTURE_RIDE_ID, accept.rideId());
    }

    @Test
    void text_on_maps_to_go_online() {
        var messages = payloadParser.parse(fixture("text_on"));
        assertEquals(1, messages.size());
        assertInstanceOf(DriverCommand.GoOnline.class, commandParser.parse(DRIVER_ID, messages.get(0)));
    }

    @Test
    void text_ride_code_resolves_against_open_offers() {
        Mockito.when(dispatchQueries.resolveOpenOfferByCode(eq(DRIVER_ID), eq("R6F1D2C3B")))
                .thenReturn(Optional.of(FIXTURE_RIDE_ID));

        var messages = payloadParser.parse(fixture("text_ride_code"));
        DriverCommand command = commandParser.parse(DRIVER_ID, messages.get(0));
        DriverCommand.AcceptRide accept = assertInstanceOf(DriverCommand.AcceptRide.class, command);
        assertEquals(FIXTURE_RIDE_ID, accept.rideId());
    }

    @Test
    void unresolvable_ride_code_maps_to_unrecognized() {
        Mockito.when(dispatchQueries.resolveOpenOfferByCode(eq(DRIVER_ID), eq("R6F1D2C3B")))
                .thenReturn(Optional.empty());

        var messages = payloadParser.parse(fixture("text_ride_code"));
        assertInstanceOf(DriverCommand.Unrecognized.class, commandParser.parse(DRIVER_ID, messages.get(0)));
    }

    @Test
    void lowercase_text_arrived_maps_to_mark_arrived() {
        var messages = payloadParser.parse(fixture("text_arrived"));
        assertInstanceOf(DriverCommand.MarkArrived.class, commandParser.parse(DRIVER_ID, messages.get(0)));
    }

    @Test
    void remaining_keywords_map_to_their_commands() {
        assertInstanceOf(DriverCommand.GoOffline.class, parseText("OFF"));
        assertInstanceOf(DriverCommand.StartTrip.class, parseText("start"));
        assertInstanceOf(DriverCommand.CompleteTrip.class, parseText("Done"));
        assertInstanceOf(DriverCommand.CancelRide.class, parseText("CANCEL"));
        DriverCommand.Unrecognized unrecognized =
                assertInstanceOf(DriverCommand.Unrecognized.class, parseText("send me a ride pls"));
        assertEquals("send me a ride pls", unrecognized.rawText());
    }

    @Test
    void status_update_payload_yields_no_messages() {
        assertTrue(payloadParser.parse(fixture("status_update")).isEmpty());
    }

    @Test
    void garbage_payload_yields_no_messages() {
        assertTrue(payloadParser.parse(fixture("garbage")).isEmpty());
    }

    @Test
    void unknown_button_payload_maps_to_unrecognized() {
        var message = new WhatsAppPayloadParser.InboundMessage("wamid.x", "919876543210", null, "DELETE:everything");
        assertInstanceOf(DriverCommand.Unrecognized.class, commandParser.parse(DRIVER_ID, message));
        assertNull(message.text());
    }

    private DriverCommand parseText(String text) {
        return commandParser.parse(DRIVER_ID,
                new WhatsAppPayloadParser.InboundMessage("wamid.t", "919876543210", text, null));
    }

    private static String fixture(String name) {
        try (InputStream in = DriverCommandParserTest.class
                .getResourceAsStream("/whatsapp-fixtures/" + name + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("Missing fixture: " + name, e);
        }
    }
}
