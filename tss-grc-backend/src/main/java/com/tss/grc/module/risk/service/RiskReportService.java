package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.dto.RiskReportDtos.AppetiteBreachRow;
import com.tss.grc.module.risk.dto.RiskReportDtos.AuditPackage;
import com.tss.grc.module.risk.dto.RiskReportDtos.RegisterStats;
import com.tss.grc.module.risk.dto.RiskReportDtos.TopRiskRow;
import com.tss.grc.module.risk.entity.AppetiteBreachEvent;
import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import com.tss.grc.module.risk.repository.AppetiteBreachEventRepository;
import com.tss.grc.module.risk.repository.RiskAcceptanceRepository;
import com.tss.grc.module.risk.repository.RiskAppetiteStatementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERM epic E-K — risk reporting + audit evidence package.
 *
 * <p>Read-only aggregations over the register for the board / committee
 * surfaces and an auditor-pullable evidence package. No new storage — every
 * figure is derived live from the canonical register, appetite statements,
 * breaches, acceptances and the E-J review posture.
 */
@Service
@RequiredArgsConstructor
public class RiskReportService {

    private final RiskRepository risks;
    private final RiskAppetiteStatementRepository appetites;
    private final AppetiteBreachEventRepository breaches;
    private final RiskAcceptanceRepository acceptances;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Top-risk / board / committee report ─────────────────────────────────

    @Transactional(readOnly = true)
    public List<TopRiskRow> topRisks(int limit) {
        int n = Math.max(1, Math.min(limit, 100));
        return risks.findTopActiveByResidual(org(), PageRequest.of(0, n))
                .stream().map(TopRiskRow::from).toList();
    }

    // ── Appetite-breach report ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AppetiteBreachRow> appetiteBreaches(boolean openOnly) {
        Long o = org();
        List<RiskAppetiteStatement> statements =
                appetites.findByOrganisationIdOrderByCreatedAtDesc(o, PageRequest.of(0, 500)).getContent();
        java.util.ArrayList<AppetiteBreachRow> out = new java.util.ArrayList<>();
        for (RiskAppetiteStatement s : statements) {
            for (AppetiteBreachEvent ev : breaches.findByAppetiteIdOrderByBreachedAtDesc(s.getId())) {
                if (openOnly && !"OPEN".equals(ev.getStatus())) continue;
                out.add(new AppetiteBreachRow(
                        ev.getId(), s.getId(), s.getTitleEn(), s.getCategoryId(),
                        ev.getBreachSeverity(), ev.getObservedValue(), s.getToleranceUpper(),
                        s.getCurrentExposure(), ev.getStatus(),
                        ev.getBreachedAt(), ev.getAcknowledgedAt()));
            }
        }
        return out;
    }

    // ── Register posture stats ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RegisterStats registerStats() {
        Long o = org();
        Map<String, Long> byLevel = new LinkedHashMap<>();
        for (Object[] row : risks.countGroupedByLevel(o)) byLevel.put((String) row[0], (Long) row[1]);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : risks.countGroupedByStatus(o)) byStatus.put((String) row[0], (Long) row[1]);

        long overdue = risks.findReviewOverdue(o, LocalDate.now()).size();
        long activeAcceptances = acceptances.countByOrganisationIdAndStatus(o, "APPROVED");
        long openBreaches = appetiteBreaches(true).size();

        return new RegisterStats(risks.countByOrganisationId(o), byLevel, byStatus,
                overdue, activeAcceptances, openBreaches);
    }

    // ── Audit evidence package ──────────────────────────────────────────────

    @Transactional   // not readOnly — records an audit-log row for the export
    public AuditPackage auditPackage() {
        Long o = org();
        AuditPackage pkg = new AuditPackage(
                o, LocalDateTime.now(),
                registerStats(),
                topRisks(10),
                appetiteBreaches(true));
        // Pulling the package is itself an audit-relevant event (who exported the
        // register posture, and when) — record it on the F7 trail.
        audit.log("RISK_AUDIT_PACKAGE_EXPORTED", "Risk", null,
                "Risk audit evidence package exported · "
                        + pkg.stats().totalRisks() + " risks, "
                        + pkg.openAppetiteBreaches().size() + " open appetite breach(es)");
        return pkg;
    }
}
