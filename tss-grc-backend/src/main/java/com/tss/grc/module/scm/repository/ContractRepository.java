package com.tss.grc.module.scm.repository;

import com.tss.grc.module.scm.entity.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    Page<Contract> findByOrganisationId(Long organisationId, Pageable pageable);
    List<Contract> findByOrganisationIdAndEndDateBefore(Long organisationId, LocalDate before);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
