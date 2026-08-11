package com.tss.grc.module.intake.spi;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything a {@link RegisterPromotionPort} is given about the submission it is turning into a
 * register record — F2-172.
 *
 * <p>Deliberately <b>not</b> the {@code IntakeRecord} entity. Fourteen adapters in fourteen
 * modules would otherwise hold a managed JPA entity from a Base package, able to mutate the
 * intake row as a side effect of creating something else; and the entity would have to become
 * part of the published contract of a package whose whole purpose is to keep the coupling
 * one-directional.
 *
 * @param intakeId        the intake row's id — adapters record it so the promoted record can be
 *                        traced back to the submission that produced it
 * @param referenceCode   the human-facing tracking code ({@code "DI-K7L9-Z4M2"}), which is what
 *                        the submitter has and therefore the useful thing to put in a title
 * @param kind            the {@code IntakeKind}
 * @param severity        triager- or submitter-asserted severity ({@code LOW…CRITICAL})
 * @param submitterName   null when the submission was anonymous
 * @param submitterEmail  null when the submission was anonymous
 * @param anonymous       whistleblower-pattern submissions must not gain an attributed owner
 * @param locale          reply locale at submission time ({@code en} / {@code ar})
 * @param acknowledgedAt  when the submission was <b>received</b>. Statutory clocks start here,
 *                        not at promotion — see {@link #statutoryDeadline(int)}
 * @param triagerNote     the note the triager typed on the promote decision, if any
 * @param actor           the authenticated triager's username, for owner/author fields
 * @param payload         the parsed form payload; never null, possibly empty
 * @param catalogTitleEn  the catalogue's English name for this kind
 * @param catalogTitleAr  the catalogue's Arabic name for this kind — the platform's bilingual
 *                        rule makes {@code *_ar} backend-mandatory on user-visible records, and
 *                        an intake payload almost never carries Arabic. Composing the Arabic
 *                        title from this plus the reference code produces a title that is
 *                        genuinely Arabic and genuinely descriptive; copying the English text
 *                        into an Arabic column would satisfy the constraint and lie to the reader
 */
public record IntakePromotionContext(
    Long intakeId,
    String referenceCode,
    String kind,
    String severity,
    String submitterName,
    String submitterEmail,
    boolean anonymous,
    String locale,
    LocalDateTime acknowledgedAt,
    String triagerNote,
    String actor,
    Map<String, Object> payload,
    String catalogTitleEn,
    String catalogTitleAr
) {

    /** A payload string field, or empty when absent, non-string or blank. */
    public Optional<String> text(String key) {
        Object v = payload.get(key);
        if (!(v instanceof String s) || s.isBlank()) return Optional.empty();
        return Optional.of(s.trim());
    }

    /** The first of {@code keys} present in the payload — form schemas disagree on field names. */
    public Optional<String> firstText(String... keys) {
        for (String k : keys) {
            Optional<String> v = text(k);
            if (v.isPresent()) return v;
        }
        return Optional.empty();
    }

    /**
     * An English title for the promoted record: the best free-text the payload offers, trimmed to
     * {@code max}, falling back to the catalogue name plus the reference code.
     *
     * <p>Never returns blank. Every target register has a {@code NOT NULL} title, so a blank here
     * is a 500 at flush time in whichever module happens to be promoting.
     */
    public String titleEn(int max, String... preferredKeys) {
        String raw = firstText(preferredKeys).orElse("");
        String candidate = raw.isBlank() ? catalogTitleEn + " — " + referenceCode : raw;
        return candidate.length() <= max ? candidate : candidate.substring(0, max);
    }

    /**
     * The Arabic title. See {@code catalogTitleAr} above for why this is composed rather than
     * copied from the English side.
     */
    public String titleAr(int max) {
        String candidate = catalogTitleAr + " — " + referenceCode;
        return candidate.length() <= max ? candidate : candidate.substring(0, max);
    }

    /** A description that always says where the record came from, even for an empty payload. */
    public String description() {
        String body = firstText("summary", "description", "scope", "observation", "exceptionScope")
            .orElse("");
        String provenance = "Promoted from intake " + referenceCode
            + (triagerNote == null || triagerNote.isBlank() ? "" : " — " + triagerNote);
        return body.isBlank() ? provenance : body + "\n\n" + provenance;
    }

    /** Who to hang the record on. Anonymous submissions attribute to the triager, not to nobody. */
    public String ownerName() {
        if (anonymous) return actor;
        if (submitterName != null && !submitterName.isBlank()) return submitterName;
        if (submitterEmail != null && !submitterEmail.isBlank()) return submitterEmail;
        return actor;
    }

    /**
     * A statutory deadline counted from <b>receipt</b>, which for PDPL Article 10 (30 days for a
     * data-subject request) is the only defensible start. Counting from the promotion instead
     * would silently extend every regulatory clock by however long the submission sat in the
     * triage queue — the mirror image of {@code IncidentAdapter}'s reasoning, where discovery is
     * the moment the rollback was recorded rather than the change's implementation date.
     */
    public LocalDate statutoryDeadline(int days) {
        LocalDateTime base = acknowledgedAt != null ? acknowledgedAt : LocalDateTime.now();
        return base.toLocalDate().plusDays(days);
    }

    /** Received-at as a date, for {@code discoveryDate} / {@code reportedAt} style columns. */
    public LocalDate receivedOn() {
        return (acknowledgedAt != null ? acknowledgedAt : LocalDateTime.now()).toLocalDate();
    }

    /**
     * Every value {@link #severityOrMedium()} can return.
     *
     * <p>Five CHECK-constrained columns are fed from that one accessor — {@code tprm.vendors.risk_rating},
     * {@code cyber.incidents.severity}, {@code compliance.findings.severity},
     * {@code policy.exceptions.risk_level} and {@code change_mgmt.change_requests.risk_level} — and all
     * five declare exactly this vocabulary. Exposing it as a constant is what lets
     * {@code PromotionVocabularyTest} check one clamp against five migrations instead of trusting a
     * switch statement to have been written by somebody who looked.
     *
     * <p>⚙ F2-180 found two adapters writing values their columns had never accepted, and this accessor
     * is the reason it was not four: severity was clamped from the start. <b>The clamp is the pattern;
     * the defects were the columns that had no clamp.</b>
     */
    public static final Set<String> SEVERITY_BANDS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    /** The band an unrecognised or absent severity becomes. */
    public static final String SEVERITY_DEFAULT = "MEDIUM";

    /** Severity normalised to the {@code LOW|MEDIUM|HIGH|CRITICAL} vocabulary every register uses. */
    public String severityOrMedium() {
        String upper = severity == null ? "" : severity.toUpperCase(java.util.Locale.ROOT);
        return SEVERITY_BANDS.contains(upper) ? upper : SEVERITY_DEFAULT;
    }
}
