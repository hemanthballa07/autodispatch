package com.autodispatch.notification.api;

/**
 * The single catalog of user-facing message texts (DRY: no scattered string
 * literals). Pure presentation — no business rules.
 */
public final class MessageCatalog {

    private MessageCatalog() {
    }

    // --- driver: offers & ride flow ---

    public static String rideOffer(RideOfferNotification offer) {
        String farePart = offer.fareAmount() == null
                ? ""
                : " Fare ₹" + offer.fareAmount().stripTrailingZeros().toPlainString() + ".";
        return "New ride: %s → %s.%s Tap Accept or reply %s within %ds."
                .formatted(offer.pickupLabel(), offer.dropLabel(), farePart,
                        offer.rideCode(), offer.replyWindowSeconds());
    }

    public static String alreadyTaken() {
        return "Sorry, this ride was already taken.";
    }

    public static String arrivedAck() {
        return "Marked: arrived at pickup. Send START when the trip begins.";
    }

    public static String startedAck() {
        return "Trip started. Send DONE when you finish.";
    }

    public static String completedAck() {
        return "Trip completed. You are AVAILABLE for new rides.";
    }

    public static String goOnlineAck() {
        return "You are ONLINE. Ride offers will arrive here.";
    }

    public static String goOfflineAck() {
        return "You are OFFLINE. Send ON to receive rides again.";
    }

    public static String cannotGoOfflineOnRide() {
        return "You have an active ride. Finish it (DONE) before going offline.";
    }

    public static String driverCancelAck() {
        return "Ride cancelled. You are AVAILABLE again.";
    }

    // --- rider-facing ---

    public static String driverAssigned(String driverName, String vehicleNo, String pickupLabel) {
        return "Driver %s (%s) is on the way to %s.".formatted(driverName, vehicleNo, pickupLabel);
    }

    public static String noDrivers() {
        return "No drivers are available right now. Please try again in a few minutes.";
    }

    public static String rideExpired() {
        return "We could not find a driver for your ride. Please request again.";
    }

    public static String rideCancelled() {
        return "Your ride was cancelled.";
    }

    // --- help & fallback ---

    public static String unknownSender() {
        return "Hi! This number serves registered campus auto drivers. "
                + "Riders, please book through the AutoDispatch app.";
    }

    public static String helpOffline() {
        return "Commands: ON — go online to receive ride offers.";
    }

    public static String helpAvailable() {
        return "Commands: OFF — go offline. Tap Accept on an offer, or reply with its R-code to take a ride.";
    }

    public static String helpOnRide() {
        return "Commands: ARRIVED — at pickup. START — begin trip. DONE — finish trip. CANCEL — cancel this ride.";
    }
}
