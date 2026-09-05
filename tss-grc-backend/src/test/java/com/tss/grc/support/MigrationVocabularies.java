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

    /**
     * Package-private since F2-461: the platform's one list of migration files, and that
     * gate is its second reader. A second walk would be free to disagree about which files
     * are migrations, which is the shape of the finding it was written for (F2-190).
     */
    public static List<Path> migrationFiles() {
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

    /* ══════════════════════════════════════════════════════════════════════════════════════
       F2-433 — column NULLABILITY and UNIQUE keys, as the whole chain leaves them.

       Why these sit here rather than in a private parser inside the gate that needs them: this
       class's own trap 3 — <b>later versions win</b> — is LOAD-BEARING for the question they
       answer.  V25 declares {@code CONSTRAINT uk_eng_code UNIQUE (organisation_id,
       engagement_code)} and V398 drops it; a parser that read only the first declaration would
       report that constraint as present forever, and the gate over it would fail on a tree that
       has already been fixed.  A copy that predates the discovery does not know about it.

       ⚠ Two traps of their own, both measured:
       <ol>
         <li><b>The filter clause is on the NEXT LINE.</b> The house style writes
             {@code CREATE UNIQUE INDEX x ON t (a, b)} and then {@code WHERE b IS NOT NULL} on its
             own line below.  A newline-bounded tail cannot see it, and the first census run
             reported <b>17</b> unfiltered keys over nullable columns where <b>6</b> exist.  It
             OVER-reports, which fails loudly — the opposite direction from F2-198's stripper bug,
             and the reason it was caught at all.</li>
         <li><b>Strip the comments.</b> V398's own header quotes the constraint it retires, so a
             raw scan reads the epitaph as the corpse (F2-193) and reports the defect as live on a
             fixed tree.</li>
       </ol>
       ══════════════════════════════════════════════════════════════════════════════════════ */

    /** A UNIQUE key (constraint- or index-form) that survives the whole migration chain. */
    public record UniqueKey(String name, String table, List<String> keyColumns, boolean filtered) {}

    private static final String SQL_TYPE =
        "(?:NVARCHAR|VARCHAR|NCHAR|CHAR|BIGINT|INT|SMALLINT|TINYINT|BIT|DATETIME2|DATETIME|DATE"
        + "|TIME|DECIMAL|NUMERIC|FLOAT|REAL|UNIQUEIDENTIFIER|ROWVERSION|TIMESTAMP|MONEY"
        + "|VARBINARY|XML)";

    private static final Pattern COMMENT = Pattern.compile("--[^\\n]*|/\\*[\\s\\S]*?\\*/");
    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE\\s+TABLE\\s+(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_DECL = Pattern.compile(
        "^\\s*\\[?(\\w+)\\]?\\s+" + SQL_TYPE + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_A_COLUMN = Pattern.compile(
        "^\\s*(CONSTRAINT|PRIMARY|UNIQUE|FOREIGN|CHECK|INDEX)\\b", Pattern.CASE_INSENSITIVE);
    /**
     * The head of an {@code ALTER TABLE ... ADD} clause. The columns are parsed out of the clause
     * BODY rather than by this pattern, because {@code ADD a T, b T, c T;} is one statement adding
     * three columns — a pattern that captures only the first (and whose tail stops at the newline
     * the house style puts between them) silently drops the rest, and a column missing from this
     * map is a column every reader of it is blind to. F2-463 found eight that way, including
     * {@code platform.audit_log.prev_hash} and {@code cyber.cyber_frameworks.framework_code}.
     */
    private static final Pattern ALTER_ADD_HEAD = Pattern.compile(
        "ALTER\\s+TABLE\\s+(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?\\s+ADD\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_COLUMN = Pattern.compile(
        "ALTER\\s+TABLE\\s+(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?\\s+ALTER\\s+COLUMN\\s+"
        + "\\[?(\\w+)\\]?\\s+" + SQL_TYPE + "\\b([^;\\n]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INBODY_UNIQUE = Pattern.compile(
        "CONSTRAINT\\s+\\[?(\\w+)\\]?\\s+UNIQUE\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_ADD_UNIQUE = Pattern.compile(
        "ALTER\\s+TABLE\\s+(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?\\s+ADD\\s+CONSTRAINT\\s+"
        + "\\[?(\\w+)\\]?\\s+UNIQUE\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_UNIQUE_INDEX = Pattern.compile(
        "CREATE\\s+UNIQUE\\s+(?:NONCLUSTERED\\s+|CLUSTERED\\s+)?INDEX\\s+\\[?(\\w+)\\]?\\s+ON\\s+"
        + "(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_CONSTRAINT = Pattern.compile(
        "ALTER\\s+TABLE\\s+(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?\\s+DROP\\s+CONSTRAINT\\s+"
        + "(?:IF\\s+EXISTS\\s+)?\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_UNIQUE_INDEX = Pattern.compile(
        "DROP\\s+INDEX\\s+(?:IF\\s+EXISTS\\s+)?\\[?(\\w+)\\]?\\s+ON\\s+"
        + "(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);

    private static Map<String, Map<String, ColumnSpec>> specCache;
    private static Map<String, Map<String, Boolean>> columnCache;
    private static List<UniqueKey> uniqueKeyCache;

    record Ev(int pos, boolean add, String key, UniqueKey value) {}

    /**
     * What a declaration says about one column: whether it is nullable, and — for a text type —
     * how wide it is.
     *
     * @param width the number in {@code NVARCHAR(n)}, {@code -1} for {@code MAX}, and {@code null}
     *              where the declaration carries no parenthesised size at all (every non-text type,
     *              and a bare {@code VARCHAR}).
     */
    public record ColumnSpec(boolean nullable, Integer width) {}

    /** The parenthesised size that follows a type name, if the declaration gives one. */
    private static final Pattern WIDTH = Pattern.compile(
        "\\s*\\(\\s*(MAX|\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private static Integer widthOf(String afterType) {
        if (afterType == null) return null;
        Matcher m = WIDTH.matcher(afterType);
        if (!m.lookingAt()) return null;
        return m.group(1).equalsIgnoreCase("MAX") ? -1 : Integer.parseInt(m.group(1));
    }

    /**
     * {@code schema.table -> column -> what the chain declares}, replayed in version order.
     *
     * <p>This is the one walk; {@link #columns()} is a second reader over its result rather than a
     * second walk, because two walks would be free to disagree about which files are packaged and
     * which declaration wins (F2-190).
     */
    public static synchronized Map<String, Map<String, ColumnSpec>> columnSpecs() {
        if (specCache == null) {
            Map<String, Map<String, ColumnSpec>> out = new LinkedHashMap<>();
            for (Path f : migrationFiles()) {
                String src = stripComments(read(f));
                Matcher ct = CREATE_TABLE.matcher(src);
                while (ct.find()) {
                    String body = balanced(src, ct.end() - 1);
                    if (body == null) continue;
                    Map<String, ColumnSpec> cols = out.computeIfAbsent(
                        qualify(ct.group(1), ct.group(2)), k -> new LinkedHashMap<>());
                    for (String line : body.split("\n")) {
                        if (NOT_A_COLUMN.matcher(line).find()) continue;
                        Matcher cd = COLUMN_DECL.matcher(line);
                        if (cd.find()) {
                            cols.put(cd.group(1).toLowerCase(), new ColumnSpec(
                                isNullable(line), widthOf(line.substring(cd.end()))));
                        }
                    }
                }
                Matcher add = ALTER_ADD_HEAD.matcher(src);
                while (add.find()) {
                    Map<String, ColumnSpec> cols = out.computeIfAbsent(
                        qualify(add.group(1), add.group(2)), k -> new LinkedHashMap<>());
                    for (String item : addedColumns(src, add.end())) {
                        if (NOT_A_COLUMN.matcher(item).find()) continue;
                        Matcher cd = COLUMN_DECL.matcher(item);
                        if (cd.find()) {
                            cols.put(cd.group(1).toLowerCase(), new ColumnSpec(
                                isNullable(item), widthOf(item.substring(cd.end()))));
                        }
                    }
                }
                Matcher alt = ALTER_COLUMN.matcher(src);
                while (alt.find()) {
                    out.computeIfAbsent(qualify(alt.group(1), alt.group(2)),
                            k -> new LinkedHashMap<>())
                        .put(alt.group(3).toLowerCase(),
                             new ColumnSpec(isNullable(alt.group(4)), widthOf(alt.group(4))));
                }
            }
            specCache = out;
        }
        return specCache;
    }

    /**
     * {@code schema.table -> column -> is the column NULLABLE}, over the whole chain.
     *
     * <p>Absent {@code NOT NULL} and absent {@code PRIMARY KEY} means nullable, which is SQL's own
     * default. {@code ALTER TABLE ... ADD} and {@code ALTER COLUMN} are replayed in version order,
     * so a column later tightened to {@code NOT NULL} reads as NOT NULL.
     */
    public static synchronized Map<String, Map<String, Boolean>> columns() {
        if (columnCache == null) {
            Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
            columnSpecs().forEach((table, cols) -> {
                Map<String, Boolean> nullable = new LinkedHashMap<>();
                cols.forEach((c, spec) -> nullable.put(c, spec.nullable()));
                out.put(table, nullable);
            });
            columnCache = out;
        }
        return columnCache;
    }

    /** Every UNIQUE key the chain leaves in place, constraint- and index-form alike. */
    public static synchronized List<UniqueKey> uniqueKeys() {
        if (uniqueKeyCache == null) {
            Map<String, UniqueKey> live = new LinkedHashMap<>();
            for (Path f : migrationFiles()) {
                for (Ev e : eventsIn(read(f))) {
                    if (e.add()) live.put(e.key(), e.value());
                    else live.remove(e.key());
                }
            }
            uniqueKeyCache = List.copyOf(live.values());
        }
        return uniqueKeyCache;
    }

    /**
     * Every add/drop of a UNIQUE key in one migration's text, in TEXT ORDER.
     *
     * <p>Package-private so the contract test can drive it over a synthetic corpus: a census that
     * can only be run against the real tree cannot be shown to fire, and a detector that has
     * stopped matching reads exactly like a clean tree (F2-235).
     */
    static List<Ev> eventsIn(String raw) {
        String src = stripComments(raw.replace("\r\n", "\n"));
        List<Ev> evs = new ArrayList<>();
            Matcher ct = CREATE_TABLE.matcher(src);
            while (ct.find()) {
                String body = balanced(src, ct.end() - 1);
                if (body == null) continue;
                String table = qualify(ct.group(1), ct.group(2));
                Matcher iu = INBODY_UNIQUE.matcher(body);
                while (iu.find()) {
                    evs.add(add(ct.end() + iu.start(), table, iu.group(1), iu.group(2), false));
                }
            }
            Matcher au = ALTER_ADD_UNIQUE.matcher(src);
            while (au.find()) {
                evs.add(add(au.start(), qualify(au.group(1), au.group(2)),
                    au.group(3), au.group(4), false));
            }
            Matcher ui = CREATE_UNIQUE_INDEX.matcher(src);
            while (ui.find()) {
                String cols = balanced(src, ui.end() - 1);
                if (cols == null) continue;
                evs.add(add(ui.start(), qualify(ui.group(2), ui.group(3)), ui.group(1),
                    cols.substring(1, cols.length() - 1),
                    hasFilter(src, ui.end() - 1 + cols.length())));
            }
            Matcher dc = DROP_CONSTRAINT.matcher(src);
            while (dc.find()) {
                evs.add(new Ev(dc.start(), false,
                    qualify(dc.group(1), dc.group(2)) + "." + dc.group(3).toLowerCase(), null));
            }
            Matcher di = DROP_UNIQUE_INDEX.matcher(src);
            while (di.find()) {
                evs.add(new Ev(di.start(), false,
                    qualify(di.group(2), di.group(3)) + "." + di.group(1).toLowerCase(), null));
            }

        evs.sort(Comparator.comparingInt(Ev::pos));
        return evs;
    }

    private static Ev add(int pos, String table, String name, String cols, boolean filtered) {
        List<String> key = new ArrayList<>();
        for (String c : cols.split(",")) {
            key.add(c.trim().replace("[", "").replace("]", "")
                .replaceAll("(?i)\\s+(asc|desc)$", "").toLowerCase());
        }
        return new Ev(pos, true, table + "." + name.toLowerCase(),
            new UniqueKey(name, table, List.copyOf(key), filtered));
    }

    /**
     * Is there a {@code WHERE} between the end of the key list and the end of the statement?
     *
     * <p>⚠ This span MUST be allowed to cross newlines — see trap 1 above.
     */
    private static boolean hasFilter(String src, int afterKeyList) {
        int stop = src.length();
        Matcher semi = Pattern.compile(";|\\bGO\\b", Pattern.CASE_INSENSITIVE)
            .matcher(src).region(afterKeyList, src.length());
        if (semi.find()) stop = semi.start();
        return Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE)
            .matcher(src.substring(afterKeyList, stop)).find();
    }

    private static boolean isNullable(String decl) {
        String up = decl == null ? "" : decl.toUpperCase();
        return !up.contains("NOT NULL") && !up.contains("PRIMARY KEY");
    }

    private static String qualify(String schema, String table) {
        return ((schema == null ? "dbo" : schema) + "." + table).toLowerCase();
    }

    /** Blank every comment, preserving newlines so line structure survives (F2-242). */
    public static String stripComments(String src) {
        Matcher m = COMMENT.matcher(src);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out,
                Matcher.quoteReplacement(m.group().replaceAll("[^\\n]", " ")));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Package-private since F2-461. Shared for the same reason as the walk: this normalises
     * CRLF to LF, and a second reader that did not would give a comment-stripping scan a
     * different answer about where a line ends.
     */
    public static String read(Path f) {
        try {
            return Files.readString(f).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + f, e);
        }
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

    /**
     * The comma-separated items of one {@code ALTER TABLE ... ADD} clause, starting at {@code from}
     * (just past the {@code ADD}).
     *
     * <p>Split at paren depth 0, so {@code DECIMAL(18,2)} stays one item, and bounded by the first
     * depth-0 {@code ;} — or, where a migration ends the batch without one, by the next {@code GO}.
     * Comments are already blanked by the caller, so a trailing {@code -- NCA / SAMA} cannot be
     * mistaken for a column.
     */
    private static List<String> addedColumns(String src, int from) {
        int depth = 0;
        int end = src.length();
        for (int i = from; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ';' && depth == 0) { end = i; break; }
            else if (depth == 0 && (c == 'G' || c == 'g') && i + 1 < src.length()
                     && (src.charAt(i + 1) == 'O' || src.charAt(i + 1) == 'o')
                     && (i == 0 || src.charAt(i - 1) == '\n')
                     && (i + 2 >= src.length() || !Character.isLetterOrDigit(src.charAt(i + 2)))) {
                end = i;
                break;
            }
        }
        List<String> items = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        depth = 0;
        for (int i = from; i < end; i++) {
            char c = src.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                items.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        items.add(cur.toString());
        return items;
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
