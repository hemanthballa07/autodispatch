package com.autodispatch.driver.internal;

import com.autodispatch.TestcontainersConfiguration;
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

/**
 * Same conditional-update race as claimRide, for driver availability:
 * 10 threads mark the same AVAILABLE driver ON_RIDE — exactly one wins.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DriverMarkOnRideConcurrencyTest {

    private static final int THREADS = 10;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void exactly_one_of_ten_concurrent_markOnRide_calls_wins() throws Exception {
        UUID driverId = driverRepository.save(new Driver(
                "Race Driver", "+919800002000", "KA-01-R1", DriverStatus.AVAILABLE, true)).getId();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = IntStream.range(0, THREADS)
                .mapToObj(i -> pool.submit(() -> {
                    start.await();
                    return tx.execute(s -> driverRepository.markOnRide(driverId));
                }))
                .toList();
        start.countDown();

        int wins = 0;
        for (Future<Integer> f : results) {
            wins += f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, wins, "exactly one markOnRide must return rowcount 1");
        assertEquals(DriverStatus.ON_RIDE,
                driverRepository.findById(driverId).orElseThrow().getStatus());
    }
}
