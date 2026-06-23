package com.autodispatch.payment.internal;

import com.autodispatch.payment.api.PaymentMethod;
import com.autodispatch.payment.api.PaymentService;
import com.autodispatch.payment.api.PaymentStatus;
import com.autodispatch.payment.api.PaymentTransactionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
class DefaultPaymentService implements PaymentService {

    private final PaymentTransactionRepository repo;

    DefaultPaymentService(PaymentTransactionRepository repo) {
        this.repo = repo;
    }

    @Override
    public PaymentTransactionView initiateRideFarePayment(UUID rideId, UUID riderId, BigDecimal amount) {
        return toView(repo.save(new PaymentTransaction(rideId, riderId, "RIDE_FARE", amount)));
    }

    @Override
    public PaymentTransactionView acknowledgePayment(UUID rideId, UUID riderId) {
        PaymentTransaction tx = repo.findByRideIdAndType(rideId, "RIDE_FARE")
                .orElseThrow(() -> new IllegalArgumentException("No RIDE_FARE payment for ride: " + rideId));
        if (!tx.getRiderId().equals(riderId)) {
            throw new IllegalArgumentException("Ride " + rideId + " not owned by rider: " + riderId);
        }
        tx.markCollected();
        return toView(repo.save(tx));
    }

    @Override
    public PaymentTransactionView applyRiderCancellationFee(UUID rideId, UUID riderId, BigDecimal amount) {
        return toView(repo.save(new PaymentTransaction(rideId, riderId, "CANCELLATION_FEE", amount)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionView> findByRideId(UUID rideId) {
        return repo.findByRideId(rideId).stream().map(this::toView).toList();
    }

    private PaymentTransactionView toView(PaymentTransaction tx) {
        return new PaymentTransactionView(
                tx.getId(),
                tx.getRideId(),
                tx.getRiderId(),
                tx.getType(),
                PaymentMethod.valueOf(tx.getMethod()),
                PaymentStatus.valueOf(tx.getStatus()),
                tx.getAmount(),
                tx.getAcknowledgedAt(),
                tx.getCreatedAt());
    }
}
