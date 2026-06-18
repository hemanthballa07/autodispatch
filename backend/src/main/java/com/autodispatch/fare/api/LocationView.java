package com.autodispatch.fare.api;

import java.util.UUID;

public record LocationView(UUID id, String name, String zone, boolean active) {
}
