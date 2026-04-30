package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberControl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CyberControlRepository extends JpaRepository<CyberControl, Long> {

    Page<CyberControl> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberControl> findAllByOrganisationId(Long orgId);

    Page<CyberControl> findByOrganisationIdAndFrameworkId(Long orgId, Long frameworkId, Pageable pageable);
}
