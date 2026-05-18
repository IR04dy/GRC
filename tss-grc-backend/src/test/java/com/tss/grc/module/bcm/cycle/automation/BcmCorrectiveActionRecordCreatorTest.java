package com.tss.grc.module.bcm.cycle.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmCorrectiveAction;
import com.tss.grc.module.bcm.cycle.service.BcmRemediationService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lock the F8 CAPA creator behaviour:
 *
 * <ul>
 *   <li>entityType() = "BcmCorrectiveAction"</li>
 *   <li>default title built from event when no template supplied</li>
 *   <li>title template substitutes %payload-key% placeholders</li>
 *   <li>due_in_days resolves to LocalDate now+N</li>
 *   <li>priority propagates from action.fields</li>
 *   <li>tenant context is set from event.organisationId for the call</li>
 *   <li>tenant context is restored to previous value after the call</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
// LENIENT: the @BeforeEach stub for createCapa is shared across most tests
// (default-title, template substitution, due-date, tenant-restore) but
// entityType_isCorrect doesn't call createCapa at all, and
// create_setsTenantFromEvent overrides the stub with its own thenAnswer to
// assert TenantContext is set mid-call. Strict mode flags both as
// UnnecessaryStubbing. LENIENT is the right knob here per the audit's
// preference for shared fixtures over per-test stub duplication.
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmCorrectiveActionRecordCreatorTest {

    private static final Long ORG_ID = 77L;
    private static final ObjectMapper M = new ObjectMapper();

    @Mock private BcmRemediationService remediation;
    @InjectMocks private BcmCorrectiveActionRecordCreator creator;

    @BeforeEach void setUp() {
        when(remediation.createCapa(any(BcmCorrectiveAction.class))).thenAnswer(inv -> {
            BcmCorrectiveAction in = inv.getArgument(0);
            in.setId(99L);
            return in;
        });
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test void entityType_isCorrect() {
        assertThat(creator.entityType()).isEqualTo("BcmCorrectiveAction");
    }

    @Test void create_defaultTitleFromEvent() throws Exception {
        JsonNode action = M.readTree("{\"type\":\"CREATE_RECORD\",\"entity\":\"BcmCorrectiveAction\"}");
        DomainEvent event = new DomainEvent(ORG_ID, "BCM_DR_TEST_FAILED", "DisasterRecoveryPlan", 17L, "corr-1", Map.of());

        String summary = creator.create(action, event);

        ArgumentCaptor<BcmCorrectiveAction> cap = ArgumentCaptor.forClass(BcmCorrectiveAction.class);
        verify(remediation).createCapa(cap.capture());
        assertThat(cap.getValue().getTitleEn()).contains("BCM_DR_TEST_FAILED").contains("DisasterRecoveryPlan#17");
        assertThat(summary).contains("BcmCorrectiveAction#99");
    }

    @Test void create_titleTemplateSubstitutesPayload() throws Exception {
        JsonNode action = M.readTree("""
            {"type":"CREATE_RECORD","entity":"BcmCorrectiveAction",
             "fields":{"title_template":"DR test failed: %plan_code%","priority":"HIGH"}}
            """);
        DomainEvent event = new DomainEvent(ORG_ID, "BCM_DR_TEST_FAILED", "DisasterRecoveryPlan", 17L,
            "corr-1", Map.of("plan_code", "DRP-2026-001"));

        creator.create(action, event);

        ArgumentCaptor<BcmCorrectiveAction> cap = ArgumentCaptor.forClass(BcmCorrectiveAction.class);
        verify(remediation).createCapa(cap.capture());
        assertThat(cap.getValue().getTitleEn()).isEqualTo("DR test failed: DRP-2026-001");
        assertThat(cap.getValue().getPriority()).isEqualTo("HIGH");
    }

    @Test void create_dueInDays_resolvesToFutureDate() throws Exception {
        JsonNode action = M.readTree("""
            {"type":"CREATE_RECORD","entity":"BcmCorrectiveAction",
             "fields":{"due_in_days":14}}
            """);
        DomainEvent event = new DomainEvent(ORG_ID, "BCM_DR_TEST_FAILED", "DisasterRecoveryPlan", 17L, "corr-1", Map.of());

        creator.create(action, event);

        ArgumentCaptor<BcmCorrectiveAction> cap = ArgumentCaptor.forClass(BcmCorrectiveAction.class);
        verify(remediation).createCapa(cap.capture());
        assertThat(cap.getValue().getDueDate()).isEqualTo(LocalDate.now().plusDays(14));
    }

    @Test void create_setsTenantFromEvent() throws Exception {
        TenantContext.clear();    // no ambient tenant
        JsonNode action = M.readTree("{\"type\":\"CREATE_RECORD\",\"entity\":\"BcmCorrectiveAction\"}");
        DomainEvent event = new DomainEvent(ORG_ID, "X", "Y", 1L, "c-1", Map.of());

        when(remediation.createCapa(any(BcmCorrectiveAction.class))).thenAnswer(inv -> {
            // At this moment the TenantContext should be ORG_ID
            assertThat(TenantContext.getCurrentTenant()).isEqualTo(ORG_ID);
            BcmCorrectiveAction in = inv.getArgument(0);
            in.setId(42L);
            return in;
        });

        creator.create(action, event);
        // After return, tenant is cleared because no previous value existed
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test void create_restoresPreviousTenantAfterCall() throws Exception {
        TenantContext.setCurrentTenant(999L);   // ambient tenant
        JsonNode action = M.readTree("{\"type\":\"CREATE_RECORD\",\"entity\":\"BcmCorrectiveAction\"}");
        DomainEvent event = new DomainEvent(ORG_ID, "X", "Y", 1L, "c-1", Map.of());

        creator.create(action, event);
        // Should restore the ambient tenant, not leak ORG_ID
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(999L);
    }
}
