package com.tss.grc.common.export;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-451 — the primitive every capped emitter calls to say what its document actually contains.
 *
 * <p>Two of these legs exist because of recorded landmines rather than because of the arithmetic:
 * the digits must be {@link Locale#ROOT} (F2-416 — a bare {@code String.format("%,d", n)} emits
 * Arabic-Indic digits on an {@code ar-SA} JVM, and every other figure in these documents is
 * {@code String.valueOf(int)}), and {@link ExportTruncation#hint} must <b>replace</b> the
 * completeness subtitle rather than append to it, because a subtitle reading "All states" beside a
 * truncated figure is the defect and not the fix.
 */
class ExportTruncationTest {

    @Test void aRegisterLargerThanTheDocumentIsTruncated() {
        assertThat(ExportTruncation.isTruncated(1_000, 12_470)).isTrue();
        assertThat(ExportTruncation.isTruncated(1_000, 1_000)).isFalse();
        assertThat(ExportTruncation.isTruncated(51, 51)).isFalse();
        assertThat(ExportTruncation.isTruncated(0, 0))
            .as("an empty register is not a truncated one -- a complete package gains no note")
            .isFalse();
    }

    @Test void theNoteNamesBothFiguresAndIsEmptyWhenTheDocumentIsComplete() {
        assertThat(ExportTruncation.note(1_000, 12_470, false)).isEqualTo("showing 1,000 of 12,470");
        assertThat(ExportTruncation.note(1_000, 12_470, true))
            .as("house rule 7 -- an Arabic-primary platform states this in both languages")
            .isEqualTo("يعرض 1,000 من 12,470");
        assertThat(ExportTruncation.note(51, 51, false))
            .as("empty, so a caller can append it unconditionally").isEmpty();
        assertThat(ExportTruncation.note(51, 51, true)).isEmpty();
    }

    @Test void theDigitsAreLocaleRootWhateverTheJvmDefaultIs() {
        // F2-416: on an ar-SA JVM a bare String.format("%,d", n) yields Arabic-Indic digits, and a
        // KPI strip mixing the two numbering systems is worse than either. Prove it against the
        // locale that would break it rather than against the ambient one.
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA"));
            assertThat(ExportTruncation.note(1_000, 12_470, false))
                .isEqualTo("showing 1,000 of 12,470");
            assertThat(ExportTruncation.note(1_000, 12_470, true))
                .isEqualTo("يعرض 1,000 من 12,470");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test void theHintReplacesACompletenessSubtitleRatherThanAppendingToIt() {
        assertThat(ExportTruncation.hint(1_000, 12_470, false, "Trail entries"))
            .as("a subtitle asserting completeness must NOT survive next to a windowed figure")
            .isEqualTo("showing 1,000 of 12,470");
        assertThat(ExportTruncation.hint(1_000, 12_470, false, "All states"))
            .doesNotContain("All states");
    }

    @Test void theHintKeepsTheSubtitleWhenNothingWasWithheld() {
        assertThat(ExportTruncation.hint(51, 51, false, "Trail entries")).isEqualTo("Trail entries");
        assertThat(ExportTruncation.hint(51, 51, false, null))
            .as("a null subtitle is empty, never the string \"null\" on a regulator document")
            .isEmpty();
    }

    @Test void aTotalSmallerThanTheIncludedCountIsNotReportedAsTruncated() {
        // Cannot arise from one Page, which is why both figures must come from the same Page --
        // but if a caller ever swaps them, reading as complete is the quiet failure and reading as
        // truncated would put a nonsense note on a regulator submission. Pinned so the choice is
        // deliberate rather than incidental.
        assertThat(ExportTruncation.isTruncated(12_470, 1_000)).isFalse();
        assertThat(ExportTruncation.note(12_470, 1_000, false)).isEmpty();
    }
}
