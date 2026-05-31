package com.visualspider.service;

import com.visualspider.dto.HealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

@Service
@Slf4j
public class HealthService {

    private final DataSource dataSource;

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HealthResponse checkHealth() {
        String databaseStatus = checkDatabase();
        String timestamp = Instant.now().toString();
        return new HealthResponse("UP", databaseStatus, timestamp);
    }

    private String checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(1) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.error("Database connection check failed", e);
            return "DOWN";
        }
    }
}
