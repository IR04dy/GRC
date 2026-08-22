package com.tss.grc.security;

import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An {@code @Entity} that does not map its table's {@code organisation_id} is outside the
 * platform's tenant machinery <b>by construction</b>, in three directions at once.
 *
 * <h2>F2-279 — the defect this closes</h2>
 * <p>{@code bcm.bia_records} was created by {@code V1__core_schemas.sql} with 29 columns,
 * including {@code organisation_id BIGINT NOT NULL DEFAULT ((1))}. {@code BiaRecord} mapped
 * <b>eleven</b> of them. Because Hibernate never names an unmapped column in its INSERT:
 * <ul>
 *   <li><b>the database default decided every row's tenant</b>, not {@code TenantContext};
 *   <li>no query could scope on it, so a planted row whose own {@code organisation_id} said
 *       2 was <b>served to an org-1 caller</b> ({@code listBia} scoped by the parent process
 *       link instead); and
 *   <li>{@code TenantWriteGuardInterceptor} — the platform-wide backstop that
 *       {@code application-prod.yml} calls the guarantee "a single missed per-service guard
 *       cannot silently write across tenants" — bails at {@code orgIdx < 0} with the comment
 *       "Entity doesn't have an organisationId — out of scope".
 * </ul>
 * <p>🔑 <b>A guard's population is its question.</b> F2-216 found and closed one narrowing in
 * that interceptor — it exempts a row whose tenant is {@code null} — and the narrowing one
 * level up, <em>the entity has no such property at all</em>, was never counted. This class
 * counts it. The same shape has now been found at a filename, a three-entry allow-list, a
 * module name, an {@code extends} clause, a field-name list inside a fix, a claim shape in a
 * gate, a naming convention, and the absence of a suffix; here it is <b>a property the entity
 * declines to declare</b>.
 *
 * <h2>Why this VERIFIES rather than asserts</h2>
 * <p>The obvious instrument would be a named map of reasons ("this table has no tenant
 * column because…"), and F2-188 measured what that produces: five claims, five false, and the
 * fluency was what made them look verified. It is not needed here, because the question is
 * answerable from the migration corpus — the same hermetic source
 * {@code MigrationVocabularies} reads for CHECK constraints. Measured 2026-08-22: of 432
 * shipped entities <b>35 declare no {@code organisationId}</b>, all 35 resolve to a
 * {@code CREATE TABLE}, <b>0 are unresolved</b>, and exactly one had the column. So there is
 * no exemption map at all.
 * <p>⚠ An unresolved table must FAIL, never be skipped: "unresolved" reads identically to
 * "clean" (F2-206), and this gate's whole value is that a new entity omitting the column
 * cannot pass quietly.
 */
class TenantColumnMappingContractTest {

    private static final Path MIGRATIONS =
        Path.of("src", "main", "resources", "db", "migration", "sqlserver");

    private static final String ORG_PROP = "organisationId";
    private static final String ORG_COL = "organisation_id";

    /**
     * Anti-vacuity floors. Each sits above the largest WRONG answer rather than above zero
     * (F2-215): a scanner that stops resolving entities, or a corpus root that stops
     * resolving, must fail rather than report a clean tree.
     */
    private static final int MIN_ENTITIES = 400;      // measured 432
    private static final int MIN_MIGRATIONS = 350;    // measured 379
    private static final int MIN_NO_ORG = 25;         // measured 35 — see noOrgEntities()

    /**
     * Entities that map {@code isDeleted} and carry no {@code @Where} to honour it — strictly
     * worse than not mapping it, because the column then reads as handled while every query
     * still returns deleted rows. Decrease-only; set from the gate's own output rather than
     * predicted, because a baseline lifted from a measurement of something else is worse than
     * no baseline: it looks measured (F2-195).
     * <p>Measured 2026-08-22 = <b>9</b>: {@code compliance.assessments},
     * {@code evidence.evidence_links}, {@code risk.risk_categories} and six
     * {@code platform.*} integration logs. It was <b>10</b> before this slice — {@code
     * BiaRecord} was the tenth, and its omission is what F2-279 found. Most of the nine are
     * append-only logs where the flag is vestigial, which is why this is a census and not a
     * hard zero: clearing it means reading each one.
     */
    private static final int BASELINE_MAPS_DELETED_WITHOUT_WHERE = 9;

    // ── the corpus ───────────────────────────────────────────────────────────────

    /**
     * SQL comment stripper. ⚠ ONE left-to-right alternation, never two sequential passes:
     * two passes let a {@code --} comment whose text contains a {@code /*} run to the next
     * closing delimiter and swallow real declarations, which reports a SMALLER population and
     * therefore reads exactly like a clean tree (F2-198; F2-211 corrected that the branch
     * ORDER within one alternation is a no-op, so the defect is the second pass, not the
     * order). Newlines are preserved so any line number stays honest (F2-242).
     */
    private static final Pattern SQL_COMMENT =
        Pattern.compile("--[^\\n]*|/\\*[\\s\\S]*?\\*/");

    static String stripSqlComments(String sql) {
        Matcher m = SQL_COMMENT.matcher(sql);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                m.group().replaceAll("[^\\n]", " ")));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static Map<String, String> migrationCorpus() {
        Map<String, String> corpus = new TreeMap<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql"))
                               .toList()) {
                String raw = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                corpus.put(p.getFileName().toString(), stripSqlComments(raw));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return corpus;
    }

    // ── the entities ─────────────────────────────────────────────────────────────

    private record EntityUnderTest(Class<?> type, String schema, String table) {
        String qualified() {
            return schema.isBlank() ? table : schema + "." + table;
        }
    }

    /** Whether the type or any supertype declares an {@code organisationId} field. */
    static boolean declaresOrganisationId(Class<?> type) {
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && ORG_PROP.equals(f.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<EntityUnderTest> shippedEntities() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(MappedSuperclass.class));
        List<EntityUnderTest> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> type;
            try {
                type = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("scanned but unloadable: " + bd.getBeanClassName(), e);
            }
            // ⚠ Exclude test fixtures by CODE SOURCE, never by a name ending in "Test" — a
            // name rule lets any member escape by being renamed (F2-192), and F2-241 records
            // this scanner reaching target/test-classes and a deliberate unprobeable fixture.
            var src = type.getProtectionDomain().getCodeSource();
            if (src != null && src.getLocation().toString().contains("test-classes")) continue;
            if (!type.isAnnotationPresent(Entity.class)) continue;   // concrete tables only
            if (!seen.add(type.getName())) continue;
            Table t = type.getAnnotation(Table.class);
            String schema = t == null ? "" : t.schema();
            String table = (t == null || t.name().isBlank()) ? type.getSimpleName() : t.name();
            out.add(new EntityUnderTest(type, schema, table));
        }
        out.sort((a, b) -> a.qualified().compareToIgnoreCase(b.qualified()));
        return out;
    }

    static List<EntityUnderTest> noOrgEntities() {
        return shippedEntities().stream().filter(e -> !declaresOrganisationId(e.type())).toList();
    }

    // ── resolving a table's columns from the migrations ──────────────────────────

    /** The body of {@code CREATE TABLE schema.table ( … )}, or null when there is none. */
    static String createTableBody(Map<String, String> corpus, String schema, String table) {
        Pattern ct = Pattern.compile(
            "CREATE\\s+TABLE\\s+\\[?" + Pattern.quote(schema) + "\\]?\\s*\\.\\s*\\[?"
            + Pattern.quote(table) + "\\]?\\s*\\(", Pattern.CASE_INSENSITIVE);
        for (String sql : corpus.values()) {
            Matcher m = ct.matcher(sql);
            if (!m.find()) continue;
            int open = m.end() - 1;
            int depth = 0;
            for (int i = open; i < sql.length(); i++) {
                char c = sql.charAt(i);
                if (c == '(') depth++;
                else if (c == ')' && --depth == 0) return sql.substring(open, i + 1);
            }
        }
        return null;
    }

    /** Whether any {@code ALTER TABLE schema.table ADD …} in the corpus adds the column. */
    static boolean alterAddsColumn(Map<String, String> corpus, String schema, String table,
                                   String column) {
        Pattern alter = Pattern.compile(
            "ALTER\\s+TABLE\\s+\\[?" + Pattern.quote(schema) + "\\]?\\s*\\.\\s*\\[?"
            + Pattern.quote(table) + "\\]?\\s+ADD\\s+[^;]{0,400}?\\b" + Pattern.quote(column)
            + "\\b", Pattern.CASE_INSENSITIVE);
        return corpus.values().stream().anyMatch(sql -> alter.matcher(sql).find());
    }

    static boolean tableHasColumn(Map<String, String> corpus, String schema, String table,
                                  String column) {
        String body = createTableBody(corpus, schema, table);
        if (body == null) return false;   // callers must have asserted resolution first
        return Pattern.compile("\\b" + Pattern.quote(column) + "\\b", Pattern.CASE_INSENSITIVE)
                   .matcher(body).find()
               || alterAddsColumn(corpus, schema, table, column);
    }

    // ── legs ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no entity omits an organisation_id its own table declares")
    void noEntityOmitsATenantColumnItsTableDeclares() throws IOException {
        Map<String, String> corpus = migrationCorpus();
        List<EntityUnderTest> noOrg = noOrgEntities();

        List<String> offenders = new ArrayList<>();
        List<String> clean = new ArrayList<>();
        for (EntityUnderTest e : noOrg) {
            if (tableHasColumn(corpus, e.schema(), e.table(), ORG_COL)) {
                offenders.add(e.qualified() + "  (" + e.type().getName() + ")");
            } else {
                clean.add(e.qualified());
            }
        }

        Path report = Path.of("target", "tenant-column-mapping.txt");
        Files.createDirectories(report.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("# F2-279 — entities that declare no organisationId property");
        lines.add("# entities scanned: " + shippedEntities().size());
        lines.add("# no organisationId: " + noOrg.size());
        lines.add("# table has organisation_id anyway (MUST BE EMPTY): " + offenders.size());
        offenders.forEach(o -> lines.add("OFFENDER  " + o));
        clean.forEach(c -> lines.add("ok        " + c));
        Files.write(report, lines);

        assertThat(offenders)
            .as("""
                An @Entity declares no organisationId but its table has the column.

                That is not a cosmetic omission. Hibernate never names an unmapped column in
                its INSERT, so the column's DEFAULT decides the row's tenant; no repository
                query can scope on it; and TenantWriteGuardInterceptor skips the entity
                entirely (orgIdx < 0 -> "out of scope"), so the platform-wide backstop does
                not cover this register.

                Map the field and stamp it from TenantContext on the create path, as
                BcmService#createBia does. There is deliberately no exemption map: mapping the
                column is always available, so this gate cannot fail on correct code.

                Report: target/tenant-column-mapping.txt""")
            .isEmpty();
    }

    @Test
    @DisplayName("every entity that omits organisationId resolves to a CREATE TABLE")
    void everyNoOrgEntityResolvesToACreateTable() {
        Map<String, String> corpus = migrationCorpus();
        List<String> unresolved = new ArrayList<>();
        for (EntityUnderTest e : noOrgEntities()) {
            if (e.schema().isBlank()) {
                unresolved.add(e.qualified() + " — @Table has no schema");
            } else if (createTableBody(corpus, e.schema(), e.table()) == null) {
                unresolved.add(e.qualified() + " — no CREATE TABLE in the migration corpus");
            }
        }
        assertThat(unresolved)
            .as("""
                A table this gate cannot resolve is a table it cannot clear. "Unresolved"
                reads identically to "clean" (F2-206), so an unresolvable entity must fail
                here rather than drop silently out of the population above.

                Measured 2026-08-22: 35 of 35 resolved. If you have added an entity whose
                table is created outside src/main/resources/db/migration/sqlserver, say so
                here explicitly rather than widening the pattern until it disappears.""")
            .isEmpty();
    }

    @Test
    @DisplayName("the population is measured, not accidentally empty")
    void thePopulationIsMeasured() {
        List<EntityUnderTest> all = shippedEntities();
        List<EntityUnderTest> noOrg = noOrgEntities();
        Map<String, String> corpus = migrationCorpus();

        assertThat(all.size())
            .as("entity scan collapsed — a gate over an empty population passes forever")
            .isGreaterThanOrEqualTo(MIN_ENTITIES);
        assertThat(corpus.size())
            .as("migration corpus did not resolve — every table would read as having no "
                + "organisation_id, i.e. as clean")
            .isGreaterThanOrEqualTo(MIN_MIGRATIONS);
        assertThat(noOrg.size())
            .as("""
                The no-organisationId population collapsed. If declaresOrganisationId() starts
                answering true for everything, the leg above has nothing to check and passes
                over any new offender. 35 measured 2026-08-22.""")
            .isGreaterThanOrEqualTo(MIN_NO_ORG);

        // the platform's own tenant-bearing majority must still be the majority, so a
        // reflection change that inverts the predicate fails here rather than silently
        assertThat(all.size() - noOrg.size())
            .as("entities that DO declare organisationId")
            .isGreaterThan(noOrg.size() * 5);
    }

    @Test
    @DisplayName("the detector fires on the shape it claims to detect")
    void theDetectorIsNotVacuous() {
        // A synthetic corpus: the leg's whole value is that it can tell these two apart, and
        // on a fixed tree the assertion above passes either way (F2-257: a "collect the
        // violations, assert empty" leg is vacuous once the tree is clean).
        Map<String, String> withOrg = Map.of("V999__x.sql", """
            CREATE TABLE demo.widget (
                [id] BIGINT IDENTITY(1,1) NOT NULL,
                [organisation_id] BIGINT NOT NULL DEFAULT ((1)),
                [name_en] NVARCHAR(200) NOT NULL
            );
            """);
        Map<String, String> withoutOrg = Map.of("V999__x.sql", """
            CREATE TABLE demo.widget (
                [id] BIGINT IDENTITY(1,1) NOT NULL,
                [name_en] NVARCHAR(200) NOT NULL
            );
            """);
        Map<String, String> addedLater = Map.of(
            "V998__a.sql", """
            CREATE TABLE demo.widget ([id] BIGINT NOT NULL);
            """,
            "V999__b.sql", """
            ALTER TABLE demo.widget ADD [organisation_id] BIGINT NOT NULL DEFAULT ((1));
            """);

        assertThat(tableHasColumn(withOrg, "demo", "widget", ORG_COL))
            .as("must see a tenant column declared in CREATE TABLE").isTrue();
        assertThat(tableHasColumn(withoutOrg, "demo", "widget", ORG_COL))
            .as("must NOT invent a tenant column that is absent").isFalse();
        assertThat(tableHasColumn(addedLater, "demo", "widget", ORG_COL))
            .as("must see a tenant column added by a later ALTER TABLE — otherwise an entity "
                + "written before the widening reads as correct forever").isTrue();
        assertThat(createTableBody(withoutOrg, "demo", "gadget"))
            .as("must not resolve a table that has no CREATE TABLE").isNull();

        // the column name must be matched on a word boundary: `organisation_id` is a suffix
        // of nothing, but `id` is a substring of it, so a sloppy pattern would clear nothing
        assertThat(tableHasColumn(withoutOrg, "demo", "widget", "id"))
            .as("sanity: `id` IS present, so the matcher is actually looking").isTrue();
    }

    @Test
    @DisplayName("the SQL comment stripper does not corrupt the corpus")
    void theStripperIsSound() {
        // The recorded failure mode: a `--` comment whose text contains `/*` makes a
        // two-pass stripper run to the next `*/` and delete real declarations. One
        // alternation cannot do that.
        String hostile = """
            -- see the note about /* wildcards in this column
            CREATE TABLE demo.widget (
                [organisation_id] BIGINT NOT NULL
            );
            /* a real block comment */
            ALTER TABLE demo.widget ADD [x] INT;
            """;
        String stripped = stripSqlComments(hostile);
        assertThat(stripped)
            .as("the CREATE TABLE must survive a line comment containing a block opener")
            .contains("CREATE TABLE demo.widget")
            .contains("organisation_id")
            .contains("ALTER TABLE demo.widget");
        assertThat(stripped).doesNotContain("a real block comment");
        assertThat(stripped).doesNotContain("see the note about");
        assertThat(stripped.chars().filter(c -> c == '\n').count())
            .as("newlines must be preserved so a reported line number stays honest")
            .isEqualTo(hostile.chars().filter(c -> c == '\n').count());
    }

    @Test
    @DisplayName("the interceptor still has the narrowing this gate exists to cover")
    void theInterceptorStillSkipsEntitiesWithNoTenantProperty() throws IOException {
        // ⚠ I first wrote "platform/tenant" here from memory; it is "common/tenant", and the
        // walk below silently covered for it — so the guess was dead code that would have
        // told the next reader the wrong package (F2-198's guessed-path family, and a
        // load-bearing fallback is how it hides). The direct path is kept because it names
        // where the file is; the walk stays as a real fallback that THROWS.
        Path src = Path.of("src", "main", "java", "com", "tss", "grc", "common",
                           "tenant", "TenantWriteGuardInterceptor.java");
        if (!Files.exists(src)) {
            try (Stream<Path> walk = Files.walk(Path.of("src", "main", "java"))) {
                src = walk.filter(p -> p.getFileName().toString()
                                        .equals("TenantWriteGuardInterceptor.java"))
                          .findFirst()
                          .orElseThrow(() -> new IllegalStateException(
                              "TenantWriteGuardInterceptor.java not found — if the "
                              + "platform-wide tenant backstop has been renamed or removed, "
                              + "this gate's premise needs rewriting, not deleting."));
            }
        }
        String code = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);
        assertThat(code)
            .as("""
                This gate exists because the interceptor cannot judge an entity that has no
                organisationId property — it looks the property up by name and returns early
                when the lookup fails. If that lookup is gone, re-read what the interceptor
                now covers before trusting either instrument.""")
            .contains("ORG_PROP")
            .containsPattern("indexOf\\(propertyNames,\\s*ORG_PROP\\)");
        assertThat(code)
            .as("the early return on a missing property is the narrowing being covered")
            .containsPattern("orgIdx\\s*<\\s*0");
    }

    @Test
    @DisplayName("BiaRecord, the one member this gate was written for, stays mapped")
    void biaRecordMapsItsTenantAndItsSoftDeleteFlag() {
        Class<?> bia = shippedEntities().stream()
            .filter(e -> "bcm.bia_records".equalsIgnoreCase(e.qualified()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "bcm.bia_records is no longer mapped by any entity"))
            .type();

        assertThat(declaresOrganisationId(bia))
            .as("""
                BiaRecord mapped 11 of bcm.bia_records' 29 columns, and organisation_id
                (NOT NULL DEFAULT ((1))) was one of the omissions — so the database default
                decided the tenant of every BIA the API created. This is a named pin, not a
                duplicate of the leg above: that one is derived and this one names the site
                the derivation was built from, so a reader can see it did not merely vanish
                from a population.""")
            .isTrue();

        Set<String> fields = new LinkedHashSet<>();
        for (Class<?> k = bia; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) fields.add(f.getName());
        }
        assertThat(fields)
            .as("is_deleted was the other consequential omission: with it unmapped and no "
                + "@Where, deleteBia wrote status='DELETED' and the row stayed in the list")
            .contains("isDeleted");
        assertThat(bia.isAnnotationPresent(org.hibernate.annotations.Where.class))
            .as("@Where(is_deleted = 0) is what makes the soft delete an actual delete, for "
                + "all five finders at once — 367 entities carry it")
            .isTrue();
    }

    @Test
    @DisplayName("a soft-delete flag is never mapped without a @Where to honour it")
    void anEntityMappingIsDeletedAlsoFiltersIt() {
        // Mapping is_deleted without @Where is strictly worse than not mapping it: the
        // column reads as handled while every query still returns deleted rows. Held as an
        // exactly-asserted census rather than a hard zero, because the platform genuinely
        // has entities whose deletion is filtered in each @Query instead.
        List<String> offenders = new ArrayList<>();
        for (EntityUnderTest e : shippedEntities()) {
            boolean maps = false;
            for (Class<?> k = e.type(); k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if ("isDeleted".equals(f.getName())) maps = true;
                }
            }
            if (!maps) continue;
            if (e.type().isAnnotationPresent(org.hibernate.annotations.Where.class)) continue;
            offenders.add(e.qualified() + "  (" + e.type().getSimpleName() + ")");
        }
        // This is a measurement first: publish it so the number is readable, and hold it
        // decrease-only rather than at zero, because clearing it is a sweep of its own.
        assertThat(offenders.size())
            .as("""
                Entities that map isDeleted and carry no @Where. Decrease-only. Do not raise
                it — if a new entity maps the flag, annotate it, or filter it in every @Query
                and record which. Members: %s""".formatted(offenders))
            .isLessThanOrEqualTo(BASELINE_MAPS_DELETED_WITHOUT_WHERE);
    }
}
