package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.dto.RiskAppetiteRequest;
import com.tss.grc.module.risk.entity.AppetiteBreachEvent;
import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import com.tss.grc.module.risk.repository.AppetiteBreachEventRepository;
import com.tss.grc.module.risk.repository.RiskAppetiteStatementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Risk appetite vs. exposure service — assessment §5.1 ERM #6.
 */
@Service
@RequiredArgsConstructor
public class RiskAppetiteService {

    private final RiskAppetiteStatementRepository repo;
    private final AppetiteBreachEventRepository breachRepo;
    /** E-H — live register exposure aggregation + F8 breach escalation. */
    private final RiskRepository risks;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<RiskAppetiteStatement> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByCreatedAtDesc(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public RiskAppetiteStatement get(Long id) {
        return require(id);
    }

    @Transactional
    public RiskAppetiteStatement create(RiskAppetiteRequest req) {
        // SEC-RISK-002: organisationId stamped from tenant context; status forced
        // to DRAFT; approvedBy/approvedAt are not bindable (sign-off goes through
        // the approve endpoint, which derives the approver from the principal).
        RiskAppetiteStatement s = new RiskAppetiteStatement();
        s.setOrganisationId(TenantContext.getCurrentTenant());
        apply(s, req);
        s.setStatus("DRAFT");
        return repo.save(s);
    }

    @Transactional
    public RiskAppetiteStatement update(Long id, RiskAppetiteRequest req) {
        RiskAppetiteStatement s = require(id);  // tenant-scoped load
        BigDecimal previousExposure = s.getCurrentExposure();
        apply(s, req);
        // Auto-evaluate breach on a genuine exposure CHANGE.
        // F2-400: BigDecimal.equals() compares SCALE as well as value, so
        // new BigDecimal("7").equals(new BigDecimal("7.00")) is FALSE. A scale-only
        // resubmission (the register recompute stores BigDecimal.valueOf(int) at
        // scale 0, then a client PUT resends "7.00") read as a change and spuriously
        // re-fired the APPETITE_BREACHED F8 escalation on an edit that changed
        // nothing. Value equality is compareTo() == 0. Null-guarded: a first-time
        // set (previous null) is a change, and compareTo(null) would NPE.
        if (req.currentExposure() != null
                && (previousExposure == null
                    || req.currentExposure().compareTo(previousExposure) != 0)) {
            evaluateExposure(s);
        }
        return repo.save(s);
    }

    @Transactional
    public RiskAppetiteStatement approve(Long id, String approvedBy) {
        RiskAppetiteStatement s = require(id);
        if (!"DRAFT".equals(s.getStatus()) && !"UNDER_REVIEW".equals(s.getStatus()))
            throw new IllegalArgumentException("Cannot approve from status: " + s.getStatus());
        s.setStatus("APPROVED");
        s.setApprovedBy(approvedBy);
        s.setApprovedAt(java.time.LocalDate.now());
        return repo.save(s);
    }

    @Transactional
    public RiskAppetiteStatement retire(Long id) {
        RiskAppetiteStatement s = require(id);
        s.setStatus("RETIRED");
        return repo.save(s);
    }

    @Transactional
    public void delete(Long id) {
        RiskAppetiteStatement s = require(id);
        s.setIsDeleted(true);
        repo.save(s);
    }

    @Transactional
    public RiskAppetiteStatement updateExposure(Long id, BigDecimal value, String notes) {
        RiskAppetiteStatement s = require(id);
        s.setCurrentExposure(value);
        evaluateExposure(s, notes);
        return repo.save(s);
    }

    /** E-H — outcome of a register→appetite exposure recompute. */
    public record ExposureRecompute(int statementsProcessed, int breachesRaised) {}

    /**
     * E-H — aggregate live residual exposure from the risk register into each
     * APPROVED appetite statement. The exposure of a statement is the worst
     * (max) residual score among the open risks in its category — directly
     * comparable to {@code toleranceUpper} on the 1..25 scale. Updating the
     * exposure re-evaluates the tolerance band, raising (and escalating) an
     * {@link AppetiteBreachEvent} when the category breaches appetite. Callable
     * on demand via the endpoint or from a scheduled sweep.
     */
    @Transactional
    public ExposureRecompute recomputeExposureFromRegister() {
        Long org = TenantContext.getCurrentTenant();
        List<RiskAppetiteStatement> approved = repo.findByOrganisationIdAndStatus(org, "APPROVED");
        int processed = 0, breaches = 0;
        for (RiskAppetiteStatement s : approved) {
            if (s.getCategoryId() == null) continue;   // category-anchored statements only
            Integer maxRes = risks.findMaxResidualScoreByCategory(org, s.getCategoryId());
            s.setCurrentExposure(maxRes == null ? BigDecimal.ZERO : BigDecimal.valueOf(maxRes));
            repo.save(s);
            processed++;
            if (evaluateExposure(s, "Auto-aggregated from live register exposure") != null) breaches++;
        }
        return new ExposureRecompute(processed, breaches);
    }

    @Transactional(readOnly = true)
    public List<AppetiteBreachEvent> listBreaches(Long appetiteId) {
        require(appetiteId);
        return breachRepo.findByAppetiteIdOrderByBreachedAtDesc(appetiteId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Long org = TenantContext.getCurrentTenant();
        // F2-422: this was `Pageable.ofSize(200)`, so "total" was the size of a window and the
        // four severity buckets were counted over it. Creation order is orthogonal to how many
        // statements exist, which is F2-420's case; `list(Pageable)` above remains the paged read.
        List<RiskAppetiteStatement> all = repo.findByOrganisationIdOrderByCreatedAtDesc(org);
        int within = 0, exceeded = 0, critical = 0, noExposure = 0;
        for (RiskAppetiteStatement s : all) {
            String severity = computeSeverity(s);
            if (severity == null) noExposure++;
            else switch (severity) {
                case "WITHIN_TOLERANCE" -> within++;
                case "EXCEEDED"         -> exceeded++;
                case "CRITICAL"         -> critical++;
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("total",            all.size());
        out.put("withinTolerance",  within);
        out.put("exceeded",         exceeded);
        out.put("critical",         critical);
        out.put("noExposure",       noExposure);
        return out;
    }

    private void apply(RiskAppetiteStatement target, RiskAppetiteRequest src) {
        // SEC-RISK-002 allow-list. status / approvedBy / approvedAt are NOT copied
        // here — they are governance fields driven by approve()/retire(), so a
        // client cannot self-attest sign-off via create/update.
        if (src.categoryId()      != null) target.setCategoryId(src.categoryId());
        if (src.titleEn()         != null) target.setTitleEn(src.titleEn());
        if (src.titleAr()         != null) target.setTitleAr(src.titleAr());
        if (src.statementEn()     != null) target.setStatementEn(src.statementEn());
        if (src.statementAr()     != null) target.setStatementAr(src.statementAr());
        if (src.riskDimension()   != null) target.setRiskDimension(src.riskDimension());
        if (src.appetiteLevel()   != null) target.setAppetiteLevel(src.appetiteLevel());
        if (src.toleranceLower()  != null) target.setToleranceLower(src.toleranceLower());
        if (src.toleranceUpper()  != null) target.setToleranceUpper(src.toleranceUpper());
        if (src.unit()            != null) target.setUnit(src.unit());
        if (src.currentExposure() != null) target.setCurrentExposure(src.currentExposure());
        if (src.nextReviewDate()  != null) target.setNextReviewDate(src.nextReviewDate());
    }

    private void evaluateExposure(RiskAppetiteStatement s) {
        evaluateExposure(s, null);
    }

    /** Creates + escalates a breach when exposure leaves the tolerance band.
     *  Returns the breach (or null if within tolerance / no exposure). */
    private AppetiteBreachEvent evaluateExposure(RiskAppetiteStatement s, String notes) {
        String severity = computeSeverity(s);
        if (severity == null || "WITHIN_TOLERANCE".equals(severity)) return null;
        // E-H dedup: if an unacknowledged breach is already on file for this
        // statement, don't raise a duplicate (keeps a scheduled/repeated recompute
        // idempotent — no breach-list spam, no repeated F8 escalation).
        if (breachRepo.existsByAppetiteIdAndStatus(s.getId(), "OPEN")) return null;
        AppetiteBreachEvent ev = new AppetiteBreachEvent();
        ev.setAppetiteId(s.getId());
        ev.setObservedValue(s.getCurrentExposure());
        ev.setBreachSeverity(severity);
        ev.setBreachedAt(LocalDateTime.now());
        ev.setNotes(notes);
        ev.setStatus("OPEN");
        AppetiteBreachEvent saved = breachRepo.save(ev);
        // E-H — fan out to F8 so a rule can escalate (notify the CRO / raise a
        // board review task). Previously the breach was recorded but silent.
        events.publishEvent(new DomainEvent(
            s.getOrganisationId(), "APPETITE_BREACHED",
            "RiskAppetiteStatement", s.getId(),
            s.getExternalId() == null ? null : s.getExternalId().toString(),
            Map.of("appetiteId",     String.valueOf(s.getId()),
                   "titleEn",        s.getTitleEn() == null ? "" : s.getTitleEn(),
                   "severity",       severity,
                   "observedValue",  String.valueOf(s.getCurrentExposure()),
                   "toleranceUpper", String.valueOf(s.getToleranceUpper()),
                   "categoryId",     s.getCategoryId() == null ? "" : String.valueOf(s.getCategoryId()))));
        return saved;
    }

    /** Returns null if exposure is missing; otherwise WITHIN_TOLERANCE | EXCEEDED | CRITICAL. */
    private String computeSeverity(RiskAppetiteStatement s) {
        BigDecimal v = s.getCurrentExposure();
        BigDecimal upper = s.getToleranceUpper();
        BigDecimal lower = s.getToleranceLower();
        if (v == null || upper == null) return null;
        // Within band → WITHIN_TOLERANCE
        if ((lower == null || v.compareTo(lower) >= 0) && v.compareTo(upper) <= 0)
            return "WITHIN_TOLERANCE";
        // Compute % above upper bound to decide EXCEEDED vs CRITICAL
        BigDecimal over = v.subtract(upper);
        if (over.signum() <= 0) return "WITHIN_TOLERANCE";
        BigDecimal pctOver = upper.signum() == 0
            ? BigDecimal.valueOf(100)
            : over.multiply(BigDecimal.valueOf(100))
                  .divide(upper, 4, java.math.RoundingMode.HALF_UP);
        return pctOver.compareTo(BigDecimal.valueOf(25)) >= 0 ? "CRITICAL" : "EXCEEDED";
    }

    private RiskAppetiteStatement require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return repo.findById(id)
            .filter(s -> s.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Appetite statement not found: " + id));
    }
}
