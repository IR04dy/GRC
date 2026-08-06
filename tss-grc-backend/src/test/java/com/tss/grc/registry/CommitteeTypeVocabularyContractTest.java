package com.tss.grc.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.tss.grc.common.model.CommitteeTypes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-77 — the committee-type vocabulary the schema enforces must be the one the application validates.
 *
 * <p><b>The defect.</b> {@code V40__committee_workflows.sql} created {@code CK_com_type} with
 * <b>8</b> values and <b>no migration ever extended it</b>. The live database's constraint allows
 * <b>22</b>, and {@link CommitteeTypes#VALUES} names the same 22 — they agreed with each other and
 * only the migration was behind. So a database <i>built from the migration folder</i> rejected 14
 * committee types the platform validates and actively uses (17 are in use in dev), and the first thing
 * to notice was {@code V159}'s seed failing on {@code AI_ETHICS} when the chain was finally run in
 * order for the first time.
 *
 * <p>🔑 <b>Why this belongs with F2-140 and F2-141 rather than with "a stale enum".</b> The vocabulary
 * was widened <b>in the database</b> — by the Python bootstrap — and the widening was never written
 * down as a migration. Nothing was wrong with the running platform, and nothing could detect it,
 * because the only two authorities anyone compared (Java and the live schema) were the two that
 * agreed. <b>The third authority was a file nobody executed.</b> That is RC-A with the drifting copy
 * outside the comparison.
 *
 * <p><b>Why a test and not a comment.</b> Adding 14 values to V40 fixes today. This asserts that the
 * migration's list and {@link CommitteeTypes#VOCABULARY} stay identical in <b>both</b> directions, so
 * the next committee type added to the enum cannot silently leave a fresh installation unable to
 * store it. Same shape as F2-94's recipient-key ratchet, and for the same reason.
 */
class CommitteeTypeVocabularyContractTest {

    private static final Path V40 = Path.of("src", "main", "resources", "db", "migration",
            "sqlserver", "V40__committee_workflows.sql");

    /** The {@code CK_com_type CHECK (committee_type IN ( … ))} value list, comments and all. */
    private static final Pattern CHECK_BLOCK = Pattern.compile(
            "CK_com_type\\s+CHECK\\s*\\(\\s*committee_type\\s+IN(.*?)\\)\\s*\\)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern LITERAL = Pattern.compile("N'([A-Z_]+)'");

    private static Set<String> vocabularyInMigration() throws IOException {
        String sql = Files.readString(V40, StandardCharsets.UTF_8);
        Matcher block = CHECK_BLOCK.matcher(sql);
        assertThat(block.find())
                .as("could not find `CK_com_type CHECK (committee_type IN (…))` in %s. If the "
                    + "constraint moved or was renamed, repoint this test — do not delete it: the "
                    + "defect it guards is a migration-built database that cannot store a committee "
                    + "type the application accepts.", V40)
                .isTrue();

        // Strip line comments before scanning. ⚠ Stated precisely, because it would be easy to claim
        // more for this than it earns: today's comment names AI_ETHICS/CYBER_SECURITY/PRIVACY_DPO as
        // *prose*, not as N'…' literals, so LITERAL would not match them either way — dropping
        // AI_ETHICS from the value list still fails this test with the comment left in. The stripping
        // is here for the comment that eventually quotes a value in SQL form, at which point a scan
        // that counted prose would pass on a constraint that does not list it. That failure mode —
        // a scan matching prose — is the one this programme has repeated most.
        String values = block.group(1).replaceAll("(?m)--.*$", "");

        Set<String> found = new TreeSet<>();
        Matcher m = LITERAL.matcher(values);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Test
    @DisplayName("V40's CHECK and CommitteeTypes name the same vocabulary, in both directions")
    void theMigrationAndTheApplicationAgree() throws IOException {
        Set<String> inMigration = vocabularyInMigration();
        Set<String> inCode = new TreeSet<>(CommitteeTypes.VALUES);

        assertThat(inMigration).as(
            "V40's CK_com_type and CommitteeTypes.VALUES must name the same committee types. A value "
            + "in the code but not the migration means a FRESH INSTALLATION cannot store a type the "
            + "application accepts — which is exactly what happened: V40 listed 8 of 22, no migration "
            + "extended it, and the live database was widened by the Python bootstrap instead. "
            + "Missing from the migration: %s. Extra in the migration: %s",
            minus(inCode, inMigration), minus(inMigration, inCode))
            .isEqualTo(inCode);
    }

    @Test
    @DisplayName("the scan sees a real list — a stripped comment must not read as an empty vocabulary")
    void theScanCannotSilentlyFindNothing() throws IOException {
        Set<String> inMigration = vocabularyInMigration();

        // If the regex or the comment-stripping breaks, `inMigration` empties and the comparison above
        // fails loudly rather than passing — but only because CommitteeTypes is non-empty. This pins
        // the floor explicitly so neither side can collapse to zero and agree.
        assertThat(inMigration.size()).as(
            "the migration scan found %d committee types; V40 declares 22. A broken scan reads as an "
            + "empty vocabulary, and an empty set compared against an empty set agrees.",
            inMigration.size())
            .isGreaterThanOrEqualTo(22);

        assertThat(CommitteeTypes.VALUES).as("CommitteeTypes.VALUES is empty").isNotEmpty();
        assertThat(inMigration).contains("BOARD", "AI_ETHICS", "CYBER_SECURITY", "PRIVACY_DPO");
    }

    private static String minus(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out.isEmpty() ? "(none)" : out.toString();
    }
}
