package com.tss.grc.module.sust.capa.repository;

import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Long> {

    Page<CorrectiveAction> findByOrganisationIdOrderByTargetDateAscIdDesc(Long org, Pageable p);

    Page<CorrectiveAction> findByOrganisationIdAndStatusOrderByTargetDateAscIdDesc(Long org, String status, Pageable p);

    Page<CorrectiveAction> findByOrganisationIdAndSourceTypeOrderByTargetDateAscIdDesc(Long org, String sourceType, Pageable p);

    Page<CorrectiveAction> findByOrganisationIdAndPriorityOrderByTargetDateAscIdDesc(Long org, String priority, Pageable p);

    Optional<CorrectiveAction> findByIdAndOrganisationId(Long id, Long org);

    /** All linked to one source — e.g. show all actions raised against CRO-001. */
    List<CorrectiveAction> findByOrganisationIdAndSourceTypeAndSourceRef(Long org, String sourceType, String sourceRef);

    /** All open + in-progress actions — used by the inbox / aggregate view. */
    List<CorrectiveAction> findByOrganisationIdAndStatusIn(Long org, List<String> statuses);
}
