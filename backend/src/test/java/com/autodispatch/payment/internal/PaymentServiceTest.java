package com.autodispatch.payment.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.payment.api.DriverLedgerService;
import com.autodispatch.payment.api.LedgerEntryView;
import com.autodispatch.payment.api.PaymentService;
import com.autodispatch.payment.api.PaymentStatus;
import com.autodispatch.payment.api.PaymentTransactionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 10 payment service gate tests (service layer, direct injection).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private DriverLedgerService ledgerService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID riderId;
    private UUID rideId;
    private UUID driverId;

    @BeforeEach
    void seed() {
        riderId = UUID.randomUUID();
        rideId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        String phone = "+91" + (7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        String waId = "+91" + (9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        jdbc.update("INSERT INTO riders (id, name, phone, created_at) VALUES (?,?,?,now())",
                riderId, "Pay Test Rider", phone);
        jdbc.update("INSERT INTO drivers (id, name, whatsapp_id, vehicle_no, status, verified, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,now(),now())",
                driverId, "Ledger Driver", waId, "KA-01-9999", "OFFLINE", true);
        jdbc.update("INSERT INTO rides (id, rider_id, status, pickup_label, drop_label, fare_amount, requested_at) "
                + "VALUES (?,?,?,?,?,?,now())",
                rideId, riderId, "COMPLETED", "Main gate", "Library", new BigDecimal("30.00"));
    }

    @Test
    void initiate_creates_pending_cash_transaction() {
        PaymentTransactionView tx = paymentService.initiateRideFarePayment(rideId, riderId, new BigDecimal("30.00"));

        assertNotNull(tx.id());
        assertEquals(rideId, tx.rideId());
        assertEquals(riderId, tx.riderId());
        assertEquals("RIDE_FARE", tx.type());
        assertEquals(PaymentStatus.PENDING, tx.status());
        assertEquals(0, tx.amount().compareTo(new BigDecimal("30.00")));
        assertNotNull(tx.createdAt());
    }

    @Test
    void acknowledge_transitions_to_collected() {
        paymentService.initiateRideFarePayment(rideId, riderId, new BigDecimal("30.00"));

        PaymentTransactionView ack = paymentService.acknowledgePayment(rideId, riderId);

        assertEquals(PaymentStatus.COLLECTED, ack.status());
        assertNotNull(ack.acknowledgedAt());
    }

    @Test
    void apply_cancellation_fee_creates_separate_transaction() {
        PaymentTransactionView fee = paymentService.applyRiderCancellationFee(
                rideId, riderId, new BigDecimal("10.00"));

        assertEquals("CANCELLATION_FEE", fee.type());
        assertEquals(PaymentStatus.PENDING, fee.status());
        assertEquals(0, fee.amount().compareTo(new BigDecimal("10.00")));
    }

    @Test
    void find_by_ride_id_returns_all_transactions() {
        paymentService.initiateRideFarePayment(rideId, riderId, new BigDecimal("30.00"));
        paymentService.applyRiderCancellationFee(rideId, riderId, new BigDecimal("10.00"));

        List<PaymentTransactionView> txs = paymentService.findByRideId(rideId);

        assertEquals(2, txs.size());
    }

    @Test
    void acknowledge_checks_rider_ownership() {
        UUID otherRiderId = UUID.randomUUID();
        paymentService.initiateRideFarePayment(rideId, riderId, new BigDecimal("30.00"));

        assertThrows(IllegalArgumentException.class,
                () -> paymentService.acknowledgePayment(rideId, otherRiderId));
    }

    @Test
    void acknowledge_without_initiation_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> paymentService.acknowledgePayment(rideId, riderId));
    }

    // ---- DriverLedgerService ---------------------------------------------------

    @Test
    void credit_earning_stores_positive_amount() {
        LedgerEntryView entry = ledgerService.creditEarning(driverId, rideId, new BigDecimal("30.00"));

        assertNotNull(entry.id());
        assertEquals(driverId, entry.driverId());
        assertEquals(rideId, entry.rideId());
        assertEquals("EARNING", entry.type());
        assertEquals(0, entry.amount().compareTo(new BigDecimal("30.00")));
        assertNotNull(entry.createdAt());
    }

    @Test
    void debit_cancellation_penalty_stores_negative_amount() {
        LedgerEntryView entry = ledgerService.debitCancellationPenalty(driverId, rideId, new BigDecimal("15.00"));

        assertEquals("CANCELLATION_PENALTY", entry.type());
        assertEquals(0, entry.amount().compareTo(new BigDecimal("-15.00")));
    }

    @Test
    void statement_returns_entries_in_descending_order() {
        ledgerService.creditEarning(driverId, rideId, new BigDecimal("30.00"));
        ledgerService.debitCancellationPenalty(driverId, rideId, new BigDecimal("10.00"));

        List<LedgerEntryView> entries = ledgerService.statement(driverId, 0, 10);

        assertEquals(2, entries.size());
        // most recent first — penalty inserted after earning, so it should be first
        assertTrue(entries.get(0).createdAt().compareTo(entries.get(1).createdAt()) >= 0,
                "entries must be newest-first");
    }
}
