package com.visualspider.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthService")
class HealthServiceTest {

    @Mock
    private DataSource dataSource;

    @InjectMocks
    private HealthService healthService;

    @Test
    @DisplayName("PG Connection refused 时 message 含 'PostgreSQL 未启动'")
    void checkHealth_pgRefused_messageContainsNotStartedHint() throws SQLException {
        when(dataSource.getConnection())
                .thenThrow(new SQLException("Connection refused: connect"));

        var response = healthService.checkHealth();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.database()).isEqualTo("DOWN");
        assertThat(response.message()).contains("PostgreSQL 未启动");
    }

    @Test
    @DisplayName("数据库不存在时 message 含 'CREATE DATABASE'")
    void checkHealth_databaseNotExist_messageContainsCreateDbHint() throws SQLException {
        when(dataSource.getConnection())
                .thenThrow(new SQLException("FATAL: database \"visual_spider4\" does not exist"));

        var response = healthService.checkHealth();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.database()).isEqualTo("DOWN");
        assertThat(response.message()).contains("CREATE DATABASE");
    }

    @Test
    @DisplayName("PG 正常时 message 为 null")
    void checkHealth_pgUp_messageIsNull() throws SQLException {
        Connection conn = mock(Connection.class);
        when(conn.isValid(1)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(conn);

        var response = healthService.checkHealth();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.database()).isEqualTo("UP");
        assertThat(response.message()).isNull();
    }
}
