# TSS GRC Platform Secure Code Review

**Classification:** Confidential — engineering and acceptance use  
**Review date:** 2026-07-26  
**Assessed baseline:** `695c907cab40a7f3321874389ea6b510cb7126ae`  
**Branch at completion:** `feat/approval-field-binding`  
**Primary applications:** Spring Boot API and React SPA (`tss-grc-ui-v2`)  
**Reviewer position:** Independent application-security reviewer and government acceptance committee  
**Opinion:** **REJECT FOR PRODUCTION ACCEPTANCE until the P0 release gates in this report are closed**

## 1. Executive opinion

The platform has a materially better security foundation than a typical product at this stage: method-level permissions are widespread, tenant filtering is an explicit engineering rule, decision provenance has received recent remediation, refresh tokens are stored in an HttpOnly cookie, password hashing uses BCrypt cost 12, security headers are present, and the selected 149 security/governance tests pass.

That foundation is not sufficient for an enterprise GRC production acceptance. One authentication defect is directly exploitable and was reproduced against the running application:

> A seven-day refresh token is accepted as a normal API bearer token. It remains accepted after logout revokes it in the refresh-token store.

This defeats the intended access-token lifetime and refresh-token revocation control. The review also found high-risk authorization defects in change approvals, attachment access, committee vote delegation, public tracking-token handling, browser-side data retention, file malware controls, and current frontend dependencies.

### Risk count

| Severity | Count |
|---|---:|
| Critical | 1 |
| High | 8 |
| Medium | 3 |
| Low / informational findings | 0 |

### Acceptance decision

| Decision lens | Opinion |
|---|---|
| Saudi government acceptance | Rejected pending P0 closure and independent retest |
| Regulated enterprise production | Rejected pending P0/P1 closure |
| Controlled internal pilot | Conditional; no real sensitive data and no public portals |
| Developer/demo environment | Acceptable with documented limitations |

This is a secure-code review, not a penetration-test certificate, PDPL legal opinion, NCA certification, or SAMA compliance attestation.

## 2. Scope and method

### Reviewed

- authentication, JWT generation/validation, refresh rotation and logout;
- method authorization, tenant context and tenant-write guard;
- high-consequence change and committee decision paths;
- generic attachments, evidence storage and file upload;
- public token-credentialed portals and production reverse-proxy logging;
- WebSocket authentication and subscription isolation;
- CCM custom SQL execution;
- browser token/data storage and production-mode behavior;
- dependency status exposed by `npm audit`;
- relevant deployment configuration and automated security tests.

### Verification performed

| Verification | Result |
|---|---|
| Focused Maven security/governance suite | **149 tests passed; 0 failed** |
| Authorization ratchet | **21** intentionally unguarded write endpoints |
| Export authorization ratchet | **40** file endpoints; **0** missing `MODULE:EXPORT` |
| TypeScript `tsc --noEmit` | Passed |
| `npm audit --omit=dev` | 1 critical and 2 moderate vulnerable production packages |
| Live access-token bearer request | HTTP 200 |
| Live refresh-token-as-bearer request | **HTTP 200 — vulnerability confirmed** |
| Logout followed by revoked-refresh-as-bearer | **HTTP 200 — revocation bypass confirmed** |

The live authentication probe logged in with the documented development administrator, printed status codes only, logged out, and did not print or retain either token.

### Not performed

- destructive database testing or cross-tenant data mutation;
- malware detonation;
- authenticated testing of every one of the hundreds of routes;
- source-assisted external network penetration testing;
- current full backend software-composition scan;
- cloud, host, SQL Server, Active Directory, or production-secret review.

## 3. Finding summary

| ID | Severity | Finding | Release gate |
|---|---|---|---|
| SCR-001 | Critical | Refresh JWTs are accepted as API bearer tokens, including after revocation | P0 |
| SCR-002 | High | Bearer authentication ignores disabled/locked state and does not bind the JWT tenant to the live user | P0 |
| SCR-003 | High | `CHANGE:WRITE` can perform approval/rejection states and forge PIR reviewer provenance | P0 |
| SCR-004 | High | Generic attachment access is tenant-scoped but not object- or classification-authorized | P0 |
| SCR-005 | High | Public bearer URLs are logged in plaintext and stored as reusable plaintext credentials | P0 |
| SCR-006 | High | Global `ADMIN_WRITE` grants on-behalf voting across all committees | P1 |
| SCR-007 | High | Sensitive GRC state persists in tenant-agnostic browser `localStorage` after logout | P1 |
| SCR-008 | High | Evidence and attachment uploads have no real malware, content-signature, or CDR gate | P1 |
| SCR-009 | High | Production frontend tree contains a critical WebSocket dependency advisory and affected router packages | P1 |
| SCR-010 | Medium | Application rate limiting trusts spoofable `X-Forwarded-For` and is node-local | P2 |
| SCR-011 | Medium | Tenant guard has fail-open execution contexts and over-broad exception classification | P2 |
| SCR-012 | Medium | CCM custom SQL becomes an unsafe cross-tenant inference surface if enabled | P2 |

---

## 4. Detailed findings

## SCR-001 — Refresh JWTs are accepted as API bearer tokens, including after revocation

- **Category:** Authentication / session management
- **Severity:** Critical
- **Current State:** `JwtTokenProvider` builds access and refresh tokens through the same token builder. Neither token carries a `typ`, purpose, audience, or equivalent claim. `validateToken` validates any correctly signed JWT. `JwtAuthenticationFilter` accepts any token for which that generic validation succeeds. Refresh-token revocation is checked only by `/api/auth/refresh`, not by bearer authentication.
- **Problem:** A refresh token stolen from the cookie, endpoint telemetry, browser tooling, host, backup, or another defect can be sent directly as `Authorization: Bearer`. It receives the full current authority set loaded for the user and remains usable for the refresh lifetime.
- **Root Cause:** Token signature validity is treated as authorization to use the token in every authentication context. Token purpose and server-side session state are not part of bearer validation.
- **Impact:** Account takeover with a substantially longer persistence window than the documented 15-minute access-token lifetime.
- **Business Impact:** A malicious action appears to be a legitimate user action in the GRC audit trail. Regulatory submissions, approvals, risk acceptances, evidence and personal data can be accessed or changed under the victim identity.
- **Technical Impact:** Logout and administrative refresh-token revocation do not terminate use of the same token as a bearer. The seven-day token becomes a de facto seven-day access token.
- **UX Impact:** Users and administrators receive a false assurance that logout or session revocation ended access.
- **Sales Impact:** This is a production-blocking authentication defect for government, banking, listed-company, and critical-infrastructure buyers.
- **Recommendation:** Add an immutable token-purpose claim (`ACCESS` / `REFRESH`) and separate validation entry points. The bearer filter must accept only `ACCESS`. The refresh endpoint must accept only `REFRESH` and require an active server-side token-store row. Prefer separate audiences and separate signing keys. Add a session/security version so password reset, disablement and compromise response can invalidate access tokens when required.
- **Alternative Solutions:** Use opaque server-side access sessions; or use an OAuth 2.1/OIDC authorization server with purpose-bound access and refresh tokens.
- **Trade-offs:** Purpose claims are a small compatible code change but existing sessions must be invalidated during rollout. Server-side access introspection gives immediate revocation at the cost of a lookup/cache dependency.
- **Estimated Effort:** 2–4 engineering days plus migration/relogin and security regression tests.
- **Priority:** P0 — fix before any pilot containing real data.
- **Expected ROI:** Extremely high; closes a directly demonstrated account/session takeover path.
- **References:** `JwtTokenProvider.java:141-185, 238-271, 312-327`; `JwtAuthenticationFilter.java:47-64`; `AuthController.java:147-190`; [OWASP API2:2023 Broken Authentication](https://owasp.org/API-Security/editions/2023/en/0xa2-broken-authentication/).

**Exploit proof:** The running API returned HTTP 200 when the refresh cookie value was used as the bearer on `GET /api/v1/changes`. After `/api/auth/logout` returned 204 and revoked the refresh record, the same refresh value still returned HTTP 200 as bearer.

## SCR-002 — Bearer authentication ignores disabled/locked state and does not bind the JWT tenant to the live user

- **Category:** Authentication / multi-tenancy
- **Severity:** High
- **Current State:** The bearer filter loads the current `UserDetails`, then unconditionally constructs an authenticated token. It does not reject `!isEnabled()` or `!isAccountNonLocked()`. It obtains the tenant from the JWT and does not compare it with the live user's organisation. The refresh endpoint mints new tokens from old claims without loading the user.
- **Problem:** Disabled or locked accounts can continue using already-issued tokens. A stale tenant claim can be combined with the live user's current roles if a user is reassigned. Platform-admin organisation switching is legitimate, but the code does not distinguish that controlled case from an ordinary user mismatch.
- **Root Cause:** Authentication state, authority state and tenant state are assembled from two sources without validating their invariants.
- **Impact:** Delayed termination of compromised or departed-user access; possible stale-tenant authorization after user movement.
- **Business Impact:** Offboarding and emergency access-revocation procedures cannot be relied on as immediate controls.
- **Technical Impact:** Spring's normal account-status checks are bypassed because the filter manually creates an authenticated token after loading the user.
- **UX Impact:** Administrators can disable or lock a user and still observe activity from that identity.
- **Sales Impact:** Weakens IAM, SAMA, NCA and customer access-governance evidence.
- **Recommendation:** Reject disabled, locked or expired accounts in the bearer filter and refresh flow. For ordinary users require `JWT.org == liveUser.organisationId`. Represent platform-admin tenant switching with an explicit impersonation/switch claim, short expiry, audit event and authorization check. Revoke all refresh tokens on disablement, password reset and material role change.
- **Alternative Solutions:** Add a cached user security stamp/version claim and reject stale versions; or perform token introspection through a centralized session service.
- **Trade-offs:** A live lookup already occurs in the bearer filter, so the status checks add negligible cost. A security-stamp strategy reduces repeated logic but requires version management.
- **Estimated Effort:** 3–5 engineering days.
- **Priority:** P0.
- **Expected ROI:** High; makes offboarding, lockout and tenant boundaries enforceable.
- **References:** `JwtAuthenticationFilter.java:50-64`; `CustomUserDetailsService.java:50-64`; `GrcUserDetails.java:44-47`; `AuthController.java:140-190`.

## SCR-003 — `CHANGE:WRITE` can perform approval/rejection states and forge PIR reviewer provenance

- **Category:** Broken function-level authorization / decision integrity
- **Severity:** High
- **Current State:** The generic `POST /api/v1/changes/{id}/transition` endpoint requires only `CHANGE:WRITE`. It accepts `EMERGENCY_APPROVED`, `CAB_APPROVED`, `CAB_REJECTED` and `POST_REVIEW`. CAB approval requires recorded votes, but the actor who effects the final decision still needs only WRITE. CAB rejection requires no recorded reject vote. Emergency approval checks only that the change type is `EMERGENCY`. The PIR reviewer name comes from `reviewer` or `reviewedBy` in the request body despite the source comment saying sign-off is system-stamped.
- **Problem:** Editing authority is also decision authority. A writer can approve an emergency, reject a CAB change, complete a review, or name another person as reviewer.
- **Root Cause:** A generic state-machine endpoint has one static permission and does not receive the authenticated actor as decision data.
- **Impact:** Segregation of duties and decision provenance can be bypassed.
- **Business Impact:** Unauthorized operational changes can be represented as CAB/emergency-approved. Post-implementation-review evidence can attribute sign-off to a person who did not perform it.
- **Technical Impact:** The state machine validates transition shape but not actor capability per target state.
- **UX Impact:** The UI can display authoritative approval and reviewer labels that the server did not authenticate.
- **Sales Impact:** This is unacceptable for ITIL change enablement, NCA-controlled environments and regulated technology operations.
- **Recommendation:** Replace decision targets with named commands/endpoints guarded by `CHANGE:APPROVE`, or add transition-aware service authorization. Require an authenticated actor for every transition and server-stamp reviewer user ID/name. CAB rejection must be backed by a reject vote or explicit authorized chair decision. Emergency approval requires a separate role, rationale, time limit and retrospective CAB review.
- **Alternative Solutions:** Model transitions as workflow-engine tasks with assignee, maker-checker and immutable decision events.
- **Trade-offs:** Named commands increase API surface but make the policy auditable. Dynamic authorization preserves one endpoint but is harder to prove and test.
- **Estimated Effort:** 5–8 engineering days.
- **Priority:** P0.
- **Expected ROI:** Extremely high for audit credibility and regulated sales.
- **References:** `ChangeManagementController.java:94-100, 124-130`; `ChangeManagementService.java:216-258, 284-293`; [OWASP API5:2023 Broken Function Level Authorization](https://owasp.org/API-Security/editions/2023/en/0xa5-broken-function-level-authorization/).

## SCR-004 — Generic attachment access is tenant-scoped but not object- or classification-authorized

- **Category:** Broken object-level authorization / data classification
- **Severity:** High
- **Current State:** Generic attachment endpoints require a global EVIDENCE permission and correctly filter the attachment row by organisation. They do not verify that the user may access the parent `entityType/entityId`. `entityType` is client-controlled. `classification` can be `PUBLIC`, `INTERNAL`, `CONFIDENTIAL` or `RESTRICTED`, but it is metadata only and never affects list, download, update, version or delete authorization.
- **Problem:** Any user with `EVIDENCE_READ` can enumerate or directly request attachments belonging to records in modules for which the user may have no record-level access. Classification does not restrict access.
- **Root Cause:** The generic file service has no parent-resource authorization contract or classification policy.
- **Impact:** Intra-tenant horizontal disclosure of evidence, contracts, minutes and other sensitive files.
- **Business Impact:** Confidential governance evidence can be disclosed to an unauthorized employee even though cross-tenant isolation remains intact.
- **Technical Impact:** File IDs become global intra-tenant object references protected only by a coarse module permission.
- **UX Impact:** A `RESTRICTED` label communicates protection that the backend does not enforce.
- **Sales Impact:** Fails enterprise document-security expectations and undermines PDPL/confidentiality claims.
- **Recommendation:** Introduce an `AttachmentAccessPolicy` registry keyed by parent entity type. Every operation must resolve the parent and invoke its read/write/delete policy. Enforce classification clearance and purpose/need-to-know. Store canonical parent type, reject unknown types, and test cross-module access with two roles.
- **Alternative Solutions:** Move files into module-owned endpoints/services; or use capability-scoped signed download URLs issued only after parent authorization.
- **Trade-offs:** A centralized policy registry preserves reuse but creates a governed extension point. Module-owned storage is clearer but duplicates API plumbing.
- **Estimated Effort:** 8–15 engineering days across the parent types in active use.
- **Priority:** P0 for sensitive production data.
- **Expected ROI:** High; closes a broad confidentiality gap in a core evidence platform.
- **References:** `AttachmentController.java:37-108`; `AttachmentService.java:43-60, 160-179`; [OWASP API1:2023 Broken Object Level Authorization](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/); [SDAIA PDPL guidance](https://dgp.sdaia.gov.sa/wps/portal/pdp/knowledgecenter/details/GPDPL).

## SCR-005 — Public bearer URLs are logged in plaintext and stored as reusable plaintext credentials

- **Category:** Sensitive credential exposure / public portals
- **Severity:** High
- **Current State:** Whistleblower, complaint, vendor-attestation and external-workflow links place the credential in the URL path. The production nginx log format records `$request` and `$http_referer`, so the credential is written verbatim. The application rate-limit filter also logs the request URI on throttling, and the audit service records request URIs when invoked. Public tokens are stored in plaintext database columns. Some token families expire; others are long-lived or reusable by design.
- **Problem:** Reverse-proxy logs, SIEM forwarding, support bundles, browser history, email security scanners and database readers become secondary credential stores.
- **Root Cause:** A bearer secret is used as a durable URL identifier without a redaction/exchange boundary.
- **Impact:** Anyone obtaining a logged URL may read status/data or submit a response as the external party.
- **Business Impact:** Whistleblower confidentiality, vendor evidence and personal-data portal trust are exposed to operational staff and systems with log access.
- **Technical Impact:** Secret rotation and revocation are inconsistent across public token families; plaintext storage permits immediate replay after database disclosure.
- **UX Impact:** The user is told to save a tracking token, but is not told that infrastructure logs may retain it.
- **Sales Impact:** Material blocker for ethics hotline, PDPL and regulated outsourcing use cases.
- **Recommendation:** Redact token path segments at nginx and application logging immediately. Store only a keyed hash of public tokens. Exchange the initial URL token once for a short-lived, HttpOnly, narrowly scoped portal session. Define expiry, rotation, single-use behavior and revocation per portal. Set `Referrer-Policy: no-referrer` for public credential pages.
- **Alternative Solutions:** Use a reference code plus a second factor/OTP; or put the secret in the URL fragment and exchange it client-side so it is not sent in the HTTP request line.
- **Trade-offs:** One-time exchange adds a portal session and recovery process. Reference plus OTP adds friction but is preferable for higher-risk portals.
- **Estimated Effort:** 8–12 engineering days across the shared token service and portals; nginx redaction is less than one day.
- **Priority:** P0; redact logs immediately.
- **Expected ROI:** Extremely high due to the sensitivity of whistleblower and personal-data workflows.
- **References:** `SecurityConfig.java:114-144`; `PublicEndpointRateLimitFilter.java:113-116`; `AuditLogService.java:102-106`; `docs/operations/nginx-production.conf:58-72`; `WorkflowRequestRecipient.java:53-54`; `WhistleblowerCase.java:47-49`; [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html).

## SCR-006 — Global `ADMIN_WRITE` grants on-behalf voting across all committees

- **Category:** Authorization / governance decision integrity
- **Severity:** High
- **Current State:** Committee voting now binds the real actor, validates the active roster, canonicalizes the target member and records `on_behalf`. Those are strong controls. However, the controller sets `mayRecordOnBehalf=true` for any user holding global `ADMIN_WRITE` or `ROLE_PLATFORM_ADMIN`; it does not prove that the actor is secretary or chair of the specific committee.
- **Problem:** A GRC or organisation administrator with global admin-write authority can record a vote for any active voting member of any committee.
- **Root Cause:** A committee-scoped delegation is represented by a platform-wide module permission.
- **Impact:** A privileged administrator can manufacture a quorate outcome while the audit trail accurately records only that it was done “on behalf.”
- **Business Impact:** Board/audit/risk committee decisions may not represent member intent.
- **Technical Impact:** Roster membership is validated for the target voter but delegated authority is not validated for the actor and committee pair.
- **UX Impact:** Committee members may see a valid-looking recorded vote they never cast.
- **Sales Impact:** High reputational and acceptance risk for board governance features.
- **Recommendation:** Create committee-specific assignments (`CHAIR`, `SECRETARY`) linked to immutable user IDs and effective dates. Authorize on-behalf recording only for an active assignment to that committee. Require rationale/source (signed minutes or in-meeting attestation), notify the represented member, and consider confirmation for material resolutions.
- **Alternative Solutions:** Disable on-behalf voting and require each member to vote; or allow batch minute entry only during a controlled meeting-close workflow with maker-checker approval.
- **Trade-offs:** Direct member voting is strongest but may not fit all board practices. Controlled minute entry preserves real-world operation with stronger evidence requirements.
- **Estimated Effort:** 5–8 engineering days plus migration of committee assignments.
- **Priority:** P1.
- **Expected ROI:** High; protects the product's most sensitive governance record.
- **References:** `CommitteeResolutionController.java:121-146`; `CommitteeResolutionService.java:176-221`.

## SCR-007 — Sensitive GRC state persists in tenant-agnostic browser `localStorage` after logout

- **Category:** Client-side data protection / tenant isolation
- **Severity:** High
- **Current State:** Risk, privacy, crisis, BCM and other workflows persist records under global keys such as `grc.privacy.ropa.v1`, `grc.privacy.dpia.v1`, `grc.risk.treatment.v1` and `grc.crisis.active.v1`. The keys are not scoped by organisation or user. Production mode may use local storage as an offline cache. Logout clears only access-token and user values from `sessionStorage`.
- **Problem:** A subsequent user on the same browser profile can inherit data from another user or tenant. Browser extensions and any same-origin script can read it. The data survives logout and browser restart.
- **Root Cause:** Demo persistence and production persistence share the same browser-origin storage model.
- **Impact:** Cross-session/intra-browser disclosure and stale data presented as current.
- **Business Impact:** Risk, privacy, crisis and governance content may remain on unmanaged endpoints outside server retention and audit controls.
- **Technical Impact:** Server authorization, retention, deletion and tenant isolation do not govern the cached copy.
- **UX Impact:** Users can see stale or foreign records and cannot distinguish them from authoritative server data.
- **Sales Impact:** Incompatible with credible enterprise endpoint, PDPL and shared-workstation controls.
- **Recommendation:** Disable demo/local-storage persistence in production builds. Make the server authoritative and explicitly classify which low-risk preferences may remain local. As an interim control, namespace cache keys by tenant and user, clear sensitive keys on logout/org switch, expire them, and display offline/stale state. Do not treat namespacing as encryption.
- **Alternative Solutions:** A managed encrypted offline store with device binding and policy enforcement; or no offline business-data cache.
- **Trade-offs:** Removing offline cache reduces resilience when disconnected but dramatically simplifies correctness, privacy and support.
- **Estimated Effort:** 10–20 engineering days because multiple lifecycle libraries are involved.
- **Priority:** P1 before real customer data.
- **Expected ROI:** High; removes an entire ungoverned copy of sensitive platform data.
- **References:** `privacyLifecycle.ts:203-206, 377-434`; `riskLifecycle.ts:162-164, 206-377`; `crisis.ts:401-403, 409-547`; `productionMode.ts:1-40`; `auth.ts:39-42`; `api.ts:810-813`; [SDAIA PDPL guidance](https://dgp.sdaia.gov.sa/wps/portal/pdp/knowledgecenter/details/GPDPL).

## SCR-008 — Evidence and attachment uploads have no real malware, content-signature, or CDR gate

- **Category:** File upload / malware
- **Severity:** High
- **Current State:** Local storage correctly limits size, uses an extension allowlist, generates random storage names and guards paths. It trusts the client-provided content type, does not check file signatures, and writes bytes directly. The separate platform file-upload page has an EICAR/extension “scan stub”; it labels some executables `SUSPICIOUS` but accepts them. The actual evidence and generic attachment paths have no antivirus or CDR control.
- **Problem:** Malicious PDF, Office, archive, image or message files can be stored and later downloaded by privileged users.
- **Root Cause:** File safety is represented by extension validation and a demonstration stub instead of a production scanning/quarantine service.
- **Impact:** Malware delivery, parser exploitation, phishing content and malicious active documents.
- **Business Impact:** The trusted evidence repository can become a malware distribution channel to auditors, executives and regulators.
- **Technical Impact:** Client MIME values are persisted and returned; archive and macro-bearing document formats are allowed without inspection.
- **UX Impact:** Users may interpret accepted/uploaded status as “safe.”
- **Sales Impact:** Blocks regulated customers that require malware scanning and content controls.
- **Recommendation:** Upload to quarantine, validate signature/MIME, scan with a maintained engine or sandbox, apply CDR to supported Office/PDF formats, then promote atomically to clean storage. Downloads must be blocked until `CLEAN`. Record scanner engine/signature version, timestamp and result. Restrict archives or recursively inspect them.
- **Alternative Solutions:** Delegate scanning and quarantine to managed object storage/security services; or initially permit only the minimum low-risk formats.
- **Trade-offs:** Asynchronous scanning adds a pending state and operational dependency. CDR can alter documents and must preserve the original under stricter access where legally required.
- **Estimated Effort:** 8–15 engineering days plus infrastructure and operations.
- **Priority:** P1.
- **Expected ROI:** High; protects high-trust users and the evidence chain.
- **References:** `LocalFileStorageSupport.java:55-59, 104-120, 150-159`; `FileUploadController.java:101-136, 225-237`; [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html).

## SCR-009 — Production frontend tree contains a critical WebSocket dependency advisory and affected router packages

- **Category:** Software supply chain
- **Severity:** High
- **Current State:** On 2026-07-26, `npm audit --omit=dev` reported one critical and two moderate vulnerable production packages. `sockjs-client@1.6.1` resolves through `faye-websocket@0.11.4` to `websocket-driver@0.7.4`, affected by resource-limit-bypass and protocol-length message-corruption advisories. `react-router-dom@6.30.3` and `react-router@6.30.3` are in affected open-redirect ranges; one direct advisory describes open redirect leading to XSS. The SSR hydration advisory is not applicable to this no-SSR SPA, but the redirect advisories require route-level review.
- **Problem:** The production dependency tree contains known vulnerable versions with fixes available.
- **Root Cause:** Version ranges allowed resolved packages to age into known advisory ranges without a release-blocking current SCA gate.
- **Impact:** Potential client-side denial of service, message corruption, open redirect or XSS depending on reachable library paths.
- **Business Impact:** Known critical advisories weaken due-diligence and customer vulnerability-management evidence.
- **Technical Impact:** Exploitability in the browser bundle has not yet been proven; the vulnerable production tree is nevertheless confirmed.
- **UX Impact:** Successful exploitation may redirect users, corrupt real-time behavior or freeze the client.
- **Sales Impact:** Enterprise security review will reject unresolved critical SCA findings.
- **Recommendation:** Upgrade/replace the affected SockJS/Faye chain and router versions, then rebuild and rerun SCA. Verify whether SockJS fallback is still required; prefer native WebSocket when supported. Add `npm audit --omit=dev` or an equivalent curated SCA policy to CI with documented exceptions and expiry.
- **Alternative Solutions:** Pin patched transitive packages using overrides after compatibility testing; or remove SockJS fallback.
- **Trade-offs:** Router remediation may require a major version and route regression work. Package overrides are faster but less maintainable.
- **Estimated Effort:** 2–6 engineering days.
- **Priority:** P1.
- **Expected ROI:** High; closes known dependency exposure and improves procurement evidence.
- **References:** `tss-grc-ui-v2/package.json`; resolved tree from `npm ls websocket-driver react-router react-router-dom`; [GHSA-mp7j-qc5w-4988](https://github.com/advisories/GHSA-mp7j-qc5w-4988), [GHSA-xv26-6w52-cph6](https://github.com/advisories/GHSA-xv26-6w52-cph6), [GHSA-jjmj-jmhj-qwj2](https://github.com/advisories/GHSA-jjmj-jmhj-qwj2).

## SCR-010 — Application rate limiting trusts spoofable `X-Forwarded-For` and is node-local

- **Category:** Abuse prevention / security configuration
- **Severity:** Medium
- **Current State:** Public portals are protected by a fixed-window in-memory counter. The key is the first value supplied in `X-Forwarded-For`, with no trusted-proxy validation. Counters are per JVM. The documented nginx deployment adds a separate `$binary_remote_addr` rate limit, which materially mitigates the issue when traffic can reach the API only through that proxy.
- **Problem:** A direct caller or a proxy chain that preserves untrusted values can rotate `X-Forwarded-For` to bypass the application throttle. Multiple nodes multiply the allowed rate.
- **Root Cause:** Proxy trust and distributed rate-limit state are not modeled explicitly.
- **Impact:** Brute force, enumeration and high-volume public submissions beyond the intended application limit.
- **Business Impact:** Portal abuse and operational noise; possible confidentiality probing.
- **Technical Impact:** The application control does not deliver the behavior described by its comments outside the reference single-node nginx topology.
- **UX Impact:** Legitimate users sharing an egress IP may be throttled while attackers bypass it.
- **Sales Impact:** Weakens abuse-control evidence for public portals.
- **Recommendation:** Make the edge gateway authoritative, strip incoming forwarding headers, set a trusted client-IP header, and ensure the backend is not publicly reachable. For multi-node deployments use a gateway or shared token-bucket store. Rate-limit by endpoint risk and token hash in addition to IP.
- **Alternative Solutions:** Spring Cloud Gateway/API gateway/WAF rate policies.
- **Trade-offs:** Shared throttling adds infrastructure; edge enforcement is simpler but must be included in every supported deployment package.
- **Estimated Effort:** 2–4 engineering days.
- **Priority:** P2, or P1 if the backend is reachable directly.
- **Expected ROI:** Medium-high.
- **References:** `PublicEndpointRateLimitFilter.java:61-81, 94-119, 135-147`; `docs/operations/nginx-production.conf:83-86, 120-133`.

## SCR-011 — Tenant guard has fail-open execution contexts and over-broad exception classification

- **Category:** Multi-tenancy / secure design
- **Severity:** Medium
- **Current State:** The newly committed insert guard blocks a non-matching organisation on a tenant-bound request. It intentionally permits rows where `organisationId` is null and every insert when `TenantContext` is absent. Both tenant and cross-tenant privilege use `InheritableThreadLocal`. A global handler maps every `SecurityException` to “cross-tenant write,” not only exceptions raised by this guard. `LicensingService` opens its own cross-tenant scope around its service operation.
- **Problem:** “Trusted off-request work” and “unexpectedly missing tenant context” are indistinguishable. A child/executor thread can inherit tenant or privilege state. Unrelated Java security failures are mislabeled as tenant blocks. A service-level privilege scope is easier for future internal callers to misuse.
- **Root Cause:** Request identity, system-job identity and cross-tenant privilege are represented by nullable/inheritable thread-local state instead of explicit execution principals.
- **Impact:** Defense-in-depth gaps, confusing telemetry and elevated risk as asynchronous processing grows.
- **Business Impact:** Tenant-isolation evidence is harder to prove conclusively.
- **Technical Impact:** The insert interceptor cannot fail closed on missing request context without also breaking legitimate jobs.
- **UX Impact:** Unrelated `SecurityException` failures can be returned as a misleading cross-tenant 403.
- **Sales Impact:** Moderate; important in architecture due diligence and multi-tenant SaaS acceptance.
- **Recommendation:** Create explicit `RequestTenantContext` and `SystemExecutionContext`; system jobs must declare target tenant and privileged scope. Use non-inheritable context plus an approved task decorator for explicit propagation. Throw a dedicated `CrossTenantWriteException`. Keep cross-tenant authorization at the caller/application-service boundary, not hidden inside a reusable domain service. Inventory nullable `organisation_id` columns instead of assuming database constraints.
- **Alternative Solutions:** Database row-level security with session context plus application checks; or explicit tenant arguments throughout repositories/services.
- **Trade-offs:** Explicit contexts require scheduler/async refactoring but are substantially easier to test and audit.
- **Estimated Effort:** 8–15 engineering days.
- **Priority:** P2; raise to P1 before multi-node/agent-heavy processing.
- **Expected ROI:** Medium-high for SaaS assurance.
- **References:** `TenantContext.java:21-40, 69-71, 99-123`; `TenantWriteGuardInterceptor.java:211-247`; `GlobalExceptionHandler.java:229-237`; `LicensingService.java:109`.

## SCR-012 — CCM custom SQL becomes an unsafe cross-tenant inference surface if enabled

- **Category:** Injection / tenant isolation / dangerous feature
- **Severity:** Medium
- **Current State:** `CUSTOM_SQL` is disabled by default, which is the correct safe default. When enabled, arbitrary SELECT text is executed through the application's shared `EntityManager`. The code requires only that the SQL text contains the substring `organisation_id`; it does not inject/bind the current organisation. The source comment says the feature requires a per-tenant database account, but the implementation uses the normal application persistence context.
- **Problem:** An enabled tenant can write a query that mentions `organisation_id` without filtering to the current tenant and infer cross-tenant values through counts/conditions. Free-form SQL also creates resource-exhaustion and database-feature abuse risk.
- **Root Cause:** A text denylist and substring test are being used as a SQL authorization boundary.
- **Impact:** Conditional cross-tenant data inference and database denial of service.
- **Business Impact:** A configuration mistake converts a disabled dangerous feature into a multi-tenant data exposure risk.
- **Technical Impact:** Read-only transaction metadata does not guarantee database read-only enforcement and does not impose query cost/time limits.
- **UX Impact:** Administrators may enable the documented feature believing tenant safety is enforced.
- **Sales Impact:** Moderate unless marketed as a production CCM capability, then high.
- **Recommendation:** Do not enable this implementation in shared SaaS. Replace free-form SQL with approved parameterized query templates or a dedicated read replica/data source whose database login is tenant-restricted. Bind the organisation server-side and apply statement timeout, row/cost limit and query audit.
- **Alternative Solutions:** A sandboxed analytics service with database row-level security; or remove CUSTOM_SQL and expose supported CCM connectors.
- **Trade-offs:** Templates reduce flexibility but are testable and supportable. A separate analytics service costs more but provides a real isolation boundary.
- **Estimated Effort:** 5–10 engineering days for templates; more for a separate analytics plane.
- **Priority:** P2 while disabled; P0 if anyone proposes enabling it against the shared application database.
- **Expected ROI:** High if CCM is commercialized.
- **References:** `CustomSqlRunner.java:50-63, 75-99, 142-167`.

---

## 5. Positive controls verified

The following controls should be retained and strengthened:

- JWT signing secrets fail startup validation when absent, short or placeholder-like.
- Passwords use BCrypt with work factor 12.
- Refresh cookies are `HttpOnly`, `Secure`, `SameSite=Strict` and path-limited.
- Refresh-token rotation and a server-side revocation store exist; SCR-001 prevents them from being effective against bearer misuse.
- Security returns 401 for unauthenticated and 403 for authenticated-but-forbidden requests.
- The main API emits HSTS, frame denial, content-type protection, referrer policy and a restrictive API CSP.
- Export endpoints have a dedicated authorization ratchet: 40 discovered file endpoints and zero missing EXPORT guards.
- The general authorization ratchet is far below its historic ceiling: 21 intentionally unguarded write endpoints.
- Committee votes now bind the real actor, active roster, canonical member and on-behalf provenance.
- CAB vote creation uses `CHANGE:APPROVE` and the authenticated actor.
- Tenant-scoped attachment row lookup and storage path traversal protections are present.
- File storage uses random server-side names and SHA-256 hashes.
- The current source pins earlier remediated backend dependencies including SQL Server JDBC, POI and Commons Lang to their documented fixed versions.

Passing tests establish regression health, not absence of exploitable defects.

## 6. Required remediation and release gates

### Gate P0 — before any real-data pilot

1. Close SCR-001 and add a regression test proving a refresh JWT is rejected as bearer before and after logout.
2. Close SCR-002 and prove disabled, locked and stale-tenant sessions are rejected.
3. Close SCR-003 with two-principal live probes for emergency approval, CAB reject/approve and PIR reviewer identity.
4. Close SCR-004 with cross-role attachment BOLA tests and classification enforcement.
5. Redact all public token URLs from proxy/application/audit logs and introduce token hashing/expiry policy under SCR-005.

### Gate P1 — before regulated production

1. Scope committee delegation to the actual committee.
2. remove production business data from tenant-agnostic browser storage;
3. deploy quarantine plus real malware/content scanning;
4. clear all production critical/high dependency advisories;
5. run a full current backend and frontend SCA scan;
6. execute an independent authenticated penetration test across at least two tenants and all high-consequence personas.

### Gate P2 — before scale-out or agentic automation

1. make rate limiting edge-authoritative and distributed;
2. replace inheritable nullable tenant privilege state with explicit execution contexts;
3. keep custom SQL disabled until it has a genuine database isolation boundary;
4. review WebSocket allowed origins and explicitly reject client `SEND` frames unless message handlers are introduced and authorized.

## 7. Saudi market and regulatory relevance

| Finding group | Saudi relevance |
|---|---|
| Authentication, authorization and audit provenance | NCA ECC identity/access, logging, secure configuration and application-security expectations |
| Public token and browser cache exposure | PDPL integrity/confidentiality, minimization, retention and accountability principles |
| Whistleblower credential exposure | Confidential reporting obligations and serious trust/reputation risk |
| Committee/change decision integrity | Government acceptance, board governance, SAMA/CMA regulated-control evidence |
| Malware scanning and supply chain | NCA secure-technology and vulnerability-management expectations |

The current official [NCA ECC 2-2024 page](https://nca.gov.sa/en/regulatory-documents/controls-list/ecc/) states that the controls protect national entities' information and technology assets. SDAIA's [PDPL guidance](https://dgp.sdaia.gov.sa/wps/portal/pdp/knowledgecenter/details/GPDPL) emphasizes integrity, confidentiality, accountability and adequate security measures. Closure should be demonstrated with technical evidence, not a compliance mapping alone.

## 8. Retest evidence required for closure

Every finding closure must include:

1. code diff and architecture decision where the security boundary changes;
2. unit tests and mutation/negative tests;
3. live two-principal or two-tenant probe where applicable;
4. 401/403/409 behavior and audit-event verification;
5. database evidence that no cross-tenant or forged decision row was created;
6. browser verification for public portals and client storage;
7. dependency scan artefact with generation timestamp;
8. rollback/operational instructions;
9. update to this report retaining the original finding and adding closure evidence.

## 9. Reproduction commands

```powershell
# Focused backend regression suite
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
mvn -o test '-Dtest=TenantWriteGuardInterceptorTest,CommitteeAuthorisationSeparationTest,CommitteeTenantIsolationTest,CommitteeVoteProvenanceTest,CommitteeVoteRecusalTallyTest,CustomSqlRunnerTest,ChangeManagementServiceTest,ControllerAuthorizationRatchetTest,ExportAuthorizationRatchetTest,JwtTokenProviderTest,PublicEndpointRateLimitFilterTest'

# Frontend compile and current production-dependency audit
npm run typecheck
npm audit --omit=dev
npm ls websocket-driver react-router react-router-dom --all
```

The live authentication proof script is intentionally not embedded because it handles a real refresh credential. The required permanent regression test is: generate a refresh token, assert bearer authentication returns 401, revoke/logout, and assert it remains 401.

## 10. Final disposition

The platform is not “insecure everywhere.” It has strong security intentions, recent high-quality governance fixes and meaningful regression coverage. The problem is sharper: several remaining boundaries are false assurances. Logout does not currently terminate a refresh token used as bearer; `RESTRICTED` attachment classification does not restrict; `CHANGE:WRITE` is still decision authority; a committee-wide secretariat role is represented by a global permission; public secrets are written into logs; and accepted files are not genuinely scanned.

For a GRC product, correctness of the control is the product. These issues must be treated as product-integrity defects, not technical debt.
