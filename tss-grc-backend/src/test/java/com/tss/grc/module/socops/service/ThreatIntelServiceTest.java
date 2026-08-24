package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.repository.CyberAssetRepository;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.ThreatIntel;
import com.tss.grc.module.socops.repository.DetectionRuleRepository;
import com.tss.grc.module.socops.repository.ThreatIntelActionRepository;
import com.tss.grc.module.socops.repository.ThreatIntelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-333 — {@code actioned} / {@code actionedAt} / {@code actionedByUserId} / {@code actionNotes}
 * on {@link ThreatIntel} are the denormalised summary of the append-only {@code threat_intel_action}
 * ledger, written by exactly one route: {@code actionIoc()} (POST /{id}/action), which APPENDS an
 * immutable {@code ThreatIntelAction} row (stamping {@code actionedByUserId} from the principal)
 * BEFORE flipping the summary and audits {@code TI_ACTIONED}. {@code save()} must never mint that
 * summary directly — it would forge a SOC-E "actioned by X at T" record with no ledger row and no
 * audit (F2-331 / F2-332's ledger-pointer shape), and {@code save} admits {@code TI_INGEST}, a role
 * {@code /action} does not (F2-330's privilege boundary).
 */
@ExtendWith(MockitoExtension.class)
class ThreatIntelServiceTest {

    private static final Long ORG = 1L;

    @Mock private ThreatIntelRepository       repo;
    @Mock private AuditLogService             audit;
    @Mock private ApplicationEventPublisher   publisher;
    @Mock private ThreatIntelActionRepository actions;
    @Mock private CyberAssetRepository        cyberAssets;
    @Mock private VulnerabilityRepository     vulnerabilities;
    @Mock private DetectionRuleRepository     detections;
    @InjectMocks private ThreatIntelService   svc;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    /** Required non-null columns plus a non-actionable severity (no TI_ACTIONABLE event). */
    private static ThreatIntel base(Long id) {
        ThreatIntel t = new ThreatIntel();
        t.setId(id);
        t.setOrganisationId(ORG);
        t.setSource("NCA");
        t.setExternalId("ADV-1");
        t.setSeverity("LOW");
        return t;
    }

    @Test
    void update_cannotForgeActionedSummaryWithoutLedger() {
        ThreatIntel existing = base(5L);
        existing.setActioned(false);
        when(repo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // An explicit-update body (id set) trying to forge the SOC-E summary.
        ThreatIntel forged = base(5L);
        forged.setActioned(true);
        forged.setActionedByUserId(999L);
        forged.setActionedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        forged.setActionNotes("forged by a caller who never opened /action");

        ThreatIntel saved = svc.save(forged);

        // copyMutable no longer copies the summary, so the loaded row's values survive untouched.
        assertThat(saved.getActioned()).isFalse();
        assertThat(saved.getActionedByUserId()).isNull();
        assertThat(saved.getActionedAt()).isNull();
        assertThat(saved.getActionNotes()).isNull();
        // The summary's only writer is actionIoc, which appends a ledger row first.
        verify(actions, never()).save(any());
    }

    @Test
    void create_isBornUnactioned_scrubbingAForgedSummary() {
        when(repo.findByOrganisationIdAndSourceAndExternalId(ORG, "NCA", "ADV-1"))
            .thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ThreatIntel forged = base(null);
        forged.setActioned(true);
        forged.setActionedByUserId(999L);
        forged.setActionedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        forged.setActionNotes("born actioned");

        ThreatIntel saved = svc.save(forged);

        assertThat(saved.getActioned()).isFalse();
        assertThat(saved.getActionedByUserId()).isNull();
        assertThat(saved.getActionedAt()).isNull();
        assertThat(saved.getActionNotes()).isNull();
        verify(actions, never()).save(any());
    }
}
