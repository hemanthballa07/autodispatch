package com.autodispatch.dispatch.api;

import java.util.UUID;

/**
 * Public entry points of the dispatch module. Adapters (e.g. the WhatsApp
 * webhook) must depend on this interface only, never on dispatch internals.
 */
public interface DispatchApi {

    /** Broadcasts a REQUESTED ride to available verified drivers (or expires it if none). */
    void startDispatch(UUID rideId);

    /** A driver (identified by WhatsApp id) accepts an offered ride. */
    void handleDriverAccept(String driverWhatsappId, UUID rideId);

    /** The assigned driver cancels; rebroadcast once, then expire. */
    void handleDriverCancel(UUID rideId);

    void markArrived(UUID rideId);

    void markStarted(UUID rideId);

    void markCompleted(UUID rideId);
}
