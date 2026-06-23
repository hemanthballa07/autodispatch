package com.autodispatch.payment.internal;

import com.autodispatch.payment.api.DriverLedgerService;
import com.autodispatch.payment.api.LedgerEntryView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
class DefaultDriverLedgerService implements DriverLedgerService {

    private final DriverLedgerEntryRepository repo;

    DefaultDriverLedgerService(DriverLedgerEntryRepository repo) {
        this.repo = repo;
    }

    @Override
    public LedgerEntryView creditEarning(UUID driverId, UUID rideId, BigDecimal amount) {
        return toView(repo.save(new DriverLedgerEntry(driverId, rideId, "EARNING", amount, null)));
    }

    @Override
    public LedgerEntryView debitCancellationPenalty(UUID driverId, UUID rideId, BigDecimal amount) {
        return toView(repo.save(new DriverLedgerEntry(driverId, rideId, "CANCELLATION_PENALTY", amount.negate(), null)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryView> statement(UUID driverId, int page, int size) {
        return repo.findByDriverIdOrderByCreatedAtDesc(driverId, PageRequest.of(page, size))
                .stream().map(this::toView).toList();
    }

    private LedgerEntryView toView(DriverLedgerEntry e) {
        return new LedgerEntryView(
                e.getId(),
                e.getDriverId(),
                e.getRideId(),
                e.getType(),
                e.getAmount(),
                e.getNote(),
                e.getCreatedAt());
    }
}
