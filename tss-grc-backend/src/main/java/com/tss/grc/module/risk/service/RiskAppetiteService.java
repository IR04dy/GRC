package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.dto.RiskAppetiteRequest;
import com.tss.grc.module.risk.entity.AppetiteBreachEvent;
import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import com.tss.grc.module.risk.repository.AppetiteBreachEventRepository;
import com.tss.grc.module.risk.repository.RiskAppetiteStatementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
        // Auto-evaluate breach on exposure change
        if (req.currentExposure() != null && !req.currentExposure().equals(previousExposure)) {
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

    @Transactional(readOnly = true)
    public List<AppetiteBreachEvent> listBreaches(Long appetiteId) {
        require(appetiteId);
        return breachRepo.findByAppetiteIdOrderByBreachedAtDesc(appetiteId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Long org = TenantContext.getCurrentTenant();
        List<RiskAppetiteStatement> all = repo.findByOrganisationIdOrderByCreatedAtDesc(org, Pageable.ofSize(200)).getContent();
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

    private void evaluateExposure(RiskAppetiteStatement s, String notes) {
        String severity = computeSeverity(s);
        if (severity == null || "WITHIN_TOLERANCE".equals(severity)) return;
        AppetiteBreachEvent ev = new AppetiteBreachEvent();
        ev.setAppetiteId(s.getId());
        ev.setObservedValue(s.getCurrentExposure());
        ev.setBreachSeverity(severity);
        ev.setBreachedAt(LocalDateTime.now());
        ev.setNotes(notes);
        ev.setStatus("OPEN");
        breachRepo.save(ev);
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
