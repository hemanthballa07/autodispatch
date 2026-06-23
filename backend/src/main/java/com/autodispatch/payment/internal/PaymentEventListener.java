package com.autodispatch.payment.internal;

import com.autodispatch.dispatch.api.RideCompletedEvent;
import com.autodispatch.payment.api.DriverLedgerService;
import com.autodispatch.payment.api.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final PaymentService paymentService;
    private final DriverLedgerService driverLedgerService;

    PaymentEventListener(PaymentService paymentService, DriverLedgerService driverLedgerService) {
        this.paymentService = paymentService;
        this.driverLedgerService = driverLedgerService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onRideCompleted(RideCompletedEvent event) {
        if (event.fareAmount() == null) {
            log.warn("Ride {} completed with null fare — skipping payment auto-post", event.rideId());
            return;
        }
        try {
            paymentService.initiateRideFarePayment(event.rideId(), event.riderId(), event.fareAmount());
            if (event.driverId() != null) {
                driverLedgerService.creditEarning(event.driverId(), event.rideId(), event.fareAmount());
            }
        } catch (Exception e) {
            log.error("Payment auto-post failed for ride {}: {}", event.rideId(), e.getMessage(), e);
        }
    }
}
