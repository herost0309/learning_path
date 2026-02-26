package com.example.awssqs;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests with Testcontainers
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // MySQL Testcontainer
    protected static final MySQLContainer<?> mysqlContainer =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("sqs_demo_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        mysqlContainer.start();
        System.setProperty("spring.datasource.url", mysqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", mysqlContainer.getUsername());
        System.setProperty("spring.datasource.password", mysqlContainer.getPassword());
    }
}
