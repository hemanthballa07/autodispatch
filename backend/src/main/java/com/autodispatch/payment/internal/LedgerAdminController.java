package com.autodispatch.payment.internal;

import com.autodispatch.payment.api.DriverLedgerService;
import com.autodispatch.payment.api.LedgerEntryView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/drivers/{driverId}/ledger")
class LedgerAdminController {

    private final DriverLedgerService ledgerService;

    LedgerAdminController(DriverLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    List<LedgerEntryView> statement(@PathVariable UUID driverId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return ledgerService.statement(driverId, page, Math.min(size, 100));
    }
}
