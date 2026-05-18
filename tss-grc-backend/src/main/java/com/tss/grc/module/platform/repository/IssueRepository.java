package com.tss.grc.module.platform.repository;

import com.tss.grc.module.platform.entity.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    Page<Issue> findByOrganisationIdOrderByIdDesc(Long organisationId, Pageable pageable);

    Optional<Issue> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<Issue> findByOrganisationIdAndIssueCode(Long organisationId, String issueCode);

    List<Issue> findByOrganisationIdAndStatusOrderByDueDateAsc(Long organisationId, String status);

    List<Issue> findByOrganisationIdAndSourceModuleOrderByIdDesc(Long organisationId, String sourceModule);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
}
