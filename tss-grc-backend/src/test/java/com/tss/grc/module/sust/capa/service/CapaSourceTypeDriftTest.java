package com.tss.grc.module.sust.capa.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CT-5 drift guard — {@link CorrectiveActionService#SOURCE_TYPES} must equal the
 * DB {@code ck_ca_source} CHECK constraint, whose authoritative definition is the
 * highest-numbered migration that re-creates it (V58 → V251 → V256 → V289 …).
 *
 * <p>History of the drift this pins down: the G4 horizon services write
 * ESG_TARGET / ENV_PERMIT corrective actions via the repository (bypassing the
 * service validate), so when V289 widened the constraint nothing forced the
 * service whitelist to follow — and manual creation for those sources 400'd
 * while the DB accepted them. Whoever widens the constraint next will land
 * here until the service list is widened too (and vice versa).
 */
class CapaSourceTypeDriftTest {

    /** Matches «ADD CONSTRAINT ck_ca_source … CHECK (source_type IN ( … ))» across lines. */
    private static final Pattern ADD_CONSTRAINT = Pattern.compile(
        "ADD\\s+CONSTRAINT\\s+ck_ca_source\\s+CHECK\\s*\\(\\s*source_type\\s+IN\\s*\\(([^)]+)\\)\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern INLINE_CONSTRAINT = Pattern.compile(
        "CONSTRAINT\\s+ck_ca_source\\s+CHECK\\s*\\(\\s*source_type\\s+IN\\s*\\(([^)]+)\\)\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern VALUE = Pattern.compile("N'([A-Z_]+)'");

    @Test
    void serviceWhitelistMatchesLatestMigrationConstraint() throws IOException {
        // Surefire runs with the module root as working directory.
        Path dir = Path.of("src/main/resources/db/migration/sqlserver");
        assertThat(dir).as("migration directory").isDirectory();

        int bestVersion = -1;
        Set<String> dbTypes = null;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql")).toList()) {
                String name = f.getFileName().toString();
                int version = Integer.parseInt(name.substring(1, name.indexOf("__")));
                String sql = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = ADD_CONSTRAINT.matcher(sql);
                if (!m.find()) {
                    m = INLINE_CONSTRAINT.matcher(sql);
                    if (!m.find()) continue;
                }
                if (version > bestVersion) {
                    bestVersion = version;
                    Set<String> types = new HashSet<>();
                    Matcher v = VALUE.matcher(m.group(1));
                    while (v.find()) types.add(v.group(1));
                    dbTypes = types;
                }
            }
        }

        assertThat(dbTypes)
            .as("no migration defines ck_ca_source — did the constraint get renamed?")
            .isNotNull();
        assertThat(CorrectiveActionService.SOURCE_TYPES)
            .as("service SOURCE_TYPES vs ck_ca_source (latest definition: V%s). "
                + "Widen BOTH the migration constraint and the service list together.", bestVersion)
            .containsExactlyInAnyOrderElementsOf(dbTypes);
    }
}
