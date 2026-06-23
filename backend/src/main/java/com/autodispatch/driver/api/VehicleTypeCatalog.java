package com.autodispatch.driver.api;

import java.util.List;

/** Read-only catalog of active vehicle types offered on this campus. */
public interface VehicleTypeCatalog {

    List<VehicleTypeView> listActive();
}
