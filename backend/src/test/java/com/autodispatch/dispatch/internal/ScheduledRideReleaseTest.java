package com.autodispatch.dispatch.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.rider.internal.Rider;
import com.autodispatch.rider.internal.RiderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves that concurrent SCHEDULED→REQUESTED releases via the affected-rows
 * conditional update allow exactly one winner — multi-instance safety gate.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ScheduledRideReleaseTest {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void exactly_one_of_two_concurrent_releases_wins() throws Exception {
        Rider rider = riderRepository.save(new Rider("Release Test Rider", "+919900009999"));
        Ride ride = new Ride(rider.getId(), "Main Gate", "Library",
                null, null, null, null,
                Instant.now().minusSeconds(60));
        UUID rideId = rideRepository.save(ride).getId();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> r1 = pool.submit(() -> {
            start.await();
            return tx.execute(s -> dispatchService.releaseScheduledOne(rideId));
        });
        Future<Boolean> r2 = pool.submit(() -> {
            start.await();
            return tx.execute(s -> dispatchService.releaseScheduledOne(rideId));
        });
        start.countDown();

        boolean win1 = r1.get(15, TimeUnit.SECONDS);
        boolean win2 = r2.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        int wins = (win1 ? 1 : 0) + (win2 ? 1 : 0);
        assertEquals(1, wins, "exactly one concurrent release must transition the ride to REQUESTED");

        Ride after = rideRepository.findById(rideId).orElseThrow();
        assertEquals(RideStatus.REQUESTED, after.getStatus());
    }
}
