package com.tss.grc.common.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-446 — what the four hand-rolled {@code esc()} helpers each got wrong.
 *
 * <p>⚠ The load-bearing legs are the ones nobody would write from the shape of the defect: the
 * <b>control character</b> (all four helpers missed every C0 character other than newline, and two
 * missed that too) and <b>Arabic being left alone</b> (an escaper that emitted a unicode escape per
 * letter would leave the audit ledger unreadable in the platform's primary language, house rule 7).
 *
 * <p>⚠ Every leg parses its output with a real {@link ObjectMapper} rather than comparing strings.
 * The claim is <i>the document is valid JSON and the value survives</i>; a string comparison would
 * pass over an escaper that produced valid-looking JSON carrying the wrong value.
 */
@DisplayName("F2-446 — one JSON string escaper, and what the hand-rolled ones missed")
class JsonTextTest {

    private static final ObjectMapper M = new ObjectMapper();

    /** The value at {@code k} of {@code {"k":"<escaped>"}}, or a parse failure. */
    private static String roundTrip(String raw) throws Exception {
        return M.readTree("{\"k\":\"" + JsonText.escape(raw) + "\"}").get("k").asText();
    }

    @Test
    @DisplayName("a quote survives, and the document still parses")
    void aQuoteIsEscaped() throws Exception {
        String raw = "INC-2026-001 \"suspected exfiltration\"";
        assertThat(roundTrip(raw))
            .as("""
                the one site a caller can reach: a break-glass activation's incidentRef is
                @NotBlank @Size(100) with NO @Pattern, so a quote reaches the ledger row that
                records an emergency privilege escalation.""")
            .isEqualTo(raw);
    }

    @Test
    @DisplayName("a backslash survives")
    void aBackslashIsEscaped() throws Exception {
        assertThat(roundTrip("share\\\\host\\path")).isEqualTo("share\\\\host\\path");
    }

    @Test
    @DisplayName("⚠ a control character survives — the half all four helpers missed")
    void aControlCharacterIsEscaped() throws Exception {
        // RFC 8259 forbids an unescaped character below U+0020 inside a string. Two of the four
        // helpers REPLACED newlines with spaces (lossy but valid); ExportAuditInterceptor's left
        // them raw (invalid); none handled tab, and none handled anything else in C0.
        assertThat(roundTrip("line\nbreak")).isEqualTo("line\nbreak");
        assertThat(roundTrip("tab\there")).isEqualTo("tab\there");
        assertThat(roundTrip("bell\u0007and\u001Funit")).isEqualTo("bell\u0007and\u001Funit");
        assertThat(roundTrip("nul\u0000byte")).isEqualTo("nul\u0000byte");
    }

    @Test
    @DisplayName("⚠ Arabic is left alone, not unicode-escaped (house rule 7)")
    void arabicIsNotEscaped() throws Exception {
        String ar = "حادث أمني — تفعيل وصول الطوارئ";
        assertThat(JsonText.escape(ar))
            .as("""
                the ledger is read by an operator. An escaper that emitted a six-character escape
                per letter would be valid JSON and unreadable, so this asserts the shape of the
                escaped text and not only that it round-trips.""")
            .isEqualTo(ar)
            .doesNotContain("\\u");
        assertThat(roundTrip(ar)).isEqualTo(ar);
    }

    @Test
    @DisplayName("null becomes the empty string, so a caller may interpolate unconditionally")
    void nullIsEmpty() throws Exception {
        assertThat(JsonText.escape(null)).isEmpty();
        assertThat(roundTrip(null)).isEmpty();
    }

    @Test
    @DisplayName("quoted() adds the quotes; escape() does not")
    void quotedWrapsAndEscapeDoesNot() {
        assertThat(JsonText.escape("a\"b")).isEqualTo("a\\\"b");
        assertThat(JsonText.quoted("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(JsonText.quoted(null)).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("⚠ the positive control: the pre-fix shape really does produce invalid JSON")
    void theUnescapedShapeIsInvalidJson() {
        // Exactly what BreakGlassService wrote before F2-446, with the caller's own quote in it.
        String incidentRef = "INC \"quoted\" ref";
        String preFix = "{\"userId\":3,\"role\":\"EMERGENCY_RESPONDER\""
            + ",\"expiresAt\":\"2026-09-03T04:00\",\"incidentRef\":\"" + incidentRef + "\"}";
        assertThatThrownBy(() -> M.readTree(preFix))
            .as("""
                without this leg the assertions above pass whether or not the defect was real
                (F2-235). A hash-chained ledger cannot be repaired later (F2-31/F2-434), so this
                is what made the one reachable site worth closing rather than tolerating.""")
            .isInstanceOf(Exception.class);

        String fixed = "{\"userId\":3,\"role\":\"EMERGENCY_RESPONDER\""
            + ",\"expiresAt\":\"2026-09-03T04:00\",\"incidentRef\":\""
            + JsonText.escape(incidentRef) + "\"}";
        assertThatCode(() -> M.readTree(fixed)).doesNotThrowAnyException();
    }
}
