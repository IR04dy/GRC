package com.tss.grc.module.incident.intake;

import com.tss.grc.module.incident.dto.IncidentRequest;
import com.tss.grc.module.incident.service.IncidentService;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code SECURITY_INCIDENT} / {@code VRP_DISCLOSURE} / {@code WORKPLACE_INJURY} /
 * {@code CONSUMER_COMPLAINT_RESPONSE} → {@code cyber.incidents} — F2-172.
 *
 * <p>Four kinds share this target and three of them do not look like a security event: a GOSI-
 * reportable workplace injury (24-hour notification) and a Maaroof consumer complaint (5 working
 * days) are both catalogued to {@code INCIDENT}. So the category comes from whichever field that
 * kind's form supplies rather than from a constant, and {@code discoveryDate} is the reporter's
 * timestamp where there is one.
 *
 * <p>{@code discoveryDate} is when the event was <b>observed</b>, not when it was promoted — the
 * same reasoning {@code IncidentAdapter} states for the change-rollback path, and for the same
 * reason: an incident-response SLA anchored to the triage moment starts at a point nobody was
 * watching.
 *
 * <h3>⚠⚠ F2-180 — three of the four kinds could never promote, and the fourth only sometimes</h3>
 *
 * <p>The category used to be whatever the submitter's form supplied, passed through untouched, and
 * {@code CK_incidents_category} accepts exactly
 * {@code CYBER | DATA_BREACH | INFRASTRUCTURE | ACCESS_CONTROL | OTHER} (or NULL). Measured against
 * the forms those four kinds actually render:
 *
 * <ul>
 *   <li>{@code SECURITY_INCIDENT} offers {@code PHISHING | MALWARE | LOST_DEVICE |
 *       UNAUTHORISED_ACCESS} — <b>none of the four is a legal value</b>, so the flagship NCA ECC 6.1
 *       path failed on the constraint for every submission that answered the question;</li>
 *   <li>{@code WORKPLACE_INJURY} supplies {@code injuryType} ({@code MINOR … FATALITY}) — none legal,
 *       and the constant fallback was {@code "HSE"}, which is not legal either;</li>
 *   <li>{@code CONSUMER_COMPLAINT_RESPONSE} supplies {@code complaintCategory} ({@code REFUND |
 *       DECEPTIVE | WARRANTY | LABELLING | OTHER}) — <b>1 of 5</b> legal, and the fallback
 *       {@code "CONSUMER"} was not;</li>
 *   <li>{@code VRP_DISCLOSURE} has no category field, so it fell through to {@code "CYBER"} and is the
 *       one path that worked.</li>
 * </ul>
 *
 * <p>🔑 The reusable part is <em>which</em> value was wrong. Severity on this same request is safe
 * because {@link IntakePromotionContext#severityOrMedium()} clamps it to the four values the column
 * accepts — a schema-aware accessor written by somebody who had checked. Category had no such
 * accessor and so leaked the submitter's vocabulary straight into a constrained column. **A closed
 * column needs a closed writer; there is no third option where the caller is trusted.**
 *
 * <h3>Why map rather than widen the constraint</h3>
 *
 * <p>{@code cyber.incidents.category} is a five-value cyber taxonomy from V4, re-asserted for fresh
 * installs by V364. Admitting {@code HSE} and {@code CONSUMER} would make a cyber register's category
 * column the union of four regulators' vocabularies and change every category-grouped dashboard
 * built on it. V261 hit this exact wall and resolved it the same way, in a comment that is the clearest
 * statement of the rule anywhere in the tree: <em>"category → 'CYBER' (CK_incidents_category is
 * strict; type kept in description)"</em>. Nothing is lost — the reporter's own word is preserved on
 * the incident's description, and the whole submission stays on the intake record either way.
 *
 * @see com.tss.grc.module.incident.intake.IncidentPromotionCategoryTest the pin
 */
@Component
@RequiredArgsConstructor
public class IncidentPromotionAdapter implements RegisterPromotionPort {

    // ── The vocabulary CK_incidents_category allows, as named constants so the pin can read them ──
    static final String CATEGORY_CYBER          = "CYBER";
    static final String CATEGORY_ACCESS_CONTROL = "ACCESS_CONTROL";
    static final String CATEGORY_DATA_BREACH    = "DATA_BREACH";
    static final String CATEGORY_INFRASTRUCTURE = "INFRASTRUCTURE";
    static final String CATEGORY_OTHER          = "OTHER";

    /**
     * Where an unrecognised sub-type lands, per kind — a security event whose sub-type this mapping has
     * not seen is still a security event, while a workplace injury or a consumer complaint is not a
     * cyber category at all and {@code OTHER} is what the column has for "none of these".
     */
    static final String FALLBACK_SECURITY  = CATEGORY_CYBER;
    static final String FALLBACK_NON_CYBER = CATEGORY_OTHER;

    /** Every value this adapter can write to {@code cyber.incidents.status}. */
    static final String PROMOTED_STATUS = "OPEN";

    /**
     * The four forms' own category vocabularies, mapped onto the column's five.
     *
     * <p>⚠ {@code UNAUTHORISED_ACCESS} and {@code UNAUTHORIZED_ACCESS} are both here on purpose:
     * {@code intakeForms.ts} spells it the British way and {@code CyberPage.tsx} the American way, and
     * a mapping that knows only one of them silently downgrades the other to the fallback. Two
     * spellings of one concept is exactly the kind of thing a lookup table hides.
     */
    private static final Map<String, String> REPORTED_TO_COLUMN = Map.ofEntries(
        // SECURITY_INCIDENT — `category`
        Map.entry("PHISHING",             CATEGORY_CYBER),
        Map.entry("MALWARE",              CATEGORY_CYBER),
        Map.entry("RANSOMWARE",           CATEGORY_CYBER),
        Map.entry("LOST_DEVICE",          CATEGORY_CYBER),
        Map.entry("UNAUTHORISED_ACCESS",  CATEGORY_ACCESS_CONTROL),
        Map.entry("UNAUTHORIZED_ACCESS",  CATEGORY_ACCESS_CONTROL),
        Map.entry("INSIDER_THREAT",       CATEGORY_ACCESS_CONTROL),
        // PRIVACY_BREACH's vocabulary reaches here when a privacy event is filed as an incident
        Map.entry("EXPOSED_DATABASE",     CATEGORY_DATA_BREACH),
        Map.entry("MISDIRECTED",          CATEGORY_DATA_BREACH),
        Map.entry("OVER_SHARING",         CATEGORY_DATA_BREACH),
        Map.entry("LOST_DOCUMENT",        CATEGORY_DATA_BREACH),
        Map.entry("DATA_LOSS",            CATEGORY_DATA_BREACH),
        // values already in the column's own vocabulary pass through
        Map.entry(CATEGORY_CYBER,          CATEGORY_CYBER),
        Map.entry(CATEGORY_ACCESS_CONTROL, CATEGORY_ACCESS_CONTROL),
        Map.entry(CATEGORY_DATA_BREACH,    CATEGORY_DATA_BREACH),
        Map.entry(CATEGORY_INFRASTRUCTURE, CATEGORY_INFRASTRUCTURE),
        Map.entry(CATEGORY_OTHER,          CATEGORY_OTHER));

    /**
     * Every value this adapter can write to {@code cyber.incidents.category} — <b>derived</b> from the
     * mapping above plus the two fallbacks, never hand-listed.
     *
     * <p>⚙ The first draft of this field did hand-list it, and immediately disagreed with the code: the
     * map gained an {@code INFRASTRUCTURE} pass-through that the literal {@code Set.of(...)} did not
     * have, so the pin would have been checking a vocabulary the adapter no longer wrote. That is the
     * F2-156 shape — two artefacts that must agree, with nobody maintaining the agreement — and it
     * appeared inside the very slice that exists to close it. Deriving costs one stream and makes the
     * disagreement impossible to write.
     */
    static final Set<String> PROMOTED_CATEGORIES = Stream.concat(
            REPORTED_TO_COLUMN.values().stream(),
            Stream.of(FALLBACK_SECURITY, FALLBACK_NON_CYBER))
        .collect(Collectors.toUnmodifiableSet());

    private final IncidentService incidents;

    @Override public String target() { return "INCIDENT"; }

    @Override public String requiredPermission() { return "INCIDENT:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        IncidentRequest req = new IncidentRequest(
            ctx.titleEn(255, "summary", "description", "responseSummary", "injuryType"),
            ctx.titleAr(255),
            ctx.severityOrMedium(),
            category(ctx),
            PROMOTED_STATUS,
            ctx.ownerName(),
            ctx.receivedOn(),
            null,                        // resolutionDate — open by definition
            description(ctx),
            null, null, null);           // timeline / lessons — nothing to record yet
        return String.valueOf(incidents.create(req).id());
    }

    /**
     * The reporter's answer translated into the column's vocabulary, or the kind's own domain when
     * the answer is one this mapping has not seen.
     *
     * <p>The fallback is per-kind rather than a single constant because the honest bucket differs: a
     * security event whose sub-type is unrecognised is still a security event ({@code CYBER}), while a
     * workplace injury or a consumer complaint is not a cyber category at all and {@code OTHER} is what
     * that column has for "none of these".
     */
    private String category(IntakePromotionContext ctx) {
        String reported = reported(ctx);
        if (reported != null) {
            String mapped = REPORTED_TO_COLUMN.get(reported);
            if (mapped != null) return mapped;
        }
        return switch (ctx.kind()) {
            case "WORKPLACE_INJURY", "CONSUMER_COMPLAINT_RESPONSE" -> FALLBACK_NON_CYBER;
            default -> FALLBACK_SECURITY;
        };
    }

    /**
     * The reporter's own category word, kept on the incident so the mapping above loses nothing —
     * the V261 precedent, whose comment says {@code CK_incidents_category} is strict and the type is
     * kept in the description.
     */
    private String description(IntakePromotionContext ctx) {
        String reported = reported(ctx);
        String body = ctx.description();
        // Nothing was translated, so there is nothing the reader cannot already see on the record.
        if (reported == null || reported.equals(category(ctx))) return body;
        String line = "Reported category: " + reported;
        return body == null || body.isBlank() ? line : line + "\n\n" + body;
    }

    private String reported(IntakePromotionContext ctx) {
        return ctx.firstText("category", "complaintCategory", "injuryType")
            .map(s -> s.trim().toUpperCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .orElse(null);
    }
}
