package com.example.awssqs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AWS SQS Message Loss Prevention Demo Application
 *
 * Demonstrates:
 * 1. Message persistence before sending
 * 2. Manual acknowledge mode
 * 3. Dead Letter Queue (DLQ) handling
 * 4. Message timeout detection and replay
 * 5. Periodic reconciliation
 */
@SpringBootApplication
@EnableScheduling
public class AwsSqsDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AwsSqsDemoApplication.class, args);
        System.out.println("AWS SQS Message Loss Prevention Demo Started");
        System.out.println("===============================================");
        System.out.println("Features Demonstrated:");
        System.out.println("1. Message persistence before sending");
        System.out.println("2. Manual acknowledge mode");
        System.out.println("3. Dead Letter Queue (DLQ) handling");
        System.out.println("4. Message timeout detection and replay");
        System.out.println("5. Periodic reconciliation");
        System.out.println("===============================================");
    }
}
