package com.autodispatch.payment.internal;

import com.autodispatch.payment.api.PaymentService;
import com.autodispatch.payment.api.PaymentTransactionView;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rides/{rideId}/payment")
class PaymentController {

    private final PaymentService paymentService;

    PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentTransactionView initiate(@PathVariable UUID rideId,
                                    @RequestParam @NotNull BigDecimal amount,
                                    @RequestAttribute("riderId") UUID riderId) {
        return paymentService.initiateRideFarePayment(rideId, riderId, amount);
    }

    @PostMapping("/acknowledge")
    PaymentTransactionView acknowledge(@PathVariable UUID rideId,
                                       @RequestAttribute("riderId") UUID riderId) {
        return paymentService.acknowledgePayment(rideId, riderId);
    }

    @GetMapping
    List<PaymentTransactionView> list(@PathVariable UUID rideId,
                                      @RequestAttribute("riderId") UUID riderId) {
        return paymentService.findByRideId(rideId);
    }
}
