package com.autodispatch.payment.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.dispatch.api.DispatchApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(scripts = "/sql/payment-auto-post-setup.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/payment-auto-post-teardown.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PaymentAutoPostTest {

    @Autowired DispatchApi dispatchApi;
    @Autowired JdbcTemplate jdbc;

    @Test
    void completing_ride_auto_creates_payment_transaction_and_ledger_entry() {
        UUID rideId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        dispatchApi.markCompleted(rideId);

        int txCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_transactions WHERE ride_id = ?",
                Integer.class, rideId);
        assertThat(txCount).isEqualTo(1);

        String txStatus = jdbc.queryForObject(
                "SELECT status FROM payment_transactions WHERE ride_id = ?",
                String.class, rideId);
        assertThat(txStatus).isEqualTo("PENDING");

        int ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger WHERE ride_id = ?",
                Integer.class, rideId);
        assertThat(ledgerCount).isEqualTo(1);

        BigDecimal finalAmt = jdbc.queryForObject(
                "SELECT final_amount FROM rides WHERE id = ?",
                BigDecimal.class, rideId);
        assertThat(finalAmt).isEqualByComparingTo(new BigDecimal("45.00"));
    }
}
