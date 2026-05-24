package com.tss.grc.module.ethics.whistleblower.repository;

import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCaseUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WhistleblowerCaseUpdateRepository extends JpaRepository<WhistleblowerCaseUpdate, Long> {

    List<WhistleblowerCaseUpdate> findByOrganisationIdAndCaseIdAndIsDeletedFalseOrderByCreatedAtAsc(
            Long organisationId, Long caseId);

    /**
     * Public-portal timeline — does NOT filter by tenant because the caller
     * resolved the case via its public submission token. Restricts to
     * reporter-visible rows only.
     */
    List<WhistleblowerCaseUpdate> findByCaseIdAndIsVisibleToReporterTrueAndIsDeletedFalseOrderByCreatedAtAsc(Long caseId);
}
