package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.ControlAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Cyber-module ControlAssessment repository.
 *
 * <p>Disambiguated bean name: the F2 unified-control-library sprint introduced
 * a second {@code ControlAssessmentRepository} at
 * {@code module/platform/controls/repository/ControlAssessmentRepository} that
 * wraps a different {@link ControlAssessment} entity. Both interfaces produce
 * the default bean name {@code controlAssessmentRepository}, which Spring
 * rejects on context refresh ("bean ... already defined ... overriding is
 * disabled"). Naming this one {@code cyberControlAssessmentRepository}
 * resolves the collision without touching callers — every consumer injects
 * by interface type and each type has exactly one bean.
 */
@Repository("cyberControlAssessmentRepository")
public interface ControlAssessmentRepository extends JpaRepository<ControlAssessment, Long> {

    /** Tenant-scoped single-row lookup — prevents cross-tenant IDOR (SEC-CYB-001). */
    Optional<ControlAssessment> findByIdAndOrganisationId(Long id, Long orgId);

    Page<ControlAssessment> findByOrganisationId(Long orgId, Pageable pageable);

    List<ControlAssessment> findAllByOrganisationId(Long orgId);

    Page<ControlAssessment> findByOrganisationIdAndControlId(Long orgId, Long controlId, Pageable pageable);
}
