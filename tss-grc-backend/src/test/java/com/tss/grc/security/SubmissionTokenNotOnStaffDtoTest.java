package com.tss.grc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.ComplaintResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintResponse;
import com.tss.grc.module.consumer.complaints.entity.Complaint;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionResponse;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCase;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-383 — the anonymous submitter's {@code submissionToken} is a BEARER CREDENTIAL, and it was
 * leaking into the STAFF-facing response DTOs of all three public-intake modules while each module's
 * public/submit shape correctly withheld it (F2-215 two-of-N; F2-282: two readers of one concept,
 * the drifted one being the credential-bearing staff shape).
 *
 * <ul>
 *   <li><b>Whistleblower</b> ({@code CaseResponse}, returned to {@code ETHICS:READ_CASES}/{@code TRIAGE}):
 *       the token authenticates the {@code permitAll POST /public/reply/{token}}
 *       ({@code WhistleblowerCaseService#reportByToken}), so a case reader could post replies AS the
 *       anonymous reporter and track them — a deanonymisation + integrity breach on the hotline.</li>
 *   <li><b>Vendor BCP</b> ({@code AttestationResponse}, returned to broad {@code TPRM:READ}): the token
 *       authenticates the {@code permitAll POST /by-token/{token}/submit}, so a reader could harvest
 *       tokens and forge a vendor's BCP attestation.</li>
 *   <li><b>Complaint</b> ({@code ComplaintResponse}, {@code CONSUMER:READ}): a lower-severity
 *       credential-scope leak — the complaint token's only public path is the read-only, PII-stripped
 *       {@code /public/track/{token}} — but a bearer credential must not sit in a wider-audience
 *       response regardless (F2-182: reachability is a fact about today, not a property of the control).</li>
 * </ul>
 *
 * <p>Hermetic (reflection over the record shapes + a plain-Jackson serialisation of the real mappers;
 * no Spring context, F2-320/F2-325). A record only ever serialises its declared components, so "no
 * {@code submissionToken} component" is a complete proof the key cannot reach the wire; the
 * serialisation legs are belt-and-braces against a future non-record refactor. The token stays on the
 * one-time submit responses (returned to the submitter, by design), which the positive control pins.
 */
class SubmissionTokenNotOnStaffDtoTest {

    private static Set<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    // ── The staff-facing response shapes must NOT carry the submitter's bearer token ──

    @Test
    void whistleblowerCaseResponseHasNoSubmissionToken() {
        assertThat(components(CaseResponse.class)).doesNotContain("submissionToken");
    }

    @Test
    void complaintResponseHasNoSubmissionToken() {
        assertThat(components(ComplaintResponse.class)).doesNotContain("submissionToken");
    }

    @Test
    void vendorBcpAttestationResponseHasNoSubmissionToken() {
        assertThat(components(AttestationResponse.class)).doesNotContain("submissionToken");
    }

    // ── Positive control: the ONE-TIME submit responses keep it, by design ──
    // (proves the check can detect presence and that the fix did not remove the wrong shape).

    @Test
    void submitResponsesStillCarryTheTokenByDesign() {
        assertThat(components(CaseSubmissionResponse.class)).contains("submissionToken");
        assertThat(components(SubmitComplaintResponse.class)).contains("submissionToken");
    }

    // ── Serialisation: the token VALUE cannot reach a staff response body ──
    // These compile against both the fixed and pre-fix trees (they reference only the entity accessor
    // and the mapper), so the pre-fix control flips them RED without a compile change.

    @Test
    void whistleblowerCaseResponseDoesNotSerialiseTheToken() throws Exception {
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        WhistleblowerCase c = new WhistleblowerCase();         // token auto-set = UUID.randomUUID()
        String token = c.getSubmissionToken().toString();
        String json = om.writeValueAsString(CaseResponse.from(c));
        assertThat(json).doesNotContain("submissionToken").doesNotContain(token);
    }

    @Test
    void complaintResponseDoesNotSerialiseTheToken() throws Exception {
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        Complaint c = new Complaint();
        String token = c.getSubmissionToken().toString();
        String json = om.writeValueAsString(ComplaintResponse.from(c));
        assertThat(json).doesNotContain("submissionToken").doesNotContain(token);
    }
}
