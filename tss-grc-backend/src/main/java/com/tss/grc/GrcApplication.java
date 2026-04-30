package com.tss.grc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TSS GRC Platform — Main Application Entry Point
 *
 * <p>Trusted Security Solutions - Enterprise Governance, Risk and Compliance Platform
 * Supporting Arabic/English, modular architecture, and Saudi regulatory compliance.
 *
 * <p>Target: Spring Boot 3.5, Java 17+, SQL Server 2019 Express Edition
 */
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableCaching
@EnableAsync
@EnableScheduling
public class GrcApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrcApplication.class, args);
    }
}
