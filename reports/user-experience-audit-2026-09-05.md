# TSS GRC — usability and user-journey audit

Date: 5 September 2026. Frontend source: `bca50906`. Environment: local React application at `http://127.0.0.1:5173`, existing backend on port 8084. The backend build identity was not verified against this frontend commit.

## Assessment

The application offers substantial functionality and a coherent desktop visual style, but the sampled experience is difficult for a first-time or occasional user. Users must understand several overlapping workflow concepts, distinguish sample content from live information, and recover from failures during an ordinary risk-review journey.

**Indicative usability rating: 4/10 for the journeys sampled.** This is an expert heuristic judgment, not a measured usability-study score or an assessment of every module. Functional breadth is stronger than task clarity and reliability.

| Area | Assessment |
|---|---|
| Desktop presentation | Consistent cards, readable grouping, prominent primary actions; excessive content before the actual register |
| Navigation and learnability | Weak: broad expanded navigation, overlapping work queues, limited personalization |
| Completing a task | Weak in the sample: risk detail failed; attestation shortcut did not lead to owner attestation |
| Feedback and trust | Weak: useful SLA labels coexist with sample data and unsupported live-trend claims |
| Arabic and English | Working language switch and RTL desktop layout; raw English statuses and technical terminology remain |
| Phone usability | Poor: confirmed content-width and clipping problem in both languages |
| Accessibility | Useful skip link and command palette; intake labels and selection semantics need correction |

## Method and limits

Walked the application as the documented `ranalyst` development persona, rather than using the administrator bypass. Inspected Arabic and English risk screens, intake, help, cybersecurity, approvals, dashboard, global search, risk detail, and the attestation destination. Checked desktop at 1440 × 1000 and phone at 390 × 844; reviewed relevant source to corroborate observations.

No business records were created, approved, deleted, exported, or otherwise changed. Submission confirmation, downstream triage, reviewer handoff, notifications after submission, and full lifecycle completion remain untested. No participant study, screen-reader session, contrast measurement, load test, or exhaustive module review was performed. Development records contain historical test data; their quality should not be assumed to represent production data. The existing backend may differ from the current source, so runtime failures need diagnosis before attributing their cause.

## Journey results

| User goal | Observed journey | Outcome |
|---|---|---|
| Start work as a risk analyst | Sign in → Risk Register | Successful sign-in and domain landing, followed by a large menu of unrelated domains |
| Find and review an existing risk | Dashboard → command palette → `RISK-0002` → search result → `/risk/2` | Search found the correct record; detail displayed only “Internal Server Error” |
| Recover from the failed review | Enterprise Risk navigation → register | Application error boundary showed “There is no underlying STOMP connection”; Reload restored the register |
| Report a new risk | Risk Register → New Risk → `/intake/risk_issue` | Opens “Raise a risk issue,” with a 14-day triage promise; does not directly create a register record at this stage |
| Understand the reporting form | Intake → contextual help | “No help for this page yet”; Knowledge Base offered as fallback |
| Find personal approvals | Approvals → Only mine | Default queue showed 12 pending requests assigned to other people; personal filter correctly returned zero |
| Perform periodic attestation | Risk Register → Periodic Risk Attestation → `/buyer-gap` | Lands on a mixed operations page with sweep controls and database/event terminology |
| Use the register on a phone | Risk Register at 390px, English and Arabic | Main content constrained to 280px; controls and content overflow; register far below initial viewport |

## Prioritized findings

### UX-01 — Risk review fails without useful recovery guidance — High

**Browser evidence:** Searching `RISK-0002` found “Ransomware Attack on Core Infrastructure.” Opening it navigated to `/risk/2`, whose main area displayed only “Internal Server Error.” The return journey produced a separate page crash containing “There is no underlying STOMP connection.” Reload restored the list. These were observed once during this session; their wider frequency is unknown.

**Impact:** A routine read journey cannot complete. Users cannot determine whether the record is unavailable, their permissions are wrong, or they should retry.

**Change:** Diagnose both runtime failures. Keep the shell usable during record failure and show a plain-language explanation, Retry, and Back to register. Do not expose connection-library terminology as user guidance.

**Acceptance:** This persona can open the known risk from search and the register, return without crashing, and recover from a simulated request failure without losing navigation. Source: `src/pages/risk/RiskDetailPage.tsx:324` renders the error as a bare message.

### UX-02 — Phone layout wastes width and clips controls — High

**Browser evidence:** At 390px, the shell grid remained `280px 110px`. Main content measured 280px wide with a 493px internal scroll width in English and 503px in Arabic. Arabic main content started at x=110. Screenshots showed unused space and clipped header actions.

**Cause supported by source:** `src/styles/shell.css:7` retains the desktop two-column grid. `src/styles/breakpoints.css:23` moves the sidebar into a fixed drawer without changing that grid to one content column.

**Change:** Make the shell a single column below the drawer breakpoint, constrain content to the viewport, and wrap action groups. Check registers, forms, and dialogs independently.

**Acceptance:** At 390px in both directions, the main area uses the available width and New Risk, search, filters, and submission actions remain reachable without accidental horizontal clipping.

### UX-03 — Live-data claims conflict with the information displayed — High

**Browser evidence:** Dashboard says “All figures live,” greets the analyst as “Aymen,” and displays trend deltas. Cybersecurity displays “Demo mode · using sample data.” The application header also says “Demo (localStorage)” while the risk register says “Live.”

**Source corroboration:** `src/pages/dashboard/ExecutiveDashboard.tsx:192` derives the 30-day delta from the current score instead of history; lines 235 and 417 onward contain the fixed greeting and fixed KPI deltas. `src/lib/useApiResource.ts:43` substitutes sample rows for empty results and failures. CyberPage uses this helper for multiple datasets.

**Impact:** Users cannot confidently distinguish actual conditions, missing access, missing data, and demonstration content. A changing score can imply an improvement or deterioration that was not measured.

**Change:** Remove unsupported changes over time. Label unavailable history honestly. Keep demonstration data in an explicit demonstration experience; authenticated empty/error states should explain the actual condition. Use the signed-in identity.

**Acceptance:** Every displayed trend has a real comparison period and source; empty or denied datasets never become operational-looking sample rows; each widget clearly states its own data state.

### UX-04 — Periodic-attestation shortcut leads to an operations utility — High

**Browser evidence:** The link promises “Periodic Risk Attestation 1 st” but leads to `/buyer-gap`. Its content includes “Run attestation sweep,” `next_attestation_date`, `RISK_ATTESTATION_OVERDUE`, evidence sweeps, and privacy sweeps.

**Impact:** A risk owner expecting to confirm or update their risk encounters system maintenance controls instead. This is a broken task mapping, even if the operations page itself works.

**Change:** Link to the user's due risk attestations and an explicit review/confirm/update form. Put operational sweep tools in an appropriate administration surface.

**Acceptance:** The shortcut opens an eligible risk or personal due-attestation queue and lets the user understand what they are confirming, when it is due, and what happens next.

### UX-05 — Approval inbox does not begin with the user's work — High

**Browser evidence:** The analyst initially saw 12 pending requests with other assignees, alongside Approve, Reject, and Delegate buttons. “Only mine” reduced the queue to zero. Source initializes `mineOnly` to false and renders actions by status transition eligibility rather than visible actor eligibility.

**Impact:** “My Work” can imply responsibilities the user does not have. The interface invites users to attempt actions before knowing whether they are eligible. This audit did not submit a decision and makes no claim that unauthorized approval succeeds.

**Change:** Default to personal assignments. Offer a clearly named team queue for eligible users. Match action visibility to authority and assignment; explain restricted actions. Replace “every gate is clear” with “You have no pending approvals” when the personal queue is empty.

**Acceptance:** A user with zero assignments sees that fact immediately. Team work and personal work are clearly distinct. Source: `src/pages/approvals/ApprovalInboxPage.tsx:50`, `:300`.

### UX-06 — Navigation requires knowledge of the product's internal organization — Medium

**Browser evidence:** The narrow analyst sees expanded groups including privacy, cybersecurity, regulatory affairs, ESG, and administration. My Work alone contains dashboard, workflow hub, submissions, requests, approvals, signatures, intake inbox, notifications, calendar, and lifecycle overview.

**Source:** Sidebar primarily filters by module entitlement. Fresh local collapse state is empty, leaving groups expanded. `src/components/layout/Sidebar.tsx:553`, `:704`.

**Impact:** Users must learn where similar-looking tasks live before they can begin. Services Center is buried in Insights & Reporting, although reporting an issue is a common entry task.

**Change:** Default to My tasks, My submissions, and relevant domain workspaces. Collapse secondary groups. Provide a searchable All modules view and a prominent Report / Request entry. Keep genuine team oversight available through explicit navigation.

**Acceptance:** An unfamiliar analyst can find their tasks, risk register, and issue-reporting entry without scrolling through unrelated domains or understanding separate workflow engines.

### UX-07 — Creation choices and risk scoring are ambiguous — Medium

**Browser evidence:** “New Risk” opens a triage intake form, while the nearby five-step Risk Identification & Assessment link is a separate path. The intake separately asks likelihood, impact, and a required “Overall severity (likelihood × impact)” using Low/Medium/High/Critical choices.

**Source:** The overall severity is an independent selection; the intake page computes a live score only for change requests. `src/lib/intakeForms.ts:141`, `src/pages/intake/IntakeFormPage.tsx:199`.

**Impact:** Users may believe they have entered a risk in the register when they are filing an issue for later triage. The scoring wording suggests a calculation that users must actually perform or guess.

**Change:** Distinguish “Report a risk for triage” from “Create assessed risk.” Explain eligibility and outcome. Define the likelihood/impact scale, derive overall severity where policy permits, and explain any override.

**Acceptance:** Before entering information, users know what record will be created, who receives it, and whether it will appear in the register immediately. Severity cannot silently contradict its stated calculation.

### UX-08 — Dashboard decisions do not connect to action — Medium

**Browser evidence:** “Needs decision” lists risk names and ages, but those entries are plain text. The analyst receives an executive dashboard rather than a personal work view. Calendar failure has a short message without an inline retry.

**Source:** `AttentionList` renders div/span elements without record destinations in `src/pages/dashboard/ExecutiveDashboard.tsx:717`.

**Change:** Every decision item should link to its record and identify the next action, responsible person, due date, and any blocker. Separate executive oversight from personal tasks.

**Acceptance:** A user can open and understand the decision directly from its card, without copying an identifier into search.

### UX-09 — Intake assistance and accessibility are incomplete — High for assistive-technology users

**Browser evidence:** Contextual help for `/intake/risk_issue` is absent. Textboxes expose placeholder text rather than the visible question as their accessible name; repeated Low/Medium/High/Critical buttons lack useful group identity in the accessibility snapshot.

**Source:** The renderer uses a div for most labels, inputs without associated IDs/labels, and selection buttons whose state is shown through styling without radio or pressed semantics. Required errors are aggregated into a toast. See `src/components/intake/IntakeFormRenderer.tsx:102`, `:162`, `:201`.

**Change:** Associate labels and hints with fields; use named fieldsets and radio controls; show persistent field-level validation with focus on the first invalid field. Add help with a completed example and scoring guidance.

**Acceptance:** A keyboard and screen-reader user can identify the question, selection, requirement, and error for each input. A separate screen-reader test is still required.

### UX-10 — User-facing terminology needs editing — Medium

**Browser evidence:** Arabic screens include `SUBMITTED` and `UNDER_REVIEW`; intake names its recipient `RISK_MANAGER`; approval copy mentions “Sprint 4 SLA engine”; some workflow entries expose raw codes. The English intake hint mentions anonymous tracking even though this form submits with `submitterIsAnonymous: false`.

**Impact:** Users must interpret implementation vocabulary and may conflate tracking without signing in with submitting anonymously.

**Change:** Localize workflow statuses, use human role names, remove development milestones from product copy, and explain exactly what identity is recorded and how tracking works.

**Acceptance:** A business user can explain the status, recipient, and confidentiality of a submission without interpreting a code or implementation term.

## Strengths to preserve

- Successful sign-in with a relevant risk landing page.
- Global search finds records by reference and gives useful title/owner context; the command palette exposes keyboard hints.
- Arabic/English switching and desktop RTL arrangement work in the sampled screens.
- Consistent cards, status colors, domain grouping, and visible workflow step counts support recognition.
- Personal approval filtering works; overdue and no-deadline states are distinguished.
- Intake explains the expected triage interval and prompts for cause, event, and impact. Source waits for a server response before rendering an acknowledgement; that final step was not exercised here.

## Recommended user journeys

**Employee:** Sign in → My work → Report a risk → concise guided form → confirmation with reference and response date → My submissions → status/history → answer a request for information → closure outcome.

**Risk analyst:** My work → assigned intake or risk → review → assess likelihood/impact with guidance → select controls and treatment owner → submit for review → see assigned reviewer and next due date → follow through to monitoring.

**Approver:** My approvals → open evidence and record → understand request, changes, and constraints → approve/return/reject with reason → see confirmation and downstream status.

**Executive:** Trusted summary → exception requiring attention → underlying record and evidence → decision or delegation → recorded outcome.

These are proposed target journeys, not claims that the full paths were successfully completed in this audit.

## Improvement order and validation

1. Restore risk detail and navigation reliability; correct phone layout and misleading live-data claims.
2. Repair the attestation destination and default approvals to personal work; make dashboard decisions actionable.
3. Clarify reporting versus creation, simplify navigation, and improve intake labels, validation, help, and bilingual copy.
4. Run a small usability study with employees, analysts, approvers, and executives in Arabic and English. Test finding assigned work, reporting a risk, reviewing an existing risk, finding submission status, and completing an eligible decision.

Proposed targets: at least 90% unassisted completion of the selected core tasks; no dead ends or lost entries; no need to guess between similar queues; personal work found within 30 seconds; all critical controls usable at 390px. These are future acceptance targets, not measured results.
