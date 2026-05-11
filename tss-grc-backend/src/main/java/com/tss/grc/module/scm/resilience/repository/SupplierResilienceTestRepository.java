package com.tss.grc.module.scm.resilience.repository;

import com.tss.grc.module.scm.resilience.entity.SupplierResilienceTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierResilienceTestRepository extends JpaRepository<SupplierResilienceTest, Long> {
    Page<SupplierResilienceTest> findByOrganisationIdOrderByScheduledForDescIdDesc(Long org, Pageable p);
    Page<SupplierResilienceTest> findByOrganisationIdAndStatusOrderByScheduledForDescIdDesc(Long org, String status, Pageable p);
    Optional<SupplierResilienceTest> findByIdAndOrganisationId(Long id, Long org);
}
