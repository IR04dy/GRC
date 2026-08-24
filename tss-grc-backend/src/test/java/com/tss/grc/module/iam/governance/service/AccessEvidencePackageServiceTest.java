package com.tss.grc.module.iam.governance.service;

import com.tss.grc.module.iam.governance.entity.AccessReviewCampaign;
import com.tss.grc.module.iam.governance.entity.AccessReviewItem;
import com.tss.grc.module.iam.governance.entity.SodViolation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-342 — the access-governance evidence package (a branded ZIP handed to an NCA ECC 2-2 /
 * SAMA CSF 3.3.5 inspector) dropped the DECIDER from two governance sheets: SoD_Violations
 * emitted the disposition note + accepted-until but not WHO accepted the risk or WHEN, and
 * Recertification_Evidence emitted the decision + WHEN (decidedAt) but not WHO decided. For a
 * four-eyes / maker-checker access review the decider identity is the load-bearing evidence —
 * F2-325's shape (the file that leaves the platform drops the actor), here on the IAM package
 * F2-325/F2-326 did not touch.
 *
 * <p>The decider is co-stamped in the write paths ({@code SodService} sets dispositionBy +
 * dispositionAt + acceptedUntil together; {@code RecertificationService} sets decidedBy +
 * decidedAt together), so the WHO is present on exactly the rows the sheets already surfaced
 * — a real column, not a blank (F2-198). This pins the extracted row-builders: the resolved
 * decider sits at its header index, and an undisposed/undecided row (null id) renders "".
 *
 * <p>Hermetic — pure row-building logic over a stub resolver; no DB / PDF / ZIP / brand
 * (F2-320 / F2-325 calibration). The production resolver is {@code TenantUserDirectory}
 * (F2-249), blank-safe via {@code resolve(id).map(labelFor).orElse("")}; the stub below models
 * its three outcomes: null id → "", unresolvable id (0) → "", resolved id → a label.
 */
class AccessEvidencePackageServiceTest {

    /** Mirrors the production blank-safe resolver's outcomes. */
    private static final Function<Long, String> DECIDER =
        id -> (id == null || id == 0L) ? "" : "user#" + id;

    private static int col(String[] headers, String name) {
        int i = Arrays.asList(headers).indexOf(name);
        assertThat(i).as("header '%s' present", name).isGreaterThanOrEqualTo(0);
        return i;
    }

    @Test
    void sodRow_carriesTheDispositionActorAndTimestampBesideTheNote() {
        SodViolation v = new SodViolation();
        v.setRuleCode("SOD-1"); v.setSeverity("HIGH"); v.setUsername("alice");
        v.setStatus("ACCEPTED_RISK"); v.setDispositionNote("compensating control X");
        v.setDispositionBy(7L);
        v.setDispositionAt(LocalDateTime.of(2026, 8, 24, 10, 0, 0));
        v.setAcceptedUntil(LocalDate.of(2027, 8, 24));

        String[] row = AccessEvidencePackageService.sodRow(v, DECIDER);
        assertThat(row).hasSameSizeAs(AccessEvidencePackageService.SOD_HEADERS);
        assertThat(row[col(AccessEvidencePackageService.SOD_HEADERS, "Dispositioned By")]).isEqualTo("user#7");
        assertThat(row[col(AccessEvidencePackageService.SOD_HEADERS, "Dispositioned (UTC)")]).isEqualTo("2026-08-24 10:00:00");
        // the pre-existing columns are unchanged
        assertThat(row[col(AccessEvidencePackageService.SOD_HEADERS, "Disposition Note")]).isEqualTo("compensating control X");
    }

    @Test
    void sodRow_blankDeciderAndTimestampWhenUndispositioned() {
        SodViolation v = new SodViolation();
        v.setRuleCode("SOD-2"); v.setStatus("OPEN");   // never dispositioned
        String[] row = AccessEvidencePackageService.sodRow(v, DECIDER);
        assertThat(row[col(AccessEvidencePackageService.SOD_HEADERS, "Dispositioned By")]).isEmpty();
        assertThat(row[col(AccessEvidencePackageService.SOD_HEADERS, "Dispositioned (UTC)")]).isEmpty();
    }

    @Test
    void recertRow_carriesTheDeciderBesideTheDecisionTimestamp() {
        AccessReviewCampaign c = new AccessReviewCampaign();
        c.setTitleEn("Q3 privileged review"); c.setScope("PRIVILEGED"); c.setStatus("CLOSED");
        c.setWindowStart(LocalDate.of(2026, 7, 1)); c.setWindowEnd(LocalDate.of(2026, 7, 31));
        AccessReviewItem i = new AccessReviewItem();
        i.setUsername("bob"); i.setRoleName("CISO"); i.setDecision("REVOKE");
        i.setDecidedBy(9L); i.setDecidedAt(LocalDateTime.of(2026, 7, 15, 9, 30, 0));

        String[] row = AccessEvidencePackageService.recertRow(c, i, DECIDER);
        assertThat(row).hasSameSizeAs(AccessEvidencePackageService.RECERT_HEADERS);
        assertThat(row[col(AccessEvidencePackageService.RECERT_HEADERS, "Decided By")]).isEqualTo("user#9");
        assertThat(row[col(AccessEvidencePackageService.RECERT_HEADERS, "Decided (UTC)")]).isEqualTo("2026-07-15 09:30:00");
        assertThat(row[col(AccessEvidencePackageService.RECERT_HEADERS, "Decision")]).isEqualTo("REVOKE");
    }

    @Test
    void recertRow_blankDeciderWhenUndecided() {
        AccessReviewCampaign c = new AccessReviewCampaign();
        c.setTitleEn("Q3"); c.setStatus("OPEN");
        c.setWindowStart(LocalDate.of(2026, 7, 1)); c.setWindowEnd(LocalDate.of(2026, 7, 31));
        AccessReviewItem i = new AccessReviewItem();
        i.setUsername("carol"); i.setRoleName("VIEWER"); i.setDecision("PENDING");   // no decidedBy/decidedAt
        String[] row = AccessEvidencePackageService.recertRow(c, i, DECIDER);
        assertThat(row[col(AccessEvidencePackageService.RECERT_HEADERS, "Decided By")]).isEmpty();
        assertThat(row[col(AccessEvidencePackageService.RECERT_HEADERS, "Decided (UTC)")]).isEmpty();
    }

    @Test
    void everyDecisionTimestampColumnHasADeciderColumn() {
        // A WHEN with no WHO is the defect this slice closes — pin the pairing so a later edit
        // cannot drop the decider again while keeping the timestamp.
        col(AccessEvidencePackageService.SOD_HEADERS, "Dispositioned (UTC)");
        col(AccessEvidencePackageService.SOD_HEADERS, "Dispositioned By");
        col(AccessEvidencePackageService.RECERT_HEADERS, "Decided (UTC)");
        col(AccessEvidencePackageService.RECERT_HEADERS, "Decided By");
    }
}
