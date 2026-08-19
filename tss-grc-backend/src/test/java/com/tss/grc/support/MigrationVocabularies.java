package com.tss.grc.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    /**
     * A single-column <em>numeric</em> CHECK — {@code weight BETWEEN 0 AND 10}, {@code pct > 0 AND
     * pct <= 1}. F2-241.
     *
     * <p><b>Why this sits beside {@link Vocabulary} rather than inside it.</b> F2-230 established
     * that a range is not a vocabulary and asserted that limit deliberately: an ordered ladder and a
     * bounded number answer different questions, and blurring them is how a sort gets called a
     * ranking. But both are the same thing to a <em>writer</em> — a value the column will refuse —
     * and F2-241 needed to ask one question of every CHECK a column carries. Excluding ranges there
     * would have shipped an invariant whose population was half its claim, which is this
     * programme's most-repeated defect.
     *
     * <p>{@code parsed} is false when the predicate is a single-column numeric rule this bound
     * reader could not reduce to an interval. It is exposed rather than skipped because a bound that
     * silently did not parse scores exactly like a bound that was satisfied.
     */
    public record Range(String constraint, String table, String column,
                        BigDecimal min, boolean minInclusive,
                        BigDecimal max, boolean maxInclusive, boolean parsed) {

        /** Whether {@code v} lies inside this interval. Unparsed ranges admit everything. */
        public boolean admits(BigDecimal v) {
            if (!parsed || v == null) {
                return true;
            }
            if (min != null) {
                int c = v.compareTo(min);
                if (c < 0 || (c == 0 && !minInclusive)) return false;
            }
            if (max != null) {
                int c = v.compareTo(max);
                if (c > 0 || (c == 0 && !maxInclusive)) return false;
            }
            return true;
        }

        /** For a failure message: the interval in ordinary notation. */
        public String interval() {
            if (!parsed) return "(unparsed: " + constraint + ")";
            return (minInclusive ? "[" : "(") + (min == null ? "-inf" : min.toPlainString())
                 + ", " + (max == null ? "+inf" : max.toPlainString()) + (maxInclusive ? "]" : ")");
        }
    }

    private static final Pattern DECLARATION = Pattern.compile(
        "(?:CONSTRAINT\\s+)?\\[?(\\w+)\\]?\\s+CHECK\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_REF = Pattern.compile(
        "(?:ALTER|CREATE)\\s+TABLE\\s+(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);
    /** An identifier followed by a comparison — run only against a body with literals removed. */
    private static final Pattern COLUMN_REF = Pattern.compile(
        "\\[?([a-z_][a-z0-9_]*)\\]?\\s*(?:<>|!=|=|\\bIN\\b|\\bIS\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LITERAL = Pattern.compile("N?'([A-Z][A-Z0-9_]*)'");
    private static final Pattern ANY_LITERAL = Pattern.compile("N?'[^']*'");
    /** A numeric comparison, for {@link Range}. The column may sit on either side. */
    private static final Pattern NUM_COMPARISON = Pattern.compile(
        "\\[?([a-z_][a-z0-9_]*)\\]?\\s*(>=|<=|>|<|=)\\s*\\(?\\s*(-?\\d+(?:\\.\\d+)?)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern BETWEEN = Pattern.compile(
        "\\[?([a-z_][a-z0-9_]*)\\]?\\s+BETWEEN\\s+\\(?\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\)?"
        + "\\s+AND\\s+\\(?\\s*(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    /** An identifier used in arithmetic — a range column may also appear as `a + b <= 100`. */
    private static final Pattern NUM_COLUMN_REF = Pattern.compile(
        "\\[?([a-z_][a-z0-9_]*)\\]?\\s*(?:>=|<=|>|<|=|\\bBETWEEN\\b|\\bIS\\b|[-+*/])",
        Pattern.CASE_INSENSITIVE);

    private static Map<String, Vocabulary> cache;
    private static List<Range> rangeCache;

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

    /**
     * Every {@code CHECK} declaration in the migration set, in version order.
     *
     * <p>F2-241 extracted this so the vocabulary reading and the range reading are <b>one walk, two
     * interpretations</b>. Copying the walk would have copied the three traps above into a second
     * place — and this class's whole reason for existing is that a copy predating a discovery does
     * not know about it. The consumers differ only in what they do with {@code body}.
     */
    private record Declaration(String constraint, String table, String body) {}

    private static List<Path> migrationFiles() {
        try (Stream<Path> walk = Files.list(MIGRATIONS)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".sql"))
                .sorted(Comparator.comparingInt(MigrationVocabularies::version))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + MIGRATIONS, e);
        }
    }

    private static List<Declaration> declarations() {
        List<Declaration> out = new ArrayList<>();
        for (Path f : migrationFiles()) {
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
                out.add(new Declaration(m.group(1), tableOf(src, m.start()), body));
            }
        }
        return out;
    }

    /** Every single-column numeric range CHECK. Parsed once per JVM. F2-241. */
    public static synchronized List<Range> ranges() {
        if (rangeCache == null) {
            rangeCache = parseRanges();
        }
        return rangeCache;
    }

    /**
     * The vocabulary CHECKs on one column, addressed the way an <em>entity</em> knows it.
     *
     * <p>{@code schema} may be empty — four entities declare {@code @Table} with a name and no
     * schema — in which case the bare table name is used. That fallback is where the danger is, and
     * it is refused rather than guessed: <b>{@code vendor_assessments} exists in both {@code scm}
     * and {@code tprm}, and both carry a vocabulary CHECK on {@code status} with <em>different</em>
     * tokens</b> ({@code CK_scm_assess_status} admits ESCALATED, {@code CK_va_status} does not). A
     * bare-name lookup would have compared {@code scm}'s initialiser against {@code tprm}'s
     * vocabulary and answered confidently. That is F2-222's filed hazard — 15 entity simple names
     * declared in two packages — and F2-240's own filing was written from the wrong twin of this
     * exact pair, which is what started F2-241.
     *
     * <p>Two vocabulary CHECKs on one column are ANDed by the database, so the admissible set is
     * their <b>intersection</b>, never their union. There are none today; computing it correctly
     * anyway costs one line and means the next one cannot silently widen what a column accepts.
     *
     * @return the tokens the column admits, or empty if it carries no vocabulary CHECK
     * @throws AssertionError if the table name is ambiguous across schemas and no schema was given
     */
    public static Set<String> vocabularyOn(String schema, String table, String column) {
        String col = column.toLowerCase();
        List<Vocabulary> hits = new ArrayList<>();
        boolean qualified = schema != null && !schema.isBlank();
        Set<String> schemasSeen = new LinkedHashSet<>();
        for (Vocabulary v : byConstraint().values()) {
            if (!v.column().equals(col)) continue;
            String vt = v.table().toLowerCase();
            String bare = vt.contains(".") ? vt.substring(vt.indexOf('.') + 1) : vt;
            if (!bare.equals(table.toLowerCase())) continue;
            schemasSeen.add(vt.contains(".") ? vt.substring(0, vt.indexOf('.')) : "");
            if (qualified && vt.contains(".")
                && !vt.equals(schema.toLowerCase() + "." + table.toLowerCase())) {
                continue;
            }
            hits.add(v);
        }
        if (!qualified && schemasSeen.size() > 1) {
            throw new AssertionError(
                "ambiguous: '" + table + "." + column + "' carries a vocabulary CHECK in more than "
                + "one schema " + schemasSeen + " and the caller named none. Resolving it by bare "
                + "table name would compare one register's value against another register's "
                + "vocabulary — see F2-222's duplicated entity simple names.");
        }
        if (hits.isEmpty()) {
            return Set.of();
        }
        Set<String> admissible = new LinkedHashSet<>(hits.get(0).values());
        for (Vocabulary v : hits.subList(1, hits.size())) {
            admissible.retainAll(v.values());   // ANDed by the database — intersect, never union
        }
        return admissible;
    }

    /** The numeric range CHECKs on one column, addressed as an entity knows it. F2-241. */
    public static List<Range> rangesOn(String schema, String table, String column) {
        String col = column.toLowerCase();
        List<Range> out = new ArrayList<>();
        for (Range r : ranges()) {
            if (!r.column().equals(col)) continue;
            String rt = r.table().toLowerCase();
            String bare = rt.contains(".") ? rt.substring(rt.indexOf('.') + 1) : rt;
            if (!bare.equals(table.toLowerCase())) continue;
            if (schema != null && !schema.isBlank() && rt.contains(".")
                && !rt.equals(schema.toLowerCase() + "." + table.toLowerCase())) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    private static Map<String, Vocabulary> parse() {
        Map<String, Vocabulary> out = new LinkedHashMap<>();
        for (Declaration d : declarations()) {
            Set<String> values = new LinkedHashSet<>();
            Matcher lit = LITERAL.matcher(d.body());
            while (lit.find()) values.add(lit.group(1));
            if (values.isEmpty()) continue;      // a range or a null-check, not a vocabulary

            Set<String> columns = new LinkedHashSet<>();
            Matcher col = COLUMN_REF.matcher(ANY_LITERAL.matcher(d.body()).replaceAll("''"));
            while (col.find()) columns.add(col.group(1).toLowerCase());
            if (columns.size() != 1) continue;   // cross-column rule — see trap 2

            out.put(d.constraint().toLowerCase(), new Vocabulary(
                d.constraint(), d.table(), columns.iterator().next(), values));
        }
        if (out.size() < 40) {
            throw new AssertionError(
                "anti-vacuity: the migration parse found only " + out.size() + " CHECK vocabularies "
                + "in " + migrationFiles().size() + " files. The platform has hundreds; a parser "
                + "that has stopped matching makes every test built on it pass while comparing "
                + "nothing.");
        }
        return out;
    }

    private static List<Range> parseRanges() {
        List<Range> out = new ArrayList<>();
        for (Declaration d : declarations()) {
            String body = ANY_LITERAL.matcher(d.body()).replaceAll("''");
            if (LITERAL.matcher(d.body()).find()) continue;   // a vocabulary, not a range

            Set<String> columns = new LinkedHashSet<>();
            Matcher col = NUM_COLUMN_REF.matcher(body);
            while (col.find()) columns.add(col.group(1).toLowerCase());
            if (columns.size() != 1) continue;                // cross-column rule — see trap 2
            String column = columns.iterator().next();

            BigDecimal min = null, max = null;
            boolean minInc = true, maxInc = true, parsed = false;

            Matcher bt = BETWEEN.matcher(body);
            if (bt.find() && bt.group(1).equalsIgnoreCase(column)) {
                min = new BigDecimal(bt.group(2));
                max = new BigDecimal(bt.group(3));
                parsed = true;
            } else {
                Matcher cmp = NUM_COMPARISON.matcher(body);
                while (cmp.find()) {
                    if (!cmp.group(1).equalsIgnoreCase(column)) continue;
                    BigDecimal v = new BigDecimal(cmp.group(3));
                    switch (cmp.group(2)) {
                        case ">=" -> { min = v; minInc = true;  parsed = true; }
                        case ">"  -> { min = v; minInc = false; parsed = true; }
                        case "<=" -> { max = v; maxInc = true;  parsed = true; }
                        case "<"  -> { max = v; maxInc = false; parsed = true; }
                        default -> { /* `= n` is a pinned value, not an interval — leave unparsed */ }
                    }
                }
            }
            if (min == null && max == null) continue;          // a NULL-check or an unreadable rule
            out.add(new Range(d.constraint(), d.table(), column, min, minInc, max, maxInc, parsed));
        }
        if (out.size() < 20) {
            throw new AssertionError(
                "anti-vacuity: the migration parse found only " + out.size() + " single-column "
                + "numeric CHECK ranges. Measured 44 in dev at F2-241; a parser that has stopped "
                + "matching makes every bound built on it admit everything.");
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
