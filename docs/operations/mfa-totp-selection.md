# MFA TOTP library — decision record

Closes Open Issue **#008** — selects the TOTP library, documents the backup-code strategy, and adds the dependency + API stub. Full MFA enrollment UI is the Sprint 13 follow-up.

| Regulatory mapping | Control |
|---|---|
| **NCA ECC-2** | Control 2-2 — Identity & access management (strong authentication for privileged accounts) |
| **SAMA CSF** | 3.2.4 — Multi-factor authentication |
| **SDAIA** | 2023 PDPL Implementing Regulations — "strong authentication for any privileged GRC role" |
| **ISO 27001 A.9.4.2** | Secure log-on procedures |

---

## 1. Requirements

What we need:

- **RFC 6238 TOTP** — compatible with Google Authenticator, Microsoft Authenticator, Authy, 1Password
- **Base32 secret** generation + parsing
- **Code verification** with a ±1 time-step window (90 seconds total) for clock drift
- **otpauth:// URI** generation for QR-code rendering
- Optional: built-in QR-code image bytes (avoid pulling a second dep)
- Java 17 compatible (project pin — see `CLAUDE.md`)
- Active maintenance — security library; abandoned project is a non-starter
- Permissive license — Apache 2.0 / MIT preferred
- Small footprint — we already carry POI, OpenPDF, ZXing transitively, so we don't want a heavyweight crypto stack

What we **don't** need (now):

- WebAuthn / FIDO2 — Sprint 14+ follow-up
- SMS / email OTP — too weak for privileged-role MFA under SAMA CSF
- HOTP (counter-based) — TOTP supersedes it for most use cases

---

## 2. Candidates compared

| Library | Group : Artifact | Last release | License | LoC pulled (≈) | QR gen built-in | Decision |
|---|---|---|---|---:|:---:|---|
| **dev.samstevens.totp** | `dev.samstevens.totp:totp` | **2024** (1.7.1) | MIT | small (+ZXing) | ✅ | **✅ Selected** |
| Aerogear OTP | `org.jboss.aerogear:aerogear-otp-java` | 2017 (1.0.0) | Apache 2.0 | tiny | ❌ | Rejected — unmaintained 7+ years |
| de.taimos:totp | `de.taimos:totp` | 2019 (1.0) | Apache 2.0 | tiny | ❌ | Rejected — unmaintained 5+ years, no QR helper |
| Java-OTP (`com.eatthepath:java-otp`) | `com.eatthepath:java-otp` | 2024 | MIT | small | ❌ | Honourable mention — would force us to add ZXing manually |
| Google Authenticator (warrenstrange) | `com.warrenstrange:googleauth` | 2022 (1.5.0) | BSD-3 | small (+ZXing) | ✅ | Honourable mention — fewer downloads than samstevens; API less ergonomic |

### Decision: `dev.samstevens.totp:totp:1.7.1`

Reasons:

1. **Actively maintained** — 1.7.1 released within the last 12 months; issue tracker is responsive.
2. **Bundled QR-code generation** via ZXing — one dep covers secret-gen + verification + QR.
3. **Ergonomic API** — `DefaultSecretGenerator`, `DefaultCodeVerifier`, `QrGenerator`, `QrData.Builder` — clean separation of concerns, easy to mock.
4. **Supports SHA-1 / SHA-256 / SHA-512** — we start with SHA-1 for authenticator-app compatibility but can upgrade if SAMA tightens guidance.
5. **MIT license** — compatible with the platform's overall licensing posture.

### Honourable mention notes

- `com.eatthepath:java-otp` is technically lovely (great test coverage, clean API) but doesn't ship a QR generator. Pulling it would mean a second dep + glue code — net cost higher than samstevens for the same outcome.
- `com.warrenstrange:googleauth` was the original 2016-era choice for many Java MFA projects. Still works, but API is older-style (mutable builders, more boilerplate) and the maintenance cadence is slower than samstevens.

---

## 3. Enrollment & verification flow

Wire diagram for the Sprint-13 follow-up:

```
[User]                  [Backend]                                   [Authenticator app]
  │
  │ POST /api/iam/mfa/enroll
  ├──────────────────────►
  │                        Generate 160-bit secret (SecureRandom)
  │                        Encode as base32
  │                        Persist (encrypted) status=PENDING_VERIFY
  │                        Build otpauth://totp/TSS-GRC:{username}?secret=...&issuer=TSS-GRC
  │                        Render QR PNG with ZXing
  │ ◄──────────────────────
  │ 200 OK { qrPng, secretBase32 }
  │
  │ Scan QR
  ├────────────────────────────────────────────────────────────────────────►
  │                                                                Display 6-digit code
  │
  │ POST /api/iam/mfa/verify { code: "123456" }
  ├──────────────────────►
  │                        DefaultCodeVerifier.isValidCode(secret, "123456")
  │                        On success: mark status=ENROLLED
  │                                    record last_used_time_step
  │                                    generate + bcrypt-hash 10 backup codes
  │ ◄──────────────────────
  │ 200 OK { backupCodes: ["XXXX-XXXX", …] }   ← shown once, never again
  │
  │ (next login)
  │ POST /api/auth/login { username, password }
  ├──────────────────────►
  │ ◄──────────────────────
  │ 200 { mfaRequired: true, mfaToken }
  │
  │ POST /api/auth/mfa/verify { mfaToken, code: "654321" }
  ├──────────────────────►
  │                        Verify TOTP or backup code
  │ ◄──────────────────────
  │ 200 { accessToken, refreshToken … }
```

---

## 4. Backup codes

| Aspect | Decision |
|---|---|
| **Count** | 10 codes per user |
| **Format** | 10 chars, base32 (`Crockford` alphabet — no I/L/O/0 ambiguity), displayed as `XXXXX-XXXXX` |
| **Storage** | bcrypt-hashed (cost factor 12) — no plaintext server-side after generation |
| **Use** | Single-use — marked `consumed_at = now()` on successful verification |
| **Regenerate** | Allowed from the Settings page; invalidates all previously unused codes (atomic swap) |
| **Display** | Once, immediately after enrollment or regeneration. After that, codes are write-only — the user must save them out-of-band. |

### Saudi-specific framing

The bilingual UI copy at backup-code-display time will read:

> **EN:** "Save these codes somewhere safe — a password manager, a printed sheet locked in your desk, or a secure note in your bank's vault app. You will not be able to see them again."

> **AR:** "احفظ هذه الرموز في مكان آمن — مدير كلمات المرور أو ورقة مطبوعة محفوظة في درج مكتبك، أو ملاحظة آمنة في تطبيق الخزينة لدى المصرف. لن تتمكن من رؤيتها مرة أخرى."

(Note: **مدير كلمات المرور** for "password manager," **تطبيق الخزينة** as a Saudi-banking-native phrasing of "vault app." Avoid the literal **خزنة** which connotes a physical safe.)

---

## 5. MFA enforcement policy

Set per-role, with platform-level overrides. Defaults:

| Role | MFA required? |
|---|---|
| `PLATFORM_ADMIN` | **Yes — mandatory** (cannot disable per-user) |
| `RISK_MANAGER`, `COMPLIANCE_LEAD`, `DPO` | **Yes** — enforced, may be temporarily waived by `PLATFORM_ADMIN` with audit trail |
| `AUDITOR` | Yes — for write-permission actions |
| `STANDARD_USER` | Optional — recommended, user-toggleable |

The enforcement check happens in `JwtAuthenticationFilter` immediately after JWT validation — a `mfa_verified=true` claim must be present for any privileged-role request. Time-bounded: the MFA claim re-expires every 12 hours (configurable via `grc.security.mfa-claim-expiry-hours`).

---

## 6. Dependency footprint

The dep added to `pom.xml`:

```xml
<dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp</artifactId>
    <version>1.7.1</version>
</dependency>
```

Transitive footprint (verified via `mvn dependency:tree` against 1.7.1):

- `commons-codec` 1.x — already in classpath via Spring
- `com.google.zxing:core` + `com.google.zxing:javase` — new (~700 KB combined, QR-code encoding)
- `org.bouncycastle:bcprov-jdk15on` — new (~5 MB) — the bcprov dep is the elephant; if footprint is a hard constraint at packaging time, the `RuntimeException`-on-fips-policy fallback in samstevens lets you exclude bcprov and rely on the JDK's built-in HMAC implementation. We accept it as-is for now; revisit if image size becomes a problem.

---

## 7. Security review notes

- **Secret entropy:** 160 bits via `SecureRandom`. RFC 4226 recommends ≥ 128 bits; we exceed.
- **Algorithm:** SHA-1 (the RFC 6238 default — supported by every authenticator app). The protocol doesn't depend on collision resistance, so SHA-1's known weaknesses don't apply here.
- **Replay protection:** server tracks `last_used_time_step` per user. A code from step T is rejected once T is recorded as used.
- **Brute-force protection:** the existing `grc.security.max-failed-login-attempts: 5` lockout policy applies to MFA verification as well as password attempts — locked accounts can't grind 6-digit codes.
- **Side-channel:** verification uses constant-time comparison (samstevens does this internally — confirmed in v1.7.1 source).
- **PII:** the otpauth URI contains the username and issuer. We strip the username and replace with the user ID for the QR payload to avoid leaking employee identities if the QR is photographed.

---

## 8. Code references

- `tss-grc-backend/pom.xml` — `samstevens-totp.version = 1.7.1`
- `tss-grc-backend/src/main/java/com/tss/grc/module/iam/mfa/TotpService.java` — API surface (interface)
- `tss-grc-backend/src/main/java/com/tss/grc/module/iam/mfa/TotpServiceStub.java` — `@Service` stub that throws `UnsupportedOperationException` referencing this issue

---

## 9. Follow-up work (Sprint 13)

1. Real `TotpService` implementation backed by `DefaultSecretGenerator` + `DefaultCodeVerifier`
2. `iam.user_mfa` table (encrypted secret, status, last_used_time_step, enrolled_at)
3. `iam.user_backup_code` table (bcrypt hash, consumed_at)
4. Enrollment UI in `tss-grc-ui-v2` — QR display, code entry, backup-code reveal
5. `JwtAuthenticationFilter` mfa_verified claim enforcement
6. `mfa_required` flag on the role table + per-role policy enforcement
7. Bilingual email template for first-time MFA setup invitation (extend issue #007's template set)
