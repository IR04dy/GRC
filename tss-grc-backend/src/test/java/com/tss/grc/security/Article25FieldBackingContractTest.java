package com.tss.grc.security;

import com.tss.grc.module.privacy.entity.DataBreach;
import com.tss.grc.module.privacy.service.PrivacyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-209 — the backend half of the Article 25 provenance pair.
 *
 * <p>The frontend's {@code ARTICLE_25_FIELDS} declares, per field of the PDPL
 * Article 25 breach notification, which property of the breach record it is read
 * from — or {@code null}, meaning the platform records no source for it and the
 * DPO must supply it in the SDAIA portal. This test resolves those claims against
 * the real {@link DataBreach} entity by reflection.
 *
 * <p><b>Why it takes two gates, and neither is sufficient.</b> F2-193 established
 * the pattern: a fabricated connector with a perfect frontend shape and a
 * nonexistent backend class passed the frontend gate and failed the backend one.
 * The same asymmetry holds here. {@code sdaiaNotificationProvenance.test.ts} can
 * check that every entry declares <em>something</em>, that the not-held set is the
 * one measured, and that the rendered value goes through the declared accessor —
 * but it cannot tell a real DTO property from a plausible-looking string. Only
 * this side can, and it is the side that catches the failure mode that produced
 * the finding: a field asserted to a regulator with nothing behind it.
 *
 * <p><b>The defect it closes.</b> {@code generateSdaiaNotification} returned a
 * complete Article 25 packet — reporting controller identity (with a hard-coded
 * CR number), categories of personal data affected, likely consequences,
 * containment measures taken and remediation measures proposed — and
 * {@code privacy.data_breaches} has a column for none of them (25 columns, read
 * from {@code sys.columns}, identical in dev and prod). The last two are not
 * metrics but statements of fact about the organisation's conduct, addressed to a
 * data-protection regulator inside a 72-hour statutory window.
 *
 * <p>Hermetic: reads a source file and a compiled entity, no database, no
 * secrets. {@code mvn -o clean package} must stay database-free.
 */
class Article25FieldBackingContractTest {

    /** Repo-relative path to the frontend module that declares the inventory. */
    private static final Path PDPL_BREACH_TS = Path.of(
        "..", "tss-grc-ui-v2", "src", "lib", "pdplBreach.ts");

    /**
     * The Article 25 fields the platform cannot fill from the record, measured
     * against the real schema. Named rather than counted: wiring one requires a
     * migration, and a count could shrink by deletion as easily as by progress.
     */
    private static final Set<String> EXPECTED_NOT_HELD = new LinkedHashSet<>(List.of(
        "Categories",    // categories of personal data involved — no column
        "Consequences",  // likely consequences for data subjects — no column
        "Containment",   // measures taken — no column; was an invented sentence
        "Proposed",      // measures proposed — no column; was an invented sentence
        "Controller",    // reporting controller identity — not on this table
        "Dpo"            // DPO contact — not on this table
    ));

    /**
     * Properties the frontend DTO exposes that are NOT fields of the entity,
     * with the reason each is legitimate. Both are computed on the client from
     * entity fields; neither invents a value.
     *
     * <p>Kept as a named map rather than a blanket allowance because that is the
     * hole this whole family of gates keeps falling through: a tolerated member
     * has to be indistinguishable from nothing, or it is indistinguishable from a
     * reviewed one (house rule 3).
     */
    private static final Map<String, String> DTO_ONLY_PROPERTIES = new LinkedHashMap<>();
    static {
        // none today — every declared source resolves to a real entity field.
        // An entry here must say which entity field it is derived FROM.
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static String readTs() throws IOException {
        assertThat(Files.exists(PDPL_BREACH_TS))
            .as("lib/pdplBreach.ts must exist at %s — the inventory is what replaced the "
                + "retired generator, so a missing file makes this gate vacuous",
                PDPL_BREACH_TS.toAbsolutePath().normalize())
            .isTrue();
        return Files.readString(PDPL_BREACH_TS, StandardCharsets.UTF_8);
    }

    /**
     * ⚠ ONE alternation, line comments FIRST — the ordering this repo has now got
     * wrong twice (F2-68(c) in the census scanner, F2-198 in a second one written
     * without reading the first). Load-bearing here because the file's header
     * QUOTES the retired field names to record what was deleted.
     */
    private static String stripComments(String src) {
        return src.replaceAll("(?<![:/])//[^\\n]*|/\\*(?s).*?\\*/", "");
    }

    private record Declared(String key, String clause, String source) {
        boolean held() { return source != null; }
    }

    private static List<Declared> parseInventory(String ts) {
        String stripped = stripComments(ts);
        Matcher block = Pattern.compile("ARTICLE_25_FIELDS[^=]*=\\s*\\[(.*?)\\n];", Pattern.DOTALL)
            .matcher(stripped);
        assertThat(block.find())
            .as("ARTICLE_25_FIELDS did not parse out of lib/pdplBreach.ts")
            .isTrue();

        List<Declared> out = new ArrayList<>();
        // ⚠ The trailing `(?:,[^}]*)?` is load-bearing: an entry may carry
        // presentation properties after `source` (e.g. `format: 'datetime'`).
        // Without it the parse silently drops exactly the entries that have been
        // edited most recently, and a smaller population reads as coverage.
        Matcher entry = Pattern.compile(
            "\\{\\s*key:\\s*'([^']+)'\\s*,\\s*clause:\\s*'([^']+)'\\s*,\\s*source:\\s*(null|'[^']+')\\s*(?:,[^}]*)?}")
            .matcher(block.group(1));
        while (entry.find()) {
            String raw = entry.group(3);
            out.add(new Declared(entry.group(1), entry.group(2),
                "null".equals(raw) ? null : raw.substring(1, raw.length() - 1)));
        }
        return out;
    }

    /** Field names of the entity, including any mapped superclass. */
    private static Set<String> entityFieldNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> c = DataBreach.class; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.isSynthetic()) names.add(f.getName());
            }
        }
        return names;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every Article 25 field that claims a source names a real field of DataBreach")
    void everyClaimedSourceResolvesToTheEntity() throws IOException {
        List<Declared> fields = parseInventory(readTs());
        assertThat(fields)
            .as("the inventory must not be empty — an empty parse makes every assertion here pass")
            .hasSizeGreaterThanOrEqualTo(12);

        Set<String> entityFields = entityFieldNames();
        List<String> unresolved = new ArrayList<>();
        for (Declared f : fields) {
            if (!f.held()) continue;
            if (entityFields.contains(f.source())) continue;
            if (DTO_ONLY_PROPERTIES.containsKey(f.source())) continue;
            unresolved.add(f.key() + " -> " + f.source());
        }

        assertThat(unresolved)
            .as("""
                An Article 25 field claims a source that is neither a field of DataBreach nor a \
                declared client-side derivation. This is the exact failure that produced F2-209: \
                the retired generator asserted the categories of personal data affected, the likely \
                consequences, the containment measures taken and the measures proposed to a \
                data-protection regulator, and privacy.data_breaches has a column for none of them. \
                Either the field has a column (add it to the entity and the DTO), or it does not \
                (declare source: null so the DPO is told to supply it). A plausible property name is \
                not provenance.""")
            .isEmpty();
    }

    @Test
    @DisplayName("the fields with no source are exactly the six measured to have no column")
    void theNotHeldSetIsTheMeasuredOne() throws IOException {
        List<Declared> fields = parseInventory(readTs());
        Set<String> notHeld = new LinkedHashSet<>();
        for (Declared f : fields) if (!f.held()) notHeld.add(f.key());

        assertThat(notHeld)
            .as("""
                The set of Article 25 fields the platform cannot fill changed. That set is a fact \
                about privacy.data_breaches, so it may only change with a migration that adds the \
                column, the entity field, the DTO field and this list, in one commit. Enforced in \
                BOTH directions: a field that gains a column must lose its place here, or the next \
                reader cannot tell a wired field from a tolerated one.""")
            .containsExactlyInAnyOrderElementsOf(EXPECTED_NOT_HELD);
    }

    /**
     * ⚠⚠ The reverse leg, and the one that catches a re-fabrication with a
     * perfect shape: it is not enough that the six declare {@code null}. If a
     * column for one of them ever appears on the entity, the {@code null} is
     * wrong and the next reader has no way to know.
     *
     * <p>F2-199's first draft of a sibling gate wrote two exemptions for pages
     * that were not in the population, and the forward assertion passed because
     * the population was already empty. The direction that catches that is the
     * one nobody writes first.
     */
    @Test
    @DisplayName("no field declared source-less actually has a column on the entity")
    void nothingDeclaredUnheldIsSecretlyAvailable() throws IOException {
        List<Declared> fields = parseInventory(readTs());
        Set<String> entityFields = entityFieldNames();

        // The property name the client would use for each unheld field, if the
        // column existed. Names it would take from the entity's own convention.
        Map<String, List<String>> candidates = Map.of(
            "Categories",   List.of("dataCategories", "categoriesAffected", "dataCategoriesJson"),
            "Consequences", List.of("likelyConsequences", "likelyConsequencesEn", "consequencesEn"),
            "Containment",  List.of("containmentMeasures", "containmentMeasuresEn", "measuresTaken"),
            "Proposed",     List.of("proposedMeasures", "proposedMeasuresEn", "measuresProposed"),
            "Controller",   List.of("controllerName", "controllerNameEn", "crNumber"),
            "Dpo",          List.of("dpoName", "dpoNameEn", "dpoEmail")
        );

        List<String> nowAvailable = new ArrayList<>();
        for (Declared f : fields) {
            if (f.held()) continue;
            for (String candidate : candidates.getOrDefault(f.key(), List.of())) {
                if (entityFields.contains(candidate)) {
                    nowAvailable.add(f.key() + " could read DataBreach." + candidate);
                }
            }
        }

        assertThat(nowAvailable)
            .as("""
                An Article 25 field is declared as "the platform does not hold this" while \
                DataBreach now has a field that would supply it. The console is telling a DPO to \
                enter something in the SDAIA portal that the record already carries. Wire it and \
                remove it from EXPECTED_NOT_HELD in the same commit.""")
            .isEmpty();
    }

    /**
     * The client mirrors the server's SDAIA state machine. F2-205 found FIVE
     * client vocabularies for one server machine — all agreeing with each other
     * and none with the server, which is how they survived: consistency among
     * copies is not evidence, it is the mechanism of concealment.
     */
    @Test
    @DisplayName("the client's SDAIA notification vocabulary matches PrivacyService.SDAIA_STATES")
    void clientVocabularyMirrorsTheServerMachine() throws Exception {
        Field f = PrivacyService.class.getDeclaredField("SDAIA_STATES");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> server = (Set<String>) f.get(null);

        Matcher m = Pattern.compile("SDAIA_NOTIFICATION_STATES\\s*=\\s*\\[(.*?)]", Pattern.DOTALL)
            .matcher(stripComments(readTs()));
        assertThat(m.find()).as("SDAIA_NOTIFICATION_STATES must be declared in lib/pdplBreach.ts").isTrue();
        Set<String> client = new LinkedHashSet<>();
        Matcher tok = Pattern.compile("'([A-Z_]+)'").matcher(m.group(1));
        while (tok.find()) client.add(tok.group(1));

        assertThat(client)
            .as("the client's SDAIA vocabulary drifted from the server's — the FE decides which "
                + "breaches still need a statutory notification from these tokens")
            .containsExactlyInAnyOrderElementsOf(server);
    }

    /**
     * ⚠ The statutory gate must read the column the server owns. This is the
     * live half of F2-209: 4 of 17 live breaches in both databases carry
     * {@code status = 'NOTIFIED'} with {@code sdaia_notification_status =
     * 'NOT_REPORTED'}, 113–193 days past a 72-hour window.
     *
     * <p>Asserted from the backend side too, because the two columns are the
     * server's and the confusion between them is a fact about the schema, not
     * about the client: {@code status} is the breach lifecycle and
     * {@code sdaiaNotificationStatus} is the regulator-notification workflow, and
     * only the second has a state machine.
     */
    @Test
    @DisplayName("DataBreach keeps the SDAIA notification state in its own column, not in status")
    void theTwoStatusColumnsAreDistinct() {
        Set<String> names = entityFieldNames();
        assertThat(names)
            .as("both columns must exist — the finding is that a client read the wrong one, which "
                + "is only possible because they are different columns answering different questions")
            .contains("status", "sdaiaNotificationStatus", "sdaiaSubmittedAt", "sdaiaSubmissionRef");
    }
}
