package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.dto.RiskAcceptanceDtos.ApproveDto;
import com.tss.grc.module.risk.dto.RiskAcceptanceDtos.CreateDto;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskAcceptance;
import com.tss.grc.module.risk.repository.RiskAcceptanceRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Risk Acceptance workflow — ERM remediation epic E-A.
 *
 * <p>A structured, time-boxed, approval-gated acceptance. {@code request}
 * snapshots the risk's residual server-side and flags Board sign-off when it
 * exceeds the appetite ceiling. {@code approve} (gated by {@code RISK_APPROVE})
 * flips the parent risk to {@code ACCEPTED}. {@code expireSweep} reopens risks
 * whose acceptance has lapsed, and {@code revoke} reopens on demand — so an
 * acceptance can never silently outlive its expiry.
 */
@Service
@RequiredArgsConstructor
public class RiskAcceptanceService {

    private final RiskAcceptanceRepository acceptances;
    private final RiskRepository risks;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;
    /** F2-164 — the audited, event-emitting transition path. No cycle: RiskService
     *  reads the acceptance REPOSITORY, never this service. */
    private final RiskService riskService;

    private Long org() { return TenantContext.getCurrentTenant(); }

    private static String code(Long riskId) { return "RISK-" + String.format("%04d", riskId); }

    @Transactional(readOnly = true)
    public List<RiskAcceptance> listForRisk(Long riskId) {
        return acceptances.findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(org(), riskId);
    }

    @Transactional
    public RiskAcceptance request(Long riskId, CreateDto dto, Long requesterId) {
        Long orgId = org();
        Risk risk = risks.findById(riskId)
            .filter(r -> Objects.equals(r.getOrganisationId(), orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + riskId));
        // ⚠ Reads RiskService's list rather than restating it. An acceptance that may be
        // raised and cannot be approved is a dead end in the governed path — see the note
        // on that constant. There is ONE list; do not add a second one here.
        if (!RiskService.RETAINABLE_STATUSES.contains(risk.getStatus()))
            throw new IllegalStateException(
                "Cannot raise an acceptance for a " + risk.getStatus() + " risk"
                + " — an acceptance is only approvable while the risk is live "
                + RiskService.RETAINABLE_STATUSES);
        if (dto.expiresAt() == null || !dto.expiresAt().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Acceptance expiry must be a future date (acceptance is time-boxed)");

        boolean board = dto.appetiteCeiling() != null && risk.getResidualScore() != null
            && risk.getResidualScore() > dto.appetiteCeiling();

        RiskAcceptance a = new RiskAcceptance();
        a.setOrganisationId(orgId);
        a.setRiskId(riskId);
        a.setJustificationEn(dto.justificationEn());
        a.setJustificationAr(dto.justificationAr());
        a.setCompensatingControlsEn(dto.compensatingControlsEn());
        a.setCompensatingControlsAr(dto.compensatingControlsAr());
        a.setResidualScoreAtAcceptance(risk.getResidualScore());   // server-stamped, not client
        a.setAppetiteCeiling(dto.appetiteCeiling());
        a.setRequiresBoard(board);
        a.setExpiresAt(dto.expiresAt());
        a.setCroSignedBy(dto.croSignedBy());
        a.setBoardSignedBy(dto.boardSignedBy());
        a.setRequestedById(requesterId);
        a.setStatus("SUBMITTED");
        RiskAcceptance saved = acceptances.save(a);
        audit.log("CREATE", "RiskAcceptance", saved.getId(),
            "Acceptance requested for " + code(riskId)
            + " · residual=" + saved.getResidualScoreAtAcceptance()
            + " · expires=" + saved.getExpiresAt()
            + (board ? " · BOARD-required (above appetite)" : ""));
        return saved;
    }

    @Transactional
    public RiskAcceptance approve(Long id, ApproveDto dto, String approver) {
        RiskAcceptance a = require(id);
        if (!"SUBMITTED".equals(a.getStatus()))
            throw new IllegalStateException("Only a SUBMITTED acceptance can be approved (was " + a.getStatus() + ")");
        if (dto != null && dto.boardSignedBy() != null && !dto.boardSignedBy().isBlank())
            a.setBoardSignedBy(dto.boardSignedBy());
        if (a.getCroSignedBy() == null || a.getCroSignedBy().isBlank())
            throw new IllegalArgumentException("CRO sign-off is required before approval");
        if (Boolean.TRUE.equals(a.getRequiresBoard()) && (a.getBoardSignedBy() == null || a.getBoardSignedBy().isBlank()))
            throw new IllegalArgumentException("Board sign-off is required — residual exceeds appetite");
        a.setStatus("APPROVED");
        a.setApprovedBy(approver);
        a.setApprovedAt(LocalDateTime.now());
        RiskAcceptance saved = acceptances.save(a);
        // The acceptance record is the authority — and F2-164 made this the ONLY
        // writer of ACCEPTED. It routes through RiskService's audited transition
        // rather than the bare setRiskStatus() this line used to call, because
        // that bare save emitted no RISK_TRANSITIONED event and wrote no
        // TRANSITION audit row: V132's SAMA §10.4 rule (OPEN → ACCEPTED skipped
        // formal review) was therefore reachable ONLY from the ungoverned
        // endpoint it exists to flag. ⚠ A terminal risk is left alone, same as
        // setRiskStatus did for CLOSED — reopening is RISK:APPROVE's own decision
        // (E-J). Tested against that same list, not against "CLOSED": an ARCHIVED risk
        // would otherwise be handed to a transition that rejects it, turning a
        // legitimate approval into a 400.
        Risk parent = risks.findById(saved.getRiskId()).orElse(null);
        if (parent != null && RiskService.RETAINABLE_STATUSES.contains(parent.getStatus())) {
            riskService.acceptOnApprovedAcceptance(saved.getRiskId());
        }
        audit.log("APPROVE", "RiskAcceptance", saved.getId(),
            "Acceptance " + saved.getId() + " APPROVED by " + approver
            + " → " + code(saved.getRiskId()) + " ACCEPTED (expires " + saved.getExpiresAt() + ")");
        events.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "RISK_ACCEPTANCE_APPROVED",
            "RiskAcceptance", saved.getId(), code(saved.getRiskId()),
            Map.of("riskId", String.valueOf(saved.getRiskId()),
                   "expiresAt", String.valueOf(saved.getExpiresAt()),
                   "requiresBoard", String.valueOf(saved.getRequiresBoard()))));
        return saved;
    }

    @Transactional
    public RiskAcceptance reject(Long id, String reason, String actor) {
        RiskAcceptance a = require(id);
        if (!"SUBMITTED".equals(a.getStatus()))
            throw new IllegalStateException("Only a SUBMITTED acceptance can be rejected");
        a.setStatus("REJECTED");
        a.setRejectionReason(reason);
        RiskAcceptance saved = acceptances.save(a);
        audit.log("REJECT", "RiskAcceptance", saved.getId(),
            "Acceptance " + saved.getId() + " REJECTED by " + actor + (reason == null ? "" : " — " + reason));
        return saved;
    }

    @Transactional
    public RiskAcceptance revoke(Long id, String reason, String actor) {
        RiskAcceptance a = require(id);
        if (!"APPROVED".equals(a.getStatus()))
            throw new IllegalStateException("Only an APPROVED acceptance can be revoked");
        a.setStatus("REVOKED");
        a.setRejectionReason(reason);
        RiskAcceptance saved = acceptances.save(a);
        setRiskStatus(saved.getRiskId(), "UNDER_REVIEW");   // reopen for reassessment
        audit.log("REVOKE", "RiskAcceptance", saved.getId(),
            "Acceptance " + saved.getId() + " REVOKED by " + actor
            + " → " + code(saved.getRiskId()) + " reopened" + (reason == null ? "" : " — " + reason));
        return saved;
    }

    /**
     * Sweep: flip APPROVED acceptances past their expiry to EXPIRED and reopen
     * their risks (UNDER_REVIEW). System-wide (all tenants) — mirrors the
     * evidence SLA scheduler. Callable via the endpoint or a scheduled job.
     */
    @Transactional
    public int expireSweep() {
        List<RiskAcceptance> due = acceptances.findByStatusAndExpiresAtLessThan("APPROVED", LocalDate.now());
        int n = 0;
        for (RiskAcceptance a : due) {
            a.setStatus("EXPIRED");
            acceptances.save(a);
            setRiskStatus(a.getRiskId(), "UNDER_REVIEW");
            audit.log("EXPIRE", "RiskAcceptance", a.getId(),
                "Acceptance " + a.getId() + " EXPIRED (was due " + a.getExpiresAt() + ") → "
                + code(a.getRiskId()) + " reopened for reassessment");
            events.publishEvent(new DomainEvent(
                a.getOrganisationId(), "RISK_ACCEPTANCE_EXPIRED",
                "RiskAcceptance", a.getId(), code(a.getRiskId()),
                Map.of("riskId", String.valueOf(a.getRiskId()), "expiresAt", String.valueOf(a.getExpiresAt()))));
            n++;
        }
        return n;
    }

    /**
     * Reopen path — deliberately NOT routed through {@code RiskService}'s audited
     * transition, unlike {@link #approve}.
     *
     * <p>{@code expireSweep} runs system-wide off-request (scheduler or endpoint,
     * all tenants), where {@code TenantContext} is unset and {@code mustGet}'s
     * tenant filter would throw — the off-request audit landmine. {@code revoke}
     * shares the helper because a revocation's audit record belongs to the
     * acceptance, which is where the reason lives.
     */
    private void setRiskStatus(Long riskId, String status) {
        risks.findById(riskId).ifPresent(r -> {
            if (!"CLOSED".equals(r.getStatus())) { r.setStatus(status); risks.save(r); }
        });
    }

    private RiskAcceptance require(Long id) {
        return acceptances.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Risk acceptance not found: " + id));
    }
}
