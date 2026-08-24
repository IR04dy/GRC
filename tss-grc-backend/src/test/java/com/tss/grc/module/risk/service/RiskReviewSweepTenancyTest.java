package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk.repository.RiskReviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ⚠⚠ F2-317 — the all-tenants risk-review sweep wrote every audit row against <b>tenant 1</b>.
 *
 * <p>{@code RiskReviewService.flag} has two callers and only one arrives with an ambient tenant:
 *
 * <pre>
 *   runOverdueSweepForTenant()   tenant-scoped query, called from RiskReviewController
 *   runOverdueSweepAllTenants()  cross-tenant query, called from RiskReviewScheduler
 * </pre>
 *
 * <p>{@code AuditLogService.recordEvent} does {@code org != null ? org : 1L} — <em>"fall back to
 * tenant 1 (system events)"</em> — so on the scheduler path every {@code RISK_REVIEW_OVERDUE} row was
 * stamped organisation 1 whatever tenant's risk it described. Tenant 1's audit log gains entries
 * naming other tenants' risk codes; those tenants get no record that their risks were flagged.
 *
 * <p>🔑 The {@code DomainEvent} two lines below already passed {@code r.getOrganisationId()}
 * explicitly. <b>Within one loop body the event was org-explicit and the audit row beside it was
 * not</b>, and that asymmetry is the whole tell.
 *
 * <p>Latent when found — the scheduler is {@code grc.risk.review-scheduler-enabled:false} and set in
 * no profile — so these legs assert a behaviour no live row had exercised yet. That is the point: 24
 * risks are past their review date unflagged, <b>20 of them in tenants 2 and 3</b>, so enabling one
 * flag would have mis-stamped twenty audit rows.
 */
class RiskReviewSweepTenancyTest {

    private RiskRepository riskRepo;
    private AuditLogService audit;
    private RiskReviewService service;

    /** The tenant that was current at the moment each audit row was written. */
    private final List<Long> auditTenants = new ArrayList<>();

    @BeforeEach
    void setUp() {
        riskRepo = mock(RiskRepository.class);
        audit = mock(AuditLogService.class);
        service = new RiskReviewService(riskRepo, mock(RiskReviewRepository.class), audit,
                                        mock(ApplicationEventPublisher.class));
        when(riskRepo.save(any(Risk.class))).thenAnswer(i -> i.getArgument(0));

        // AuditLogService reads TenantContext, so capture what it WOULD have stamped. A mock that
        // records only its arguments cannot see this defect: the organisation is never an argument.
        doAnswer(inv -> {
            auditTenants.add(TenantContext.getCurrentTenant());
            return null;
        }).when(audit).log(anyString(), anyString(), anyLong(), anyString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        auditTenants.clear();
    }

    private static Risk overdue(long id, long org) {
        Risk r = new Risk();
        r.setId(id);
        r.setOrganisationId(org);
        r.setReviewDate(LocalDate.now().minusDays(9));
        r.setReviewStatus("CURRENT");
        return r;
    }

    /**
     * ⚠⚠ The leg the defect turns on. A mocked audit service that only records its <em>arguments</em>
     * is blind here, because the organisation is not an argument — it is read from the thread. So the
     * stub captures {@code TenantContext} at the moment of the call.
     */
    @Test
    @DisplayName("the all-tenants sweep stamps each audit row with the risk's OWN organisation")
    void everyAuditRowCarriesItsOwnRisksTenant() {
        when(riskRepo.findReviewOverdueAllOrgs(any(LocalDate.class)))
            .thenReturn(List.of(overdue(11L, 1L), overdue(22L, 2L), overdue(33L, 3L)));

        int flagged = service.runOverdueSweepAllTenants();

        assertThat(flagged).isEqualTo(3);
        assertThat(auditTenants).as("""
            An audit row from the cross-tenant sweep was written under the wrong organisation.

            AuditLogService falls back to tenant 1 when TenantContext is unset, so before F2-317 this \
            read [1, 1, 1]: three tenants' risks, all audited against tenant 1. The DomainEvent \
            beside it was already org-explicit.""")
            .containsExactly(1L, 2L, 3L);
    }

    /**
     * ⚠ The ambient tenant is <b>restored</b>, not cleared. {@code runOverdueSweepForTenant} is called
     * from a controller, so the request continues after this returns — clearing would strip its tenant
     * mid-request, which is a regression the obvious {@code finally { clear(); }} would have shipped.
     * ({@code KriSchedulerService} clears and is right to: it has no ambient tenant to lose.)
     */
    @Test
    @DisplayName("the caller's tenant survives the sweep rather than being cleared")
    void theAmbientTenantIsRestoredNotCleared() {
        TenantContext.setCurrentTenant(7L);
        when(riskRepo.findReviewOverdue(eq(7L), any(LocalDate.class)))
            .thenReturn(List.of(overdue(44L, 7L)));

        service.runOverdueSweepForTenant();

        assertThat(TenantContext.getCurrentTenant()).as("""
            The controller path continues inside the same request after this returns. Clearing the \
            tenant here would strip it mid-request — which is what a plain finally { clear() } does, \
            and why this method restores instead.""")
            .isEqualTo(7L);
    }

    /** And a scheduler path leaves no tenant behind on the thread it borrowed. */
    @Test
    void theSchedulerPathLeavesNoTenantSetAfterwards() {
        TenantContext.clear();
        when(riskRepo.findReviewOverdueAllOrgs(any(LocalDate.class)))
            .thenReturn(List.of(overdue(55L, 2L)));

        service.runOverdueSweepAllTenants();

        assertThat(TenantContext.getCurrentTenant()).as("""
            The scheduler had no ambient tenant, so restoring it must leave none — a sweep that \
            leaked tenant 2 onto a pooled thread would make the NEXT job on that thread write as \
            tenant 2.""")
            .isNull();
    }

    /** An already-flagged risk is skipped without disturbing the tenant of the rows around it. */
    @Test
    void anAlreadyFlaggedRiskIsSkippedAndTheRowsAroundItAreStillCorrect() {
        Risk already = overdue(66L, 2L);
        already.setReviewStatus("OVERDUE");
        when(riskRepo.findReviewOverdueAllOrgs(any(LocalDate.class)))
            .thenReturn(List.of(overdue(11L, 1L), already, overdue(33L, 3L)));

        assertThat(service.runOverdueSweepAllTenants()).isEqualTo(2);
        assertThat(auditTenants).containsExactly(1L, 3L);
    }

    /**
     * F2-317 — every caller of a cross-tenant finder must set the tenant <b>from the row</b> before
     * writing an audit entry.
     *
     * <p>The population is <b>two</b> today: this service's {@code flag}, and
     * {@code EvidenceItemService.expireStaleEvidence}, which already did it and carries an [[F2-242]]
     * comment saying why. 🔑 The correct idiom was in the tree beside the one place it was not
     * applied ([[F2-195]]). This leg exists because the population will grow — a third
     * {@code *AllOrgs} finder is one method away, and its author has no reason to know that
     * {@code AuditLogService} silently falls back to <b>tenant 1</b> rather than failing.
     *
     * <p>⚠ <b>File-scoped, deliberately.</b> My first draft looked forward a fixed number of
     * characters from the call site and <b>failed on this very fix</b>, because the call is
     * {@code return flag(repo.findReviewOverdueAllOrgs(...))} and the per-row setter lives in
     * {@code flag} — which the long javadoc above it pushed past the window. A gate that fails on
     * correct code trains the next reader to edit the assertion ([[F2-214]]), and a character window
     * is a fragile way to ask a per-method question. So this asks whether the CLASS anchors a tenant
     * at all: it catches a cross-tenant sweep in a class that never does, and would miss one that
     * anchors in a different method of the same class. Said out loud rather than implied.
     *
     * <p>⚠ It keys on the {@code AllOrgs} / {@code AllTenants} naming convention, which [[F2-217]]
     * warns measures the list its author knew. Stated rather than hidden: a cross-tenant finder named
     * some other way is invisible here, and the count below fails if the two known ones stop
     * resolving, so the answer cannot quietly shrink to nothing.
     */
    @Test
    @DisplayName("every cross-tenant sweep sets the tenant from the row before auditing")
    void crossTenantSweepsAnchorTheTenantOnTheRow() throws IOException {
        Path main = Paths.get("src", "main", "java", "com", "tss", "grc", "module");
        Pattern finder = Pattern.compile("\\.(?:find|count)\\w*(?:AllOrgs|AllTenants)\\w*\\(");
        Pattern comments = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

        List<String> missing = new ArrayList<>();
        int callers = 0;
        try (Stream<Path> walk = Files.walk(main)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = comments.matcher(Files.readString(p, StandardCharsets.UTF_8))
                    .replaceAll(m -> " ".repeat(m.group().length()));
                if (src.contains("interface ") && src.contains("Repository")) {
                    continue;                                   // the declaration, not a caller
                }
                Matcher m = finder.matcher(src);
                boolean anchorsATenant = src.contains("TenantContext.setCurrentTenant(");
                while (m.find()) {
                    callers++;
                    if (!anchorsATenant) {
                        missing.add(p.getFileName() + " calls " + m.group()
                                    + " and never anchors a tenant anywhere in the class");
                    }
                }
            }
        }

        assertThat(callers).as("""
            No cross-tenant finder call sites were found, so this leg is vacuous. Either the naming             convention changed or the walk is reading the wrong tree.""")
            .isGreaterThanOrEqualTo(2);
        assertThat(missing).as("""
            A cross-tenant sweep writes without anchoring the tenant on the row it is processing.

            AuditLogService does `org != null ? org : 1L`, so every audit row such a sweep writes is             stamped tenant 1 whatever tenant's record it describes: tenant 1's log gains entries             about other tenants, and those tenants get no record at all. Set the tenant from the             row, and RESTORE the ambient one if the method has an on-request caller.""")
            .isEmpty();
    }
}
