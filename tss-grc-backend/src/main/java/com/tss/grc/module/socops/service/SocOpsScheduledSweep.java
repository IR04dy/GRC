package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.AppsecFinding;
import com.tss.grc.module.socops.entity.CryptoAsset;
import com.tss.grc.module.socops.entity.CyberInsurance;
import com.tss.grc.module.socops.entity.DlpEvent;
import com.tss.grc.module.socops.entity.ForensicsArtefact;
import com.tss.grc.module.socops.entity.IamJmlEvent;
import com.tss.grc.module.socops.entity.PentestEngagement;
import com.tss.grc.module.socops.entity.RegulatorExamination;
import com.tss.grc.module.socops.entity.SeasonalOverlay;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.AppsecFindingRepository;
import com.tss.grc.module.socops.repository.CryptoAssetRepository;
import com.tss.grc.module.socops.repository.CyberInsuranceRepository;
import com.tss.grc.module.socops.repository.DlpEventRepository;
import com.tss.grc.module.socops.repository.ForensicsArtefactRepository;
import com.tss.grc.module.socops.repository.IamJmlEventRepository;
import com.tss.grc.module.socops.repository.PentestEngagementRepository;
import com.tss.grc.module.socops.repository.RegulatorExaminationRepository;
import com.tss.grc.module.socops.repository.SeasonalOverlayRepository;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Time-based F8 trigger sweep for SOC Operations. Closes the loop on the
 * V151 / V153 automation rules whose triggers fire from elapsed-time
 * conditions rather than user-driven state changes.
 *
 * <p>Sweeps covered (hourly):
 * <ul>
 *   <li>{@code SECURITY_EVENT_SLA_APPROACHING} — sla_due_at within next 30 min</li>
 *   <li>{@code SECURITY_EVENT_SLA_BREACHED}    — sla_due_at already past</li>
 *   <li>{@code REGULATOR_EXAM_RESPONSE_DUE}    — written-response window ≤14 days</li>
 *   <li>{@code CYBER_INSURANCE_RENEWAL_90D}    — renewal_due_at in (60d, 90d]</li>
 *   <li>{@code CYBER_INSURANCE_RENEWAL_30D}    — renewal_due_at in (0d, 30d]</li>
 *   <li>{@code IAM_PRIVILEGED_REVIEW_DUE}      — privileged_review_due_at ≤14 days</li>
 *   <li>{@code CRYPTO_CERT_EXPIRING_30D}       — TLS cert valid_until ≤30 days</li>
 *   <li>{@code CRYPTO_CERT_EXPIRED}            — TLS cert valid_until past</li>
 *   <li>{@code PENTEST_RETEST_DUE}             — retest_due_at ≤7 days, not yet retested</li>
 *   <li>{@code SEASONAL_OVERLAY_ACTIVATED}     — overlay's window opens today (Hajj / Ramadan etc.)</li>
 *   <li>{@code FORENSICS_HOLD_DISPOSAL_DUE}    — legal-hold retention_until ≤14 days</li>
 *   <li>{@code APPSEC_SLA_BREACHED}            — open finding past sla_due_at (catches over-time breaches)</li>
 *   <li>{@code DLP_CRITICAL_UNREVIEWED}        — CRITICAL DLP sitting unreviewed &gt;24 hours</li>
 * </ul>
 *
 * <p>Idempotency. We dedupe by audit log — a fire is recorded as
 * {@code SOCOPS_SWEEP_FIRED:<TRIGGER>} on the same entity. Re-running the
 * sweep after a restart doesn't double-fire because the audit history is
 * checked before each publish. This mirrors the {@code CalendarReminderScheduler}
 * pattern (per-stop audit marker, no dedicated dedup table).
 *
 * <p>Tenant discovery. We union the distinct {@code organisation_id}s
 * across all the SOC ops tables we sweep, so a tenant that's onboarded
 * SOC ops but not any one specific surface still gets scanned for the
 * surfaces it does have.
 *
 * <p>The hourly cadence is the sweet spot for SAMA / NCA SLA windows
 * (Critical 1h is the tightest). 30-min approaching is detected at most
 * 30 min late which is acceptable for the secondary nudge — the primary
 * page already fires from the {@code SECURITY_EVENT_CRITICAL_NEW}
 * service-side trigger when the row first lands.
 */
@Component
@RequiredArgsConstructor
public class SocOpsScheduledSweep {

    private static final Logger log = LoggerFactory.getLogger(SocOpsScheduledSweep.class);

    /** Audit action_type used for per-trigger-per-entity dedup. Kept short
     *  because {@code action_type} is {@code VARCHAR(40)} — prefix (9) plus
     *  the longest trigger {@code CYBER_INSURANCE_RENEWAL_30D} (27) = 36. */
    static final String AUDIT_PREFIX = "SOC_FIRE:";

    private final SecurityEventRepository        securityEvents;
    private final RegulatorExaminationRepository regulatorExams;
    private final CyberInsuranceRepository       cyberInsurance;
    private final IamJmlEventRepository          iamJml;
    private final CryptoAssetRepository          cryptoAssets;
    private final PentestEngagementRepository    pentests;
    private final SeasonalOverlayRepository      seasonalOverlays;
    private final ForensicsArtefactRepository    forensics;
    private final AppsecFindingRepository        appsec;
    private final DlpEventRepository             dlp;
    private final AuditLogRepository             auditRepo;
    private final AuditLogService                audit;
    private final ApplicationEventPublisher      publisher;

    /**
     * Hourly tick. Skips at least the first minute after boot so the app
     * has time to finish wiring up. Configurable.
     */
    @Scheduled(fixedDelayString = "${grc.socops.sweep-period-ms:3600000}",
               initialDelayString = "${grc.socops.sweep-initial-delay-ms:60000}")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today   = LocalDate.now();
        for (Long orgId : discoverTenants()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                int fired = 0;
                fired += sweepSecurityEventSla(orgId, now);
                fired += sweepRegulatorResponseDue(orgId, now);
                fired += sweepCyberInsuranceRenewals(orgId, today);
                fired += sweepIamPrivilegedReviewsDue(orgId, now);
                fired += sweepCryptoCertExpiry(orgId, now);
                fired += sweepPentestRetestDue(orgId, now);
                fired += sweepSeasonalOverlayActivated(orgId, now);
                fired += sweepForensicsHoldDisposalDue(orgId, now);
                fired += sweepAppsecSlaBreached(orgId, now);
                fired += sweepDlpCriticalUnreviewed(orgId, now);
                if (fired > 0) {
                    log.info("[socops sweep] org={} fired {} trigger(s)", orgId, fired);
                }
            } catch (Exception ex) {
                log.error("[socops sweep] org={} scan failed", orgId, ex);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Union the SOC-ops tenant set across EVERY surface the tick sweeps. Tiny lists — no need
     * for fancy aggregation; a {@code LinkedHashSet} dedupes while keeping iteration order stable.
     *
     * <p>F2-376 — this used to union {@code SecurityEvent} alone, on the theory that it is the
     * canonical "this tenant uses SOC ops" signal. But the compliance/governance trackers (a SAMA
     * regulator examination with a response due, a cyber-insurance renewal, a TLS cert, a pentest
     * retest, a forensics hold, an appsec SLA, a privileged-review recertification …) are used
     * independently of SIEM ingest, so a tenant with any of those but no {@code SecurityEvent} was
     * invisible to discovery and NONE of its escalations fired. Now every swept surface contributes
     * its own {@code findDistinctOrganisationIds()} — mirrors {@code ComplianceDeadlineScheduler},
     * which unions two surfaces for exactly this reason. Package-private for the unit test.
     */
    List<Long> discoverTenants() {
        java.util.LinkedHashSet<Long> set = new java.util.LinkedHashSet<>();
        set.addAll(securityEvents.findDistinctOrganisationIds());
        set.addAll(regulatorExams.findDistinctOrganisationIds());
        set.addAll(cyberInsurance.findDistinctOrganisationIds());
        set.addAll(iamJml.findDistinctOrganisationIds());
        set.addAll(cryptoAssets.findDistinctOrganisationIds());
        set.addAll(pentests.findDistinctOrganisationIds());
        set.addAll(seasonalOverlays.findDistinctOrganisationIds());
        set.addAll(forensics.findDistinctOrganisationIds());
        set.addAll(appsec.findDistinctOrganisationIds());
        set.addAll(dlp.findDistinctOrganisationIds());
        return new java.util.ArrayList<>(set);
    }

    // ── Security event SLA ────────────────────────────────────────────

    private int sweepSecurityEventSla(Long org, LocalDateTime now) {
        // findApproachingSlaBreach already gives us everything with
        // sla_due_at <= threshold; we split into "approaching" (within 30 min
        // ahead) and "breached" (past now) bands here.
        LocalDateTime in30min = now.plusMinutes(30);
        List<SecurityEvent> rows = securityEvents.findApproachingSlaBreach(org, in30min);
        int fired = 0;
        for (SecurityEvent e : rows) {
            String trigger = e.getSlaDueAt().isBefore(now)
                ? "SECURITY_EVENT_SLA_BREACHED"
                : "SECURITY_EVENT_SLA_APPROACHING";
            if (alreadyFired(org, "SecurityEvent", e.getId(), trigger)) continue;
            publisher.publishEvent(DomainEvent.of(org, trigger, "SecurityEvent", e.getId(),
                Map.of("severity", e.getSeverity() == null ? "" : e.getSeverity(),
                       "status",   e.getStatus()   == null ? "" : e.getStatus(),
                       "slaDueAt", e.getSlaDueAt().toString())));
            markFired(org, "SecurityEvent", e.getId(), trigger);
            fired++;
        }
        return fired;
    }

    // ── Regulator exam response due ───────────────────────────────────

    private int sweepRegulatorResponseDue(Long org, LocalDateTime now) {
        List<RegulatorExamination> rows = regulatorExams.findResponseDueBetween(
            org, now, now.plusDays(14));
        int fired = 0;
        for (RegulatorExamination e : rows) {
            String trigger = "REGULATOR_EXAM_RESPONSE_DUE";
            if (alreadyFired(org, "RegulatorExamination", e.getId(), trigger)) continue;
            publisher.publishEvent(DomainEvent.of(org, trigger, "RegulatorExamination", e.getId(),
                Map.of("regulator",       e.getRegulator()       == null ? "" : e.getRegulator(),
                       "examinationCode", e.getExaminationCode() == null ? "" : e.getExaminationCode(),
                       "responseDueAt",   e.getResponseDueAt().toString())));
            markFired(org, "RegulatorExamination", e.getId(), trigger);
            fired++;
        }
        return fired;
    }

    // ── Cyber insurance renewal ───────────────────────────────────────

    private int sweepCyberInsuranceRenewals(Long org, LocalDate today) {
        // Two windows: AR-137 fires at 90 days, AR-138 fires at 30 days.
        // We emit both triggers from the same scan but dedup independently so
        // a policy can fire the 90-day rule on Monday and the 30-day rule
        // 60 days later from the same row.
        int fired = 0;
        for (CyberInsurance p : cyberInsurance.findRenewalsDueBetween(org, today.plusDays(61), today.plusDays(90))) {
            if (alreadyFired(org, "CyberInsurance", p.getId(), "CYBER_INSURANCE_RENEWAL_90D")) continue;
            publisher.publishEvent(DomainEvent.of(org, "CYBER_INSURANCE_RENEWAL_90D",
                "CyberInsurance", p.getId(),
                Map.of("policyNumber", p.getPolicyNumber() == null ? "" : p.getPolicyNumber(),
                       "carrier",      p.getCarrierName()  == null ? "" : p.getCarrierName(),
                       "renewalDueAt", p.getRenewalDueAt() == null ? "" : p.getRenewalDueAt().toString())));
            markFired(org, "CyberInsurance", p.getId(), "CYBER_INSURANCE_RENEWAL_90D");
            fired++;
        }
        for (CyberInsurance p : cyberInsurance.findRenewalsDueBetween(org, today, today.plusDays(30))) {
            if (alreadyFired(org, "CyberInsurance", p.getId(), "CYBER_INSURANCE_RENEWAL_30D")) continue;
            publisher.publishEvent(DomainEvent.of(org, "CYBER_INSURANCE_RENEWAL_30D",
                "CyberInsurance", p.getId(),
                Map.of("policyNumber", p.getPolicyNumber() == null ? "" : p.getPolicyNumber(),
                       "carrier",      p.getCarrierName()  == null ? "" : p.getCarrierName(),
                       "renewalDueAt", p.getRenewalDueAt() == null ? "" : p.getRenewalDueAt().toString())));
            markFired(org, "CyberInsurance", p.getId(), "CYBER_INSURANCE_RENEWAL_30D");
            fired++;
        }
        return fired;
    }

    // ── IAM privileged-access reviews ─────────────────────────────────

    private int sweepIamPrivilegedReviewsDue(Long org, LocalDateTime now) {
        List<IamJmlEvent> rows = iamJml.findPrivilegedReviewsDueBetween(org, now, now.plusDays(14));
        int fired = 0;
        for (IamJmlEvent e : rows) {
            String trigger = "IAM_PRIVILEGED_REVIEW_DUE";
            if (alreadyFired(org, "IamJmlEvent", e.getId(), trigger)) continue;
            publisher.publishEvent(DomainEvent.of(org, trigger, "IamJmlEvent", e.getId(),
                Map.of("subject",    e.getSubjectUsername() == null ? "" : e.getSubjectUsername(),
                       "role",       e.getRoleOrEntitlement() == null ? "" : e.getRoleOrEntitlement(),
                       "department", e.getSubjectDepartment() == null ? "" : e.getSubjectDepartment())));
            markFired(org, "IamJmlEvent", e.getId(), trigger);
            fired++;
        }
        return fired;
    }

    // ── TLS cert expiry ───────────────────────────────────────────────

    private int sweepCryptoCertExpiry(Long org, LocalDateTime now) {
        // Two bands: expired (valid_until in the past) and expiring in next 30d.
        // Using two narrow ranges instead of one wide one lets us dedup on a
        // per-trigger basis — a cert that expires today fires EXPIRING_30D
        // earlier and EXPIRED on the day-of, both legitimately.
        int fired = 0;
        for (CryptoAsset c : cryptoAssets.findExpiringBetween(org, now.minusYears(5), now)) {
            if (alreadyFired(org, "CryptoAsset", c.getId(), "CRYPTO_CERT_EXPIRED")) continue;
            publisher.publishEvent(DomainEvent.of(org, "CRYPTO_CERT_EXPIRED",
                "CryptoAsset", c.getId(),
                Map.of("assetCode",  c.getAssetCode()  == null ? "" : c.getAssetCode(),
                       "subjectDn",  c.getSubjectDn()  == null ? "" : c.getSubjectDn(),
                       "validUntil", c.getValidUntil() == null ? "" : c.getValidUntil().toString())));
            markFired(org, "CryptoAsset", c.getId(), "CRYPTO_CERT_EXPIRED");
            fired++;
        }
        for (CryptoAsset c : cryptoAssets.findExpiringBetween(org, now, now.plusDays(30))) {
            if (alreadyFired(org, "CryptoAsset", c.getId(), "CRYPTO_CERT_EXPIRING_30D")) continue;
            publisher.publishEvent(DomainEvent.of(org, "CRYPTO_CERT_EXPIRING_30D",
                "CryptoAsset", c.getId(),
                Map.of("assetCode",  c.getAssetCode()  == null ? "" : c.getAssetCode(),
                       "subjectDn",  c.getSubjectDn()  == null ? "" : c.getSubjectDn(),
                       "validUntil", c.getValidUntil() == null ? "" : c.getValidUntil().toString())));
            markFired(org, "CryptoAsset", c.getId(), "CRYPTO_CERT_EXPIRING_30D");
            fired++;
        }
        return fired;
    }

    // ── Pentest retest due ────────────────────────────────────────────

    private int sweepPentestRetestDue(Long org, LocalDateTime now) {
        // 7-day window matches NCA expectation: re-test within a week of
        // the deadline so the closure-of-findings packet is ready before
        // the regulator's draft-report visit.
        List<PentestEngagement> rows = pentests.findRetestDueBetween(org, now, now.plusDays(7));
        int fired = 0;
        for (PentestEngagement p : rows) {
            String trigger = "PENTEST_RETEST_DUE";
            if (alreadyFired(org, "PentestEngagement", p.getId(), trigger)) continue;
            publisher.publishEvent(DomainEvent.of(org, trigger, "PentestEngagement", p.getId(),
                Map.of("engagementCode", p.getEngagementCode() == null ? "" : p.getEngagementCode(),
                       "vendor",         p.getVendorName()     == null ? "" : p.getVendorName(),
                       "retestDueAt",    p.getRetestDueAt()    == null ? "" : p.getRetestDueAt().toString())));
            markFired(org, "PentestEngagement", p.getId(), trigger);
            fired++;
        }
        return fired;
    }

    // ── Seasonal overlay activated ────────────────────────────────────

    private int sweepSeasonalOverlayActivated(Long org, LocalDateTime now) {
        // Day-wide window — pick up overlays whose starts_at landed any time
        // today even if the scheduler missed an hour. Audit-log dedup keeps
        // the next 23 ticks quiet for the same overlay.
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfToday   = startOfToday.plusDays(1);
        List<SeasonalOverlay> rows = seasonalOverlays.findStartingBetween(org, startOfToday, endOfToday);
        int fired = 0;
        for (SeasonalOverlay s : rows) {
            String trigger = "SEASONAL_OVERLAY_ACTIVATED";
            if (alreadyFired(org, "SeasonalOverlay", s.getId(), trigger)) continue;
            publisher.publishEvent(DomainEvent.of(org, trigger, "SeasonalOverlay", s.getId(),
                Map.of("overlayCode", s.getOverlayCode() == null ? "" : s.getOverlayCode(),
                       "titleEn",     s.getTitleEn()     == null ? "" : s.getTitleEn(),
                       "hijriLabel",  s.getHijriLabel()  == null ? "" : s.getHijriLabel(),
                       "tightenKris", Boolean.TRUE.equals(s.getAutoTightenKris()),
                       "tightenSlas", Boolean.TRUE.equals(s.getAutoTightenSlas()))));
            markFired(org, "SeasonalOverlay", s.getId(), trigger);
            fired++;
        }
        return fired;
    }

    // ── Forensics hold disposal due ───────────────────────────────────

    private int sweepForensicsHoldDisposalDue(Long org, LocalDateTime now) {
        // 14-day lead time before retention_until — gives legal counsel a
        // window to either extend the hold or sign off on disposal before
        // the deadline passes silently.
        List<ForensicsArtefact> rows = forensics.findHoldDisposalDueBetween(org, now, now.plusDays(14));
        int fired = 0;
        for (ForensicsArtefact a : rows) {
            String trigger = "FORENSICS_HOLD_DISPOSAL_DUE";
            if (alreadyFired(org, "ForensicsArtefact", a.getId(), trigger)) continue;
            publisher.publishEvent(DomainEvent.of(org, trigger, "ForensicsArtefact", a.getId(),
                Map.of("artefactCode",   a.getArtefactCode()   == null ? "" : a.getArtefactCode(),
                       "caseCode",       a.getCaseCode()       == null ? "" : a.getCaseCode(),
                       "custodian",      a.getCurrentCustodian() == null ? "" : a.getCurrentCustodian(),
                       "retentionUntil", a.getRetentionUntil() == null ? "" : a.getRetentionUntil().toString())));
            markFired(org, "ForensicsArtefact", a.getId(), trigger);
            fired++;
        }
        return fired;
    }

    // ── Appsec SLA breached (over-time) ───────────────────────────────

    private int sweepAppsecSlaBreached(Long org, LocalDateTime now) {
        // The ingest service fires APPSEC_SLA_BREACHED for findings that were
        // already past SLA at first sight (re-imports of stale data). This
        // sweep catches the more common case — findings that breach over
        // time because nobody fixed them.
        List<AppsecFinding> rows = appsec.findSlaBreached(org, now);
        int fired = 0;
        for (AppsecFinding f : rows) {
            String trigger = "APPSEC_SLA_BREACHED";
            if (alreadyFired(org, "AppsecFinding", f.getId(), trigger)) continue;
            publisher.publishEvent(DomainEvent.of(org, trigger, "AppsecFinding", f.getId(),
                Map.of("scanner",  f.getScanner()  == null ? "" : f.getScanner(),
                       "scanType", f.getScanType() == null ? "" : f.getScanType(),
                       "severity", f.getSeverity() == null ? "" : f.getSeverity(),
                       "slaDueAt", f.getSlaDueAt() == null ? "" : f.getSlaDueAt().toString())));
            markFired(org, "AppsecFinding", f.getId(), trigger);
            fired++;
        }
        return fired;
    }

    // ── DLP critical unreviewed ───────────────────────────────────────

    private int sweepDlpCriticalUnreviewed(Long org, LocalDateTime now) {
        // 24h cutoff — the ingest service already fires on landing; this
        // catches the queue that builds when nobody triages. Tightened
        // further by AR-136 condition_json if the rule wants e.g. 4h for
        // BLOCK-action critical events.
        LocalDateTime cutoff = now.minusHours(24);
        List<DlpEvent> rows = dlp.findCriticalUnreviewedBefore(org, cutoff);
        int fired = 0;
        for (DlpEvent d : rows) {
            String trigger = "DLP_CRITICAL_UNREVIEWED";
            if (alreadyFired(org, "DlpEvent", d.getId(), trigger)) continue;
            publisher.publishEvent(DomainEvent.of(org, trigger, "DlpEvent", d.getId(),
                Map.of("sourceSystem",       d.getSourceSystem()       == null ? "" : d.getSourceSystem(),
                       "actionTaken",        d.getActionTaken()        == null ? "" : d.getActionTaken(),
                       "dataClassification", d.getDataClassification() == null ? "" : d.getDataClassification(),
                       "eventTime",          d.getEventTime()          == null ? "" : d.getEventTime().toString())));
            markFired(org, "DlpEvent", d.getId(), trigger);
            fired++;
        }
        return fired;
    }

    // ── Audit-log dedup ───────────────────────────────────────────────

    private boolean alreadyFired(Long org, String entityType, Long entityId, String trigger) {
        String marker = AUDIT_PREFIX + trigger;
        List<AuditLogEntry> rows = auditRepo
            .findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(org, entityType, entityId);
        for (AuditLogEntry row : rows) {
            if (marker.equals(row.getActionType())) return true;
        }
        return false;
    }

    private void markFired(Long org, String entityType, Long entityId, String trigger) {
        audit.log(AUDIT_PREFIX + trigger, entityType, entityId,
            "Sweep fired " + trigger + " at " + LocalDateTime.now());
    }
}
