package com.tss.grc.module.ethics.repository;

import com.tss.grc.module.ethics.entity.ScreeningHit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Ethics-module screening hit repository (legacy whistleblower screening).
 *
 * <p>Named explicitly to avoid a bean-name collision with the AML Sanctions
 * Screening repository ({@code com.tss.grc.module.aml.sanctions.repository.ScreeningHitRepository},
 * Sprint 13 PR #215). Spring Data derives bean names from the simple class
 * name, so two repositories named {@code ScreeningHitRepository} in different
 * packages would otherwise refuse to co-exist with bean-definition overriding
 * disabled.
 */
@Repository("ethicsScreeningHitRepository")
public interface ScreeningHitRepository extends JpaRepository<ScreeningHit, Long> {
    List<ScreeningHit> findBySubjectIdOrderByScreenedAtDesc(Long subjectId);
}
