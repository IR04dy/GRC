package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberSubControl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CyberSubControlRepository extends JpaRepository<CyberSubControl, Long> {

    Page<CyberSubControl> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberSubControl> findAllByOrganisationId(Long orgId);

    Page<CyberSubControl> findByOrganisationIdAndControlId(Long orgId, Long controlId, Pageable pageable);

    List<CyberSubControl> findAllByOrganisationIdAndControlId(Long orgId, Long controlId);
}
