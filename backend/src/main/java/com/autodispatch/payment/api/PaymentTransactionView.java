package com.autodispatch.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionView(
        UUID id,
        UUID rideId,
        UUID riderId,
        String type,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        Instant acknowledgedAt,
        Instant createdAt) {
}
