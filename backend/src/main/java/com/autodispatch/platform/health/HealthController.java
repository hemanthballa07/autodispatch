package com.autodispatch.platform.health;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness summary for the app and its two backing stores.
 * Returns 200 when everything is UP, 503 otherwise.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(JdbcTemplate jdbcTemplate, RedisConnectionFactory redisConnectionFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        String db = checkDb();
        String redis = checkRedis();
        boolean allUp = "UP".equals(db) && "UP".equals(redis);

        Map<String, String> body = Map.of(
                "app", "UP",
                "db", db,
                "redis", redis);
        return ResponseEntity.status(allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private String checkDb() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkRedis() {
        try {
            var connection = RedisConnectionUtils.getConnection(redisConnectionFactory);
            try {
                String pong = connection.ping();
                return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
            } finally {
                RedisConnectionUtils.releaseConnection(connection, redisConnectionFactory);
            }
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
