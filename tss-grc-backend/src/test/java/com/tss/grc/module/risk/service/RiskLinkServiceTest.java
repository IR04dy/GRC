package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.dto.RiskLinkDtos.CreateDto;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskLink;
import com.tss.grc.module.risk.repository.RiskLinkRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskLinkServiceTest {

    private static final Long ORG = 1L, OTHER = 99L, RID = 7L;

    @Mock RiskLinkRepository links;
    @Mock RiskRepository risks;
    @Mock AuditLogService audit;
    @Mock CommitteeRepository committees;
    @Mock PolicyDocumentRepository policies;
    @InjectMocks RiskLinkService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private Risk risk(Long org) {
        Risk r = new Risk(); r.setId(RID); r.setOrganisationId(org); return r;
    }

    @Test void create_persistsTypedLink_andNormalisesType() {
        when(risks.findById(RID)).thenReturn(Optional.of(risk(ORG)));
        when(links.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskLink out = service.create(RID, new CreateDto("asset", "Core Banking DB", 42L, "primary store"));

        assertThat(out.getLinkType()).isEqualTo("ASSET");   // upper-cased
        assertThat(out.getTargetRef()).isEqualTo("Core Banking DB");
        assertThat(out.getTargetId()).isEqualTo(42L);
        assertThat(out.getOrganisationId()).isEqualTo(ORG);
    }

    @Test void create_unknownType_throws() {
        when(risks.findById(RID)).thenReturn(Optional.of(risk(ORG)));
        assertThatThrownBy(() -> service.create(RID, new CreateDto("WIDGET", "x", null, null)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("link type");
        verify(links, never()).save(any());
    }

    @Test void create_committeeLink_validatesRegister_andAutoFillsRef() {
        Committee c = new Committee();
        c.setId(7L); c.setOrganisationId(ORG); c.setNameEn("Board Risk Committee");
        when(risks.findById(RID)).thenReturn(Optional.of(risk(ORG)));
        when(committees.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(c));
        when(links.save(any())).thenAnswer(i -> i.getArgument(0));

        // blank targetRef → auto-filled from the committee name
        RiskLink out = service.create(RID, new CreateDto("committee", null, 7L, "governing committee"));

        assertThat(out.getLinkType()).isEqualTo("COMMITTEE");
        assertThat(out.getTargetId()).isEqualTo(7L);
        assertThat(out.getTargetRef()).isEqualTo("Board Risk Committee");
    }

    @Test void create_committeeLink_unknownCommittee_throws() {
        when(risks.findById(RID)).thenReturn(Optional.of(risk(ORG)));
        when(committees.findByIdAndOrganisationId(999L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(RID, new CreateDto("COMMITTEE", "X", 999L, null)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("governance register");
        verify(links, never()).save(any());
    }

    @Test void create_policyLink_validatesRegister_andAutoFillsRef() {
        PolicyDocument p = new PolicyDocument();
        p.setId(11L); p.setOrganisationId(ORG); p.setTitleEn("Enterprise Risk Management Policy");
        when(risks.findById(RID)).thenReturn(Optional.of(risk(ORG)));
        when(policies.findByIdAndOrganisationId(11L, ORG)).thenReturn(Optional.of(p));
        when(links.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskLink out = service.create(RID, new CreateDto("policy", null, 11L, "governing policy"));

        assertThat(out.getLinkType()).isEqualTo("POLICY");
        assertThat(out.getTargetRef()).isEqualTo("Enterprise Risk Management Policy");
    }

    @Test void create_policyLink_unknownPolicy_throws() {
        when(risks.findById(RID)).thenReturn(Optional.of(risk(ORG)));
        when(policies.findByIdAndOrganisationId(404L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(RID, new CreateDto("POLICY", "X", 404L, null)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("policy register");
        verify(links, never()).save(any());
    }

    @Test void create_blankRef_nonRegisterType_throws() {
        when(risks.findById(RID)).thenReturn(Optional.of(risk(ORG)));
        // PROCESS has no register to auto-fill from → a blank ref must be rejected.
        assertThatThrownBy(() -> service.create(RID, new CreateDto("PROCESS", "  ", null, null)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("targetRef is required");
        verify(links, never()).save(any());
    }

    @Test void create_crossTenantRisk_throws() {
        when(risks.findById(RID)).thenReturn(Optional.of(risk(OTHER)));
        assertThatThrownBy(() -> service.create(RID, new CreateDto("PROCESS", "Onboarding", null, null)))
            .isInstanceOf(EntityNotFoundException.class);
        verify(links, never()).save(any());
    }

    @Test void delete_softDeletes() {
        RiskLink l = new RiskLink(); l.setId(5L); l.setOrganisationId(ORG); l.setRiskId(RID);
        l.setLinkType("SUPPLIER"); l.setTargetRef("Acme");
        when(links.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(l));
        when(links.save(any())).thenAnswer(i -> i.getArgument(0));

        service.delete(5L);

        assertThat(l.getIsDeleted()).isTrue();
        verify(links).save(l);
    }
}
