package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.metrics.entity.EsgMetric;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Roadmap closure (plan §0 item 10) — the ISAE 3410 assurance-lineage query: one
 * UNION over every assurable disclosure object of a reporting year, projecting
 * (object_type, code, name, has_data, has_evidence, verified). Evidence = the
 * V250 {@code evidence_item_id} FK or a legacy {@code evidence_ref}; "verified"
 * follows each object's own attestation state (GHG lifecycle, CMA/TCFD approval);
 * NULL where the concept doesn't apply (metrics, SGI).
 */
public interface AssuranceLineageRepository extends Repository<EsgMetric, Long> {

    @Query(value = """
        SELECT 'ESG_METRIC' AS object_type, m.metric_code AS code, m.metric_name_en AS name_en,
               CASE WHEN EXISTS (SELECT 1 FROM sust.esg_metric_reading r
                                 WHERE r.organisation_id = m.organisation_id AND r.metric_id = m.id)
                    THEN 1 ELSE 0 END AS has_data,
               CASE WHEN m.evidence_item_id IS NOT NULL OR NULLIF(m.evidence_ref, '') IS NOT NULL
                    THEN 1 ELSE 0 END AS has_evidence,
               CAST(NULL AS INT) AS verified
        FROM sust.esg_metric m
        WHERE m.organisation_id = :org AND m.reporting_year = :year AND m.is_deleted = 0 AND m.status = 'ACTIVE'
        UNION ALL
        SELECT 'GHG_RECORD', CONCAT('S', g.scope, '-', g.id), g.source_name_en,
               1,
               CASE WHEN g.evidence_item_id IS NOT NULL OR NULLIF(g.evidence_ref, '') IS NOT NULL
                    THEN 1 ELSE 0 END,
               CASE WHEN g.status IN ('VERIFIED', 'APPROVED') THEN 1 ELSE 0 END
        FROM sust.ghg_emission_record g
        WHERE g.organisation_id = :org AND g.reporting_year = :year AND g.is_deleted = 0
        UNION ALL
        SELECT 'CMA_DISCLOSURE', c.article, c.title_en,
               CASE WHEN c.disclosure_status IN ('DISCLOSED', 'PARTIAL') THEN 1 ELSE 0 END,
               CASE WHEN c.evidence_item_id IS NOT NULL OR NULLIF(c.evidence_ref, '') IS NOT NULL
                    THEN 1 ELSE 0 END,
               CASE WHEN c.approval_status = 'APPROVED' THEN 1 ELSE 0 END
        FROM sust.cma_disclosure c
        WHERE c.organisation_id = :org AND c.reporting_year = :year AND c.is_deleted = 0
        UNION ALL
        SELECT 'TCFD_ITEM', CONCAT(t.pillar, '-', t.recommendation_code), t.title_en,
               CASE WHEN t.disclosure_status IN ('DISCLOSED', 'PARTIAL') THEN 1 ELSE 0 END,
               CASE WHEN t.evidence_item_id IS NOT NULL OR NULLIF(t.evidence_ref, '') IS NOT NULL
                    THEN 1 ELSE 0 END,
               CASE WHEN t.approval_status = 'APPROVED' THEN 1 ELSE 0 END
        FROM sust.tcfd_item t
        WHERE t.organisation_id = :org AND t.reporting_year = :year AND t.is_deleted = 0
        UNION ALL
        SELECT 'SGI_COMMITMENT', s.commitment_code, s.title_en,
               1,
               CASE WHEN s.evidence_item_id IS NOT NULL OR NULLIF(s.evidence_ref, '') IS NOT NULL
                    THEN 1 ELSE 0 END,
               CAST(NULL AS INT)
        FROM sust.sgi_commitment s
        WHERE s.organisation_id = :org AND s.is_deleted = 0 AND s.status = 'ACTIVE'
        ORDER BY object_type, code
        """, nativeQuery = true)
    List<Object[]> lineageRows(@Param("org") Long org, @Param("year") Short year);
}
