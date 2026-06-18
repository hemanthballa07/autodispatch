package com.autodispatch.notification.internal;

import com.autodispatch.common.api.PhoneNumbers;
import com.autodispatch.dispatch.api.DispatchApi;
import com.autodispatch.dispatch.api.DispatchQueries;
import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.driver.api.DriverOnRideException;
import com.autodispatch.driver.api.DriverSummary;
import com.autodispatch.notification.api.MessageCatalog;
import com.autodispatch.notification.api.WhatsAppGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Translates DriverCommands into calls on the domain's public APIs. ZERO
 * business rules live here: eligibility, state transitions, and claims are
 * all enforced inside the dispatch/driver modules.
 */
@Component
class WebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessor.class);

    private final DriverAvailabilityService driverAvailability;
    private final DispatchApi dispatchApi;
    private final DispatchQueries dispatchQueries;
    private final WhatsAppGateway gateway;
    private final DriverCommandParser commandParser;

    WebhookProcessor(DriverAvailabilityService driverAvailability,
                     DispatchApi dispatchApi,
                     DispatchQueries dispatchQueries,
                     WhatsAppGateway gateway,
                     DriverCommandParser commandParser) {
        this.driverAvailability = driverAvailability;
        this.dispatchApi = dispatchApi;
        this.dispatchQueries = dispatchQueries;
        this.gateway = gateway;
        this.commandParser = commandParser;
    }

    void process(WhatsAppPayloadParser.InboundMessage message) {
        try {
            String waId = PhoneNumbers.toE164(message.fromWaId());
            Optional<DriverSummary> driverOpt = driverAvailability.findByWhatsappId(waId);
            if (driverOpt.isEmpty() || !driverOpt.get().verified()) {
                gateway.sendText(waId, MessageCatalog.unknownSender());
                return;
            }
            DriverSummary driver = driverOpt.get();
            driverAvailability.recordInbound(driver.id());
            execute(driver, commandParser.parse(driver.id(), message));
        } catch (Exception e) {
            log.error("Webhook processing failed for message {}", message.messageId(), e);
        }
    }

    private void execute(DriverSummary driver, DriverCommand command) {
        String to = driver.whatsappId();
        switch (command) {
            case DriverCommand.GoOnline ignored -> {
                try {
                    driverAvailability.goOnline(driver.id());
                    gateway.sendText(to, MessageCatalog.goOnlineAck());
                } catch (DriverOnRideException e) {
                    gateway.sendText(to, MessageCatalog.helpOnRide());
                }
            }
            case DriverCommand.GoOffline ignored -> {
                try {
                    driverAvailability.goOffline(driver.id());
                    gateway.sendText(to, MessageCatalog.goOfflineAck());
                } catch (DriverOnRideException e) {
                    gateway.sendText(to, MessageCatalog.cannotGoOfflineOnRide());
                }
            }
            case DriverCommand.AcceptRide accept -> {
                try {
                    dispatchApi.handleDriverAccept(driver.whatsappId(), accept.rideId());
                } catch (RuntimeException e) {
                    log.warn("Accept rejected for driver {}: {}", driver.id(), e.getMessage());
                    gateway.sendText(to, helpFor(driver));
                }
            }
            case DriverCommand.MarkArrived ignored ->
                    onActiveRide(driver, dispatchApi::markArrived, MessageCatalog.arrivedAck());
            case DriverCommand.StartTrip ignored ->
                    onActiveRide(driver, dispatchApi::markStarted, MessageCatalog.startedAck());
            case DriverCommand.CompleteTrip ignored ->
                    onActiveRide(driver, dispatchApi::markCompleted, MessageCatalog.completedAck());
            case DriverCommand.CancelRide ignored ->
                    onActiveRide(driver, dispatchApi::handleDriverCancel, MessageCatalog.driverCancelAck());
            case DriverCommand.Unrecognized ignored -> gateway.sendText(to, helpFor(driver));
        }
    }

    private void onActiveRide(DriverSummary driver, Consumer<UUID> action, String ack) {
        Optional<UUID> activeRide = dispatchQueries.findActiveRideForDriver(driver.id());
        if (activeRide.isEmpty()) {
            gateway.sendText(driver.whatsappId(), helpFor(driver));
            return;
        }
        try {
            action.accept(activeRide.get());
            gateway.sendText(driver.whatsappId(), ack);
        } catch (RuntimeException e) {
            log.warn("Ride command rejected for driver {}: {}", driver.id(), e.getMessage());
            gateway.sendText(driver.whatsappId(), helpFor(driver));
        }
    }

    private String helpFor(DriverSummary driver) {
        return switch (driver.state()) {
            case OFFLINE -> MessageCatalog.helpOffline();
            case AVAILABLE -> MessageCatalog.helpAvailable();
            case ON_RIDE -> MessageCatalog.helpOnRide();
        };
    }
}
