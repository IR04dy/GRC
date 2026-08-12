package com.tss.grc.module.privacy_lc.repository;

import com.tss.grc.module.privacy_lc.entity.DsrCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DsrCaseRepository extends JpaRepository<DsrCase, Long> {
    List<DsrCase> findByOrganisationIdOrderByFiledAtDesc(Long organisationId);
    Optional<DsrCase> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);

    /**
     * Overdue = SLA deadline past + the case still open.
     *
     * <p>F2-196: this javadoc used to name the closed set as
     * {@code (not DELIVERED/REJECTED/EXTENDED)} — a third artefact asserting that EXTENDED
     * is terminal, alongside a sweep that passed it and a state machine that contradicted
     * both. It named the value the caller <em>should</em> pass, which made the one correct
     * caller look like the deviation. Pass {@link com.tss.grc.module.privacy_lc.DsrCaseLifecycle#TERMINAL_LIST}
     * and nothing else; the set is derived there from the machine.
     */
    List<DsrCase> findByOrganisationIdAndStatusNotInAndSlaDeadlineBefore(Long organisationId, List<String> closedStatuses, LocalDate before);

    /** Open-case count as the complement of the terminal set — see {@code DsrCaseLifecycle}. */
    long countByOrganisationIdAndStatusNotIn(Long organisationId, List<String> terminalStatuses);

    // ── the two questions callers actually ask ────────────────────────────────────────────
    // F2-196: the terminal set is bound HERE rather than at each call site, so the vocabulary
    // never crosses a module boundary. Two callers live outside privacy_lc — the PDPL breach
    // sweep in platform/completion and the Three Lines roll-up in governance — and a
    // kernel-ish module importing DsrCaseLifecycle would be an ADR-002 inversion that
    // ModuleGraphRatchetTest correctly refuses (it caught exactly that on this slice's first
    // full build). Asking the repository is both legal and narrower: a caller that cannot name
    // a status cannot invent its own idea of "still open".

    /** Cases past their SLA deadline and not yet terminal — the PDPL breach population. */
    default List<DsrCase> findOverdueOpen(Long organisationId, LocalDate before) {
        return findByOrganisationIdAndStatusNotInAndSlaDeadlineBefore(
            organisationId, com.tss.grc.module.privacy_lc.DsrCaseLifecycle.TERMINAL_LIST, before);
    }

    /** How many cases are still live — the complement of the declared terminal set. */
    default long countOpen(Long organisationId) {
        return countByOrganisationIdAndStatusNotIn(
            organisationId, com.tss.grc.module.privacy_lc.DsrCaseLifecycle.TERMINAL_LIST);
    }
}
