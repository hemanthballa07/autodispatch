package com.autodispatch.dispatch.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.driver.internal.Driver;
import com.autodispatch.driver.internal.DriverRepository;
import com.autodispatch.driver.internal.DriverStatus;
import com.autodispatch.rider.internal.Rider;
import com.autodispatch.rider.internal.RiderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the atomic-claim contract under real concurrency against Postgres:
 * 10 simultaneous claims for one BROADCASTING ride — exactly one wins.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RideClaimConcurrencyTest {

    private static final int THREADS = 10;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void exactly_one_of_ten_concurrent_claims_wins() throws Exception {
        Rider rider = riderRepository.save(new Rider("Claim Test Rider", "+919900000001"));
        List<Driver> drivers = IntStream.range(0, THREADS)
                .mapToObj(i -> driverRepository.save(new Driver(
                        "Claim Driver " + i, "+9198000010" + i, "KA-01-C" + i,
                        DriverStatus.AVAILABLE, true)))
                .toList();

        Ride ride = new Ride(rider.getId(), "Main Gate", "Library");
        ride.transitionTo(RideStatus.BROADCASTING);
        UUID rideId = rideRepository.save(ride).getId();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = drivers.stream()
                .map(driver -> pool.submit(() -> {
                    start.await();
                    return tx.execute(s -> rideRepository.claimRide(rideId, driver.getId()));
                }))
                .toList();
        start.countDown();

        int wins = 0;
        int losses = 0;
        for (Future<Integer> f : results) {
            int rowCount = f.get(15, TimeUnit.SECONDS);
            if (rowCount == 1) {
                wins++;
            } else {
                assertEquals(0, rowCount, "row count can only be 0 or 1");
                losses++;
            }
        }
        pool.shutdown();

        assertEquals(1, wins, "exactly one claim must return rowcount 1");
        assertEquals(THREADS - 1, losses, "the other nine must return 0");

        Ride after = rideRepository.findById(rideId).orElseThrow();
        assertEquals(RideStatus.ASSIGNED, after.getStatus());
        assertNotNull(after.getDriverId(), "winning driver must be recorded");
        assertNotNull(after.getAssignedAt());
        assertTrue(drivers.stream().anyMatch(d -> d.getId().equals(after.getDriverId())),
                "assigned driver must be one of the claimers");
    }
}
