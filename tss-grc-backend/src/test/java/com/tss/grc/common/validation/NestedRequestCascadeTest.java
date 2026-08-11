package com.tss.grc.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.tss.grc.module.admin.committee.dto.CommitteeDtos;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos;
import com.tss.grc.module.platform.export.TabularExportRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * F2-184 — behaviour, not structure. {@link InertValidationConstraintTest} proves that nothing
 * declares a constraint no endpoint validates; this proves the five cascades it repaired actually
 * <b>reject</b>, through a real Hibernate Validator rather than by reading the annotation.
 *
 * <p>The distinction matters here more than usual, because the whole finding is that an annotation's
 * <i>presence</i> proves nothing about its <i>effect</i>. A test that asserted "the field carries
 * {@code @Valid}" would repeat the mistake it is checking for.
 */
class NestedRequestCascadeTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) factory.close();
    }

    private static Set<String> paths(Object bean) {
        Set<String> out = new TreeSet<>();
        for (ConstraintViolation<Object> v : validator.validate(bean)) {
            out.add(v.getPropertyPath().toString());
        }
        return out;
    }

    // ---------------------------------------------------------------- sanctions bulk import

    private static SanctionsDtos.EntryCreateRequest entry(String externalId, String type,
                                                          String nameEn, String severity) {
        return new SanctionsDtos.EntryCreateRequest(
                externalId, type, nameEn, "الاسم", null, null, "SA", null,
                LocalDate.of(2026, 1, 1), "OFAC", "reason", "سبب", severity);
    }

    @Test
    void aBulkSanctionsImportNowRejectsAnEntryWithNoName() {
        var req = new SanctionsDtos.EntryBulkImportRequest(1L, List.of(
                entry("OFAC-1", "INDIVIDUAL", "Real Name", "REVIEW_REQUIRED"),
                entry("OFAC-2", "INDIVIDUAL", "   ", "REVIEW_REQUIRED")));

        // The whole point: a sanctions entry with a blank name passes NOT NULL, is stored, and never
        // matches a screening query — a sanctioned party that is not flagged. Before the cascade
        // was restored this produced zero violations.
        assertThat(paths(req)).contains("entries[1].primaryNameEn");
    }

    @Test
    void aBulkSanctionsImportNowRejectsAnEntityTypeOutsideItsVocabulary() {
        // aml.sanctions_list_entry has ZERO check constraints, so this @Pattern was the only thing
        // narrowing entity_type — and it never ran.
        var req = new SanctionsDtos.EntryBulkImportRequest(1L, List.of(
                entry("OFAC-3", "individual", "Real Name", "REVIEW_REQUIRED")));
        assertThat(paths(req)).contains("entries[0].entityType");
    }

    @Test
    void aBulkSanctionsImportNowRejectsABlankExternalId() {
        // This one also closes a second defect for free. SanctionsListService's duplicate check is
        // existsByListIdAndEntryExternalId(...), so a NULL id compiles to `= NULL`, which is never
        // true in SQL: every null-id entry was CREATED rather than skipped, on every re-import.
        // Refusing it at the boundary makes that query unreachable.
        var req = new SanctionsDtos.EntryBulkImportRequest(1L, List.of(
                entry(null, "INDIVIDUAL", "Real Name", "REVIEW_REQUIRED")));
        assertThat(paths(req)).contains("entries[0].entryExternalId");
    }

    @Test
    void aValidBulkSanctionsImportIsStillAccepted() {
        // Negative control. Without this, every assertion above is satisfied by a validator that
        // refuses everything — and the fix would look correct while breaking the endpoint.
        var req = new SanctionsDtos.EntryBulkImportRequest(1L, List.of(
                entry("OFAC-1", "INDIVIDUAL", "Real Name", "STRONG_MATCH_REQUIRED"),
                entry("OFAC-2", "VESSEL", "Some Vessel", "REVIEW_REQUIRED")));
        assertThat(paths(req)).isEmpty();
    }

    // ---------------------------------------------------------------- bcm quantitative loss lines

    private static BiaQuantitativeDtos.BiaQuantitativeLossLineDto line(
            String timeBand, String lossType, BigDecimal amount, String confidence) {
        return new BiaQuantitativeDtos.BiaQuantitativeLossLineDto(
                null, timeBand, lossType, amount, "basis", "أساس", confidence, Boolean.FALSE);
    }

    private static BiaQuantitativeDtos.BiaQuantitativeRequest bia(
            List<BiaQuantitativeDtos.BiaQuantitativeLossLineDto> lines) {
        return new BiaQuantitativeDtos.BiaQuantitativeRequest(
                7L, "BIA-Q-1", LocalDate.of(2026, 3, 1), 2026, "SAR", "notes", "ملاحظات",
                new BigDecimal("1000"), "Preparer", null, lines);
    }

    @Test
    void aLossLineOutsideTheTimeBandVocabularyIsNowRejected() {
        // bcm.bia_quantitative_loss_line has zero CHECKs, so this @Pattern was the only guard.
        assertThat(paths(bia(List.of(
                line("24h", "REVENUE_LOSS", new BigDecimal("5"), "HIGH"),
                line("3d", "REVENUE_LOSS", new BigDecimal("5"), "HIGH")))))
                .contains("lossLines[1].timeBand");
    }

    @Test
    void aNegativeLossAmountIsNowRejected() {
        assertThat(paths(bia(List.of(line("72h", "LEGAL_COSTS", new BigDecimal("-1"), "LOW")))))
                .contains("lossLines[0].amountSar");
    }

    @Test
    void aValidQuantitativeBiaIsStillAccepted() {
        assertThat(paths(bia(List.of(
                line("1w", "REGULATORY_FINE", new BigDecimal("152000000"), "MEDIUM"),
                line("1m", "CUSTOMER_CHURN", BigDecimal.ZERO, null))))).isEmpty();
    }

    // ---------------------------------------------------------------- the RBAC grant matrix

    @Test
    void aNullModuleInTheGrantMatrixIsNowA400NotAnNpe() {
        // ⚠ State this one accurately: putMatrix already REJECTED an unknown pair with a 400, so
        // this was never an authorisation bypass. What the missing cascade cost is the diagnostic —
        // g.module().toUpperCase() on a null answered 500. The service guard is intact either way.
        var req = new GovernanceDtos.MatrixUpdateRequest(List.of(
                new GovernanceDtos.GrantPair("RISK", "READ"),
                new GovernanceDtos.GrantPair(null, "READ")));
        assertThat(paths(req)).contains("grants[1].module");
    }

    @Test
    void aValidGrantMatrixIsStillAccepted() {
        assertThat(paths(new GovernanceDtos.MatrixUpdateRequest(List.of(
                new GovernanceDtos.GrantPair("RISK", "READ"),
                new GovernanceDtos.GrantPair("RISK", "WRITE"))))).isEmpty();
    }

    // ---------------------------------------------------------------- committee attendance

    private static CommitteeDtos.AttendanceRequest attendance(String guestName, String coiNote) {
        return new CommitteeDtos.AttendanceRequest(List.of(new CommitteeDtos.AttendanceMark(
                null, guestName, "External auditor", Boolean.TRUE, Boolean.TRUE, coiNote)));
    }

    @Test
    void anOverlongGuestNameIsNowRejectedRatherThanTruncatedByTheDatabase() {
        // guestName's @Size(255) equals admin.committee_meeting_attendee.member_name exactly, so
        // enforcing it converts a truncation 500 into a 400 naming the field. It cannot break a
        // request that works today, which is what made this one safe to switch on.
        assertThat(paths(attendance("g".repeat(256), "note"))).contains("marks[0].guestName");
    }

    @Test
    void butAnUnboundedConflictOfInterestNoteIsStillAccepted() {
        // The deliberate half of the same edit, and the reason it needs its own test: coi_note is
        // NVARCHAR(MAX), so the @Size(1000) that had never run was DELETED rather than enabled.
        // Switching it on would have imposed a limit nobody chose on a COI declaration. If someone
        // "restores consistency" by adding the cap back, this fails.
        assertThat(paths(attendance("Guest", "x".repeat(5_000)))).isEmpty();
    }

    // ---------------------------------------------------------------- the export DoS cap

    private static TabularExportRequest export(int rows) {
        List<List<String>> data = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) data.add(List.of("v"));
        return new TabularExportRequest("ESG", "SustainabilityAnnex", "ar",
                List.of(new TabularExportRequest.Sheet("Sheet1", List.of("h"), data)));
    }

    @Test
    void theFiftyThousandRowExportCapIsNowEnforced() {
        // Not cosmetic: the 20-sheet cap ran, the per-sheet row cap did not, so a caller could post
        // 20 sheets of unbounded rows into an in-memory POI workbook.
        assertThat(paths(export(50_001))).contains("sheets[0].rows");
    }

    @Test
    void anExportInsideTheCapIsStillAccepted() {
        assertThat(paths(export(10))).isEmpty();
    }

    @Test
    void aSheetWithNoNameIsNowRejected() {
        var req = new TabularExportRequest("ESG", "Annex", "en",
                List.of(new TabularExportRequest.Sheet("  ", List.of("h"), List.of(List.of("v")))));
        assertThat(paths(req)).contains("sheets[0].name");
    }

    // ---------------------------------------------------------------- anti-vacuity

    @Test
    void theValidatorItselfEnforcesTopLevelConstraints() {
        // If the Validator were misconfigured, every `isEmpty()` above would pass vacuously and the
        // cascade assertions would be the only thing holding — so prove it rejects a constraint that
        // was ALREADY working before this slice, on the outer type rather than a nested one.
        var req = new TabularExportRequest("", "Annex", "en",
                List.of(new TabularExportRequest.Sheet("S", List.of("h"), List.of(List.of("v")))));
        assertThat(paths(req)).contains("module");
    }
}
