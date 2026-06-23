package com.autodispatch.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {
    PaymentTransactionView initiateRideFarePayment(UUID rideId, UUID riderId, BigDecimal amount);
    PaymentTransactionView acknowledgePayment(UUID rideId, UUID riderId);
    PaymentTransactionView applyRiderCancellationFee(UUID rideId, UUID riderId, BigDecimal amount);
    List<PaymentTransactionView> findByRideId(UUID rideId);
}
