package com.tss.grc.common.json;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

/**
 * F2-446 — the one answer to "how do I put this value inside a JSON string literal?"
 *
 * <p><b>Why this exists.</b> Thirteen sites in {@code src/main/java} build a JSON document by
 * string concatenation and interpolate a value straight into it, and the escaping that did exist
 * was <b>three different functions under one name</b>:
 *
 * <ul>
 *   <li>{@code ExportAuditInterceptor.esc} escaped {@code \} and {@code "} — and left a raw
 *       newline, which is <b>invalid JSON</b> (RFC 8259 forbids an unescaped control character
 *       inside a string);</li>
 *   <li>{@code AccessEvidencePackageService.esc} / {@code RegulatorPackageService.esc} escaped
 *       {@code \} and {@code "} and <i>replaced</i> newlines with spaces — still nothing for tab
 *       or any other C0 control character;</li>
 *   <li>{@code AnnexExportService.esc} is not a JSON escaper at all. It is a <b>CSV</b> escaper
 *       carrying an OWASP formula-injection guard, and it must stay that way — see
 *       {@code AuditJsonEscapingContractTest}, which pins that boundary in both directions
 *       because a later "make these consistent" sweep would delete the guard.</li>
 * </ul>
 *
 * <p>🔑 One name, three contracts: a reader who finds {@code esc} in one file and carries the
 * idiom into another gets a different function (F2-419's shape, F2-190's remedy).
 *
 * <p><b>Why it delegates to Jackson rather than hand-rolling a fourth.</b>
 * {@link JsonStringEncoder} is the encoder Jackson itself uses to write every response this
 * platform serves, so it is correct by the same mechanism the API is correct by — and it handles
 * the control characters all four hand-rolled helpers missed. It is on the classpath already.
 *
 * <p>⚠ It escapes the string <b>body</b> and adds no surrounding quotes, which is what the call
 * sites need ({@code "\"" + escape(x) + "\""}). Non-ASCII is left alone: an escaper that
 * emitted a six-character unicode escape for every Arabic letter would leave the audit ledger
 * unreadable in the platform's primary language (house rule 7), and {@code JsonTextTest}
 * asserts that rather than assuming it.
 *
 * <p>⚠ The sentence above says "six-character unicode escape" rather than writing one,
 * because javac processes unicode escapes in the LEXICAL phase — before comments are
 * stripped — so a bare backslash-u in a Java comment is an "illegal unicode escape" and the
 * file does not compile. Measured 2026-09-03: it cost one round trip.
 *
 * <p>⚠⚠ <b>The reason "nothing parses those columns today" is a weaker defence here than usual.</b>
 * {@code platform.audit_log} is hash-chained (F2-31), so a malformed value written into it
 * <b>cannot be repaired later</b> without breaking the seal — F2-434 established that and F2-445
 * measured six ledger rows that must be left corrupted for exactly that reason. Corruption written
 * to that ledger is permanent by construction, which is why the reachable site (a break-glass
 * activation's {@code incidentRef}) was worth closing rather than tolerating.
 */
public final class JsonText {

    private JsonText() {
    }

    /**
     * The RFC 8259 escaped <i>body</i> of a JSON string — no surrounding quotes.
     *
     * <p>{@code null} becomes the empty string, so a caller may interpolate unconditionally; the
     * alternative is the literal four characters {@code null} inside quotes, which is a value
     * rather than an absence.
     */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        // ⚠ The local is declared char[] deliberately. F2-430's charset gate FAILS on a
        //    new String(...) whose first argument's type it cannot resolve rather than skipping
        //    it (F2-206: unresolved reads identically to clean), and it fired on this line within
        //    one build. It is also the point worth making to a reader: quoteAsString returns
        //    char[], so this construction is charset-independent — it is the byte[] overload that
        //    silently uses the platform default, which on this JDK-17 box is Cp1252.
        char[] quoted = JsonStringEncoder.getInstance().quoteAsString(s);
        return new String(quoted);
    }

    /**
     * {@code "value"} — the escaped body with its quotes, for a caller that wants the whole
     * literal rather than to write the quotes itself.
     */
    public static String quoted(String s) {
        return "\"" + escape(s) + "\"";
    }
}
