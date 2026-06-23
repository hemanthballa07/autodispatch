package com.autodispatch.driver.internal;

import com.autodispatch.driver.api.VehicleTypeCatalog;
import com.autodispatch.driver.api.VehicleTypeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicle-types")
class VehicleTypeController {

    private final VehicleTypeCatalog vehicleTypeCatalog;

    VehicleTypeController(VehicleTypeCatalog vehicleTypeCatalog) {
        this.vehicleTypeCatalog = vehicleTypeCatalog;
    }

    @GetMapping
    List<VehicleTypeView> list() {
        return vehicleTypeCatalog.listActive();
    }
}
