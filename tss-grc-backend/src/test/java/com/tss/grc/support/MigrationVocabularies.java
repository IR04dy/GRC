package com.tss.grc.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Every single-column {@code CHECK} vocabulary in the migration set, read from the SQL.
 *
 * <p><b>From the migrations, never from the database</b>, so {@code mvn -o clean package} stays
 * hermetic — that is a house rule, and a test that needs SQL Server to run is a test CI cannot run.
 *
 * <p><b>Why this class exists rather than a fourth copy of the parser.</b> Three test classes had
 * already grown their own version of this walk ({@code PromotionVocabularyTest},
 * {@code VendorPromotionStatusTest}, {@code AutomationRecipientKeyRatchetTest}), and F2-178's
 * lesson — recorded as {@code SeatWindowPredicateSharingTest}, which fails the build when one
 * predicate is written twice — is that duplicated parsing drifts. Each of the traps documented below
 * was found once, at a cost, and a copy that predates the discovery does not know about it.
 *
 * <p><b>Three traps, all measured:</b>
 * <ol>
 *   <li><b>Strip the string literals before looking for a column name.</b> {@code COLUMN_REF}'s
 *       first draft ran against the raw CHECK body and pulled a column called {@code PEND} out of
 *       {@code ratification_state <> N'PENDING'} — because {@code PENDING} contains {@code IN} and
 *       {@code IN} is one of the operators. 🔑 <b>A regex over SQL that still has its string
 *       literals in it is parsing two languages at once.</b></li>
 *   <li><b>More than one column means a cross-column rule, not a vocabulary.</b> That is also what
 *       {@code sys.check_constraints} says about them — {@code parent_column_id} is NULL for
 *       {@code ck_change_pso} and {@code ck_change_ratification_clock}.</li>
 *   <li><b>Later versions win.</b> A constraint dropped and recreated by a later migration must be
 *       read as it will exist after the whole chain has run — the state a deployed server is in, not
 *       the state its first definition described.</li>
 * </ol>
 */
public final class MigrationVocabularies {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");

    /** A closed vocabulary: one column, one constraint, the tokens it admits. */
    public record Vocabulary(String constraint, String table, String column, Set<String> values) {}

    private static final Pattern DECLARATION = Pattern.compile(
        "(?:CONSTRAINT\\s+)?\\[?(\\w+)\\]?\\s+CHECK\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_REF = Pattern.compile(
        "(?:ALTER|CREATE)\\s+TABLE\\s+(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);
    /** An identifier followed by a comparison — run only against a body with literals removed. */
    private static final Pattern COLUMN_REF = Pattern.compile(
        "\\[?([a-z_][a-z0-9_]*)\\]?\\s*(?:<>|!=|=|\\bIN\\b|\\bIS\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LITERAL = Pattern.compile("N?'([A-Z][A-Z0-9_]*)'");
    private static final Pattern ANY_LITERAL = Pattern.compile("N?'[^']*'");

    private static Map<String, Vocabulary> cache;

    private MigrationVocabularies() {}

    /** Keyed by lower-cased constraint name. Parsed once per JVM — four test classes read this. */
    public static synchronized Map<String, Vocabulary> byConstraint() {
        if (cache == null) {
            cache = parse();
        }
        return cache;
    }

    /**
     * The tokens {@code constraint} admits.
     *
     * @throws AssertionError if no such single-column vocabulary exists in the migrations. That is
     *         the F2-177 {@code 'AUDIT_COMMITTE'} shape: a constraint name one letter wrong fails
     *         exactly like a missing one, so it must be loud rather than an empty set — an empty set
     *         would make every assertion built on it pass while comparing nothing.
     */
    public static Set<String> valuesOf(String constraint) {
        Vocabulary v = byConstraint().get(constraint.toLowerCase());
        if (v == null) {
            throw new AssertionError(
                "no single-column CHECK vocabulary named '" + constraint + "' in "
                + MIGRATIONS + ". Either the name is misspelled, the constraint is a cross-column "
                + "rule (which has no single vocabulary), or it was never written. Parsed "
                + byConstraint().size() + " vocabularies.");
        }
        return v.values();
    }

    private static Map<String, Vocabulary> parse() {
        List<Path> files;
        try (Stream<Path> walk = Files.list(MIGRATIONS)) {
            files = walk.filter(p -> p.getFileName().toString().endsWith(".sql"))
                .sorted(Comparator.comparingInt(MigrationVocabularies::version))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + MIGRATIONS, e);
        }
        Map<String, Vocabulary> out = new LinkedHashMap<>();
        for (Path f : files) {
            String src;
            try {
                src = Files.readString(f).replace("\r\n", "\n");
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + f, e);
            }
            Matcher m = DECLARATION.matcher(src);
            while (m.find()) {
                String body = balanced(src, m.end() - 1);
                if (body == null) continue;

                Set<String> values = new LinkedHashSet<>();
                Matcher lit = LITERAL.matcher(body);
                while (lit.find()) values.add(lit.group(1));
                if (values.isEmpty()) continue;      // a range or a null-check, not a vocabulary

                Set<String> columns = new LinkedHashSet<>();
                Matcher col = COLUMN_REF.matcher(ANY_LITERAL.matcher(body).replaceAll("''"));
                while (col.find()) columns.add(col.group(1).toLowerCase());
                if (columns.size() != 1) continue;   // cross-column rule — see trap 2

                out.put(m.group(1).toLowerCase(), new Vocabulary(
                    m.group(1), tableOf(src, m.start()), columns.iterator().next(), values));
            }
        }
        if (out.size() < 40) {
            throw new AssertionError(
                "anti-vacuity: the migration parse found only " + out.size() + " CHECK vocabularies "
                + "in " + files.size() + " files. The platform has hundreds; a parser that has "
                + "stopped matching makes every test built on it pass while comparing nothing.");
        }
        return out;
    }

    /** The nearest {@code CREATE TABLE} / {@code ALTER TABLE} above this point owns the constraint. */
    private static String tableOf(String src, int at) {
        Matcher m = TABLE_REF.matcher(src.substring(0, at));
        String last = "?";
        while (m.find()) {
            last = m.group(1) == null ? m.group(2) : m.group(1) + "." + m.group(2);
        }
        return last;
    }

    private static String balanced(String src, int open) {
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            if (src.charAt(i) == '(') depth++;
            else if (src.charAt(i) == ')' && --depth == 0) return src.substring(open, i + 1);
        }
        return null;
    }

    private static int version(Path p) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(p.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }
}
