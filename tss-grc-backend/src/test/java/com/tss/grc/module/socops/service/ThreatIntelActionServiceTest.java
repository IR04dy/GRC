package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberAsset;
import com.tss.grc.module.cyber.entity.Vulnerability;
import com.tss.grc.module.cyber.repository.CyberAssetRepository;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.DetectionRule;
import com.tss.grc.module.socops.entity.ThreatIntel;
import com.tss.grc.module.socops.entity.ThreatIntelAction;
import com.tss.grc.module.socops.repository.DetectionRuleRepository;
import com.tss.grc.module.socops.repository.ThreatIntelActionRepository;
import com.tss.grc.module.socops.repository.ThreatIntelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SOC-E — {@link ThreatIntelService#actionIoc}. Actioning an IOC appends an immutable action row
 * that links the IOC to a concrete platform target (ASSET / VULNERABILITY / DETECTION), validated
 * to exist in the tenant, and flips the parent's denormalised {@code actioned} flag.
 */
@ExtendWith(MockitoExtension.class)
class ThreatIntelActionServiceTest {

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

    private static ThreatIntel ti(Long id) {
        ThreatIntel t = new ThreatIntel();
        t.setId(id);
        t.setOrganisationId(ORG);
        t.setSource("MISP");
        t.setExternalId("IOC-1");
        t.setActioned(false);
        return t;
    }

    @Test
    void actionIoc_linksDetection_appendsRow_andFlipsActioned() {
        ThreatIntel t = ti(5L);
        DetectionRule det = new DetectionRule(); det.setId(9L); det.setRuleCode("DET-9"); det.setTitleEn("Phish detect");
        when(repo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(t));
        when(detections.findByIdAndOrganisationId(9L, ORG)).thenReturn(Optional.of(det));
        when(actions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ThreatIntel result = svc.actionIoc(5L, "LINK_DETECTION", "DETECTION", 9L, "linked to phishing use-case");

        ArgumentCaptor<ThreatIntelAction> cap = ArgumentCaptor.forClass(ThreatIntelAction.class);
        verify(actions).save(cap.capture());
        ThreatIntelAction a = cap.getValue();
        assertThat(a.getThreatIntelId()).isEqualTo(5L);
        assertThat(a.getActionType()).isEqualTo("LINK_DETECTION");
        assertThat(a.getTargetType()).isEqualTo("DETECTION");
        assertThat(a.getTargetId()).isEqualTo(9L);
        assertThat(a.getTargetLabel()).contains("DET-9").contains("Phish detect");
        // parent summary flag flipped
        assertThat(result.getActioned()).isTrue();
        assertThat(result.getActionNotes()).isEqualTo("linked to phishing use-case");
    }

    @Test
    void actionIoc_assetTarget_resolvesLabelFromAsset() {
        ThreatIntel t = ti(5L);
        CyberAsset asset = new CyberAsset(); asset.setId(3L); asset.setAssetName("Core DB Server");
        when(repo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(t));
        when(cyberAssets.findByIdAndOrganisationId(3L, ORG)).thenReturn(Optional.of(asset));
        when(actions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.actionIoc(5L, "BLOCK", "ASSET", 3L, null);

        ArgumentCaptor<ThreatIntelAction> cap = ArgumentCaptor.forClass(ThreatIntelAction.class);
        verify(actions).save(cap.capture());
        assertThat(cap.getValue().getTargetLabel()).isEqualTo("Core DB Server");
    }

    @Test
    void actionIoc_unknownActionType_rejected() {
        when(repo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(ti(5L)));
        assertThatThrownBy(() -> svc.actionIoc(5L, "TELEPORT", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(actions, never()).save(any());
    }

    @Test
    void actionIoc_targetTypeWithoutId_rejected() {
        when(repo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(ti(5L)));
        assertThatThrownBy(() -> svc.actionIoc(5L, "BLOCK", "ASSET", null, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(actions, never()).save(any());
    }

    @Test
    void actionIoc_unknownTarget_rejected() {
        when(repo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(ti(5L)));
        when(vulnerabilities.findByIdAndOrganisationId(404L, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.actionIoc(5L, "PATCH", "VULNERABILITY", 404L, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(actions, never()).save(any());
    }

    @Test
    void actionIoc_noTarget_defaultsToReviewed_noTargetPersisted() {
        ThreatIntel t = ti(5L);
        when(repo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(t));
        when(actions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.actionIoc(5L, null, null, 999L /* stray id, no type */, "done");

        ArgumentCaptor<ThreatIntelAction> cap = ArgumentCaptor.forClass(ThreatIntelAction.class);
        verify(actions).save(cap.capture());
        ThreatIntelAction a = cap.getValue();
        assertThat(a.getActionType()).isEqualTo("REVIEWED");
        assertThat(a.getTargetType()).isNull();
        assertThat(a.getTargetId()).isNull();       // stray id dropped when no target type
    }

    @Test
    void listActions_returnsTenantScopedHistory() {
        ThreatIntel t = ti(5L);
        ThreatIntelAction a1 = new ThreatIntelAction(); a1.setId(1L);
        when(repo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(t));
        when(actions.findByOrganisationIdAndThreatIntelIdOrderByActionedAtDesc(ORG, 5L)).thenReturn(List.of(a1));

        assertThat(svc.listActions(5L)).extracting(ThreatIntelAction::getId).containsExactly(1L);
    }
}
