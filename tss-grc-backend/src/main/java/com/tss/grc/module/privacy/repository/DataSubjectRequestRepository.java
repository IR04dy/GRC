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

    /**
     * Open requests whose recorded statutory deadline has already passed — the figure the
     * dashboard's "SLA breaches" tile has always been named for.
     *
     * <p><b>F2-458.</b> {@code PrivacyService.getStats} used to answer that question with
     * {@code countByOrganisationIdAndStatus(orgId, "ESCALATED")}. Escalation is what an
     * operator does <em>when somebody notices</em> a request is running late; the register
     * carries the deadline itself in {@code due_date}, so the proxy was measurable against
     * the real thing and diverged badly. Measured 2026-09-04, byte-identical in dev and the
     * deployed database: <b>12 of 12 open requests are past their {@code due_date}</b> (74
     * to 504 days over) while <b>2</b> carry status {@code ESCALATED} — so the dashboard's
     * radial gauge reported <b>83% "within SLA"</b> where the honest answer is 0%.
     *
     * <p>Pass {@link com.tss.grc.module.privacy.DsrRequestLifecycle#TERMINAL_LIST} and
     * today's date. The {@code StatusNotIn} half is what makes the result a subset of the
     * open count by construction, so the two figures can never disagree about which
     * population they describe — the relation the caller used to enforce with a
     * {@code Math.min} clamp on the client.
     *
     * <p>⚠ {@code DueDateBefore} excludes a row whose {@code due_date} is NULL, because a
     * NULL comparison is UNKNOWN rather than true. That is the correct reading — a request
     * with no recorded deadline is not <em>provably</em> past one — and it means this count
     * is not the complement of "within SLA": an open request with no deadline is in neither
     * bucket. Measured 0 such rows in both databases; filed rather than given a third
     * figure nothing renders (F2-184).
     */
    long countByOrganisationIdAndStatusNotInAndDueDateBefore(
            Long organisationId, java.util.List<String> terminalStatuses, java.time.LocalDate asOf);
}
