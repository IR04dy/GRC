package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.AppetiteBreachEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppetiteBreachEventRepository extends JpaRepository<AppetiteBreachEvent, Long> {
    List<AppetiteBreachEvent> findByAppetiteIdOrderByBreachedAtDesc(Long appetiteId);

    /** E-H — dedup guard: an unacknowledged (OPEN) breach already on file, so a
     *  repeated exposure recompute doesn't spam duplicate breaches + F8 events. */
    boolean existsByAppetiteIdAndStatus(Long appetiteId, String status);
}
