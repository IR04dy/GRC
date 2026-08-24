package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * ⚠⚠ F2-324 — the positive half of the {@code source_module} derivation, and <b>its package is its
 * fixture</b>.
 *
 * <p>{@code AuditLogService} now derives {@code source_module} from the caller's package when the
 * builder did not say, because {@code log(...)} constructs its own builder and <b>686 of the platform's
 * 722 audit-writing sites use it</b> — so the column was NULL on 87.1% of dev rows and 93.6% of prod
 * rows, and its two readers (the {@code sourceModule} filter on {@code GET /api/v1/audit-log} and the
 * BCM evidence package's {@code audit_log.csv}) saw a fraction of the ledger.
 *
 * <p>🔑 <b>This test cannot live in {@code module/platform/auditlog}</b>, and that is the whole reason
 * it is here. A walk that failed and returned {@code AuditLogService}'s own frame would derive
 * {@code platform} — which is exactly what a correct walk derives for a caller in that package. So an
 * assertion made from the ledger's own package cannot distinguish the two. Asserting {@code bcm} from
 * {@code module/bcm} can: only a walk that actually skipped every {@code AuditLogService} frame,
 * including the CGLIB subclass and Spring's AOP interceptors, can produce it.
 *
 * <p>⚠ Moving this file changes what it asserts. Its negative twin — a caller outside
 * {@code com.tss.grc.module} deriving nothing — is in
 * {@code com.tss.grc.security.AuditSourceModuleContractTest} for the same reason.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditModuleDerivationTest {

    @Mock private AuditLogRepository repo;
    @Mock private ObjectProvider<HttpServletRequest> requests;

    private AuditLogEntry writeOne(Runnable call) {
        // ⚠ recordEvent dereferences the SAVED row (it reads back the persisted id for the
        // hash chain), so a mock returning null NPEs before any assertion is reached.
        when(repo.save(any(AuditLogEntry.class))).thenAnswer(i -> i.getArgument(0));
        TenantContext.setCurrentTenant(1L);
        try {
            call.run();
        } finally {
            TenantContext.clear();
        }
        ArgumentCaptor<AuditLogEntry> cap = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repo).save(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("F2-324: log(...) from a module derives that module, not the ledger's own")
    void theConvenienceMethodDerivesTheCallingModule() {
        AuditLogService svc = new AuditLogService(repo, requests);
        AuditLogEntry row = writeOne(() -> svc.log("CUSTOM", "BcmAssessmentCycle", 7L, "probe"));

        assertThat(row.getSourceModule()).as("""
            log(...) did not derive the calling module. This is the single fix that makes 686 audit \
            sites attributable — without it the platform-wide module filter and the BCM evidence \
            package's audit_log.csv both see a fraction of the ledger.""")
            .isEqualTo("bcm");
        assertThat(row.getSourceModule()).as("""
            The derivation landed on the ledger's own module rather than on the caller's, which is what \
            a frame walk that fails to skip AuditLogService (or its CGLIB subclass, or Spring's AOP \
            interceptors) produces. That failure is invisible from a test in the auditlog package, \
            because there the correct answer is 'platform' too.""")
            .isNotEqualTo("platform");
    }

    @Test
    @DisplayName("F2-324: an explicit sourceModule still wins over the derivation")
    void anExplicitValueWins() {
        AuditLogService svc = new AuditLogService(repo, requests);
        AuditLogEntry row = writeOne(() -> svc.recordEvent(
            new AuditLogService.AuditEventBuilder()
                .actionType("CUSTOM").entityType("Thing").entityId(1L)
                .sourceModule("compliance")
                .message("explicit")));

        assertThat(row.getSourceModule()).as("""
            The derivation overrode an explicit value. 24 sites name their module deliberately — \
            ExportAuditInterceptor derives it from the request path, which no stack walk can see — so \
            the derivation must only fill the gap.""")
            .isEqualTo("compliance");
    }

    @Test
    @DisplayName("F2-324: logSystem(...) is derived too — org-less is not module-less")
    void theSystemVariantIsAlsoAttributed() {
        AuditLogService svc = new AuditLogService(repo, requests);
        TenantContext.clear();
        AuditLogEntry row = writeOne(() -> svc.logSystem("CUSTOM", "Database", null, "no tenant"));

        assertThat(row.getSourceModule()).as("""
            logSystem declares an event to belong to no TENANT (F2-320). That says nothing about which \
            module wrote it, and a row that can name neither is unreachable from both readers.""")
            .isEqualTo("bcm");
    }
}
