package com.visualspider.service;

import com.visualspider.dto.HealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

@Service
@Slf4j
public class HealthService {

    private static final String MESSAGE_PREFIX = "PG_NOT_READY: ";

    private final DataSource dataSource;

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HealthResponse checkHealth() {
        DbCheckResult result = checkDatabase();
        String timestamp = Instant.now().toString();
        return new HealthResponse("UP", result.status, timestamp, result.message);
    }

    private DbCheckResult checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(1)) {
                return DbCheckResult.up();
            }
            DbCheckResult down = DbCheckResult.down("PG_NOT_READY: 数据库连接无效（isValid 返回 false）。请检查本机 PostgreSQL 服务状态，详见 docs/runbook.md。");
            log.warn(down.message);
            return down;
        } catch (SQLException e) {
            DbCheckResult down = translateSqlException(e);
            log.error(buildBanner(down.message), e);
            return down;
        } catch (Exception e) {
            DbCheckResult down = DbCheckResult.down(MESSAGE_PREFIX + "数据库连接异常：" + e.getClass().getSimpleName() + "，详见 docs/runbook.md。");
            log.error(buildBanner(down.message), e);
            return down;
        }
    }

    private DbCheckResult translateSqlException(SQLException e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        if (raw.contains("Connection refused") || raw.contains("Connection reset")
                || raw.contains("the database system is starting up")
                || raw.contains("the database system is shutting down")) {
            return DbCheckResult.down(
                    MESSAGE_PREFIX + "PostgreSQL 未启动，请手工启动本机服务（默认端口 5432，库 visual_spider4，用户 postgres，密码 123456）。"
                            + "启动后请告诉我继续。详见 docs/runbook.md。");
        }
        if (raw.contains("FATAL: database") && raw.contains("does not exist")) {
            return DbCheckResult.down(
                    MESSAGE_PREFIX + "数据库 visual_spider4 不存在，请先 `psql -U postgres -c 'CREATE DATABASE visual_spider4;'`。"
                            + "（测试库还需 `CREATE DATABASE visual_spider4_test;`）");
        }
        if (raw.contains("password authentication failed")) {
            return DbCheckResult.down(
                    MESSAGE_PREFIX + "PostgreSQL 密码错误（默认期望 postgres/123456）。"
                            + "可设置环境变量 DB_USERNAME / DB_PASSWORD 覆盖，详见 application.yml。");
        }
        return DbCheckResult.down(MESSAGE_PREFIX + "数据库连接失败：" + sanitize(raw) + "，详见 docs/runbook.md。");
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
    }

    private String buildBanner(String message) {
        return "\n"
                + "================================================================\n"
                + "  " + message + "\n"
                + "================================================================";
    }

    private record DbCheckResult(String status, String message) {
        static DbCheckResult up() {
            return new DbCheckResult("UP", null);
        }

        static DbCheckResult down(String message) {
            return new DbCheckResult("DOWN", message);
        }
    }
}
