package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.KriReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KriReadingRepository extends JpaRepository<KriReading, Long> {
    List<KriReading> findTop60ByKriIdOrderByReadingAtDesc(Long kriId);
    Optional<KriReading> findFirstByKriIdOrderByReadingAtDesc(Long kriId);
}
