package com.tss.grc.module.iam.governance.service;

import com.tss.grc.common.json.JsonText;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.governance.entity.SodRule;
import com.tss.grc.module.iam.governance.entity.SodViolation;
import com.tss.grc.module.iam.governance.repository.SodRuleRepository;
import com.tss.grc.module.iam.governance.repository.SodViolationRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService.AuditEventBuilder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RBAC Access-Governance P2 (plan §6.4) — the segregation-of-duties engine:
 * <ul>
 *   <li><b>Detective scan</b> (nightly + on-demand): evaluates every active rule
 *       against the live grant landscape and writes {@code iam.sod_violations};
 *       idempotent — a conflict with a still-active violation row (OPEN, or
 *       ACCEPTED_RISK within its expiry) is not re-raised.</li>
 *   <li><b>Preventive check</b>: {@link #grantConflicts} tells the access-request
 *       approval which PAIR rules the new role would newly complete for the
 *       beneficiary; override requires GRC_ADMINISTRATOR + documented justification
 *       and still records the violation (source OVERRIDE).</li>
 *   <li><b>Disposition workflow</b>: OPEN → ACCEPTED_RISK (expiry mandatory) |
 *       REMEDIATED; the violating user cannot disposition their own violation.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SodService {

    private static final Logger log = LoggerFactory.getLogger(SodService.class);

    private final SodRuleRepository rules;
    private final SodViolationRepository violations;
    private final AuditLogService audit;

    // ── rules & violations (read) ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SodRule> listRules() {
        return rules.findAllForOrg(org());
    }

    @Transactional(readOnly = true)
    public List<SodViolation> listViolations(String status) {
        Long org = org();
        return status == null || status.isBlank()
            ? violations.findByOrganisationIdAndIsDeletedFalseOrderByDetectedAtDesc(org)
            : violations.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByDetectedAtDesc(org, status.toUpperCase(Locale.ROOT));
    }

    // ── detective scan ───────────────────────────────────────────────────

    @Transactional
    public int scan() {
        return scanForOrg(org());
    }

    /** Org-explicit so the nightly scheduler can run it without a request context. */
    @Transactional
    public int scanForOrg(Long org) {
        int raised = 0;
        for (SodRule rule : rules.findActiveForOrg(org)) {
            List<Object[]> hits = switch (rule.getRuleType()) {
                case "PAIR" -> violations.pairConflictUsers(org,
                    rule.getModuleA(), rule.getActionA(), rule.getModuleB(), rule.getActionB());
                case "STRUCTURAL" -> structuralHits(org, rule);
                default -> List.of();
            };
            for (Object[] hit : hits) {
                Long userId = ((Number) hit[0]).longValue();
                String username = String.valueOf(hit[1]);
                if (hasActiveViolation(org, rule.getId(), userId)) continue;
                SodViolation v = new SodViolation();
                v.setOrganisationId(org);
                v.setRuleId(rule.getId());
                v.setRuleCode(rule.getRuleCode());
                v.setSeverity(rule.getSeverity());
                v.setUserId(userId);
                v.setUsername(username);
                v.setDetailEn(ruleDetail(rule));
                v.setDetectionSource("SCAN");
                violations.save(v);
                raised++;
            }
        }
        if (raised > 0) {
            audit.recordEvent(new AuditEventBuilder()
                .actionType("CREATE").sourceModule("IAM").entityType("SodViolation")
                .message("SoD detective scan raised " + raised + " violation(s) for org " + org));
        }
        return raised;
    }

    /**
     * Nightly detective scan across all tenants (02:30 UTC).
     *
     * <p>⚠⚠ F2-318 — the tenant is anchored per organisation. {@code AuditLogService.recordEvent}
     * does {@code org != null ? org : 1L} ("fall back to tenant 1 for system events"), and this runs
     * off-request, so before this every {@code CREATE}/{@code SodViolation} row {@link #scanForOrg}
     * wrote landed in organisation 1 whatever tenant's violation it described — tenant 1's audit log
     * gaining entries about other tenants' segregation-of-duties breaches (NCA ECC 2-2 / SAMA CSF
     * 3.3.5), and those tenants getting none.
     *
     * <p>🔑 The tell was in the message: {@code "for org " + org}. <b>Three of this family's four
     * instances interpolate the organisation into the audit prose</b>, which is what an author writes
     * when the row's own tenant column will not carry it. The previous javadoc here said
     * "off-request-safe", which was true about not crashing and silent about attribution.
     *
     * <p>⚠ The ambient tenant is <b>restored</b>, not cleared: {@link #scanForOrg} is also reachable
     * on-request, and restoring is correct whether or not a caller supplied one.
     */
    @Scheduled(cron = "0 30 2 * * *")
    public void nightlyScan() {
        Long ambient = TenantContext.getCurrentTenant();
        try {
            for (Long org : violations.distinctOrganisationIds()) {
                try {
                    TenantContext.setCurrentTenant(org);
                    int n = scanForOrg(org);
                    if (n > 0) log.info("nightly SoD scan: org {} raised {} violation(s)", org, n);
                } catch (RuntimeException e) {
                    log.warn("nightly SoD scan failed for org {}: {}", org, e.getMessage());
                }
            }
        } finally {
            TenantContext.setCurrentTenant(ambient);
        }
    }

    private List<Object[]> structuralHits(Long org, SodRule rule) {
        if (rule.getRolePatternCsv() == null || rule.getRolePatternCsv().isBlank()) return List.of();
        List<Object[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String pattern : rule.getRolePatternCsv().split(",")) {
            String p = pattern.trim();
            if (p.isEmpty()) continue;
            List<Object[]> hits = switch (rule.getStructuralKey()) {
                case "READONLY_PERSONA_WRITE" -> violations.readonlyPersonaWriters(org, p);
                case "SERVICE_MULTI_MODULE"   -> violations.serviceMultiModuleUsers(org, p);
                default -> List.<Object[]>of();
            };
            for (Object[] hit : hits) {
                if (seen.add(((Number) hit[0]).longValue())) out.add(hit);
            }
        }
        return out;
    }

    private boolean hasActiveViolation(Long org, Long ruleId, Long userId) {
        for (SodViolation v : violations.findByOrganisationIdAndRuleIdAndUserIdAndIsDeletedFalse(org, ruleId, userId)) {
            if ("OPEN".equals(v.getStatus())) return true;
            if ("ACCEPTED_RISK".equals(v.getStatus())
                    && v.getAcceptedUntil() != null && !v.getAcceptedUntil().isBefore(LocalDate.now())) {
                return true;
            }
        }
        return false;
    }

    private static String ruleDetail(SodRule r) {
        return "PAIR".equals(r.getRuleType())
            ? r.getModuleA() + "/" + r.getActionA() + " × " + r.getModuleB() + "/" + r.getActionB()
            : r.getStructuralKey() + " [" + r.getRolePatternCsv() + "]";
    }

    // ── disposition workflow ─────────────────────────────────────────────

    @Transactional
    public SodViolation disposition(Long id, String status, String note, LocalDate acceptedUntil, Long actor) {
        SodViolation v = violations.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("SoD violation not found: " + id));
        if (!"OPEN".equals(v.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Violation already dispositioned (" + v.getStatus() + ")");
        }
        if (actor != null && actor.equals(v.getUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The violating user cannot disposition their own SoD violation");
        }
        if ("ACCEPTED_RISK".equals(status) && acceptedUntil == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "ACCEPTED_RISK requires acceptedUntil (risk acceptance must expire)");
        }
        v.setStatus(status);
        v.setDispositionBy(actor);
        v.setDispositionAt(LocalDateTime.now());
        v.setDispositionNote(note);
        v.setAcceptedUntil("ACCEPTED_RISK".equals(status) ? acceptedUntil : null);
        SodViolation saved = violations.save(v);
        audit.log("UPDATE", "SodViolation", saved.getId(),
            "SoD violation " + saved.getRuleCode() + "/" + saved.getUsername() + " → " + status);
        return saved;
    }

    // ── preventive check (called from access-request approval) ──────────

    /**
     * PAIR rules the grant of {@code roleId} to {@code userId} would newly complete —
     * i.e. the role contributes at least one side and the post-grant state holds both.
     */
    @Transactional(readOnly = true)
    public List<SodRule> grantConflicts(Long org, Long userId, Long roleId) {
        Set<String> roleGrants = new HashSet<>();
        for (Object[] g : violations.roleGrantPairs(roleId)) {
            roleGrants.add(g[0] + "/" + g[1]);
        }
        List<SodRule> conflicts = new ArrayList<>();
        for (SodRule rule : rules.findActiveForOrg(org)) {
            if (!"PAIR".equals(rule.getRuleType())) continue;
            String a = rule.getModuleA() + "/" + rule.getActionA();
            String b = rule.getModuleB() + "/" + rule.getActionB();
            boolean roleA = roleGrants.contains(a), roleB = roleGrants.contains(b);
            if (!roleA && !roleB) continue;   // role contributes nothing to this rule
            boolean afterA = roleA || violations.countUserGrant(userId, rule.getModuleA(), rule.getActionA()) > 0;
            boolean afterB = roleB || violations.countUserGrant(userId, rule.getModuleB(), rule.getActionB()) > 0;
            if (afterA && afterB) conflicts.add(rule);
        }
        return conflicts;
    }

    /** Record violations for a grant approved past the preventive check (override). */
    @Transactional
    public void recordOverride(Long org, Long userId, String username, List<SodRule> conflicted,
                               String justification, Long approver) {
        for (SodRule rule : conflicted) {
            if (hasActiveViolation(org, rule.getId(), userId)) continue;
            SodViolation v = new SodViolation();
            v.setOrganisationId(org);
            v.setRuleId(rule.getId());
            v.setRuleCode(rule.getRuleCode());
            v.setSeverity(rule.getSeverity());
            v.setUserId(userId);
            v.setUsername(username);
            v.setDetailEn(ruleDetail(rule) + " — granted with override");
            v.setDetectionSource("OVERRIDE");
            // F2-185(b): store the justification VERBATIM. The 24-character "Override
            // justification: " prefix that used to sit here made the effective budget 976 against a
            // NVARCHAR(1000) column, so a justification the DTO's @Size(max = 1000) accepts
            // overflowed by 24 → SQL Server 8152 → HTTP 500 on an SoD override, rolling back the
            // grant it was authorising. Nothing is lost by dropping it: this same row carries
            // detection_source = 'OVERRIDE' and a detail_en ending "granted with override".
            v.setDispositionNote(justification);
            violations.save(v);
        }
        audit.recordEvent(new AuditEventBuilder()
            .actionType("CREATE").sourceModule("IAM").entityType("SodViolation")
            .newValueJson("{\"userId\":" + userId + ",\"rules\":\""
                + JsonText.escape(conflicted.stream().map(SodRule::getRuleCode)
                    .reduce((x, y) -> x + "," + y).orElse(""))
                + "\",\"approvedBy\":" + approver + "}")
            .message("SoD override recorded for " + username + " (" + conflicted.size() + " rule(s))"));
    }

    /** GRC_ADMINISTRATOR / PLATFORM_ADMIN check for overrides + exception closures. */
    @Transactional(readOnly = true)
    public boolean isGovernanceAdmin(Long userId) {
        return userId != null
            && (violations.countUserRole(userId, "GRC_ADMINISTRATOR") > 0
                || violations.countUserRole(userId, "PLATFORM_ADMIN") > 0);
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }
}
