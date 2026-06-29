package com.tss.grc.module.compliance.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C-12 / review item 20 — {@link AuditFindingRecordCreator}.
 *
 * <p>Verifies the F8 CREATE_RECORD handler opens a control-gap finding on
 * EVIDENCE_REJECTED, de-duplicates against an already-open gap (so it never
 * doubles up with rule AR-17 which reopens existing gaps), and substitutes the
 * event payload into the title template.
 */
@ExtendWith(MockitoExtension.class)
class AuditFindingRecordCreatorTest {

    private static final Long ORG = 7L;
    private static final ObjectMapper OM = new ObjectMapper();

    @Mock private AuditFindingRepository findings;
    @InjectMocks private AuditFindingRecordCreator creator;

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static DomainEvent rejectEvent() {
        return new DomainEvent(ORG, "EVIDENCE_REJECTED", "EvidenceItem", 55L,
            "evidence:55", Map.of("subControlId", "12"));
    }
    private static JsonNode action(String json) {
        try { return OM.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test void entityType_isAuditFinding() {
        assertThat(creator.entityType()).isEqualTo("AuditFinding");
    }

    @Test void create_opensFinding_whenNoneExists() {
        when(findings.findByOrganisationIdAndClauseRef(ORG, "evidence:55")).thenReturn(List.of());
        when(findings.save(any(AuditFinding.class))).thenAnswer(i -> {
            AuditFinding f = i.getArgument(0); f.setId(900L); return f;
        });

        String result = creator.create(action(
            "{\"type\":\"CREATE_RECORD\",\"entity\":\"AuditFinding\",\"fields\":{"
            + "\"severity\":\"HIGH\",\"title_template\":\"Evidence rejected — gap (%subControlId%)\",\"due_in_days\":30}}"),
            rejectEvent());

        ArgumentCaptor<AuditFinding> cap = ArgumentCaptor.forClass(AuditFinding.class);
        verify(findings).save(cap.capture());
        AuditFinding f = cap.getValue();
        assertThat(f.getOrganisationId()).isEqualTo(ORG);
        assertThat(f.getSeverity()).isEqualTo("HIGH");
        assertThat(f.getStatus()).isEqualTo("OPEN");
        assertThat(f.getClauseRef()).isEqualTo("evidence:55");   // source marker for dedup
        assertThat(f.getTitle()).contains("12");                 // %subControlId% substituted
        assertThat(f.getDueDate()).isNotNull();
        assertThat(result).contains("Created AuditFinding#900");
    }

    @Test void create_skips_whenOpenFindingAlreadyExists() {
        AuditFinding open = new AuditFinding();
        open.setId(1L); open.setStatus("OPEN"); open.setClauseRef("evidence:55");
        when(findings.findByOrganisationIdAndClauseRef(ORG, "evidence:55")).thenReturn(List.of(open));

        String result = creator.create(action(
            "{\"type\":\"CREATE_RECORD\",\"entity\":\"AuditFinding\",\"fields\":{}}"), rejectEvent());

        verify(findings, never()).save(any());
        assertThat(result).contains("already open");
    }
}
