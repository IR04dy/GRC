package com.tss.grc.config;

import com.tss.grc.support.MigrationVocabularies;
import com.tss.grc.support.MigrationVocabularies.ColumnSpec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-463 — <b>{@code @Column(length)} is inert for DDL under {@code ddl-auto=none}, so nothing made
 * it agree with the column; and two gates read it as the column's real width.</b>
 *
 * <h2>The defect</h2>
 *
 * <p>Eighteen entities declared a width the column does not have — fifteen wider, three narrower —
 * and the migration chain, the dev database and the deployed database <b>agree with each other on
 * all eighteen</b>. The entity is the sole outlier every time:
 *
 * <pre>
 *   scm.vendors.category               declared 100  real  50
 *   bcm.bcm_plans.plan_type            declared 100  real  30
 *   risk.risks.risk_level              declared  20  real   8
 *   scm.vendors.service_type           declared 100  real 200
 * </pre>
 *
 * <p>Under {@code ddl-auto=none} the number creates nothing and validates nothing, so no request
 * behaves differently for it — which is exactly why it could drift. What reads it is
 * {@code RequestSizeCapMirrorTest}: {@code everyEnabledSizeCapEqualsTheColumnItGuards} compares a
 * {@code @Size(max)} against {@code col.length()}, and F2-428's {@code everyDeclaredFormCapFitsThe
 * ColumnItGuards} / {@code noFormCapExceedsTheColumnItUniquelyResolvesTo} compare a frontend
 * {@code maxLength} against it. 🔑 <b>So a cap set to the declaration passes the gate whose whole
 * purpose is preventing an overflow, and still overflows</b> — SQL Server 2628/8152, which F2-432
 * turned into a 400 naming the field, so the failure is legible and the cap is still wrong.
 *
 * <p>The three <i>narrower</i> ones fail the other way, and that direction matters more than it
 * looks: a form legitimately capping {@code scm.vendors.service_type} at the column's real 200
 * would be <b>refused</b> by the mirror gate against a declared 100 — a gate failing on correct
 * code, the one failure a gate must never have (F2-214). Fixing only the wider direction would
 * leave the other looking checked (F2-215).
 *
 * <p>⚠ <b>Latent, and saying which matters</b> (F2-283): all eighteen columns are rendered by a
 * closed dropdown today, so no cap is paired to a fictional width and nothing is currently
 * refused or overflowing. This is a latent-hazard closure — what it buys is that the number stops
 * being a fiction the two mirror gates can certify against.
 *
 * <h2>Why the fix is the entity and not the column</h2>
 *
 * <p>Three artefacts agree against it, and widening eighteen columns would be a migration over two
 * live databases (F2-183) to accommodate a number nothing enforces. Correcting the declaration is
 * provably behaviour-neutral: {@code application-dev.yml} and {@code application-prod.yml} both set
 * {@code ddl-auto: none}, so Hibernate neither creates nor validates the column from it.
 *
 * <h2>What this asserts</h2>
 *
 * <ol>
 *   <li><b>Hard zero</b> — a {@code @Column(length = n)} with {@code n != 255} on a mapped String
 *       field equals the width the migration chain leaves on that column.</li>
 *   <li><b>Census</b> — the {@code length == 255} population, which this instrument
 *       <b>cannot judge</b>, measured and named rather than elided.</li>
 *   <li><b>Self-test</b> — the width parser fires on the shapes it claims and on none it must
 *       not.</li>
 *   <li><b>Floors</b> — the entity walk and the chain walk both resolved something, and nothing
 *       was skipped silently.</li>
 * </ol>
 *
 * <p>⚠⚠ <b>{@code length()} answers 255 whether or not the author wrote it</b>, so reflection alone
 * cannot tell a deliberate 255 from an absent one, and a gate that demanded equality over the whole
 * population would fail on 1,683 fields nobody has declared anything about. 255 is therefore the
 * one value this gate is silent on, and leg 2 says so with a number — a hard zero over a population
 * the instrument cannot see is the most reassuring possible artefact (F2-205). Measured, <b>55 of
 * those 1,683 sit over a column narrower than 255</b>; every one is a field whose author never
 * declared a width, so the fix there is to write one, which is a sweep rather than a correction.
 */
@DisplayName("F2-463 · a declared column width is the column's width")
class ColumnWidthDeclarationContractTest {

    /** JPA's own default, and therefore the one width a declaration cannot be distinguished from. */
    private static final int JPA_DEFAULT_LENGTH = 255;

    private record Declared(String type, String field, String table, String column, int length) {
        String where() {
            return table + "." + column + "  (" + type + "#" + field + ")";
        }
    }

    private static List<Declared> named;          // length != 255, chain knows the column
    private static List<Declared> namedUnknown;   // length != 255, chain does not know the column
    private static List<Declared> silent;         // length == 255 -- indistinguishable from absent
    private static int entities;
    private static int unloadable;
    private static int mappedStringFields;

    private static synchronized void measure() {
        if (named != null) {
            return;
        }
        named = new ArrayList<>();
        namedUnknown = new ArrayList<>();
        silent = new ArrayList<>();
        Map<String, Map<String, ColumnSpec>> chain = MigrationVocabularies.columnSpecs();

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> type;
            try {
                type = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                unloadable++;
                continue;
            }
            if (!ships(type)) {
                continue;                       // a synthetic fixture another gate builds (F2-241)
            }
            Table table = type.getAnnotation(Table.class);
            if (table == null || table.name().isBlank()) {
                continue;
            }
            entities++;
            // application.yml sets hibernate.default_schema: platform, so an @Table with no schema
            // resolves there and NOT to the connection's dbo (F2-447).
            String key = ((table.schema().isBlank() ? "platform" : table.schema())
                          + "." + table.name()).toLowerCase();
            Map<String, ColumnSpec> cols = chain.get(key);

            for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()
                        || f.isAnnotationPresent(Transient.class)
                        || f.getType() != String.class) {
                        continue;
                    }
                    Column col = f.getAnnotation(Column.class);
                    if (col == null) {
                        continue;
                    }
                    mappedStringFields++;
                    String colName = (col.name().isBlank() ? camelToSnake(f.getName()) : col.name())
                            .toLowerCase();
                    Declared d = new Declared(type.getSimpleName(), f.getName(), key, colName,
                            col.length());
                    if (col.length() == JPA_DEFAULT_LENGTH) {
                        silent.add(d);
                    } else if (cols == null || cols.get(colName) == null
                               || cols.get(colName).width() == null) {
                        namedUnknown.add(d);
                    } else {
                        named.add(d);
                    }
                }
            }
        }
    }

    /** Exclude anything a test built: a fixture entity is deliberately malformed (F2-241). */
    private static boolean ships(Class<?> type) {
        var src = type.getProtectionDomain().getCodeSource();
        return src != null && src.getLocation().toString().replace('\\', '/').contains("/classes/");
    }

    private static String camelToSnake(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                b.append('_').append(Character.toLowerCase(ch));
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    private static Integer chainWidth(Declared d) {
        return MigrationVocabularies.columnSpecs().get(d.table()).get(d.column()).width();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  1 · the invariant
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a declared @Column(length) is the width the migration chain gives that column")
    void aDeclaredWidthIsTheColumnsWidth() {
        measure();
        List<String> wrong = new ArrayList<>();
        for (Declared d : named) {
            int real = chainWidth(d);
            if (real == d.length()) {
                continue;
            }
            wrong.add(String.format("%s declares %d, the chain gives %s%s", d.where(), d.length(),
                    real == -1 ? "MAX" : String.valueOf(real),
                    real != -1 && d.length() > real
                            ? "  -- a cap set to the declaration passes the mirror gate and still overflows"
                            : "  -- the mirror gate would refuse a cap the column permits"));
        }
        assertThat(wrong)
                .as("""
                    A @Column(length) that is not the column's width is a fiction, and \
                    RequestSizeCapMirrorTest reads it as the truth: a @Size cap or a frontend \
                    maxLength set to it passes the gate whose purpose is preventing an overflow. \
                    Take the width from the migration that creates the column -- it is inert for \
                    DDL here (ddl-auto=none), so correcting it changes no behaviour: %s""", wrong)
                .isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  2 · the population this instrument cannot judge, measured rather than elided
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the 255 population is measured, because reflection cannot tell it from an absent declaration")
    void theAmbiguousPopulationIsMeasured() {
        measure();
        long over = silent.stream()
                .filter(d -> {
                    Map<String, ColumnSpec> cols = MigrationVocabularies.columnSpecs().get(d.table());
                    if (cols == null) return false;
                    ColumnSpec s = cols.get(d.column());
                    return s != null && s.width() != null && s.width() != -1
                           && s.width() < JPA_DEFAULT_LENGTH;
                })
                .count();

        // Exact, not a ceiling: a change here is a field gaining or losing a declaration, and the
        // next reader should have to look rather than watch a tolerance absorb it (F2-204).
        assertThat(over)
                .as("""
                    Fields whose @Column names no length -- so JPA's 255 stands -- over a column \
                    narrower than 255. This gate is silent on them BY CONSTRUCTION: length() \
                    answers 255 either way, so demanding equality would fail on every field nobody \
                    declared anything about. The fix for one of these is to write the width, which \
                    is a sweep rather than a correction (F2-188).""")
                .isEqualTo(58L);

        assertThat(silent).as("the ambiguous population itself").hasSize(1690);
        assertThat(namedUnknown)
                .as("""
                    A declared length whose column the chain does not name -- a column created only \
                    by the Python bootstrap (F2-255), or an implicit column name this walk resolved \
                    differently from Hibernate. Held exactly so 'unresolved' cannot read as 'clean' \
                    (F2-206): %s""",
                    namedUnknown.stream().map(Declared::where).sorted().toList())
                .isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  3 · the detector, over synthetic input, in both directions
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the entity-to-chain lookup resolves a width for every declaration form")
    void theWidthParserFiresOnTheShapesItClaims() {
        Map<String, Map<String, ColumnSpec>> chain = MigrationVocabularies.columnSpecs();

        // must-read: a CREATE TABLE body, bracket-quoted and bare, and an ALTER that widens later.
        assertThat(chain.get("scm.vendors").get("category").width())
                .as("a bare CREATE TABLE column declaration").isEqualTo(50);
        assertThat(chain.get("risk.risks").get("risk_level").width())
                .as("a bracket-quoted CREATE TABLE column declaration").isEqualTo(8);
        assertThat(chain.get("aml.sanctions_list_entry").get("severity").width())
                .as("VARCHAR, not NVARCHAR -- the width is a character count either way").isEqualTo(30);
        assertThat(chain.get("cyber.cyber_frameworks").get("name").width())
                .as("a later ALTER COLUMN wins over the CREATE TABLE that preceded it (V390)")
                .isEqualTo(255);
        assertThat(chain.get("cyber.cyber_frameworks").get("description_en").width())
                .as("MAX reads as -1 rather than as a number").isEqualTo(-1);

        // must-NOT-read: a type with no parenthesised size has no width to compare against.
        assertThat(chain.get("risk.risks").get("id").width())
                .as("BIGINT carries no width").isNull();
        assertThat(chain.get("risk.risks").get("organisation_id").width())
                .as("a non-text column carries no width").isNull();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  4 · anti-vacuity
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("both walks resolved a real population, and nothing was skipped in silence")
    void thePopulationIsMeasured() {
        measure();
        // Above the largest WRONG answer, not above zero (F2-215). A walk that resolves nothing
        // makes leg 1 pass over an empty list, which is what a rotted detector looks like.
        assertThat(entities).as("shipped @Entity types with a @Table(name)")
                .isGreaterThan(400);
        assertThat(mappedStringFields).as("mapped String fields carrying a @Column")
                .isGreaterThan(3_500);
        assertThat(named).as("declarations this gate can judge").hasSizeGreaterThan(2_000);
        assertThat(MigrationVocabularies.columnSpecs()).as("tables the chain declares")
                .hasSizeGreaterThan(400);
        assertThat(unloadable)
                .as("an @Entity that will not load is a field set this gate never sees, and a "
                    + "smaller population reads exactly like a cleaner tree")
                .isZero();

    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  5 · the claim that makes the fix inert
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("no runnable profile lets Hibernate act on a declared width")
    void theDeclarationIsInertForSchemaHandling() throws Exception {
        // Correcting eighteen numbers is only behaviour-neutral while nothing generates or
        // validates DDL from them. Both runnable profiles say so; assert it rather than repeat it.
        for (String profile : List.of("application-dev.yml", "application-prod.yml")) {
            String yml = java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/resources", profile));
            assertThat(yml.replaceAll("\\s+", " "))
                    .as("%s must keep ddl-auto at none, or a declared width starts creating or "
                        + "validating a column and this gate's premise is gone", profile)
                    .containsPattern("ddl-auto: (none|\\$\\{HIBERNATE_DDL_AUTO:none\\})");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  6 · the boundary: what the fix must NOT have moved
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the corrected declarations are the column's width in both directions")
    void theCorrectionsTookTheColumnsOwnWidth() {
        measure();
        // Two from each direction, pinned by value. A later 'tidy-up' that rounds one of these
        // back up to a friendlier number would pass leg 1 only by also editing the migration.
        Map<String, Integer> pinned = Map.of(
                "scm.vendors.category", 50,          // was 100 -- the widest fiction
                "risk.risks.risk_level", 8,          // was  20 -- CRITICAL fits with nothing spare
                "scm.vendors.service_type", 200,     // was 100 -- the mirror gate would refuse 200
                "aml.sanctions_list_entry.severity", 30);
        for (var e : pinned.entrySet()) {
            String[] parts = e.getKey().split("\\.");
            Declared d = named.stream()
                    .filter(x -> x.table().equals(parts[0] + "." + parts[1])
                                 && x.column().equals(parts[2]))
                    .findFirst().orElseThrow(() ->
                            new AssertionError("no declaration found for " + e.getKey()
                                               + " -- the landing names a field that is gone"));
            assertThat(d.length()).as("%s", e.getKey()).isEqualTo(e.getValue());
        }
    }

    /** Publish the population, so a reader can see what was judged and what was not. */
    @Test
    @DisplayName("publish the width census")
    void publish() throws Exception {
        measure();
        StringBuilder sb = new StringBuilder();
        sb.append("F2-463 declared column widths vs the migration chain\n")
          .append("shipped entities ").append(entities)
          .append(" | mapped String fields ").append(mappedStringFields)
          .append(" | judged ").append(named.size())
          .append(" | silent-255 ").append(silent.size()).append("\n\n");
        var out = new TreeSet<String>();
        for (Declared d : named) {
            out.add(String.format("%-58s declared %-5d chain %s", d.where(), d.length(),
                    chainWidth(d) == -1 ? "MAX" : String.valueOf(chainWidth(d))));
        }
        out.forEach(l -> sb.append(l).append('\n'));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/column-widths.txt"),
                sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(java.nio.file.Files.readString(
                java.nio.file.Path.of("target/column-widths.txt"),
                java.nio.charset.StandardCharsets.UTF_8))
                .as("the artefact on disk is the one this run wrote, not the previous run's (F2-265)")
                .isEqualTo(sb.toString());
    }
}
