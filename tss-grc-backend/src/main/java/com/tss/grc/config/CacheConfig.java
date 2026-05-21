package com.tss.grc.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * Pre-declares every cache name the platform uses. Without this list
     * Caffeine creates caches lazily on first access — which works, but
     * Spring's licensing/permissions caches WOULD then fall through to
     * "no cache" the very first time and log a WARN per request until
     * the cache materialises. JWT issuance specifically called the
     * `licenses` cache on every login → one WARN per login until first
     * write. We now register it up front.
     *
     * <p>{@code recordStats()} is also enabled so the Micrometer
     * Caffeine metrics show hit/miss counts in /actuator/metrics —
     * the platform was logging "not recording statistics" without it.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            "dashboardStats",
            "heatmapData",
            "riskByCategory",
            "permissions",
            // F9 module-licensing cache — read on every JWT issuance.
            "licenses"
        );
        manager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
        );
        return manager;
    }
}
