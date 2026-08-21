package com.tss.grc.common.reference;

import java.security.SecureRandom;

/**
 * F2-266 — the platform's one way to mint a human-facing business reference.
 *
 * <p><b>What this is for.</b> A <i>business reference</i> is the string a human, an auditor or a
 * regulator cites to say <em>which</em> record they mean — an FIU submission's alert code, an
 * intake tracking code. F2-172(d) established the rule that has since been applied one instance
 * per slice: <b>only the server may issue a tracking code or a statutory deadline</b>. This is the
 * mechanism, so the next application is a call rather than a twelfth hand-rolled generator
 * (F2-190).
 *
 * <p><b>Entropy, not a sequence, and that is the whole design.</b> F2-48 had to fix a collision in
 * a generator whose suffix was effectively constant, and
 * {@code ClientSuppliedReferenceRatchetTest}'s own javadoc records that
 * {@code NcaEccFilingService}'s {@code NCA-<year>-001} is safe <em>only</em> because a UNIQUE index
 * limits a tenant to one filing per fiscal year. A per-tenant sequence needs a read-then-write, so
 * two concurrent creates race and the loser either collides or silently reuses a number. Two 4-char
 * segments over a 32-symbol alphabet is 32^8 ≈ 1.1 × 10^12 values, so a random code needs no read,
 * no lock and no retry — and a UNIQUE index on the column remains the backstop rather than the
 * mechanism.
 *
 * <p><b>The alphabet excludes I, O, 0 and 1</b> because these codes are read aloud, dictated down a
 * phone line and re-typed off a printed regulator submission.
 *
 * <p>⚠ <b>The format is deliberately identical to what {@code IntakeService} minted before this
 * class existed</b> ({@code PREFIX-XXXX-XXXX}). Intake codes are quoted to the public on the
 * whistleblowing and breach-report portals, so changing their shape would invalidate a code
 * somebody is holding. Extracting a shared primitive must not change either caller's output, which
 * is asserted rather than assumed.
 */
public final class ReferenceCodeMint {

    /** No I, O, 0 or 1 — these codes get dictated and re-typed. */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** Characters per segment. Two segments give 32^8 values. */
    static final int SEGMENT = 4;

    private static final SecureRandom RNG = new SecureRandom();

    private ReferenceCodeMint() { }

    /**
     * {@code prefix-XXXX-XXXX}.
     *
     * @param prefix the family the reference belongs to — {@code "AML-2026"}, {@code "SPEAKUP"}.
     *               Callers that want a year in the reference put it in the prefix, so this class
     *               owns no clock and a test can assert the shape without one.
     */
    public static String mint(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException(
                "a reference code needs a prefix: a bare '-XXXX-XXXX' names no family, and the "
                + "prefix is what tells a reader which register the code belongs to");
        }
        return prefix + "-" + segment() + "-" + segment();
    }

    private static String segment() {
        StringBuilder sb = new StringBuilder(SEGMENT);
        for (int i = 0; i < SEGMENT; i++) {
            sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
