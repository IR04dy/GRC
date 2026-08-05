package com.tss.grc.module.admin.committee.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CE-13 — the one place that reads a meeting's {@code agenda_json}, because the platform writes it
 * in two key shapes and every reader so far picked one and rendered the other blank.
 *
 * <p><b>Measured 2026-08-05, dev, all 34 meetings.</b> 24 agendas — every one of the twelve real
 * committees, in both quarters, including the CAB's own next sitting {@code MTG-IT-26Q3} — key their
 * items {@code titleEn}/{@code titleAr}. The other 8 (the older DEMO meetings) and <em>everything</em>
 * {@link CommitteeCalendarService#populateAgenda} builds key them {@code topicEn}/{@code topicAr}.
 * Nothing wrote both.
 *
 * <p><b>Both readers were wrong, in opposite directions.</b> The frontend's agenda list reads
 * {@code topicEn} and so printed an ordered list of empty bullets for those 24; the CE-14a agenda-pack
 * PDF reads {@code titleEn} and so prints {@code —} for the 8 <em>and</em> for every agenda the builder
 * produces — which is to say, for exactly the agendas S7b taught to carry the CAB queue. S7f's own
 * probe passed because the meeting it exported was a seeded one. A control that reads the wrong key is
 * not a weaker control; it is a blank page that looks like a meeting with nothing to discuss.
 *
 * <p><b>Why tolerate rather than migrate.</b> Rewriting 24 rows of seeded JSON is a data mutation for
 * a display bug, and it would fix only the rows that exist today — the next seeder or import would
 * reintroduce the shape. {@code topicEn} stays the canonical key for writers (it is what
 * {@code CommitteeCalendarService}'s class javadoc has documented since V40 and what {@code rowOf}
 * emits); readers accept either. {@code CommitteeAgendaVocabularyTest} pins both halves.
 *
 * <p>⚠ The same mismatch exists one field over: the builder writes {@code isMandatory} and the PDF's
 * item-type column reads {@code mandatory}, so every built item printed "Standing". {@link #mandatory}
 * accepts both for the same reason.
 */
public final class CommitteeAgendaJson {

    private CommitteeAgendaJson() { }

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Canonical writer keys — what {@code rowOf} emits and what new code must emit. */
    public static final String CANONICAL_TITLE_EN = "topicEn";
    public static final String CANONICAL_TITLE_AR = "topicAr";
    public static final String CANONICAL_MANDATORY = "isMandatory";

    /** The seeded deviation, accepted on read and never written. */
    public static final String LEGACY_TITLE_EN = "titleEn";
    public static final String LEGACY_TITLE_AR = "titleAr";
    public static final String LEGACY_MANDATORY = "mandatory";

    /** Items, or an empty list for null / blank / unparseable JSON — an agenda is never a hard error. */
    public static List<Map<String, Object>> items(String agendaJson) {
        if (agendaJson == null || agendaJson.isBlank()) return List.of();
        try {
            List<Map<String, Object>> parsed =
                JSON.readValue(agendaJson, new TypeReference<List<Map<String, Object>>>() { });
            return parsed == null ? List.of() : parsed;
        } catch (Exception notAnAgenda) {
            return List.of();
        }
    }

    /**
     * The item's text in the requested language, falling through: requested key → its legacy twin →
     * the English pair. Returns null when the item names nothing at all, so a caller can say so
     * rather than print an empty cell.
     */
    public static String topic(Map<String, Object> item, boolean arabic) {
        if (item == null) return null;
        String v = arabic
            ? firstNonBlank(item, CANONICAL_TITLE_AR, LEGACY_TITLE_AR, CANONICAL_TITLE_EN, LEGACY_TITLE_EN)
            : firstNonBlank(item, CANONICAL_TITLE_EN, LEGACY_TITLE_EN);
        return v;
    }

    /** True when the item is marked mandatory under either key. */
    public static boolean mandatory(Map<String, Object> item) {
        if (item == null) return false;
        Object v = item.get(CANONICAL_MANDATORY);
        if (v == null) v = item.get(LEGACY_MANDATORY);
        if (v == null) return false;
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v))
            || "1".equals(String.valueOf(v));
    }

    /**
     * How many agenda items were tabled by {@code sourceModule} — the S7b "items for decision" block.
     *
     * <p>Keyed on {@code sourceModule} rather than on the {@code "For decision: "} prefix on purpose:
     * the prefix is display text in two languages and would silently stop matching the first time it
     * is reworded, whereas the marker is the referral's own module name.
     */
    public static int itemsFrom(String agendaJson, String sourceModule) {
        return topicsFrom(agendaJson, sourceModule).size();
    }

    /** The key a referral line carries naming the record it tables. Only referral lines have it. */
    public static final String DECISION_REF = "decisionRef";

    /**
     * The references tabled for decision by {@code sourceModule}, in agenda order.
     *
     * <p><b>Why a dedicated key and not {@code sourceModule} alone.</b> A charter template's standing
     * item carries a source module too, so counting by module reported dev's CAB agenda as tabling one
     * item when the only match was the standing row *Change-management major changes*. And matching the
     * title text would depend on the {@code "For decision: "} prefix, which is display copy in two
     * languages and would stop matching the first time anyone reworded it. The reference is data.
     *
     * <p>An agenda built before this key existed carries decision lines without it; {@code
     * CommitteePort.Sitting#tables} falls back to the title for exactly those, and re-running
     * {@code populateAgenda} upgrades them. Tolerant on read, one shape on write — the same rule as the
     * title keys above.
     */
    public static List<String> tabledRefs(String agendaJson, String sourceModule) {
        if (sourceModule == null) return List.of();
        List<String> out = new java.util.ArrayList<>();
        for (Map<String, Object> item : items(agendaJson)) {
            if (!Objects.equals(sourceModule, item.get("sourceModule"))) continue;
            Object ref = item.get(DECISION_REF);
            if (ref != null && !String.valueOf(ref).isBlank()) {
                out.add(String.valueOf(ref));
            } else {
                // Pre-CE-13 referral line: keep the title so the fallback match can still find it,
                // rather than dropping it and reporting a tabled item as untabled.
                String topic = topic(item, false);
                if (topic != null && topic.startsWith("For decision:")) out.add(topic);
            }
        }
        return out;
    }

    /**
     * The English topic text of every item tabled by {@code sourceModule}, in agenda order.
     *
     * <p><b>Why the text and not just a count.</b> A referring module needs to answer "is <em>this</em>
     * record on the agenda?", not only "are any?". The referral row cannot answer it — {@code
     * populateAgenda} does not stamp {@code meeting_id} on the referrals it tables — and a count says
     * three lines exist without saying which three. The agenda line is built as
     * {@code code + " — " + subject} (see {@code CommitteeCalendarService}), so the caller matches on
     * the <b>reference code</b>, which is a data field the builder deliberately puts there for
     * readability, not on the {@code "For decision: "} prefix, which is display text in two languages
     * and would stop matching the first time it is reworded.
     */
    public static List<String> topicsFrom(String agendaJson, String sourceModule) {
        if (sourceModule == null) return List.of();
        List<String> out = new java.util.ArrayList<>();
        for (Map<String, Object> item : items(agendaJson)) {
            if (Objects.equals(sourceModule, item.get("sourceModule"))) {
                String topic = topic(item, false);
                if (topic != null) out.add(topic);
            }
        }
        return out;
    }

    private static String firstNonBlank(Map<String, Object> item, String... keys) {
        for (String k : keys) {
            Object v = item.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }
}
