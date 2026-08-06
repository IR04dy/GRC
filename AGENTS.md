# AGENTS.md

**This file is a pointer, not a second authority.**

## Read [`CLAUDE.md`](CLAUDE.md)

`CLAUDE.md` is the single governing instruction file for this repository, for every
agent and every tool. Everything you need — build commands, the JDK-17 requirement,
credentials, house rules, the enforced ratchets, the next free migration version — is
there and is kept current there.

---

## Why this file no longer duplicates it

`AGENTS.md` used to be `CLAUDE.md` copied verbatim with a blind `Claude`→`Codex`
substitution. That copy asserted a **remote that does not exist**
(`Ayman-Server/Codex-GRC-2`; the real remote is `Ayman-Server/Claude-GRC-2`) — the
defect W0-6 and finding **F-8** were raised for. The remote was corrected on
2026-07-25 in `99a719da`.

Correcting the remote fixed one wrong fact and left the mechanism that produced it:
**a duplicated instruction file with nothing keeping it in sync.** By 2026-08-06 the
copy had drifted on every figure this programme had spent weeks correcting, and each
one was individually dangerous to an agent that read this file instead of `CLAUDE.md`:

| This file said | Actually | What it would have cost |
|---|---|---|
| Authorization ratchet **≤ 62** | **21**, enforced by `ControllerAuthorizationRatchetTest` | Three times the real ceiling — it invites waving through unguarded write endpoints the build in fact rejects |
| `V1→V295 packaged; next free **V296**` | head **V358** | V296 has been taken since 2026-07-13. Reusing a taken version is a **Flyway checksum failure on the deployed server** |
| **94**-permission catalogue, **24** modules | **114** permissions, **31** modules (of **59** packages) | Scopes 35 module packages out of the RBAC catalogue, the test bar and every plan |

None of these were introduced here. They were true in `CLAUDE.md` once, were found to
be false, were fixed there — and stayed false here, because a copy does not receive
corrections. Two authorities for one truth is the same root cause the findings register
records as **RC-A**; this was its instance in the governance layer.

So the duplication is gone rather than refreshed. A pointer cannot drift.

`GovernanceDocumentIntegrityTest` keeps it that way: it fails if this file grows back
into a copy of `CLAUDE.md`, and it fails if any instruction file names a remote that
does not exist.
