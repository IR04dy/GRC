package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.dto.RiskRequest;
import com.tss.grc.module.risk.dto.RiskResponse;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskCategory;
import com.tss.grc.module.risk.repository.RiskCategoryRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskRepository riskRepository;
    private final RiskCategoryRepository riskCategoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    /** Re-reads DB-computed columns after save — null in unit tests, set at runtime. */
    @PersistenceContext
    private EntityManager entityManager;

    /** Optional — null in unit tests, autowired by Spring at runtime. */
    @Autowired(required = false)
    private BroadcastHelper bcast;
    private void emit(Long orgId, Long id, Object payload) {
        if (bcast != null) bcast.entity(orgId, "risk", id, payload);
    }

    @Transactional(readOnly = true)
    public Page<RiskResponse> list(String status, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        Page<Risk> page = (status != null && !status.isBlank())
            ? riskRepository.findByOrgAndStatus(orgId, status, pageable)
            : riskRepository.findByOrganisationId(orgId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RiskResponse get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
        return toResponse(risk);
    }

    @Transactional
    public RiskResponse create(RiskRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = new Risk();
        risk.setOrganisationId(orgId);
        applyRequest(risk, req);
        risk.setIdentifiedDate(LocalDate.now());
        risk = riskRepository.save(risk);
        // The DB computes inherent_score / residual_score / risk_level — refresh
        // so the F8 RISK_CREATED payload and the response carry real values (not
        // null), otherwise riskLevel=CRITICAL escalation rules never match on create.
        refreshComputed(risk);
        audit.log("CREATE", "Risk", risk.getId(),
            "Risk " + riskCodeFor(risk.getId())
            + " raised · " + safe(risk.getTitleEn())
            + " · residual=" + safeNum(risk.getResidualScore()));
        // Fan out to F8 — the engine listens for RISK_CREATED and matches
        // enabled rules against the payload (riskLevel, residualScore,
        // ownerId, complianceFrameworkClause). Seed rules can route
        // CRITICAL risks to the CRO, PDPL-anchored risks to the DPO, etc.
        // Same shape as FindingService (PR #50).
        eventPublisher.publishEvent(new DomainEvent(
            orgId, "RISK_CREATED",
            "Risk", risk.getId(), riskCodeFor(risk.getId()),
            payloadFor(risk, null)));
        RiskResponse out = toResponse(risk);
        emit(orgId, risk.getId(), out);
        return out;
    }

    @Transactional
    public RiskResponse update(Long id, RiskRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
        Integer prevResidual = risk.getResidualScore();
        applyRequest(risk, req);
        risk = riskRepository.save(risk);
        refreshComputed(risk);   // recompute inherent/residual score + risk_level after L×I change
        audit.log("UPDATE", "Risk", id,
            "Risk " + riskCodeFor(id)
            + " updated · residual " + safeNum(prevResidual) + " → " + safeNum(risk.getResidualScore()));
        // No F8 event on UPDATE — same policy as FindingService /
        // CapaTaskService (PRs #46/#50). Status moves go through the
        // dedicated transition methods which DO emit.
        RiskResponse out = toResponse(risk);
        emit(orgId, id, out);
        return out;
    }

    @Transactional
    public void delete(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
        risk.setIsDeleted(true);
        riskRepository.save(risk);
        audit.log("DELETE", "Risk", id,
            "Risk " + riskCodeFor(id) + " soft-deleted (status=" + risk.getStatus() + ")");
        // No F8 on delete — admin-only policy matches FindingService.
        emit(orgId, id, java.util.Map.of("id", id, "deleted", true));
    }

    // ── Risk lifecycle workflow (ISO 31000 / COSO ERM) ────────────────────────
    // State machine:
    //   OPEN → UNDER_REVIEW → MITIGATING → CLOSED
    //   OPEN | UNDER_REVIEW → ACCEPTED (formal risk acceptance)
    //   CLOSED → OPEN (reopen on recurrence)

    @Transactional
    public RiskResponse submitForReview(Long id) { return doTransition(id, "UNDER_REVIEW", new String[]{"OPEN"}); }

    @Transactional
    public RiskResponse approve(Long id) { return doTransition(id, "MITIGATING", new String[]{"UNDER_REVIEW"}); }

    @Transactional
    public RiskResponse accept(Long id) { return doTransition(id, "ACCEPTED", new String[]{"OPEN", "UNDER_REVIEW"}); }

    @Transactional
    public RiskResponse close(Long id) { return doTransition(id, "CLOSED", new String[]{"MITIGATING", "ACCEPTED"}); }

    @Transactional
    public RiskResponse reopen(Long id) { return doTransition(id, "OPEN", new String[]{"CLOSED"}); }

    /**
     * Shared transition path — five state-machine methods used to repeat
     * the same 5 lines each. Centralising lets us audit + emit F8 once
     * (so a new transition can't accidentally skip either) and keeps the
     * tenant-scope + state-guard checks bolted together.
     */
    private RiskResponse doTransition(Long id, String target, String[] allowedFrom) {
        Risk r = mustGet(id);
        require(r, allowedFrom);
        String previousStatus = r.getStatus();
        r.setStatus(target);
        Risk saved = riskRepository.save(r);
        audit.log("TRANSITION", "Risk", id,
            "Risk " + riskCodeFor(id) + " " + previousStatus + " → " + target);
        // Fan out to F8 — single RISK_TRANSITIONED event matches the
        // FINDING_TRANSITIONED / CRISIS_AAR_TRANSITIONED shape (PRs #50,
        // #53). Rules target a specific step via condition_json
        // {"status":"ACCEPTED"} rather than us emitting five distinct
        // event names. previousStatus is carried so a rule can match
        // 'OPEN → ACCEPTED' (formal acceptance without review — SAMA
        // §10.4 governance flag) separately from 'UNDER_REVIEW →
        // ACCEPTED' (review-completed acceptance, normal path).
        eventPublisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "RISK_TRANSITIONED",
            "Risk", id, riskCodeFor(id),
            payloadFor(saved, previousStatus)));
        RiskResponse out = toResponse(saved);
        emit(saved.getOrganisationId(), id, out);
        return out;
    }

    /**
     * Risk-incident retrospective: when an incident is logged for this risk,
     * bump the residual likelihood by 1 (capped at 5) so the heatmap reflects
     * the realised event. Returns the updated risk.
     */
    @Transactional
    public RiskResponse bumpLikelihoodFromIncident(Long id) {
        Risk r = mustGet(id);
        Integer current = r.getResidualLikelihood();
        int next = Math.min(5, (current == null ? 1 : current) + 1);
        r.setResidualLikelihood(next);
        return toResponse(riskRepository.save(r));
    }

    private Risk mustGet(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
    }

    private void require(Risk r, String... allowed) {
        for (String s : allowed) if (s.equals(r.getStatus())) return;
        throw new IllegalArgumentException(
            "Risk must be in " + java.util.Arrays.toString(allowed) + " (was " + r.getStatus() + ")");
    }

    /** Re-read the DB-computed columns (inherent_score / residual_score /
     *  risk_level) into the entity after an insert or L×I update so the response
     *  DTO and the F8 payload carry real values instead of null. No-op in unit
     *  tests where no persistence context is wired. */
    private void refreshComputed(Risk r) {
        if (entityManager == null || r.getId() == null) return;
        entityManager.flush();
        entityManager.refresh(r);
    }

    private void applyRequest(Risk risk, RiskRequest req) {
        risk.setTitleEn(req.titleEn());
        risk.setTitleAr(req.titleAr());
        risk.setDescriptionEn(req.descriptionEn());
        risk.setDescriptionAr(req.descriptionAr());
        risk.setCategoryId(req.categoryId());
        risk.setOwnerId(req.ownerId());
        risk.setInherentLikelihood(req.inherentLikelihood());
        risk.setInherentImpact(req.inherentImpact());
        risk.setResidualLikelihood(req.residualLikelihood());
        risk.setResidualImpact(req.residualImpact());
        if (req.status() != null) risk.setStatus(req.status());
        risk.setReviewDate(req.reviewDate());
    }

    private RiskResponse toResponse(Risk r) {
        String categoryNameEn = null;
        String categoryNameAr = null;
        if (r.getCategoryId() != null) {
            RiskCategory cat = riskCategoryRepository.findById(r.getCategoryId()).orElse(null);
            if (cat != null) {
                categoryNameEn = cat.getNameEn();
                categoryNameAr = cat.getNameAr();
            }
        }

        String ownerFullNameEn = null;
        String ownerUsername = null;
        if (r.getOwnerId() != null) {
            User owner = userRepository.findById(r.getOwnerId()).orElse(null);
            if (owner != null) {
                ownerFullNameEn = owner.getFullNameEn();
                ownerUsername = owner.getUsername();
            }
        }

        String riskCode = "RISK-" + String.format("%04d", r.getId());

        return new RiskResponse(
            r.getId(), r.getExternalId(),
            riskCode,
            r.getTitleEn(), r.getTitleAr(), r.getDescriptionEn(), r.getDescriptionAr(),
            r.getCategoryId(), categoryNameEn,
            categoryNameEn, categoryNameAr,
            r.getOwnerId(), ownerFullNameEn,
            ownerFullNameEn, ownerUsername,
            r.getStatus(),
            r.getInherentLikelihood(), r.getInherentImpact(), r.getInherentScore(),
            r.getResidualLikelihood(), r.getResidualImpact(), r.getResidualScore(),
            r.getRiskLevel(),
            r.getIdentifiedDate(), r.getReviewDate(),
            r.getCreatedAt(), r.getUpdatedAt()
        );
    }

    /** Canonical payload Map for F8 rule-condition matching on Risk
     *  events. Engine evaluates equality + contains on these string
     *  keys; values are coerced to "" so the matcher never NPEs on a
     *  null. {@code previousStatus} only carries on TRANSITIONED.
     *
     *  <p>{@code riskLevel} (LOW/MEDIUM/HIGH/CRITICAL) is the most useful
     *  rule key — it lets seed rules route CRITICAL risks to the CRO
     *  without each rule re-computing severity from likelihood × impact.
     *  {@code complianceFrameworkClause} enables regulator-anchored
     *  routing (a PDPL-tagged risk auto-routes to the DPO). */
    private static Map<String, Object> payloadFor(Risk r, String previousStatus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("riskCode",           riskCodeFor(r.getId()));
        m.put("externalId",         r.getExternalId() == null ? "" : r.getExternalId().toString());
        m.put("titleEn",            nullSafe(r.getTitleEn()));
        m.put("status",             nullSafe(r.getStatus()));
        m.put("riskLevel",          nullSafe(r.getRiskLevel()));
        m.put("residualScore",      r.getResidualScore() == null ? "" : String.valueOf(r.getResidualScore()));
        m.put("inherentScore",      r.getInherentScore() == null ? "" : String.valueOf(r.getInherentScore()));
        m.put("ownerId",            r.getOwnerId() == null ? "" : String.valueOf(r.getOwnerId()));
        m.put("categoryId",         r.getCategoryId() == null ? "" : String.valueOf(r.getCategoryId()));
        m.put("complianceFrameworkClause", nullSafe(r.getComplianceFrameworkClause()));
        if (previousStatus != null) m.put("previousStatus", previousStatus);
        return m;
    }

    /** Match the human-readable code shape used in {@link #toResponse}. */
    private static String riskCodeFor(Long id) {
        return id == null ? "RISK-0000" : "RISK-" + String.format("%04d", id);
    }

    private static String nullSafe(String v) { return v == null ? "" : v; }
    private static String safe(String v) { return v == null ? "" : v; }
    private static String safeNum(Integer v) { return v == null ? "-" : v.toString(); }
}
