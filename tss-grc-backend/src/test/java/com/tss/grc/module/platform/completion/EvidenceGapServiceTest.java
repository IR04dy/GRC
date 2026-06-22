package com.tss.grc.module.platform.completion;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.entity.EvidenceConnector;
import com.tss.grc.module.evidence.repository.EvidenceConnectorRepository;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for {@link EvidenceGapService} (§5.9 Evidence slice, extracted from
 * the former BuyerGapService — QUAL-BCMP-003). Locks connector-registry tenant
 * scope + audit logging.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenceGapServiceTest {

    private static final Long ORG_ID = 41L;

    @Mock private EvidenceItemRepository      evidence;
    @Mock private EvidenceConnectorRepository connectors;
    @Mock private AuditLogService             audit;
    @Mock private ApplicationEventPublisher   eventPublisher;
    @Mock private EntityManager               em;

    @InjectMocks private EvidenceGapService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test void updateConnectorStatus_404OnUnknownCode() {
        when(connectors.findByOrganisationIdAndCode(ORG_ID, "GHOST"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateConnectorStatus(
                "GHOST", new EvidenceGapService.ConnectorPatch("ENABLED", null)))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void updateConnectorStatus_appliesStatus() {
        EvidenceConnector c = new EvidenceConnector();
        c.setId(50L); c.setOrganisationId(ORG_ID); c.setCode("AD");
        c.setStatus("AVAILABLE");
        when(connectors.findByOrganisationIdAndCode(ORG_ID, "AD")).thenReturn(Optional.of(c));
        when(connectors.save(any(EvidenceConnector.class))).thenAnswer(inv -> inv.getArgument(0));

        EvidenceConnector saved = service.updateConnectorStatus("AD",
            new EvidenceGapService.ConnectorPatch("ENABLED", null));

        assertThat(saved.getStatus()).isEqualTo("ENABLED");
        verify(audit).log(eq("UPDATE"), eq("EvidenceConnector"), anyLong(), any());
    }
}
