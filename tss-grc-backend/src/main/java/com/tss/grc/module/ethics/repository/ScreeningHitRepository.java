package com.tss.grc.module.ethics.repository;

import com.tss.grc.module.ethics.entity.ScreeningHit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningHitRepository extends JpaRepository<ScreeningHit, Long> {
    List<ScreeningHit> findBySubjectIdOrderByScreenedAtDesc(Long subjectId);
}
