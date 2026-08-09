package com.tss.grc.module.risk.intake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-175 — the status a promoted risk is created with must be a value the column accepts.
 *
 * <h3>The defect</h3>
 *
 * <p>{@link RiskPromotionAdapter} passed {@code "IDENTIFIED"}, and {@code CK_risks_status} allows
 * {@code OPEN|UNDER_REVIEW|MITIGATING|ACCEPTED|CLOSED|ARCHIVED|TRANSFERRED}. So <b>promoting a
 * {@code RISK_ISSUE} intake had never once succeeded</b> — the insert died on the constraint and the
 * caller got a 409 whose message describes a foreign-key problem the table does not even have
 * (`risk.risks` conflict reported as {@code conflictKind: REFERENCE}).
 *
 * <p>⚙ Two independent reasons nobody saw it, and both are the reusable part. F2-172 live-probed
 * promotion into {@code privacy.dpia} and reasoned about the other twelve adapters from their source.
 * And the only intake kind routed to {@code RISK} is {@code RISK_ISSUE}, whose only producer was the
 * CRITICAL-drift monitor — which F2-175 then measured as <b>never having run</b>. A defect behind two
 * blind spots needs a probe of the <em>chain</em>, not of either end.
 *
 * <h3>Why this test reads the migration instead of the database</h3>
 *
 * <p>Because {@code mvn -o clean package} is hermetic and must stay that way — it needs no database and
 * no secrets. The migration SQL is the only copy of that vocabulary inside the repository, so it is the
 * only thing a unit test can honestly check against. A test asserting {@code PROMOTED_STATUS == "OPEN"}
 * would pass forever and prove nothing: it would restate the constant rather than compare it to the
 * authority. 🔑 <b>A pin has to read the other artefact.</b>
 */
class RiskPromotionStatusTest {

    private static final Path MIGRATIONS =
        Path.of("src/main/resources/db/migration/sqlserver");

    /** The last `CK_risks_status … CHECK (status IN (…))` in migration-version order wins. */
    private static Set<String> allowedStatuses() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.list(MIGRATIONS)) {
            files = walk.filter(p -> p.getFileName().toString().endsWith(".sql"))
                .sorted(java.util.Comparator.comparingInt(RiskPromotionStatusTest::version))
                .toList();
        }
        Pattern p = Pattern.compile(
            "CK_risks_status\\s+CHECK\\s*\\(\\s*status\\s+IN\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);
        // ⚠ Extract the quoted literals with a regex rather than splitting on commas and stripping
        // characters. The first version did `.replace("N", "")` to drop the N of an `N'…'` literal and
        // removed the N from every value too — 'OPEN' became 'OPE'. The anti-vacuity assertion below is
        // the only reason that surfaced as a clear failure instead of a vocabulary that silently never
        // matches, which is the same defect class this test exists to catch.
        Pattern literal = Pattern.compile("N?'([A-Z_]+)'");
        Set<String> latest = new LinkedHashSet<>();
        for (Path f : files) {
            Matcher m = p.matcher(Files.readString(f).replace("\r\n", "\n"));
            while (m.find()) {
                latest.clear();
                Matcher lit = literal.matcher(m.group(1));
                while (lit.find()) latest.add(lit.group(1));
            }
        }
        return latest;
    }

    private static int version(Path p) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(p.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    @Test
    @DisplayName("the promoted status is one CK_risks_status actually allows")
    void promotedStatusIsInTheConstrainedVocabulary() throws IOException {
        Set<String> allowed = allowedStatuses();

        // Anti-vacuity first: an empty set would make the assertion below pass on nothing, which is how
        // a regex that stops matching turns a guard into a decoration.
        assertThat(allowed).as(
            "the CK_risks_status vocabulary must be readable from the migrations — if this is empty the "
            + "constraint was renamed or reshaped and the parser below needs updating, NOT deleting")
            .contains("OPEN", "CLOSED")
            .hasSizeGreaterThanOrEqualTo(5);

        assertThat(RiskPromotionAdapter.PROMOTED_STATUS).as(
            "⚠ RiskPromotionAdapter said IDENTIFIED for as long as it existed, which CK_risks_status has "
            + "never allowed — so every promotion of a RISK_ISSUE intake failed on the constraint and "
            + "surfaced as a 409 blaming a foreign key. Allowed: %s", allowed)
            .isIn(allowed);
    }

    @Test
    @DisplayName("no promotion adapter reintroduces the retired value")
    void noAdapterUsesIdentified() throws IOException {
        Path adapters = Path.of("src/main/java");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(adapters)) {
            for (Path f : walk.filter(x -> x.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f).replace("\r\n", "\n")
                    .replaceAll("(?s)/\\*.*?\\*/", "")          // the javadoc explains it on purpose
                    .replaceAll("(?m)^\\s*//.*$", "");
                if (src.contains("implements RegisterPromotionPort") && src.contains("\"IDENTIFIED\"")) {
                    offenders.add(f.getFileName().toString());
                }
            }
        }
        assertThat(offenders).as(
            "IDENTIFIED reads like the right governance answer — 'raised, not assessed' — which is why it "
            + "was written and why it would be written again. It is not a value risk.risks accepts. "
            + "Found: %s", offenders)
            .isEmpty();
    }
}
