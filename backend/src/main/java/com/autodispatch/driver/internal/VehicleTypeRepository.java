package com.autodispatch.driver.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface VehicleTypeRepository extends JpaRepository<VehicleType, UUID> {

    List<VehicleType> findByActiveTrueOrderByNameAsc();
}
