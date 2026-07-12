package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.EnvPermitViolation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnvPermitViolationRepository extends JpaRepository<EnvPermitViolation, Long> {

    List<EnvPermitViolation> findByOrganisationIdAndPermitIdAndIsDeletedFalseOrderByViolationDateDesc(
        Long organisationId, Long permitId);
}
