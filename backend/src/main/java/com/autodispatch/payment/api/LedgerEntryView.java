package com.autodispatch.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryView(
        UUID id,
        UUID driverId,
        UUID rideId,
        String type,
        BigDecimal amount,
        String note,
        Instant createdAt) {
}
