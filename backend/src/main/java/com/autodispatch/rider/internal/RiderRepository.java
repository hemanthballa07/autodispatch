package com.autodispatch.rider.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiderRepository extends JpaRepository<Rider, UUID> {

    Optional<Rider> findByPhone(String phone);
}
