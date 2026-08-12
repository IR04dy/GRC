package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.DataSubjectRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, Long> {
    Page<DataSubjectRequest> findByOrganisationId(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);

    /**
     * Open-request count as the complement of the terminal set — pass
     * {@link com.tss.grc.module.privacy.DsrRequestLifecycle#TERMINAL_LIST}. F2-196: the
     * caller used to sum four named open statuses, which dropped every status nobody had
     * remembered to name.
     */
    long countByOrganisationIdAndStatusNotIn(Long organisationId, java.util.List<String> terminalStatuses);
}
