package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.VulnException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VulnExceptionRepository extends JpaRepository<VulnException, Long> {

    Page<VulnException> findByOrganisationId(Long orgId, Pageable pageable);

    List<VulnException> findAllByOrganisationId(Long orgId);
}
