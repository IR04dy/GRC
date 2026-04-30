package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.DataBreach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataBreachRepository extends JpaRepository<DataBreach, Long> {
    Page<DataBreach> findByOrganisationId(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
