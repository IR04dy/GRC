package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.ForensicsCustodyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SOC-D — read + append access to the forensic chain-of-custody ledger.
 * No update/delete finders by design: the ledger is append-only (also enforced
 * by the {@code trg_fce_append_only} DB trigger).
 */
@Repository
public interface ForensicsCustodyEventRepository extends JpaRepository<ForensicsCustodyEvent, Long> {

    /** The full ordered chain for one artefact (tenant-scoped). */
    List<ForensicsCustodyEvent> findByOrganisationIdAndArtefactIdOrderBySeqNoAsc(Long organisationId, Long artefactId);

    /** Highest sequence number already recorded for an artefact (0 if none) — the
     *  next appended row uses {@code maxSeq + 1}. */
    @Query("SELECT COALESCE(MAX(e.seqNo), 0) FROM ForensicsCustodyEvent e "
         + "WHERE e.organisationId = :orgId AND e.artefactId = :artefactId")
    int maxSeqNo(@Param("orgId") Long orgId, @Param("artefactId") Long artefactId);
}
