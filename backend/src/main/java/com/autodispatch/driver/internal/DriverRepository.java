package com.autodispatch.driver.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    Optional<Driver> findByWhatsappId(String whatsappId);

    List<Driver> findByStatus(DriverStatus status);

    /**
     * Unconditional status write used by the availability service after its
     * own domain checks (e.g. the ON_RIDE rejection in goOffline).
     */
    @Modifying
    @Query(value = """
            UPDATE drivers
               SET status = :status,
                   updated_at = now()
             WHERE id = :driverId
            """, nativeQuery = true)
    int updateStatus(@Param("driverId") UUID driverId, @Param("status") String status);

    /** WhatsApp 24h session tracking: stamp the latest inbound message time. */
    @Modifying
    @Query(value = """
            UPDATE drivers
               SET last_inbound_at = now(),
                   updated_at = now()
             WHERE id = :driverId
            """, nativeQuery = true)
    int touchLastInbound(@Param("driverId") UUID driverId);

    /**
     * Atomically marks an AVAILABLE driver as ON_RIDE. The conditional WHERE
     * makes concurrent callers race safely: exactly one wins.
     *
     * @return affected row count (1 = won, 0 = driver was not AVAILABLE)
     */
    @Modifying
    @Query(value = """
            UPDATE drivers
               SET status = 'ON_RIDE',
                   updated_at = now()
             WHERE id = :driverId
               AND status = 'AVAILABLE'
            """, nativeQuery = true)
    int markOnRide(@Param("driverId") UUID driverId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE drivers SET verified=TRUE, updated_at=now() WHERE id=:id", nativeQuery = true)
    int verifyDriver(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE drivers SET suspended=TRUE, status='OFFLINE', updated_at=now() WHERE id=:id", nativeQuery = true)
    int suspendDriver(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE drivers SET suspended=FALSE, updated_at=now() WHERE id=:id", nativeQuery = true)
    int unsuspendDriver(@Param("id") UUID id);

    List<Driver> findAllByOrderByCreatedAtDesc();
}
