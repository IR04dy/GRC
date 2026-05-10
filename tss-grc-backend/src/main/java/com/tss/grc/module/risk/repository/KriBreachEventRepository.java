package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.KriBreachEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KriBreachEventRepository extends JpaRepository<KriBreachEvent, Long> {
    List<KriBreachEvent> findByKriIdOrderByBreachedAtDesc(Long kriId);
    List<KriBreachEvent> findByKriIdAndStatusOrderByBreachedAtDesc(Long kriId, String status);
}
