package com.tss.grc.module.intake.service;

import com.tss.grc.module.intake.dto.IntakePublicStatusDto;
import com.tss.grc.module.intake.entity.IntakeRecord;
import com.tss.grc.module.intake.repository.IntakeRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SEC-FIN-001 regression — the public, unauthenticated case-tracker endpoint
 * must return a minimal projection only, never submitter PII or the payload.
 */
@ExtendWith(MockitoExtension.class)
class IntakeServiceTest {

    @Mock IntakeRecordRepository repo;
    @InjectMocks IntakeService service;

    private static IntakeRecord recordWithPii(String code) {
        IntakeRecord r = new IntakeRecord();
        r.setReferenceCode(code);
        r.setKind("SPEAKUP");
        r.setStatus("TRIAGE");
        r.setAcknowledgedAt(LocalDateTime.now());
        r.setSlaDueAt(LocalDateTime.now().plusDays(7));
        r.setSubmitterName("Jane Whistleblower");
        r.setSubmitterEmail("jane@example.com");
        r.setSubmitterPhone("+966500000000");
        r.setPayload("{\"allegation\":\"sensitive\"}");
        r.setAssignedTo("DPO");
        return r;
    }

    @Test void publicTracker_returnsOnlyPiiFreeStatus() {
        when(repo.findByReferenceCode("REF-123")).thenReturn(Optional.of(recordWithPii("REF-123")));

        Optional<IntakePublicStatusDto> out = service.findPublicStatusByReferenceCode("REF-123");

        assertThat(out).isPresent();
        IntakePublicStatusDto dto = out.get();
        assertThat(dto.referenceCode()).isEqualTo("REF-123");
        assertThat(dto.kind()).isEqualTo("SPEAKUP");
        assertThat(dto.status()).isEqualTo("TRIAGE");
        assertThat(dto.acknowledgedAt()).isNotNull();
        assertThat(dto.slaDueAt()).isNotNull();
    }

    @Test void publicTracker_dtoStructurallyExcludesPii() {
        Set<String> fields = Arrays.stream(IntakePublicStatusDto.class.getRecordComponents())
            .map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder(
            "referenceCode", "kind", "status", "acknowledgedAt", "slaDueAt");
        assertThat(fields).doesNotContain(
            "submitterName", "submitterEmail", "submitterPhone", "payload", "assignedTo", "history");
    }

    @Test void publicTracker_unknownCode_empty() {
        when(repo.findByReferenceCode("NOPE")).thenReturn(Optional.empty());
        assertThat(service.findPublicStatusByReferenceCode("NOPE")).isEmpty();
    }
}
