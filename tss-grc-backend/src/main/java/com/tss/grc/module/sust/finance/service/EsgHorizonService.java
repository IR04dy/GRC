package com.tss.grc.module.sust.finance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.repository.CorrectiveActionRepository;
import com.tss.grc.module.sust.finance.entity.CbamLine;
import com.tss.grc.module.sust.finance.entity.EnvPermit;
import com.tss.grc.module.sust.finance.entity.EsgTarget;
import com.tss.grc.module.sust.finance.entity.EsgTargetActual;
import com.tss.grc.module.sust.finance.entity.IfrsRequirement;
import com.tss.grc.module.sust.finance.repository.CbamLineRepository;
import com.tss.grc.module.sust.finance.repository.EnvPermitRepository;
import com.tss.grc.module.sust.finance.repository.EsgTargetActualRepository;
import com.tss.grc.module.sust.finance.repository.EsgTargetRepository;
import com.tss.grc.module.sust.finance.repository.IfrsRequirementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Green-Finance uplift G4 (plan §5.4–§5.7) — the regulatory-horizon capabilities:
 * ESG targets & transition trajectory (off-track ⇒ auto-CAPA), the IFRS S1/S2
 * readiness checklist, NCEC permit expiry surveillance (⇒ auto-CAPA), and the
 * EU CBAM exposure screen. Auto-CAPAs are idempotent by (sourceType, sourceRef).
 */
@Service
@RequiredArgsConstructor
public class EsgHorizonService {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");   // 5 % trajectory tolerance

    private final EsgTargetRepository targets;
    private final EsgTargetActualRepository actuals;
    private final IfrsRequirementRepository ifrs;
    private final EnvPermitRepository permits;
    private final CbamLineRepository cbam;
    private final CorrectiveActionRepository capas;
    private final AuditLogService audit;

    // ── targets & trajectory (§5.5) ──────────────────────────────────────

    public record TargetView(EsgTarget target, List<EsgTargetActual> actuals,
                             BigDecimal expectedForLatestActual, String trajectory) {}

    @Transactional(readOnly = true)
    public List<TargetView> listTargets() {
        Long org = org();
        return targets.findByOrganisationIdAndIsDeletedFalseOrderByTargetCodeAsc(org).stream()
            .map(t -> {
                List<EsgTargetActual> acts =
                    actuals.findByOrganisationIdAndTargetIdAndIsDeletedFalseOrderByYearAsc(org, t.getId());
                if (acts.isEmpty()) return new TargetView(t, acts, null, "NO_DATA");
                EsgTargetActual latest = acts.get(acts.size() - 1);
                BigDecimal expected = expectedValue(t, latest.getYear());
                return new TargetView(t, acts, expected, trajectoryOf(t, latest.getValue(), expected));
            })
            .toList();
    }

    @Transactional
    public EsgTarget createTarget(EsgTarget t) {
        Long org = org();
        if (targets.existsByOrganisationIdAndTargetCodeAndIsDeletedFalse(org, t.getTargetCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Target code already exists: " + t.getTargetCode());
        }
        if (t.getTargetYear() <= t.getBaselineYear()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetYear must be after baselineYear");
        }
        t.setOrganisationId(org);
        EsgTarget saved = targets.save(t);
        audit.log("CREATE", "EsgTarget", saved.getId(), "ESG target created: " + saved.getTargetCode());
        return saved;
    }

    @Transactional
    public EsgTargetActual addActual(Long targetId, Integer year, BigDecimal value, String notes) {
        Long org = org();
        targets.findByOrganisationIdAndIdAndIsDeletedFalse(org, targetId)
            .orElseThrow(() -> new EntityNotFoundException("Target not found: " + targetId));
        if (actuals.existsByOrganisationIdAndTargetIdAndYearAndIsDeletedFalse(org, targetId, year)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Actual already recorded for " + year);
        }
        EsgTargetActual a = new EsgTargetActual();
        a.setOrganisationId(org);
        a.setTargetId(targetId);
        a.setYear(year);
        a.setValue(value);
        a.setNotes(notes);
        return actuals.save(a);
    }

    /** Off-track targets raise an idempotent auto-CAPA; returns how many were raised. */
    @Transactional
    public int trajectorySweep() {
        Long org = org();
        int raised = 0;
        for (TargetView v : listTargets()) {
            if (!"OFF_TRACK".equals(v.trajectory()) || !"ACTIVE".equals(v.target().getStatus())) continue;
            EsgTargetActual latest = v.actuals().get(v.actuals().size() - 1);
            String ref = "ESG_TARGET:" + v.target().getTargetCode() + ":" + latest.getYear();
            if (capas.existsByOrganisationIdAndSourceTypeAndSourceRef(org, "ESG_TARGET", ref)) continue;
            CorrectiveAction ca = new CorrectiveAction();
            ca.setOrganisationId(org);
            ca.setSourceType("ESG_TARGET");
            ca.setSourceRef(ref);
            ca.setTitleEn("Off-track ESG target: " + v.target().getTargetCode() + " (" + latest.getYear() + ")");
            ca.setTitleAr("انحراف عن مستهدف الاستدامة: " + v.target().getTargetCode());
            ca.setDescriptionEn("Actual " + latest.getValue() + " " + v.target().getUnit()
                + " vs expected " + v.expectedForLatestActual() + " on the baseline->target trajectory.");
            ca.setPriority("HIGH");
            ca.setTargetDate(LocalDate.now().plusMonths(3));
            capas.save(ca);
            raised++;
            audit.log("AUTO_CREATE", "CorrectiveAction", null,
                "Auto-CAPA raised for off-track target " + v.target().getTargetCode());
        }
        return raised;
    }

    // ── IFRS S1/S2 checklist (§5.4) ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<IfrsRequirement> listIfrs() {
        return ifrs.findByOrganisationIdAndIsDeletedFalseOrderByReqCodeAsc(org());
    }

    @Transactional
    public IfrsRequirement setIfrsStatus(Long id, String status, String coverageRef, String ownerName) {
        if (!List.of("COVERED", "PARTIAL", "GAP").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be COVERED/PARTIAL/GAP");
        }
        IfrsRequirement r = ifrs.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Requirement not found: " + id));
        r.setStatus(status);
        if (coverageRef != null) r.setCoverageRef(coverageRef);
        if (ownerName != null) r.setOwnerName(ownerName);
        IfrsRequirement saved = ifrs.save(r);
        audit.log("UPDATE", "IfrsRequirement", saved.getId(),
            "IFRS " + saved.getReqCode() + " -> " + status);
        return saved;
    }

    public record IfrsSummary(int total, int covered, int partial, int gap, BigDecimal coveragePct) {}

    @Transactional(readOnly = true)
    public IfrsSummary ifrsSummary() {
        List<IfrsRequirement> all = listIfrs();
        int covered = 0, partial = 0, gap = 0;
        for (IfrsRequirement r : all) {
            switch (r.getStatus()) {
                case "COVERED" -> covered++;
                case "PARTIAL" -> partial++;
                default -> gap++;
            }
        }
        BigDecimal pct = all.isEmpty() ? BigDecimal.ZERO
            : BigDecimal.valueOf(covered + partial * 0.5).divide(BigDecimal.valueOf(all.size()), MC)
                .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        return new IfrsSummary(all.size(), covered, partial, gap, pct);
    }

    // ── NCEC permits (§5.6) ──────────────────────────────────────────────

    public record PermitView(EnvPermit permit, long daysToExpiry) {}

    @Transactional(readOnly = true)
    public List<PermitView> listPermits() {
        LocalDate today = LocalDate.now();
        return permits.findByOrganisationIdAndIsDeletedFalseOrderByExpiryDateAsc(org()).stream()
            .map(p -> new PermitView(p, java.time.temporal.ChronoUnit.DAYS.between(today, p.getExpiryDate())))
            .toList();
    }

    @Transactional
    public EnvPermit createPermit(EnvPermit p) {
        Long org = org();
        if (permits.existsByOrganisationIdAndPermitNumberAndIsDeletedFalse(org, p.getPermitNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Permit already registered: " + p.getPermitNumber());
        }
        p.setOrganisationId(org);
        EnvPermit saved = permits.save(p);
        audit.log("CREATE", "EnvPermit", saved.getId(), "NCEC permit registered: " + saved.getPermitNumber());
        return saved;
    }

    /** Permits expiring within {@code leadDays} (or already expired) raise an idempotent auto-CAPA. */
    @Transactional
    public int permitExpirySweep(int leadDays) {
        Long org = org();
        LocalDate today = LocalDate.now();
        int raised = 0;
        for (EnvPermit p : permits.findByOrganisationIdAndIsDeletedFalseOrderByExpiryDateAsc(org)) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, p.getExpiryDate());
            if (days > leadDays) continue;
            if (days < 0 && !"EXPIRED".equals(p.getStatus())) {
                p.setStatus("EXPIRED");
                permits.save(p);
            }
            String ref = "ENV_PERMIT:" + p.getPermitNumber() + ":" + p.getExpiryDate();
            if (capas.existsByOrganisationIdAndSourceTypeAndSourceRef(org, "ENV_PERMIT", ref)) continue;
            CorrectiveAction ca = new CorrectiveAction();
            ca.setOrganisationId(org);
            ca.setSourceType("ENV_PERMIT");
            ca.setSourceRef(ref);
            ca.setTitleEn((days < 0 ? "EXPIRED NCEC permit: " : "NCEC permit expiring: ") + p.getPermitNumber());
            ca.setTitleAr((days < 0 ? "تصريح بيئي منتهٍ: " : "تصريح بيئي يوشك على الانتهاء: ") + p.getPermitNumber());
            ca.setDescriptionEn("Facility " + p.getFacilityEn() + " — permit expires " + p.getExpiryDate()
                + " (" + days + " days). Renew with NCEC before operations are affected.");
            ca.setPriority(days < 0 ? "CRITICAL" : "HIGH");
            ca.setTargetDate(p.getExpiryDate().isAfter(today) ? p.getExpiryDate() : today.plusWeeks(2));
            capas.save(ca);
            raised++;
            audit.log("AUTO_CREATE", "CorrectiveAction", null,
                "Auto-CAPA raised for permit " + p.getPermitNumber() + " (" + days + "d to expiry)");
        }
        return raised;
    }

    // ── CBAM (§5.7) ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CbamLine> listCbam() {
        return cbam.findByOrganisationIdAndIsDeletedFalseOrderByPeriodYearDescPeriodQuarterDesc(org());
    }

    @Transactional
    public CbamLine createCbamLine(CbamLine c) {
        c.setOrganisationId(org());
        c.setTotalEmbeddedTco2e(c.getExportVolumeTonnes().multiply(c.getEmbeddedTco2ePerTonne(), MC)
            .setScale(4, RoundingMode.HALF_UP));
        CbamLine saved = cbam.save(c);
        audit.log("CREATE", "CbamLine", saved.getId(),
            "CBAM line: " + saved.getCnGroup() + " " + saved.getPeriodYear() + "-Q" + saved.getPeriodQuarter());
        return saved;
    }

    public record CbamSummary(BigDecimal totalEmbeddedTco2e, Map<String, BigDecimal> byGroup, int lines) {}

    @Transactional(readOnly = true)
    public CbamSummary cbamSummary() {
        Map<String, BigDecimal> byGroup = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        List<CbamLine> all = listCbam();
        for (CbamLine c : all) {
            total = total.add(c.getTotalEmbeddedTco2e());
            byGroup.merge(c.getCnGroup(), c.getTotalEmbeddedTco2e(), BigDecimal::add);
        }
        return new CbamSummary(total.setScale(4, RoundingMode.HALF_UP), byGroup, all.size());
    }

    // ── trajectory math ──────────────────────────────────────────────────

    /** Linear interpolation of the baseline→target trajectory at {@code year}. */
    static BigDecimal expectedValue(EsgTarget t, int year) {
        int span = t.getTargetYear() - t.getBaselineYear();
        int elapsed = Math.min(Math.max(year - t.getBaselineYear(), 0), span);
        BigDecimal progress = BigDecimal.valueOf(elapsed).divide(BigDecimal.valueOf(span), MC);
        return t.getBaselineValue()
            .add(t.getTargetValue().subtract(t.getBaselineValue()).multiply(progress, MC))
            .setScale(4, RoundingMode.HALF_UP);
    }

    /** Direction-aware on/off-track with a 5 % tolerance band. */
    static String trajectoryOf(EsgTarget t, BigDecimal actual, BigDecimal expected) {
        boolean reduction = t.getTargetValue().compareTo(t.getBaselineValue()) < 0;
        BigDecimal band = expected.abs().multiply(TOLERANCE, MC);
        if (reduction) {
            return actual.compareTo(expected.add(band)) > 0 ? "OFF_TRACK" : "ON_TRACK";
        }
        return actual.compareTo(expected.subtract(band)) < 0 ? "OFF_TRACK" : "ON_TRACK";
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }
}
