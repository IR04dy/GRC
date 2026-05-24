package com.tss.grc.module.iam.mfa;

import java.util.List;

/**
 * TOTP-based multi-factor authentication — API surface for Open Issue <strong>#008</strong>.
 *
 * <p>This interface marks the contract that the full MFA enrollment flow (Sprint 13)
 * will implement. It exists now — together with the {@code dev.samstevens.totp:totp}
 * dependency in {@code pom.xml} — so callers can wire to the API surface before the
 * real implementation lands, and so the dependency selection is exercised by the
 * build immediately rather than as part of a separate PR later.
 *
 * <p>All methods currently throw {@link UnsupportedOperationException}. Do not call
 * them from production paths until issue #008's Sprint 13 follow-up is closed.
 *
 * <h3>Design summary (full detail in {@code docs/operations/mfa-totp-selection.md})</h3>
 * <ul>
 *   <li><strong>Algorithm:</strong> RFC 6238 TOTP, SHA-1, 30-second window, 6-digit codes.
 *       Matches Google Authenticator, Microsoft Authenticator, Authy, 1Password defaults.</li>
 *   <li><strong>Secret encoding:</strong> Base32, 160-bit secrets generated via a SecureRandom.</li>
 *   <li><strong>Storage:</strong> secret encrypted at rest via the platform's existing
 *       column-encryption mechanism; never returned to the client after enrollment.</li>
 *   <li><strong>Backup codes:</strong> 10 single-use codes generated at enrollment,
 *       bcrypt-hashed before storage, marked consumed on use.</li>
 *   <li><strong>Replay protection:</strong> last-used time step recorded per user; codes
 *       from past time steps within the verification window are accepted at most once.</li>
 * </ul>
 *
 * <h3>Saudi-regulatory framing</h3>
 * <ul>
 *   <li><strong>NCA ECC-2 control 2-2</strong> — Identity & access management; strong
 *       authentication for privileged accounts.</li>
 *   <li><strong>SAMA CSF 3.2.4</strong> — Multi-factor authentication for sensitive
 *       financial-services data access.</li>
 *   <li><strong>SDAIA</strong> — "strong authentication for any privileged GRC role"
 *       requirement under the 2023 Personal Data Protection guidance.</li>
 * </ul>
 */
public interface TotpService {

    /**
     * Begin MFA enrollment for a user. Generates a 160-bit secret, encodes it as
     * base32, persists the (encrypted) secret with status {@code PENDING_VERIFY},
     * and returns the {@code otpauth://} URI for QR-code rendering by the client.
     *
     * <p>The user must then submit a TOTP code (via {@link #verifyCode}) to confirm
     * the secret was stored correctly; only after a successful verification is the
     * enrollment marked {@code ENROLLED} and MFA enforced on subsequent logins.
     *
     * @param userId internal user PK (BIGINT)
     * @return new enrollment record with secret URI + base32 secret (for manual entry fallback)
     * @throws UnsupportedOperationException until Sprint 13
     */
    TotpEnrollment enrollUser(Long userId);

    /**
     * Verify a 6-digit TOTP code against the user's stored secret. Accepts a code
     * from the current 30-second step, ±1 step (90-second total tolerance) for
     * clock drift. Replay-protected: a previously consumed step is rejected.
     *
     * @param userId internal user PK
     * @param code   6-digit user input (leading zeros preserved)
     * @return {@code true} if the code matches an unused step
     * @throws UnsupportedOperationException until Sprint 13
     */
    boolean verifyCode(Long userId, String code);

    /**
     * Generate fresh single-use backup codes. Each is a 10-character base32 string
     * displayed once to the user at generation time and bcrypt-hashed for storage.
     * Used when the user's authenticator app is unavailable.
     *
     * <p>Calling this method invalidates any unused backup codes from a prior call,
     * so the user always has at most 10 active codes.
     *
     * @param userId internal user PK
     * @return the 10 plain-text codes to display once
     * @throws UnsupportedOperationException until Sprint 13
     */
    List<String> generateBackupCodes(Long userId);

    /**
     * Result of a successful enrollment kickoff. The {@code secretBase32} and the
     * {@code otpauthUri} both encode the same secret — show the QR (for app users)
     * and offer the base32 string as a manual-entry fallback.
     */
    record TotpEnrollment(
            Long userId,
            String secretBase32,
            String otpauthUri,
            String issuer
    ) {}
}
