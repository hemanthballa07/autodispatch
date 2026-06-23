package com.autodispatch.driver.internal;

import com.autodispatch.driver.api.VehicleTypeCatalog;
import com.autodispatch.driver.api.VehicleTypeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
class VehicleTypeCatalogService implements VehicleTypeCatalog {

    private final VehicleTypeRepository vehicleTypeRepository;

    VehicleTypeCatalogService(VehicleTypeRepository vehicleTypeRepository) {
        this.vehicleTypeRepository = vehicleTypeRepository;
    }

    @Override
    public List<VehicleTypeView> listActive() {
        return vehicleTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(vt -> new VehicleTypeView(vt.getId(), vt.getName()))
                .toList();
    }
}
