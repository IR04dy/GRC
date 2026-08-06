package com.tss.grc.module.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tss.grc.module.platform.dto.NotificationResponse;
import com.tss.grc.module.platform.entity.Notification;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-38 — the notification a recipient is served must carry what the row holds.
 *
 * <p><b>The defect.</b> {@code toResponse} passed {@code null} for four of ten fields. Two of them,
 * {@code severity} and {@code linkUrl}, <b>are not columns on {@code platform.notifications} at
 * all</b> — they could never be non-null, on any of the 445 live rows, and are now gone from the
 * record. The third, {@code bodyAr}, <b>is</b> a column: {@code message_ar} holds real Arabic on 42
 * rows, and the mapper discarded it, so an Arabic-primary product served an English-only body. The
 * fourth pair, {@code entityType}/{@code entityId}, is populated on <b>all 445 rows</b> and was
 * exposed by nothing, which is why the frontend could not build a link and why {@code linkUrl}
 * existed as a null placeholder in the first place.
 *
 * <p><b>Why a reflective test and not ten assertions.</b> Ten hand-written assertions are exactly
 * what was already absent, and adding them fixes today's ten fields without preventing the eleventh.
 * {@link #everyFieldOnTheRecordIsFedFromTheRow()} walks the record's components by reflection and
 * fails on any that comes back null from a fully-populated row — so <b>adding a field to the record
 * and forgetting to map it fails the build</b>, which is the actual failure mode here. That is the
 * difference between testing the fix and testing the class of defect.
 */
class NotificationResponseMappingTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 8, 6, 7, 30, 0);

    /** Every column the entity has, each with a value distinguishable from the others. */
    private static Notification fullyPopulatedRow() {
        Notification n = new Notification();
        n.setId(4242L);
        n.setOrganisationId(1L);
        n.setRecipientId(7L);
        n.setTitleEn("Evidence request escalation");
        n.setTitleAr("تصعيد طلب دليل");
        n.setMessageEn("Two requests are past due.");
        n.setMessageAr("طلبان متأخران عن موعدهما.");
        n.setNotificationType("EVIDENCE_REQUEST_ESCALATION");
        n.setEntityType("EvidenceRequest");
        n.setEntityId(9001L);
        n.setIsRead(false);
        n.setCreatedAt(WHEN);
        return n;
    }

    /**
     * Invokes the mapper directly rather than through {@code listForUser}.
     *
     * <p>{@code toResponse} is the unit under test and touches none of the service's three
     * dependencies, so nulls are honest here — going through {@code listForUser} would have meant
     * mocking a Spring Data repository to prove a field-copy, which tests the stub as much as the
     * mapper. It is also why this class needs no mocking framework at all.
     */
    private static NotificationResponse mapOne(Notification row) {
        try {
            NotificationService service = new NotificationService(null, null, null);
            var m = NotificationService.class.getDeclaredMethod("toResponse", Notification.class);
            m.setAccessible(true);
            return (NotificationResponse) m.invoke(service, row);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "could not invoke NotificationService.toResponse(Notification) — if it was "
                    + "renamed or inlined, repoint this test at the new mapper rather than deleting "
                    + "it: the defect it guards is a field silently mapped to null", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no field on the record comes back null from a fully-populated row")
    void everyFieldOnTheRecordIsFedFromTheRow() throws Exception {
        NotificationResponse got = mapOne(fullyPopulatedRow());

        assertThat(NotificationResponse.class.isRecord())
                .as("this test walks record components; if the DTO stops being a record, rewrite it "
                    + "rather than deleting it")
                .isTrue();

        for (RecordComponent rc : NotificationResponse.class.getRecordComponents()) {
            Object value = rc.getAccessor().invoke(got);
            assertThat(value)
                    .as("NotificationResponse.%s came back null from a row where every column is "
                        + "populated, so the mapper is dropping it. This is F2-38 exactly: "
                        + "toResponse hard-coded null for four of ten fields, two of which were not "
                        + "columns at all and two of which were. If a new field genuinely has no "
                        + "column, do not add it to this record — a field that can never carry "
                        + "information reads as data and is worse than an absent one.", rc.getName())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("the Arabic body survives the mapping — the field the defect actually dropped")
    void arabicBodyIsServed() {
        NotificationResponse got = mapOne(fullyPopulatedRow());
        assertThat(got.bodyAr())
                .as("message_ar is a real column holding Arabic on 42 of 445 live rows, and the "
                    + "mapper passed null for it. Arabic is this platform's primary language; a "
                    + "bilingual record that serves only English is a half-built record.")
                .isEqualTo("طلبان متأخران عن موعدهما.");
        assertThat(got.titleAr()).isEqualTo("تصعيد طلب دليل");
    }

    @Test
    @DisplayName("entityType and entityId are exposed, so a client can build the link the server cannot")
    void entityReferenceIsExposed() {
        NotificationResponse got = mapOne(fullyPopulatedRow());
        assertThat(got.entityType()).isEqualTo("EvidenceRequest");
        assertThat(got.entityId()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("the record declares no field the table cannot fill")
    void theRecordDoesNotDeclareFieldsWithNoColumn() {
        List<String> names = List.of(NotificationResponse.class.getRecordComponents()).stream()
                .map(RecordComponent::getName)
                .toList();

        assertThat(names)
                .as("`severity` and `linkUrl` are not columns on platform.notifications. They were "
                    + "declared here and hard-coded null for the lifetime of the endpoint. Deriving "
                    + "a severity from notification_type was rejected on measurement: that column "
                    + "carries two vocabularies, only 48 of 445 rows have a severity-shaped value, "
                    + "and the other 397 would have been assigned one that never existed.")
                .doesNotContain("severity", "linkUrl");
    }
}
