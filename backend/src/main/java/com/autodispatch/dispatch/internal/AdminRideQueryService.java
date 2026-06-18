package com.autodispatch.dispatch.internal;

import com.autodispatch.dispatch.api.AdminRideQueries;
import com.autodispatch.dispatch.api.AdminRideView;
import com.autodispatch.dispatch.api.RideStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class AdminRideQueryService implements AdminRideQueries {

    private static final String BASE_SQL = """
            SELECT r.id, r.rider_id, ri.name AS rider_name, ri.phone AS rider_phone,
                   r.driver_id, d.name AS driver_name, d.whatsapp_id AS driver_whatsapp_id,
                   d.vehicle_no AS driver_vehicle_no,
                   r.pickup_label, r.drop_label, r.status,
                   r.fare_amount, r.requested_at, r.assigned_at, r.completed_at, r.cancel_reason
              FROM rides r
              LEFT JOIN riders ri ON r.rider_id = ri.id
              LEFT JOIN drivers d ON r.driver_id = d.id
            """;

    private static final RowMapper<AdminRideView> ROW_MAPPER = (rs, rowNum) -> new AdminRideView(
            rs.getObject("id", UUID.class),
            rs.getObject("rider_id", UUID.class),
            rs.getString("rider_name"),
            rs.getString("rider_phone"),
            rs.getObject("driver_id", UUID.class),
            rs.getString("driver_name"),
            rs.getString("driver_whatsapp_id"),
            rs.getString("driver_vehicle_no"),
            rs.getString("pickup_label"),
            rs.getString("drop_label"),
            rs.getString("status"),
            rs.getBigDecimal("fare_amount"),
            rs.getTimestamp("requested_at") != null ? rs.getTimestamp("requested_at").toInstant() : null,
            rs.getTimestamp("assigned_at") != null ? rs.getTimestamp("assigned_at").toInstant() : null,
            rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
            rs.getString("cancel_reason")
    );

    private final RideRepository rideRepository;
    private final JdbcTemplate jdbc;

    AdminRideQueryService(RideRepository rideRepository, JdbcTemplate jdbc) {
        this.rideRepository = rideRepository;
        this.jdbc = jdbc;
    }

    @Override
    public List<AdminRideView> listRides(String status, LocalDate date, int page, int size) {
        if (status != null) {
            RideStatus.valueOf(status); // throws IAE on unknown status name
        }
        StringBuilder sql = new StringBuilder(BASE_SQL).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            sql.append(" AND r.status = ?");
            params.add(status);
        }
        if (date != null) {
            sql.append(" AND r.requested_at >= ? AND r.requested_at < ?");
            params.add(java.sql.Timestamp.from(date.atStartOfDay(ZoneOffset.UTC).toInstant()));
            params.add(java.sql.Timestamp.from(date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        sql.append(" ORDER BY r.requested_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add((long) page * size);
        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Override
    public Optional<AdminRideView> findRideById(UUID rideId) {
        String sql = BASE_SQL + " WHERE r.id = ?";
        List<AdminRideView> results = jdbc.query(sql, ROW_MAPPER, rideId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public RideStats countStats() {
        long active = rideRepository.countByStatusIn(RideBookingService.ACTIVE_STATUSES);
        long completedToday = rideRepository.countByStatusAndCompletedAtAfter(
                RideStatus.COMPLETED,
                LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant());
        return new RideStats(active, completedToday);
    }
}
