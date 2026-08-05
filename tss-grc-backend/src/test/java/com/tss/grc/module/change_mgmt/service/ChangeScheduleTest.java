package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.module.change_mgmt.dto.OutageDeclarationRequest;
import com.tss.grc.module.change_mgmt.dto.SchedulePublishRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S8g / CE-8 — the forward schedule, the projected service outage, and schedule-wide collisions.
 *
 * <p>What these tests protect: the FSC's value is that a published document says what the business was
 * told, and the PSO's value is that "there will be an outage" carries a length and a named service.
 * Both degrade silently — a digest that is not reproducible, or an outage flag that survives its own
 * withdrawal, leaves a screen that still looks right.
 */
class ChangeScheduleTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");

    private static String v356() {
        try (Stream<Path> walk = Files.walk(MIGRATIONS)) {
            return walk.filter(p -> p.getFileName().toString().startsWith("V356__"))
                       .findFirst()
                       .map(p -> {
                           try {
                               return Files.readString(p, StandardCharsets.UTF_8);
                           } catch (IOException e) {
                               throw new UncheckedIOException(e);
                           }
                       })
                       .orElseThrow(() -> new AssertionError("V356 is missing"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ChangeRequest change(boolean expected, Integer minutes, String services) {
        ChangeRequest c = new ChangeRequest();
        c.setOutageExpected(expected);
        c.setOutageMinutes(minutes);
        c.setServicesAffected(services);
        return c;
    }

    // ── the PSO predicate ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a declared outage needs the flag AND a positive duration AND named services")
    void outageNeedsAllThree() {
        assertThat(change(true, 90, "Customer portal").hasDeclaredOutage()).isTrue();
        assertThat(change(false, 90, "Customer portal").hasDeclaredOutage())
            .as("flag off — nothing is declared no matter what the other fields say").isFalse();
        assertThat(change(true, null, "Customer portal").hasDeclaredOutage())
            .as("\"there will be an outage\" with no length is not something a service owner can plan around")
            .isFalse();
        assertThat(change(true, 0, "Customer portal").hasDeclaredOutage())
            .as("zero minutes is not an outage").isFalse();
        assertThat(change(true, 90, null).hasDeclaredOutage())
            .as("an unnamed outage tells every service owner to assume it is theirs").isFalse();
        assertThat(change(true, 90, "   ").hasDeclaredOutage())
            .as("whitespace is not a service name").isFalse();
    }

    @Test
    @DisplayName("the Java predicate and ck_change_pso agree on what a declaration requires")
    void javaAndSqlAgreeOnThePso() {
        String sql = v356();
        assertThat(sql).contains("CONSTRAINT ck_change_pso CHECK");
        assertThat(sql)
            .as("the CHECK must demand a positive duration, or hasDeclaredOutage is stricter than the "
              + "database and a direct UPDATE can create a declaration the UI refuses to believe")
            .contains("outage_minutes > 0");
        assertThat(sql)
            .as("and it must demand a named service")
            .contains("LEN(LTRIM(services_affected)) > 0");
    }

    @Test
    @DisplayName("V356 proves its constraints reject, rather than asserting they exist")
    void migrationProvesTheConstraintsEnforce() {
        String sql = v356();
        // Reading sys.check_constraints proves a NAME; is_disabled/is_not_trusted proves it is armed;
        // only an attempted write proves it fires.
        assertThat(sql).contains("@rejPso").contains("@rejSha").contains("@rejWin");
        assertThat(sql).contains("BEGIN TRANSACTION").contains("ROLLBACK TRANSACTION");
        assertThat(sql).contains("is_disabled = 1 OR is_not_trusted = 1");
        assertThat(sql)
            .as("and the flag must arrive inert, asserted over existing rows rather than promised")
            .contains("WHERE outage_expected = 1");
        assertThat(sql).contains("a self-check row survived");
    }

    @Test
    @DisplayName("V356 forbids the publication row growing updated_at or status")
    void publicationStaysImmutable() {
        // A published schedule that can be edited afterwards is not a publication. This is the shape
        // somebody reaches for when they want to "fix" a published document, so the migration refuses
        // it rather than trusting a convention.
        assertThat(v356())
            .contains("COLUMN_NAME IN ('updated_at', 'status')")
            .contains("is not a publication");
    }

    // ── the digest ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the snapshot digest is reproducible and 64 hex characters")
    void digestIsReproducible() {
        List<ChangeScheduleService.ScheduleEntry> entries = List.of(sample());
        String a = ChangeScheduleService.toJson(entries);
        String b = ChangeScheduleService.toJson(entries);
        assertThat(a).isEqualTo(b);
        String d = ChangeScheduleService.sha256(a);
        assertThat(d).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(ChangeScheduleService.sha256(a)).isEqualTo(d);
    }

    @Test
    @DisplayName("a changed snapshot changes the digest — the tamper-evidence claim")
    void digestDetectsChange() {
        String one = ChangeScheduleService.toJson(List.of(sample()));
        String two = ChangeScheduleService.toJson(List.of(sampleWith("Payments gateway")));
        assertThat(two).isNotEqualTo(one);
        assertThat(ChangeScheduleService.sha256(two)).isNotEqualTo(ChangeScheduleService.sha256(one));
    }

    @Test
    @DisplayName("the snapshot escapes quotes and newlines so a title cannot break the JSON")
    void snapshotEscapes() {
        // A service name with a quote in it would otherwise produce a payload that no reader can parse
        // — and since the digest is over the text, the corruption would verify perfectly.
        String json = ChangeScheduleService.toJson(List.of(sampleWith("Portal \"A\"\nand B")));
        assertThat(json).contains("\\\"A\\\"").contains("\\n");
        assertThat(json).doesNotContain("A\"\nand");
    }

    // ── the DTOs are narrow ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the publish DTO carries no publisher, no timestamp and no entry list")
    void publishDtoIsNarrow() {
        List<String> fields = Stream.of(SchedulePublishRequest.class.getRecordComponents())
            .map(RecordComponent::getName).toList();
        assertThat(fields)
            .as("ck_csp_published only checks the publisher fields are POPULATED, not who populated "
              + "them; and a caller-supplied entry list would let the published document differ from "
              + "what the platform holds, which is exactly what the digest exists to prevent")
            .doesNotContain("publishedByName", "publishedById", "publishedAt", "entries",
                            "entriesJson", "contentSha256", "entryCount");
        assertThat(fields).containsExactly("from", "to", "notes");
    }

    @Test
    @DisplayName("the outage DTO bounds the duration at both ends")
    void outageDtoIsBounded() throws Exception {
        // @Min(1) mirrors ck_change_pso. @Max exists so the number is a plan rather than a typo: an
        // outage entered in seconds by mistake would otherwise publish a five-figure minute count.
        List<String> fields = Stream.of(OutageDeclarationRequest.class.getRecordComponents())
            .map(RecordComponent::getName).toList();
        assertThat(fields).containsExactly("outageExpected", "outageMinutes", "servicesAffected");

        // ⚠ Read the ACCESSOR, not the record component. `@Min`/`@Max` do not target
        // RECORD_COMPONENT, so `RecordComponent.getAnnotation` returns null for them and a test that
        // asked there would silently pass on an unannotated field — a check that cannot fail.
        var minutes = OutageDeclarationRequest.class.getDeclaredMethod("outageMinutes");
        assertThat(minutes.getAnnotation(jakarta.validation.constraints.Min.class))
            .as("the DTO must bound the duration below, mirroring ck_change_pso's outage_minutes > 0")
            .isNotNull();
        assertThat(minutes.getAnnotation(jakarta.validation.constraints.Min.class).value())
            .isEqualTo(1L);
        assertThat(minutes.getAnnotation(jakarta.validation.constraints.Max.class))
            .as("an unbounded duration lets a typo publish nonsense to the business — an outage "
              + "entered in seconds would render as a five-figure minute count")
            .isNotNull();
    }

    // ── vocabularies and the population ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("the collision vocabulary is closed and distinguishes an outage clash")
    void collisionVocabulary() {
        assertThat(ChangeScheduleService.COLLISION_REASONS)
            .containsExactly("WINDOW_OVERLAP", "OUTAGE_OVERLAP", "INSIDE_FREEZE_WINDOW");
        assertThat(ChangeScheduleService.OUTAGE_OVERLAP)
            .as("two simultaneous DECLARED outages is a business-visible incident being scheduled on "
              + "purpose, and must not read the same as two overlapping windows")
            .isNotEqualTo(ChangeScheduleService.WINDOW_OVERLAP);
    }

    @Test
    @DisplayName("the forward population is statuses at or before delivery, not terminal ones")
    void forwardStatusesAreForward() {
        assertThat(ChangeScheduleService.FORWARD_STATUSES)
            .containsExactly("CAB_APPROVED", "EMERGENCY_APPROVED", "SCHEDULED", "IMPLEMENTING");
        assertThat(ChangeScheduleService.FORWARD_STATUSES)
            .as("a forward schedule that lists finished work is a history, and the business plans "
              + "around it as though the outage were still coming")
            .doesNotContain("IMPLEMENTED", "CLOSED", "ROLLED_BACK", "POST_REVIEW", "CAB_REJECTED",
                            "CANCELLED");
    }

    @Test
    @DisplayName("the forward statuses are real states of the change machine")
    void forwardStatusesAreRealStates() {
        java.util.Set<String> known = statesFromTheMachine();
        assertThat(known).as("anti-vacuity").hasSizeGreaterThan(10);
        for (String s : ChangeScheduleService.FORWARD_STATUSES)
            assertThat(known)
                .as("%s is on the forward schedule's population but is not a state the machine can "
                  + "reach, so it matches no row and the FSC silently shrinks", s)
                .contains(s);
    }

    @Test
    @DisplayName("the schedule record publishes what it could not see")
    void unscheduledCountIsPartOfTheRecord() {
        // ⚠ 2 of 17 dev changes carry no scheduled_start. They cannot collide with anything, and a
        // schedule that dropped them silently would report a clean window with undated work behind it.
        List<String> fields = Stream.of(ChangeScheduleService.ForwardSchedule.class.getRecordComponents())
            .map(RecordComponent::getName).toList();
        assertThat(fields)
            .as("a blind spot that is not in the payload is a blind spot the reader cannot know about "
              + "— the same requirement S8f's `undetectable` list meets")
            .contains("unscheduledCount");
        assertThat(fields).contains("latestPublication", "latestPublicationStale",
                                    "outageCount", "totalOutageMinutes", "freezeCount");
    }

    @Test
    @DisplayName("a stale publication is distinguishable from never having published")
    void staleIsNotTheSameAsNever() {
        // "Nobody has ever published the schedule" and "the published one expired in January" are
        // different governance failures, and one boolean beside a nullable field keeps them apart.
        List<String> fields = Stream.of(ChangeScheduleService.ForwardSchedule.class.getRecordComponents())
            .map(RecordComponent::getName).toList();
        assertThat(fields).contains("latestPublication").contains("latestPublicationStale");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private static ChangeScheduleService.ScheduleEntry sample() {
        return sampleWith("Customer portal");
    }

    private static ChangeScheduleService.ScheduleEntry sampleWith(String services) {
        return new ChangeScheduleService.ScheduleEntry(1L, "CHG-2025-0001", "en", "ar",
            "NORMAL", "HIGH", "SCHEDULED",
            LocalDateTime.of(2026, 9, 1, 22, 0), LocalDateTime.of(2026, 9, 2, 2, 0),
            true, 90, services, true, null);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<String> statesFromTheMachine() {
        try {
            java.lang.reflect.Field f =
                ChangeManagementService.class.getDeclaredField("NORMAL_TRANSITIONS");
            f.setAccessible(true);
            java.util.Map<String, java.util.Set<String>> t =
                (java.util.Map<String, java.util.Set<String>>) f.get(null);
            java.util.Set<String> all = new java.util.HashSet<>(t.keySet());
            t.values().forEach(all::addAll);
            return all;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("NORMAL_TRANSITIONS is gone or renamed — fix the reference", e);
        }
    }
}
