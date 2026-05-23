package com.tss.grc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

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
        // Pin the JVM default timezone to Asia/Riyadh before Spring starts.
        //
        // The platform is Saudi-only — every LocalDate.now() / LocalDateTime.now()
        // call (100+ across services) returns a date in JVM-default TZ. The
        // production Docker image runs Alpine which defaults to UTC; without
        // this pin, between 9pm UTC (12am Riyadh) and midnight UTC (3am Riyadh)
        // every LocalDate.now() returns YESTERDAY's date from the user's
        // perspective — wrong audit findings due dates, wrong SLA windows,
        // wrong committee resolution review-due banners, wrong overdue
        // sweep triggers. This is a 3-hours/day silent data-quality bug.
        //
        // Honour an explicit -Duser.timezone= JVM flag if the operator wants
        // to override (e.g. a multi-tenant deployment serving non-Saudi orgs
        // in the future). The Dockerfile already sets it via JAVA_OPTS for
        // defense-in-depth; this @main hook is the safety net for non-Docker
        // launches (mvn spring-boot:run, IDE, plain java -jar).
        if (System.getProperty("user.timezone") == null
         || System.getProperty("user.timezone").isBlank()) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"));
            System.setProperty("user.timezone", "Asia/Riyadh");
        }
        SpringApplication.run(GrcApplication.class, args);
    }
}
