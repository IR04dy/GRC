package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.DetectionRule;
import com.tss.grc.module.socops.repository.DetectionRuleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SOC-E — {@link DetectionRuleService}. A detection rule is the SOC's ATT&CK-mapped content;
 * the MITRE technique is mandatory, the type/status are enum-checked, and a duplicate rule code
 * is rejected as 400 (not a unique-constraint 500).
 */
@ExtendWith(MockitoExtension.class)
class DetectionRuleServiceTest {

    private static final Long ORG = 1L;

    @Mock private DetectionRuleRepository   repo;
    @Mock private AuditLogService           audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private DetectionRuleService svc;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    private static DetectionRule rule(String code, String type, String technique) {
        DetectionRule d = new DetectionRule();
        d.setRuleCode(code);
        d.setTitleEn("Detect " + code);
        d.setDetectionType(type);
        d.setMitreTechniques(technique);
        return d;
    }

    @Test
    void create_requiresMitreTechnique() {
        DetectionRule d = rule("DET-1", "SIEM", "   ");   // blank technique
        assertThatThrownBy(() -> svc.save(d)).isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_rejectsUnknownType() {
        DetectionRule d = rule("DET-2", "MAGIC", "T1566");
        assertThatThrownBy(() -> svc.save(d)).isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_rejectsDuplicateCode() {
        DetectionRule d = rule("DET-3", "EDR", "T1059");
        when(repo.findByOrganisationIdAndRuleCode(ORG, "DET-3")).thenReturn(Optional.of(new DetectionRule()));
        assertThatThrownBy(() -> svc.save(d)).isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_happyPath_defaultsStatus_savesAndBroadcasts() {
        DetectionRule d = rule("DET-4", "siem", "T1566,T1059.001");   // lower-case type is normalised
        when(repo.findByOrganisationIdAndRuleCode(ORG, "DET-4")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> { DetectionRule x = inv.getArgument(0); x.setId(7L); return x; });

        DetectionRule saved = svc.save(d);

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getOrganisationId()).isEqualTo(ORG);
        assertThat(saved.getDetectionType()).isEqualTo("SIEM");     // normalised
        assertThat(saved.getStatus()).isEqualTo("DRAFT");           // defaulted
        assertThat(saved.getEnabled()).isTrue();
        verify(audit).log(eq("DETECTION_RULE_CREATE"), eq("DetectionRule"), eq(7L), any());
        verify(publisher).publishEvent(any(DomainEvent.class));
    }

    @Test
    void list_withTechnique_usesFilterQuery() {
        Pageable pg = Pageable.ofSize(50);
        svc.list("T1566", pg);
        verify(repo).findByOrganisationIdAndMitreTechniquesContainingIgnoreCaseOrderByCreatedAtDesc(
            eq(ORG), eq("T1566"), any());
        verify(repo, never()).findByOrganisationIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void list_noTechnique_usesPlainQuery() {
        Pageable pg = Pageable.ofSize(50);
        svc.list(null, pg);
        verify(repo).findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any());
    }
}
