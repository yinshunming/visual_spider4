package com.visualspider.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers 配置：所有 Repository 集成测试共享一个 PostgreSQL 容器，
 * 通过 @ContextConfiguration(initializers = ...) 或 @Import 引入。
 *
 * 容器在第一个测试类启动时拉起，后续测试复用，节省 CI 时间。
 */
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("visual_spider4_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            POSTGRES.start();
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword(),
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
            ).applyTo(applicationContext.getEnvironment());
        }
    }
}
