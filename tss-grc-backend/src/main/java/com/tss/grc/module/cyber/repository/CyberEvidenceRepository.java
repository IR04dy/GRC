package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberEvidence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CyberEvidenceRepository extends JpaRepository<CyberEvidence, Long> {

    Page<CyberEvidence> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberEvidence> findAllByOrganisationId(Long orgId);
}
