package com.tss.grc.module.incident.intake;

import com.tss.grc.module.incident.dto.IncidentRequest;
import com.tss.grc.module.incident.dto.IncidentResponse;
import com.tss.grc.module.incident.service.IncidentService;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-180 — {@link IncidentPromotionAdapter} must never hand {@code cyber.incidents.category} a value
 * the column refuses, and must not silently discard the reporter's own word when it translates one.
 *
 * <p>These are the behaviour legs; {@code PromotionVocabularyTest} is the schema leg, and neither
 * replaces the other. The pin proves the declared vocabulary matches the migration. This proves the
 * adapter only ever produces values from it — including for the four form vocabularies that were
 * measured, one by one, as the reason three of this adapter's four kinds could never promote.
 *
 * <p>⚠ The tests are written from {@code intakeForms.ts} — the vocabularies a submitter is actually
 * offered — rather than from the adapter's mapping table. A test written by reading the implementation
 * can only agree with it, which is how {@code MlModelDriftServiceTest} came to assert a hand-rolled
 * reference-code prefix as correct (F2-175) and how {@code PermissionEvaluator}'s discarded-id overload
 * kept a decoy alive for months (F2-159).
 */
@ExtendWith(MockitoExtension.class)
class IncidentPromotionCategoryTest {

    @Mock IncidentService incidents;
    @Captor ArgumentCaptor<IncidentRequest> captor;

    private IncidentPromotionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new IncidentPromotionAdapter(incidents);
        when(incidents.create(any())).thenReturn(response());
    }

    // ── the four kinds' own form vocabularies ────────────────────────────────────────

    @Test
    @DisplayName("SECURITY_INCIDENT: none of the form's four categories is a legal column value")
    void securityIncidentCategoriesAreAllTranslated() {
        // intakeForms.ts SECURITY_INCIDENT.category — the exact select the reporter sees.
        for (String reported : new String[] {"PHISHING", "MALWARE", "LOST_DEVICE", "UNAUTHORISED_ACCESS"}) {
            IncidentRequest req = promote("SECURITY_INCIDENT", Map.of("category", reported));
            assertThat(req.category()).as(
                "'%s' is what the form offers and CK_incidents_category has never accepted it — before "
                + "F2-180 this promotion died on the constraint and surfaced as a 409 about a foreign key",
                reported)
                .isIn(IncidentPromotionAdapter.PROMOTED_CATEGORIES);
        }
    }

    @Test
    @DisplayName("WORKPLACE_INJURY: injuryType is not a cyber category, and HSE was not one either")
    void workplaceInjuryLandsInOther() {
        for (String injury : new String[] {"MINOR", "MEDICAL", "LOST_TIME", "PERMANENT", "FATALITY",
                                           "OCCUPATIONAL"}) {
            IncidentRequest req = promote("WORKPLACE_INJURY", Map.of("injuryType", injury));
            assertThat(req.category()).as(
                "a GOSI-reportable injury (24-hour notification) is not a category of cyber incident; "
                + "OTHER is what the column has for that, and the old constant 'HSE' was not in the "
                + "vocabulary at all")
                .isEqualTo(IncidentPromotionAdapter.CATEGORY_OTHER);
        }
    }

    @Test
    @DisplayName("CONSUMER_COMPLAINT_RESPONSE: 4 of the 5 complaint categories were illegal")
    void consumerComplaintLandsInOther() {
        for (String complaint : new String[] {"REFUND", "DECEPTIVE", "WARRANTY", "LABELLING", "OTHER"}) {
            IncidentRequest req = promote("CONSUMER_COMPLAINT_RESPONSE",
                Map.of("complaintCategory", complaint));
            assertThat(req.category()).isEqualTo(IncidentPromotionAdapter.CATEGORY_OTHER);
        }
    }

    @Test
    @DisplayName("VRP_DISCLOSURE — the one path that worked — still resolves to CYBER")
    void vulnerabilityDisclosureStaysCyber() {
        IncidentRequest req = promote("VRP_DISCLOSURE", Map.of());
        assertThat(req.category()).as(
            "this kind has no category field, so it fell through to the CYBER constant and was the only "
            + "one of the four that could ever promote. The fix must not move it.")
            .isEqualTo(IncidentPromotionAdapter.CATEGORY_CYBER);
    }

    // ── the mapping's own properties ─────────────────────────────────────────────────

    @Test
    @DisplayName("UNAUTHORISED_ACCESS maps to ACCESS_CONTROL in both spellings")
    void bothSpellingsOfUnauthorisedAccessMap() {
        assertThat(promote("SECURITY_INCIDENT", Map.of("category", "UNAUTHORISED_ACCESS")).category())
            .isEqualTo(IncidentPromotionAdapter.CATEGORY_ACCESS_CONTROL);
        assertThat(promote("SECURITY_INCIDENT", Map.of("category", "UNAUTHORIZED_ACCESS")).category())
            .as("intakeForms.ts spells it with an s and CyberPage.tsx with a z; a mapping that knows "
                + "one of them downgrades the other to the generic fallback without saying so")
            .isEqualTo(IncidentPromotionAdapter.CATEGORY_ACCESS_CONTROL);
    }

    @Test
    @DisplayName("an unrecognised sub-type falls back by domain, not to one constant")
    void unknownSubtypeFallsBackPerKind() {
        assertThat(promote("SECURITY_INCIDENT", Map.of("category", "SUPPLY_CHAIN_TAMPERING")).category())
            .as("a security event whose sub-type is unknown is still a security event")
            .isEqualTo(IncidentPromotionAdapter.CATEGORY_CYBER);
        assertThat(promote("WORKPLACE_INJURY", Map.of("injuryType", "HEAT_STRESS")).category())
            .as("an unknown injury type is still not a cyber category")
            .isEqualTo(IncidentPromotionAdapter.CATEGORY_OTHER);
    }

    @Test
    @DisplayName("lower case and padding do not defeat the mapping")
    void reportedCategoryIsNormalisedBeforeMapping() {
        // F2-179's shape: the database's collation is case-insensitive and Java's equals is not, so a
        // connector sending 'phishing' must not fall through to the generic fallback.
        assertThat(promote("SECURITY_INCIDENT", Map.of("category", "  phishing ")).category())
            .isEqualTo(IncidentPromotionAdapter.CATEGORY_CYBER);
        assertThat(promote("SECURITY_INCIDENT", Map.of("category", "unauthorised_access")).category())
            .isEqualTo(IncidentPromotionAdapter.CATEGORY_ACCESS_CONTROL);
    }

    @Test
    @DisplayName("the reporter's own category survives the translation, on the record")
    void translatedCategoryIsPreservedInTheDescription() {
        IncidentRequest req = promote("SECURITY_INCIDENT",
            Map.of("category", "PHISHING", "description", "Mail claiming to be from payroll."));
        assertThat(req.category()).isEqualTo(IncidentPromotionAdapter.CATEGORY_CYBER);
        assertThat(req.description()).as(
            "mapping PHISHING to CYBER is only acceptable because nothing is lost by it — the V261 "
            + "precedent, whose comment reads 'CK_incidents_category is strict; type kept in description'")
            .contains("PHISHING")
            .contains("Mail claiming to be from payroll.");
    }

    @Test
    @DisplayName("a category that needed no translation is not annotated")
    void untranslatedCategoryAddsNoNoise() {
        IncidentRequest req = promote("SECURITY_INCIDENT",
            Map.of("category", "CYBER", "description", "Body."));
        assertThat(req.description()).as(
            "'Reported category: CYBER' above a record whose category is CYBER is noise, and noise in "
            + "every description is how a useful annotation stops being read")
            .doesNotContain("Reported category")
            .contains("Body.");
    }

    @Test
    @DisplayName("no category answer at all leaves the description untouched")
    void absentCategoryAddsNothing() {
        // ⚙ Asserting the property rather than the exact string, because the exact string is not this
        // adapter's to decide: IntakePromotionContext#description already appends its own
        // "Promoted from intake <ref>" traceability line, and the first draft of this test asserted
        // equality with "Body." and failed on that. What matters here is that no category annotation was
        // invented, which is what the promoted record's reader would be misled by.
        assertThat(promote("SECURITY_INCIDENT", Map.of("description", "Body.")).description())
            .doesNotContain("Reported category")
            .contains("Body.");
    }

    @Test
    @DisplayName("status and severity also stay inside their columns' vocabularies")
    void statusAndSeverityAreConstrained() {
        IncidentRequest req = promote("SECURITY_INCIDENT", "not-a-severity", Map.of());
        assertThat(req.status()).isEqualTo(IncidentPromotionAdapter.PROMOTED_STATUS);
        assertThat(req.severity()).as("severityOrMedium clamps; this is why F2-180 found two broken "
            + "adapters and not four")
            .isIn(IntakePromotionContext.SEVERITY_BANDS);
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────

    private IncidentRequest promote(String kind, Map<String, Object> payload) {
        return promote(kind, "HIGH", payload);
    }

    private IncidentRequest promote(String kind, String severity, Map<String, Object> payload) {
        adapter.promote(ctx(kind, severity, payload));
        verify(incidents, org.mockito.Mockito.atLeastOnce()).create(captor.capture());
        return captor.getValue();
    }

    private static IntakePromotionContext ctx(String kind, String severity, Map<String, Object> payload) {
        return new IntakePromotionContext(
            41L, "SI-A1B2-C3D4", kind, severity, "Rana", "rana@example.sa", false, "en",
            LocalDateTime.of(2026, 8, 11, 9, 30), null, "compliance",
            payload, "Report a security event", "الإبلاغ عن حدث أمني");
    }

    /**
     * F2-281 added eight PDPL-classification components. They are left at the
     * unclassified shape (false + nulls) deliberately: this test is about the
     * promotion adapter's category mapping, and a promoted incident has not been
     * classified by the cyber -> PDPL bridge yet, so false IS the real value
     * rather than a placeholder standing in for one.
     */
    private static IncidentResponse response() {
        return new IncidentResponse(7_001L, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null,
            false, null, null, null, null, null, null, null);
    }
}
