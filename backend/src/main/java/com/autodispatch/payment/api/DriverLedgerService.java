package com.autodispatch.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface DriverLedgerService {
    LedgerEntryView creditEarning(UUID driverId, UUID rideId, BigDecimal amount);
    LedgerEntryView debitCancellationPenalty(UUID driverId, UUID rideId, BigDecimal amount);
    List<LedgerEntryView> statement(UUID driverId, int page, int size);
}
