package com.tss.grc.module.platform.calendar.service;

import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.module.platform.calendar.dto.CalendarEventDto;
import com.tss.grc.module.platform.calendar.entity.CalendarEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-182 — {@code calendar_events.recurrence} was validated on the wrong class.
 *
 * <p><b>The shape, which is the reusable part.</b> {@code CalendarEventDto} carried
 * {@code @Pattern(regexp = "NONE|DAILY|WEEKLY|MONTHLY|QUARTERLY|ANNUAL")} on its
 * {@code recurrence} field. That type is the <b>outbound</b> payload, assembled by
 * {@code CalendarEventDto.from(entity)}; both endpoints bind the nested
 * {@code CalendarEventDto.CreateRequest}, whose {@code recurrence} had no annotation at all. Bean
 * validation runs only against a parameter marked {@code @Valid}, so the annotation constrained
 * nothing — while reading exactly like the thing a reviewer was looking for.
 *
 * <p>🔑 <b>A validation constraint on a response DTO is worse than no constraint at all, because it
 * answers the question a reader was about to ask.</b> This is the {@code safeRole(role){return role;}}
 * family again (F2-175, F2-179): an artefact whose <em>name or position</em> claims a check that its
 * behaviour does not perform. The tell here is structural rather than lexical — the class is never a
 * {@code @RequestBody}.
 *
 * <p><b>What it cost.</b> {@code IcsBuilder.rruleFor} switches on the value with
 * {@code default -> null}, and its caller emits an RRULE only when non-null. So
 * {@code recurrence: "monthly"} produced an {@code .ics} event carrying <b>no recurrence rule</b> —
 * a quarterly audit day, DR test or report deadline appeared in the subscriber's calendar once and
 * then never again. It fails closed and silently, which is why it survived: the event is there, so
 * nothing looks broken until the second occurrence does not arrive.
 *
 * <p>⚙ {@code orDefault} rather than {@code require}, and the reason is a product one: {@code NONE}
 * is a real member of the vocabulary rather than a null stand-in, and an event that says nothing
 * about recurrence has always been accepted as non-recurring. Only a value the column could never
 * hold is refused.
 */
class CalendarRecurrenceVocabularyTest {

    @Test
    @DisplayName("a lower-case recurrence is normalised, so the .ics actually carries an RRULE")
    void lowerCaseRecurrenceStillProducesARecurrenceRule() {
        // The defect, end to end: what the writer stores is what IcsBuilder switches on.
        assertThat(rruleFor("monthly")).as(
            "before the writer was closed this was null — rruleFor's default branch — and the caller "
            + "emitted no RRULE line at all, so the series became a single event")
            .isNotNull()
            .contains("FREQ=MONTHLY");

        assertThat(rruleFor("QUARTERLY")).contains("FREQ=MONTHLY;INTERVAL=3");
        assertThat(rruleFor("annual")).contains("FREQ=YEARLY");
    }

    @Test
    @DisplayName("NONE is a member of the vocabulary, not the absence of one")
    void silenceMeansDoesNotRepeat() {
        assertThat(CalendarService.RECURRENCE.orDefault(null, CalendarService.RECURRENCE_NONE))
            .isEqualTo("NONE");
        assertThat(CalendarService.RECURRENCE.orDefault("   ", CalendarService.RECURRENCE_NONE))
            .isEqualTo("NONE");
        assertThat(CalendarService.RECURRENCE.tokens())
            .contains(CalendarService.RECURRENCE_NONE)
            .containsExactlyInAnyOrder("NONE", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "ANNUAL");
    }

    /**
     * ⚠ Driven through {@link CalendarService#applyCreate}, not through {@code RECURRENCE} directly —
     * and that distinction is not pedantry. Written the direct way, this leg let a mutation swapping
     * {@code orDefault} for {@code coerce} survive: {@code coerce} would silently record a typo'd
     * recurrence as {@code NONE}, turning a caller's mistake into a compliance event that quietly
     * never repeats, which is the exact harm this finding was about. Second instance of the same
     * mistake in one test class.
     */
    @Test
    @DisplayName("a value chk_calendar_recurrence could never hold is refused by the writer")
    void anImpossibleRecurrenceIsRefusedByTheWriter() {
        CalendarEventDto.CreateRequest req = new CalendarEventDto.CreateRequest();
        req.setTitleEn("Fortnightly nothing");
        req.setEventType("DR_TEST");
        req.setStartsAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        req.setRecurrence("FORTNIGHTLY");

        CalendarEvent e = new CalendarEvent();
        assertThatThrownBy(() -> CalendarService.applyCreate(e, req))
            .as("coerce would store NONE here and report success — a series that silently never repeats")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("recurrence")
            .hasMessageContaining("FORTNIGHTLY");
        assertThat(e.getRecurrence()).as(
            "nothing of the caller's value is written on the refusal path. ⚙ Note the assertion is "
            + "'still the entity default', not 'null': CalendarEvent initialises recurrence to NONE, "
            + "so an isNull() check here fails against correct code — which it did, and the red "
            + "baseline then made the mutation harness report an unrelated mutation as caught.")
            .isEqualTo(new CalendarEvent().getRecurrence())
            .isNotEqualTo("FORTNIGHTLY");
    }

    /**
     * The regression guard for the misplacement itself, not just for the casing.
     *
     * <p>Re-adding a bean-validation annotation to the outbound {@code CalendarEventDto} would look
     * like tightening the API and would change nothing — and the next reader would take it for the
     * guard. Asserted by reflection over the real fields, because the point is what the class
     * <em>declares</em>, not what a source regex happens to match.
     */
    @Test
    @DisplayName("the outbound DTO declares no validation constraint on recurrence")
    void theOutboundDtoDoesNotPretendToValidate() throws Exception {
        Field outbound = CalendarEventDto.class.getDeclaredField("recurrence");
        assertThat(constraintNames(outbound)).as(
            "this class is never bound as a @RequestBody, so any constraint here is inert. If a "
            + "recurrence rule needs enforcing, CalendarService.RECURRENCE is the authority — it "
            + "normalises rather than merely refusing, and ClosedVocabularyPinTest checks it against "
            + "chk_calendar_recurrence, which a regex in an annotation never was.")
            .isEmpty();

        // Anti-vacuity: the same reflection MUST see a constraint that is genuinely there, or this
        // test would pass for a class whose annotations it simply cannot read.
        Field boundEventType = CalendarEventDto.CreateRequest.class.getDeclaredField("eventType");
        assertThat(constraintNames(boundEventType)).as(
            "eventType on the INBOUND CreateRequest is annotated and does run — proof the walk works")
            .contains("Pattern", "NotBlank");
    }

    private static java.util.List<String> constraintNames(Field f) {
        return Arrays.stream(f.getAnnotations())
            .map(a -> a.annotationType())
            .filter(t -> t.getPackageName().startsWith("jakarta.validation.constraints"))
            .map(Class::getSimpleName)
            .toList();
    }

    /**
     * Drive the real writer and the real builder, so the assertion is about shipped behaviour.
     *
     * <p>⚠ This helper used to call {@code CalendarService.RECURRENCE.orDefault(...)} itself, and a
     * mutation restoring the original {@code req.getRecurrence() == null ? "NONE" : …} writer
     * <b>survived</b> — because the test never went through {@code applyCreate}. It asserted that
     * {@link ClosedVocabulary} works, which nothing doubted. 🔑 <b>A test that reaches around the code
     * under test measures the library, not the site</b>; the mutation is what exposed it, and it is the
     * same family as F2-175's SLA coincidence, where an assertion could not fail.
     */
    private static String rruleFor(String recurrenceAsSupplied) {
        CalendarEventDto.CreateRequest req = new CalendarEventDto.CreateRequest();
        req.setTitleEn("Quarterly DR test");
        req.setEventType("DR_TEST");
        req.setStartsAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        req.setRecurrence(recurrenceAsSupplied);
        req.setRecurrenceUntil(LocalDate.of(2027, 9, 1));

        CalendarEvent entity = new CalendarEvent();
        CalendarService.applyCreate(entity, req);      // the actual writer

        CalendarEventDto dto = new CalendarEventDto();
        dto.setTitleEn(entity.getTitleEn());
        dto.setEventType(entity.getEventType());
        dto.setStartsAt(entity.getStartsAt());
        dto.setRecurrence(entity.getRecurrence());     // what the writer stored
        dto.setRecurrenceUntil(entity.getRecurrenceUntil());

        String ics = IcsBuilder.build(java.util.List.of(dto), "TSS GRC");
        return Arrays.stream(ics.split("\r\n"))
            .filter(l -> l.startsWith("RRULE:"))
            .findFirst()
            .orElse(null);
    }

    @Test
    @DisplayName("the writer stores the normalised token, which is what every reader then sees")
    void theWriterStoresTheCanonicalToken() {
        CalendarEventDto.CreateRequest req = new CalendarEventDto.CreateRequest();
        req.setTitleEn("Quarterly DR test");
        req.setEventType("DR_TEST");
        req.setStartsAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        req.setRecurrence("quarterly");

        CalendarEvent e = new CalendarEvent();
        CalendarService.applyCreate(e, req);

        assertThat(e.getRecurrence()).isEqualTo("QUARTERLY");

        req.setRecurrence(null);
        CalendarEvent silent = new CalendarEvent();
        CalendarService.applyCreate(silent, req);
        assertThat(silent.getRecurrence()).isEqualTo("NONE");
    }
}
