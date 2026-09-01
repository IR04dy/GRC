package com.tss.grc.common.util;

import java.util.Locale;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-readable external identifiers of the form {@code PREFIX-YYYY-XXXXXXXXXXXXXXX}.
 *
 * <p><b>F2-48 — this exists because the same broken generator was copy-pasted nine times.</b> Every
 * one of them was:
 *
 * <pre>{@code
 *   String.format("PREFIX-%d-%s", year,
 *       Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(0, 6));
 * }</pre>
 *
 * <p>The epoch in milliseconds is <b>11</b> hex digits, so {@code substring(0, 6)} keeps the
 * <em>high-order</em> six and discards the low five. The value therefore only changes when bit 20
 * rolls over — <b>once per 2²⁰ ms ≈ 17.5 minutes</b>. Every record created inside one window got an
 * identical external id and collided with that table's {@code uk_*_external} unique index.
 *
 * <p>The failure did not look like an identifier problem. The insert fails at flush, so the caller
 * saw "an unexpected database error" (500), and anything that had joined its transaction to a
 * best-effort {@code @Transactional} hook died with {@code UnexpectedRollbackException} (503) on a
 * completely unrelated operation. It also hid behind seed data: the demo rows carry hand-written
 * codes, so a generator that works exactly once per window looks fine until two records are raised
 * in the same 17 minutes.
 *
 * <p>Affected registers, all with the unique index in place and therefore all capped at one new
 * record per organisation per window: approval requests, audit engagements, audit findings, BCM
 * crisis invocations, cyber vulnerabilities, knowledge articles, PDPL DSR cases, regulatory changes
 * and CAPA tasks. Several are the worst possible place for it — the approvals kernel, a crisis
 * invocation raised <em>during</em> a crisis, and a DSR case that starts a statutory PDPL clock.
 *
 * <p>This implementation keeps the full millisecond (nothing truncated) and appends four random hex
 * digits, so two records raised in the same millisecond on different threads still differ.
 * Uniqueness is still enforced by the database; this only stops the generator from guaranteeing a
 * collision.
 */
public final class ExternalIds {

    private ExternalIds() {}

    /**
     * @param prefix short register prefix, e.g. {@code "CAPA"}, {@code "DSR"}, {@code "VULN"}
     * @return an id of the form {@code PREFIX-2026-19FA8B4BF3C616E} (prefix, year, ms-hex, random)
     */
    public static String generate(String prefix) {
        return String.format(Locale.ROOT, "%s-%d-%s%04X",
            prefix,
            LocalDateTime.now().getYear(),
            Long.toHexString(System.currentTimeMillis()).toUpperCase(),
            ThreadLocalRandom.current().nextInt(0x10000));
    }
}
