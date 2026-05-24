# JWT secret key management & rotation

Closes Open Issue **#001** — defines how the `JWT_SECRET` (and optional `JWT_SECRET_PREVIOUS`) environment variables are generated, rotated, and revoked across `dev`/`staging`/`prod`.

| Regulatory mapping | Control |
|---|---|
| **NCA ECC-2** | Control 2-3 — Cryptographic key management (generation strength, rotation cadence, revocation procedures) |
| **SAMA CSF** | 3.3.1 — Cryptography (key lifecycle, escrow, separation of duties) |
| **PDPL Art. 19** | Security safeguards proportionate to the risk of processing |
| **ISO 27001 A.10.1** | Cryptographic controls |

---

## 1. Background

The TSS GRC backend issues stateless JWTs. Tokens are signed with `HS256` using a single shared secret loaded at boot from the `JWT_SECRET` environment variable. The secret is the **only** thing stopping an attacker who has read your config from minting tokens for any user in any tenant — treat it as a tier-1 secret, equivalent to a root DB password.

The class enforcing the rules: `com.tss.grc.security.JwtTokenProvider` (see `validateSecret(String)`).

Boot-time requirements (already enforced — fail-fast at startup):

- `JWT_SECRET` env var **must** be set
- Length **≥ 32 ASCII characters** (256 bits — minimum HS256 strength)
- **Must not** contain the strings `change-this`, `change_me`, `placeholder`, or `provide_jwt_secret` (case-insensitive)

If any of these checks fail the application refuses to start, by design.

---

## 2. Generating a strong secret

Use a cryptographically strong RNG. Examples (pick any one):

```bash
# OpenSSL — 48 bytes → 64 base64 chars (> minimum 32)
openssl rand -base64 48

# /dev/urandom — same idea
head -c 48 /dev/urandom | base64

# Python
python -c "import secrets, base64; print(base64.b64encode(secrets.token_bytes(48)).decode())"
```

**Do not** use:

- A passphrase, even a long one (entropy is too low even at 40+ chars)
- A UUID (only ~122 bits of entropy, but more importantly, UUIDs leak structure)
- A value committed to git, ever — even rotation history files

---

## 3. Storing the secret

| Environment | Mechanism |
|---|---|
| `dev` | `.env` file at repo root, **gitignored**. Each developer generates their own. |
| `staging` | Docker secret or container-runtime env injection — never baked into the image |
| `prod` | KMS-backed secret store (AWS Secrets Manager / Azure Key Vault / HashiCorp Vault). Injected into the container at start. |

The secret **must not** appear in:

- `application*.yml` (any profile)
- Container image layers
- CI/CD logs (use masked variables)
- Audit log payloads
- Sentry error breadcrumbs (it's an env var — verify Sentry's PII scrubber catches `JWT_*`)

---

## 4. Rotation policy

| Trigger | Frequency |
|---|---|
| **Scheduled** | Every **90 days** (per NCA ECC-2 control 2-3) |
| **Personnel change** | Within 24h of any departure with secret access |
| **Suspected compromise** | Immediately — see §6 |
| **Major release** | Optional but recommended |

The 90-day cadence aligns with the existing `grc.security.password-expiry-days: 90` policy and keeps a single calendar reminder cycle for the security team.

---

## 5. Performing a zero-downtime rotation (24h overlap window)

The platform supports a **24-hour overlap window** via the optional `JWT_SECRET_PREVIOUS` env var. During the window, both the old and new keys validate; only the new key is used to sign newly issued tokens. Old tokens age out naturally as their `exp` claim is reached (15 min for access tokens, 7 days for refresh tokens — the refresh token lifetime sets the practical overlap minimum).

### Step-by-step

> **Note:** for a fully zero-downtime rotation, the overlap window should be **≥ the refresh-token lifetime** (default 7 days), not 24h. The 90-day cadence still applies to when the *primary* key is rotated; the overlap window length is independent. Use 24h only if you can accept that all active refresh tokens older than 24h will require a re-login.

1. **Generate the new secret** (see §2). Call it `NEW_SECRET`. Save the current value of `JWT_SECRET` as `OLD_SECRET`.

2. **Update the secret store** with both values:
   - `JWT_SECRET_PREVIOUS` = `OLD_SECRET`
   - `JWT_SECRET` = `NEW_SECRET`

3. **Rolling-restart the backend pods/containers.** Each pod re-reads env vars on startup.
   - Watch the boot log for: `JwtTokenProvider initialised: … previousKey=present (rotation grace window active)`
   - Tokens signed by `OLD_SECRET` will continue to validate via the fallback path
   - Tokens issued from now on are signed by `NEW_SECRET`

4. **Wait the overlap window.** Minimum: 24h (covers same-day clock skew + delayed clients). Recommended: 7 days + 1h (longer than the refresh-token TTL).

5. **Clear `JWT_SECRET_PREVIOUS`.** Update the secret store so the env var is unset/blank. Rolling-restart again.
   - The boot log should now say: `… previousKey=absent`
   - Any tokens still signed by `OLD_SECRET` (extreme outliers — refresh tokens that haven't been used) will fail verification on next request and the user will be redirected to login. This is intentional.

6. **Audit log entry.** Record the rotation with: rotation operator, old key fingerprint (SHA-256 of the secret bytes — never the secret itself), new key fingerprint, start/end timestamps.

### What happens if the operator skips step 5?

Nothing immediately — but the *next* rotation must rotate `JWT_SECRET_PREVIOUS` first (to the about-to-be-retired `JWT_SECRET`), then `JWT_SECRET` to the new value, in a single deploy. Failing to clear leaves an unused-but-still-validating key in production, which weakens the security posture.

---

## 6. Emergency revocation (suspected compromise)

If `JWT_SECRET` is believed to be compromised — leaked in logs, a stolen laptop with `.env`, a departing administrator — the response is **immediate, no overlap window**:

1. **Generate `NEW_SECRET`** (see §2).
2. **Set `JWT_SECRET` = `NEW_SECRET` and clear `JWT_SECRET_PREVIOUS`** (`unset` or set to empty string) in the secret store.
3. **Rolling-restart the backend.**
4. **All existing JWTs are now invalid.** Effects:
   - Active access tokens: next API call returns `401 Unauthorized` → client redirects to login
   - Active refresh tokens: next refresh attempt returns `401` → client redirects to login
   - WebSocket connections: dropped on next heartbeat (JwtAuthenticationFilter re-validates)
5. **Force-expire active sessions in the DB.** The platform doesn't currently maintain a server-side refresh-token store (see Open Issue **#002**), so this step is implicit — there's nothing on the server to invalidate. When #002 is resolved, this step will become "delete all rows from `auth.refresh_tokens`".
6. **Notify the security team and file a CAPA** in the platform under the `SECURITY_INCIDENT` category. Cite this runbook section.
7. **Audit log entry** as in §5 step 6, plus `revocation_reason`.

### Session invalidation behaviour (user-facing)

After an emergency revocation:

- **Browser sessions** — on the next XHR/fetch, the `Authorization` header is rejected. The frontend's 401 interceptor in `tss-grc-ui-v2/src/lib/api.ts` clears `sessionStorage` and routes to `/login`.
- **Refresh attempts** — the HttpOnly refresh cookie is now signed by the old key. The `/api/auth/refresh` endpoint returns 401. The browser sees the same login redirect.
- **Mobile / API clients** — receive 401 on the next call. They must re-authenticate using stored credentials (or re-prompt the user, depending on the client's design).

A bilingual banner can be shown post-login by emitting an F8 event of trigger `SESSION_INVALIDATED_BY_KEY_ROTATION` from the deployment pipeline — out of scope for issue #001, but recommended for issue #002 follow-up.

---

## 7. Operational checks

### Verify the rotation is active

```bash
# Tail the backend log after restart
grep "JwtTokenProvider initialised" /var/log/tss-grc/backend.log | tail -1
# Expected during overlap window:
#   JwtTokenProvider initialised: access=900000ms refresh=604800000ms keyBytes=64 previousKey=present (rotation grace window active)
# Expected steady state:
#   JwtTokenProvider initialised: access=900000ms refresh=604800000ms keyBytes=64 previousKey=absent
```

### Detect a rogue boot with a missing secret

The application refuses to start — the orchestrator (Kubernetes / systemd / Docker Compose) will see the container exit with `IllegalStateException: JWT_SECRET environment variable is not set`. Wire this to your alert pipeline (`grep -F "JWT_SECRET environment variable is not set"` on the boot log).

---

## 8. Audit & evidence

For SAMA CSF / NCA ECC-2 audit evidence, retain:

1. The generation command output (without the secret itself — record the SHA-256 fingerprint and the operator's username)
2. The rotation calendar entries (auto-created via the platform's Calendar module, event type `KEY_ROTATION`, 90-day recurrence)
3. The audit-log entries from §5 step 6 / §6 step 7
4. The deploy log showing both the boot-time validation pass and the steady-state log line from §7

Recommended evidence retention: **7 years**, aligned with the platform's audit-log retention policy.

---

## 9. Known gaps & follow-ups

| Gap | Tracked in |
|---|---|
| No server-side refresh token store — can't revoke a single user's session without rotating the global key | Open Issue **#002** |
| No `kid` (key ID) header on JWTs — would let multiple primary keys coexist by token claim rather than try/fallback | Sprint 13 backlog |
| No HSM integration — secret lives in env vars rather than being signed by a KMS endpoint | Sprint 14 backlog |

---

## 10. Code references

- `tss-grc-backend/src/main/java/com/tss/grc/security/JwtTokenProvider.java`
  - `validateSecret(String)` — boot-time strength validation
  - `loadPreviousSecret(String)` — overlap-key loader (returns null when unset)
  - `parseClaims(String)` — verification with primary-then-previous fallback
- `tss-grc-backend/src/main/resources/application.yml`
  - `grc.jwt.secret` ← `JWT_SECRET`
  - `grc.jwt.secret-previous` ← `JWT_SECRET_PREVIOUS`
- `tss-grc-backend/src/test/java/com/tss/grc/security/JwtTokenProviderTest.java`
  - Locks the validation contract; covers null/blank/short/placeholder/rotation-key cases
