package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.DataBreach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataBreachRepository extends JpaRepository<DataBreach, Long> {
    Page<DataBreach> findByOrganisationId(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);

    /**
     * Breaches whose SDAIA notification has actually left the building — pass
     * {@link com.tss.grc.module.privacy.service.PrivacyService#SDAIA_DISCHARGED}.
     *
     * <p><b>F2-458.</b> The PDPL dashboard's "Regulator-notified" figure used to be
     * {@code countByOrganisationIdAndStatus(orgId, "NOTIFIED")} — the breach <em>lifecycle</em>
     * status, not the column that records what was said to the regulator. F2-209 had already
     * measured that those two disagree and corrected the reader in {@code PdplBreachConsole};
     * this is the reader it did not touch. Measured 2026-09-04, byte-identical in dev and the
     * deployed database: <b>4 of 17 breaches are {@code status = NOTIFIED}</b> while
     * <b>every one of the 17 is {@code sdaia_notification_status = NOT_REPORTED}</b> and
     * {@code sdaia_submitted_at} is NULL on all of them — so the dashboard told an operator
     * four breaches had been reported to SDAIA when none had.
     */
    long countByOrganisationIdAndSdaiaNotificationStatusIn(
            Long organisationId, java.util.List<String> sdaiaStates);
}
