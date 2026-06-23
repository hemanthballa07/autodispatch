package com.autodispatch.payment.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface DriverLedgerEntryRepository extends JpaRepository<DriverLedgerEntry, UUID> {
    Page<DriverLedgerEntry> findByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);
}
