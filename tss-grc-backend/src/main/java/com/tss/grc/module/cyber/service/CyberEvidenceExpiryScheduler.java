package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberEvidence;
import com.tss.grc.module.cyber.repository.CyberEvidenceRepository;
import com.tss.grc.module.evidence.dto.EvidenceRequestCreateDto;
import com.tss.grc.module.evidence.dto.EvidenceRequestResponse;
import com.tss.grc.module.evidence.service.EvidenceRequestService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * CY-J — the cyber evidence expiry sweep.
 *
 * <p>Cyber evidence carries an {@code expiry_date} but nothing ever swept it, so stale evidence
 * looked current forever — an audit/regulator failure mode (NCA/SAMA reviews expect fresh,
 * re-collected evidence). This sweep is the missing clock:
 *
 * <ul>
 *   <li>Finds live evidence ({@code CURRENT}/{@code PENDING}) whose {@code expiryDate} has passed
 *       and flips it to {@code STALE} + stamps {@code staleSince}.</li>
 *   <li>Auto-raises an evidence <b>re-request</b> through the shared {@code module/evidence}
 *       subsystem, anchored to the evidence ({@code entityType=CYBER_EVIDENCE}, the same anchor
 *       CY-A wired), so it lands in the evidence-request queue + the evidence's own request panel;
 *       the new request id is recorded on {@code reRequestId}.</li>
 *   <li>Audits {@code CYBER_EVIDENCE_EXPIRED}, notifies the {@value #NOTIFY_ROLE} role, and fires
 *       an F8 {@code CYBER_EVIDENCE_EXPIRED} event.</li>
 * </ul>
 *
 * <p>Idempotent: once a row is {@code STALE} it drops out of the {@code CURRENT}/{@code PENDING}
 * filter, so the re-request is raised exactly once. Config-gated by
 * {@code grc.cyber-evidence-expiry.scheduler-enabled} (mirrors the vuln-SLA + CY-E CAPA sweeps).
 * Runs system-wide; {@link TenantContext} is set per-row so the request + audit land under the
 * evidence's own organisation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CyberEvidenceExpiryScheduler {

    /** "Live" statuses that a passed expiry_date should knock into STALE. */
    private static final List<String> LIVE_STATUSES = List.of("CURRENT", "PENDING");
    /** Days given to re-collect the expired evidence. */
    private static final int RE_REQUEST_DUE_DAYS = 14;
    /** Role notified when evidence expires. */
    private static final String NOTIFY_ROLE = "SECURITY_ANALYST";

    private final CyberEvidenceRepository   evidenceRepo;
    private final EvidenceRequestService    evidenceRequests;
    private final NotificationService       notifications;
    private final AuditLogService           audit;
    private final ApplicationEventPublisher publisher;

    @Value("${grc.cyber-evidence-expiry.scheduler-enabled:false}")
    private boolean enabled;

    /** Poll on the configured interval (default hourly; dev flips it to 1 min). */
    @Scheduled(fixedDelayString = "${grc.cyber-evidence-expiry.scheduler-interval-ms:3600000}")
    public void runSweep() {
        if (!enabled) {
            log.trace("Cyber evidence expiry scheduler disabled (grc.cyber-evidence-expiry.scheduler-enabled=false).");
            return;
        }
        try {
            int expired = sweep();
            if (expired > 0) log.info("Cyber evidence expiry sweep → {} evidence row(s) marked STALE + re-requested", expired);
        } catch (Exception e) {
            log.error("Cyber evidence expiry sweep failed: {}", e.getMessage(), e);
        }
    }

    /** One expiry pass over every tenant's live, dated evidence. Returns rows expired. */
    @Transactional
    public int sweep() {
        LocalDate today = LocalDate.now();
        int expired = 0;
        for (CyberEvidence ev : evidenceRepo.findByStatusInAndExpiryDateBefore(LIVE_STATUSES, today)) {
            expireAndReRequest(ev, today);
            expired++;
        }
        return expired;
    }

    private void expireAndReRequest(CyberEvidence ev, LocalDate today) {
        Long org = ev.getOrganisationId();
        ev.setStatus("STALE");
        ev.setStaleSince(today);

        withTenant(org, () -> {
            // Re-request through the shared evidence subsystem (CY-A anchor: CYBER_EVIDENCE).
            EvidenceRequestCreateDto dto = new EvidenceRequestCreateDto(
                "Re-collect expired evidence: " + nz(ev.getTitleEn()),
                ev.getTitleAr() == null || ev.getTitleAr().isBlank() ? null : "إعادة جمع دليل منتهٍ: " + ev.getTitleAr(),
                "Evidence \"" + nz(ev.getTitleEn()) + "\" expired on " + ev.getExpiryDate()
                    + " and was marked STALE by the expiry sweep. Please re-collect a current version.",
                null,                               // subControlId
                "CYBER_EVIDENCE", ev.getId(),       // generic entity anchor
                null,                               // assignedToId
                null,                               // departmentId
                today.plusDays(RE_REQUEST_DUE_DAYS),
                "MEDIUM",
                "Auto-raised by the cyber evidence expiry sweep (CY-J).");
            EvidenceRequestResponse req = evidenceRequests.create(dto, null);
            ev.setReRequestId(req.id());

            audit.log("CYBER_EVIDENCE_EXPIRED", "CyberEvidence", ev.getId(),
                "Evidence \"" + nz(ev.getTitleEn()) + "\" expired " + ev.getExpiryDate()
                    + " → STALE; re-request " + req.requestCode() + " raised (due " + dto.dueDate() + ")");

            notifications.broadcastToRole(org, NOTIFY_ROLE,
                "Cyber evidence expired — re-request raised",
                "دليل سيبراني منتهٍ — تم رفع طلب إعادة جمع",
                "\"" + nz(ev.getTitleEn()) + "\" expired on " + ev.getExpiryDate()
                    + " and was marked STALE; a re-request has been opened.",
                "CYBER_EVIDENCE_EXPIRED", "CyberEvidence", ev.getId());

            publisher.publishEvent(DomainEvent.of(org, "CYBER_EVIDENCE_EXPIRED", "CyberEvidence", ev.getId(),
                Map.of("titleEn",     nz(ev.getTitleEn()),
                       "expiryDate",  String.valueOf(ev.getExpiryDate()),
                       "reRequestId", req.id())));
        });

        evidenceRepo.save(ev);
    }

    /** Run under the row's own tenant (services + AuditLogService read org from TenantContext),
     *  then restore whatever was bound before. */
    private void withTenant(Long org, Runnable r) {
        Long previous = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(org);
            r.run();
        } finally {
            if (previous != null) TenantContext.setCurrentTenant(previous);
            else TenantContext.clear();
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
