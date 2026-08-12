package com.tss.grc.module.platform.completion;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import com.tss.grc.module.privacy_lc.entity.DsrCase;
import com.tss.grc.module.privacy_lc.repository.DsrCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * §5.4 Privacy slice of the buyer-assessment completion bundle — cross-border
 * transfer risk listing, special-category listing, the DSR fulfilment SLA
 * sweep (PDPL Article 4 — 30 days) and the PDPL Article 23 72-hour breach
 * deadline maintenance.
 *
 * <p>Extracted from the former {@code BuyerGapService} god-class
 * (QUAL-BCMP-003). Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class PrivacyGapService {

    private final ProcessingActivityRepository ropa;
    private final DsrCaseRepository            dsrCases;
    private final CrisisEventRepository        crises;
    private final AuditLogService              audit;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ══════════════════════════════════════════════════════════════
    // §5.4 Privacy — cross-border + special-category listings
    // ══════════════════════════════════════════════════════════════

    public record AtRiskTransfer(
        Long   id,
        String activityName,
        String receivingCountry,
        String adequacyDecision,
        String transferMechanism,
        String dataOfficeReference,
        String riskNote
    ) {}

    /**
     * Lists processing activities flagged as cross-border that look risky:
     * either no adequacy decision recorded, or NONE/PARTIAL with no
     * transfer mechanism documented. The {@code riskNote} surfaces the
     * reason for each row.
     *
     * <p>PERFORMANCE: the at-risk predicate is pushed into JPQL via
     * {@link ProcessingActivityRepository#findAtRiskCrossBorder} so we
     * only hydrate the rows that will actually be returned to the client.
     * The previous version pulled every cross-border RoPA into memory
     * and filtered in Java, which made the response O(all-cross-border-rows)
     * even if the result was a single row.
     */
    @Transactional(readOnly = true)
    public List<AtRiskTransfer> atRiskTransfers() {
        Long o = org();
        List<ProcessingActivity> hits = ropa.findAtRiskCrossBorder(o);
        List<AtRiskTransfer> out = new ArrayList<>(hits.size());
        for (ProcessingActivity p : hits) {
            String adequacy  = p.getAdequacyDecision();
            String mechanism = p.getTransferMechanism();
            String reason;
            if (adequacy == null) {
                reason = "Adequacy decision missing";
            } else if ("NONE".equals(adequacy)) {
                reason = "Non-adequate destination with no transfer mechanism";
            } else { // PARTIAL is the only remaining case the SQL filter returns
                reason = "Partial-adequacy destination needs an explicit mechanism";
            }
            out.add(new AtRiskTransfer(
                p.getId(), p.getActivityNameEn(), p.getReceivingCountry(),
                adequacy, mechanism, p.getDataOfficeReference(), reason));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ProcessingActivity> specialCategoryActivities() {
        return ropa.findByOrganisationIdAndSpecialCategoryFlagTrue(org());
    }

    // ══════════════════════════════════════════════════════════════
    // §5.4 #1 — DSR fulfilment SLA sweep (PDPL Article 4 — 30 days)
    // ══════════════════════════════════════════════════════════════

    public record DsrSlaSweepResult(long openOverdue, long firedEvents) {}

    @Transactional
    public DsrSlaSweepResult dsrSlaSweep() {
        Long o = org();
        // F2-196: this list was `{DELIVERED, REJECTED, EXTENDED}` — a hand-rolled copy of the
        // owning service's identical query one module away, with EXTENDED added. EXTENDED is
        // not terminal: the declared machine gives it five outgoing edges, and entering it
        // pushes slaDeadline out by 30 days, so the deadline ALREADY accounts for the Article 4
        // extension. Excluding it meant an extended case could never breach — measured
        // 2026-08-12 in dev AND the deployed database: this sweep ran on 2026-06-22, stamped 7
        // of 12 cases, and skipped the 2 EXTENDED ones that were already 4 and 11 days past
        // their extended deadlines. Ask the repository for the open population; this module
        // must not name DSR statuses at all (and ADR-002 forbids it importing privacy_lc's
        // lifecycle class — ModuleGraphRatchetTest refused exactly that).
        List<DsrCase> overdue = dsrCases.findOverdueOpen(o, LocalDate.now());

        long fired = 0;
        LocalDateTime now = LocalDateTime.now();
        for (DsrCase c : overdue) {
            boolean stampedNow = false;
            if (c.getSlaBreachedAt() == null) {
                c.setSlaBreachedAt(now);
                stampedNow = true;
            }
            // CORRECTNESS: dedup against `!Boolean.TRUE.equals(...)` not
            // `Boolean.FALSE.equals(...)` — the latter returns false when
            // the flag is null, which is the OPPOSITE of what we want
            // (a null flag means "never notified" and SHOULD fire).
            boolean shouldFire = !Boolean.TRUE.equals(c.getSlaBreachNotified());
            if (shouldFire) {
                eventPublisher.publishEvent(new DomainEvent(
                    o, "PDPL_DSR_SLA_BREACH", "DsrCase", c.getId(), null,
                    Map.of("externalId", String.valueOf(c.getExternalId()),
                           "type", String.valueOf(c.getType()),
                           "slaDeadline", String.valueOf(c.getSlaDeadline()),
                           "status", String.valueOf(c.getStatus()))));
                c.setSlaBreachNotified(true);
                fired++;
                // Only write the audit row on the first detection. Prior
                // code logged on every sweep run → tens of duplicate rows
                // per overdue case once a daily sweep was wired.
                audit.log("SLA_BREACH", "DsrCase", c.getId(),
                    "DSR " + c.getExternalId() + " missed PDPL 30-day SLA (deadline "
                    + c.getSlaDeadline() + ")");
            }
            // Only save when something actually changed — avoids
            // gratuitous UPDATEs + audit-interceptor noise.
            if (stampedNow || shouldFire) {
                dsrCases.save(c);
            }
        }
        return new DsrSlaSweepResult(overdue.size(), fired);
    }

    // ══════════════════════════════════════════════════════════════
    // §5.4 #2 — PDPL Article 23 72-hour breach deadline maintenance
    // ══════════════════════════════════════════════════════════════

    public record Pdpl72hSweepResult(
        long personalDataCrises, long deadlinesSet, long newlyBreached) {}

    /**
     * For every crisis with personal_data_affected=true:
     *   - if pdpl_72h_deadline_at is null and detection_time is set,
     *     compute it = detection_time + 72h
     *   - if the deadline has passed and notification hasn't been drafted,
     *     flip pdpl_72h_breached=true and emit PDPL_72H_BREACH
     */
    @Transactional
    public Pdpl72hSweepResult pdpl72hSweep() {
        Long o = org();
        // Use a permissive page to walk all rows; in practice these counts are tiny.
        List<CrisisEvent> all = crises.findByOrganisationIdOrderByIdDesc(o,
            org.springframework.data.domain.PageRequest.of(0, 500)).getContent();

        long pd = 0, set = 0, breached = 0;
        LocalDateTime now = LocalDateTime.now();
        for (CrisisEvent ce : all) {
            if (!Boolean.TRUE.equals(ce.getPersonalDataAffected())) continue;
            pd++;
            boolean changed = false;
            if (ce.getPdpl72hDeadlineAt() == null && ce.getDetectionTime() != null) {
                ce.setPdpl72hDeadlineAt(ce.getDetectionTime().plusHours(72));
                set++;
                changed = true;
            }
            if (ce.getPdpl72hDeadlineAt() != null
                && ce.getPdplNotificationDraftedAt() == null
                && ce.getPdpl72hDeadlineAt().isBefore(now)
                && !Boolean.TRUE.equals(ce.getPdpl72hBreached())) {
                ce.setPdpl72hBreached(true);
                breached++;
                changed = true;
                eventPublisher.publishEvent(new DomainEvent(
                    o, "PDPL_72H_BREACH", "CrisisEvent", ce.getId(), null,
                    Map.of("eventCode", String.valueOf(ce.getEventCode()),
                           "title", String.valueOf(ce.getTitleEn()),
                           "deadline", String.valueOf(ce.getPdpl72hDeadlineAt()))));
                audit.log("PDPL_72H_BREACH", "CrisisEvent", ce.getId(),
                    "Crisis " + ce.getEventCode() + " missed PDPL 72-hour window");
            }
            // PERFORMANCE: only persist when we actually mutated the row.
            // The prior unconditional save() fired Hibernate UPDATEs +
            // F7 audit-interceptor rows for every event scanned, even
            // ones that weren't touched.
            if (changed) {
                crises.save(ce);
            }
        }
        return new Pdpl72hSweepResult(pd, set, breached);
    }
}
