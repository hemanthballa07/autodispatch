package com.autodispatch.rider.internal;

import com.autodispatch.rider.api.RiderLookup;
import com.autodispatch.rider.api.RiderSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class DefaultRiderLookup implements RiderLookup {

    private final RiderRepository riderRepository;

    DefaultRiderLookup(RiderRepository riderRepository) {
        this.riderRepository = riderRepository;
    }

    @Override
    public Optional<RiderSummary> findById(UUID riderId) {
        return riderRepository.findById(riderId)
                .map(r -> new RiderSummary(r.getId(), r.getName(), r.getPhone()));
    }
}
