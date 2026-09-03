package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmExerciseScenario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Scenario-library repository (Slice 10-A extends the original
 * V111 catalog).
 *
 * <p>The library is currently a global catalog — {@code is_system = 1}
 * rows seeded once, shared across every tenant — so finders do NOT
 * carry {@code organisationId}. Tenant isolation kicks in at the
 * instantiation step (the new exercise row is org-scoped) and at the
 * usage-count increment (audit + F8 emit go through TenantContext).
 *
 * <p>Soft-delete is enforced at the entity level via {@code @Where}.
 */
public interface BcmExerciseScenarioRepository
    extends JpaRepository<BcmExerciseScenario, Long> {

    // ─── Legacy finders (V111 catalog) ──────────────────────────────────
    List<BcmExerciseScenario> findByScenarioCategoryOrderByTitleEnAsc(String category);
    List<BcmExerciseScenario> findByExerciseTypeOrderByTitleEnAsc(String type);
    List<BcmExerciseScenario> findAllByOrderByTitleEnAsc();
    Optional<BcmExerciseScenario> findByScenarioCode(String code);

    // ─── Slice 10-A — library filters ───────────────────────────────────

    List<BcmExerciseScenario> findByScenarioTypeInOrderByTitleEnAsc(List<String> scenarioTypes);

    List<BcmExerciseScenario> findByComplexityOrderByTitleEnAsc(String complexity);

    /**
     * Top-N most-instantiated scenarios across all tenants. Powers the
     * "Popular this quarter" pinned row at the top of the library page.
     */
    List<BcmExerciseScenario> findTop20ByOrderByUsageCountDescTitleEnAsc();

    /**
     * Free-text containing search across the bilingual titles. Service
     * layer applies the Lucene-style filtering, this query is just the
     * blunt LIKE-fallback that works on every collation.
     */
    @Query("""
        SELECT s FROM BcmExerciseScenario s
        WHERE (:q IS NULL OR LOWER(s.titleEn) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                          OR LOWER(s.titleAr) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                          OR LOWER(s.scenarioCode) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\')
        ORDER BY s.titleEn ASC
        """)
    Page<BcmExerciseScenario> search(@Param("q") String q, Pageable pageable);

    /**
     * Combined filter — used by the library page. All filters are
     * optional; passing {@code null} for any one of them is a wildcard.
     */
    @Query("""
        SELECT s FROM BcmExerciseScenario s
        WHERE (:type IS NULL OR s.scenarioType = :type)
          AND (:complexity IS NULL OR s.complexity = :complexity)
          AND (:framework IS NULL OR s.frameworkAlignment LIKE CONCAT('%', :framework, '%') ESCAPE '\\')
          AND (:q IS NULL OR LOWER(s.titleEn) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                          OR LOWER(s.titleAr) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
                          OR LOWER(s.scenarioCode) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\')
        ORDER BY s.usageCount DESC, s.titleEn ASC
        """)
    Page<BcmExerciseScenario> filter(
        @Param("type") String scenarioType,
        @Param("complexity") String complexity,
        @Param("framework") String framework,
        @Param("q") String q,
        Pageable pageable);

    /**
     * Recommended-for-framework query — returns the scenarios whose
     * {@code framework_alignment} string contains the requested code
     * (e.g. {@code SAMA_CSF}, {@code NCA_ECC2}, {@code PDPL}).
     */
    @Query("""
        SELECT s FROM BcmExerciseScenario s
        WHERE s.frameworkAlignment LIKE CONCAT('%', :framework, '%') ESCAPE '\\'
        ORDER BY s.usageCount DESC, s.titleEn ASC
        """)
    List<BcmExerciseScenario> findByFrameworkAlignmentContaining(@Param("framework") String framework);
}
