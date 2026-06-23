package com.autodispatch.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    List<PaymentTransaction> findByRideId(UUID rideId);
    Optional<PaymentTransaction> findByRideIdAndType(UUID rideId, String type);
}
