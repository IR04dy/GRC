package com.tss.grc.module.ethics.whistleblower.repository;

import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerRetaliationComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhistleblowerRetaliationComplaintRepository
        extends JpaRepository<WhistleblowerRetaliationComplaint, Long> {

    List<WhistleblowerRetaliationComplaint>
            findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    List<WhistleblowerRetaliationComplaint>
            findByOrganisationIdAndRelatedCaseIdAndIsDeletedFalseOrderByCreatedAtDesc(
                    Long organisationId, Long relatedCaseId);

    Optional<WhistleblowerRetaliationComplaint>
            findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);
}
