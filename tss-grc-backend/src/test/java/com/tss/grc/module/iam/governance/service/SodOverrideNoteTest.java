package com.tss.grc.module.iam.governance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tss.grc.module.iam.governance.entity.SodRule;
import com.tss.grc.module.iam.governance.entity.SodViolation;
import com.tss.grc.module.iam.governance.repository.SodRuleRepository;
import com.tss.grc.module.iam.governance.repository.SodViolationRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.Column;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * F2-185(b) — the half {@code RequestSizeCapMirrorTest} structurally cannot see.
 *
 * <p>{@code ApproveAccessRequest.overrideJustification} is capped at 1000 and
 * {@code iam.sod_violation.disposition_note} is {@code NVARCHAR(1000)}. Cap equals column, so the
 * structural mirror was satisfied — and the write still failed, because
 * {@code SodService#recordOverride} prepended {@code "Override justification: "}: 24 characters, an
 * effective budget of 976, and a 1000-character justification the DTO accepts overflowing the column
 * by exactly the length of the prefix. SQL Server answers 8152, Spring answers 500, and because the
 * violation is written in the same transaction as the grant it authorises, the whole SoD override
 * rolls back.
 *
 * <p>🔑 <b>A length check that stops at the boundary misses everything the code adds after it.</b>
 * The prefix was invisible to every scan in this family: it is not an annotation, not a vocabulary,
 * not a column. Only concatenating the two numbers finds it — which is what the arithmetic test below
 * does, deliberately, rather than asserting on the absence of a string literal.
 *
 * <p>Nothing in the suite asserted the old prefix, so this is also the first test this method has
 * ever had.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SodOverrideNoteTest {

    private static final Long ORG = 1L;

    @Mock private SodRuleRepository rules;
    @Mock private SodViolationRepository violations;
    @Mock private AuditLogService audit;

    @InjectMocks private SodService service;

    private static SodRule pairRule() {
        SodRule r = new SodRule();
        r.setId(7L);
        r.setRuleCode("SOD-PAY-01");
        r.setRuleType("PAIR");
        r.setSeverity("HIGH");
        r.setModuleA("PAYMENTS");
        r.setActionA("CREATE");
        r.setModuleB("PAYMENTS");
        r.setActionB("APPROVE");
        return r;
    }

    private SodViolation captureRecorded(String justification) {
        when(violations.findByOrganisationIdAndRuleIdAndUserIdAndIsDeletedFalse(ORG, 7L, 42L))
                .thenReturn(List.of());
        when(violations.save(any(SodViolation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordOverride(ORG, 42L, "ranalyst", List.of(pairRule()), justification, 9L);

        ArgumentCaptor<SodViolation> cap = ArgumentCaptor.forClass(SodViolation.class);
        verify(violations).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void theJustificationIsStoredVerbatimWithNoPrefix() {
        SodViolation v = captureRecorded("Compensating control: dual review by Internal Audit.");

        assertThat(v.getDispositionNote())
                .isEqualTo("Compensating control: dual review by Internal Audit.");

        // The information the prefix used to carry is on the same row twice over — which is why
        // dropping it costs nothing, and why the alternative fix (lowering the DTO cap to 976 and
        // leaking a storage detail into the API contract) was the wrong one.
        assertThat(v.getDetectionSource()).isEqualTo("OVERRIDE");
        assertThat(v.getDetailEn()).endsWith("granted with override");
    }

    @Test
    void aJustificationAtTheDtoCapStillFitsTheColumn() throws Exception {
        int columnLength = SodViolation.class.getDeclaredField("dispositionNote")
                .getAnnotation(Column.class).length();
        // ⚠ Read the FIELD, not the RecordComponent. jakarta.validation.constraints.Size has no
        // RECORD_COMPONENT target, so javac copies it to the backing field and the accessor and
        // RecordComponent#getAnnotation answers null — which reads exactly like "there is no cap".
        int dtoCap = com.tss.grc.module.iam.governance.dto.GovernanceDtos.ApproveAccessRequest.class
                .getDeclaredField("overrideJustification")
                .getAnnotation(jakarta.validation.constraints.Size.class).max();

        SodViolation v = captureRecorded("j".repeat(dtoCap));

        // The whole finding in one assertion: the longest value the boundary ACCEPTS must be a value
        // the column can HOLD, after everything the service does to it.
        assertThat(v.getDispositionNote()).hasSize(dtoCap);
        assertThat(v.getDispositionNote().length())
                .as("a justification of exactly the DTO cap (%d) must fit disposition_note (%d) — "
                    + "if the service prepends or appends anything, this is where it shows up",
                    dtoCap, columnLength)
                .isLessThanOrEqualTo(columnLength);
    }

    @Test
    void anAlreadyOpenViolationIsNotRecordedTwice() {
        // Negative control for the captor above: without this, a captureRecorded() that silently
        // stopped saving anything would fail as "no interactions" rather than as a wrong note, and
        // the two tests above would be asserting on an unrelated failure.
        SodViolation open = new SodViolation();
        open.setStatus("OPEN");
        when(violations.findByOrganisationIdAndRuleIdAndUserIdAndIsDeletedFalse(ORG, 7L, 42L))
                .thenReturn(List.of(open));

        service.recordOverride(ORG, 42L, "ranalyst", List.of(pairRule()), "anything", 9L);

        verify(violations, org.mockito.Mockito.never()).save(any(SodViolation.class));
        verify(audit).recordEvent(any());
    }
}
