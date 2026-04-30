package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberFramework;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CyberFrameworkRepository extends JpaRepository<CyberFramework, Long> {

    Page<CyberFramework> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberFramework> findAllByOrganisationId(Long orgId);
}
