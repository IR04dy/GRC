package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
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
