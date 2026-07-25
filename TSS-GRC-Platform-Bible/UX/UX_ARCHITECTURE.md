# UX Architecture

## Baseline opinion

The current React application demonstrates impressive coverage but weak experience containment:

- more than 300 routed pages are registered centrally;
- several major pages exceed 1,000 lines and combine unrelated sub-journeys;
- many modules expose registers, dashboards, settings and specialist tools at the same navigation level;
- route and tab abundance makes the platform feel larger than the user's job;
- historical reviews identify mock/demo fallback behaviour and inconsistent workflow realism;
- Arabic strings exist at scale, but bilingual existence is not equivalent to Arabic task success.

The redesign must reduce cognitive load without removing professional depth.

## UX north star

> A user opens TSS and immediately knows what changed, what requires action, why it matters, when it is due and what evidence will complete it.

## Experience shells

### 1. Employee and control-owner shell

- My Work
- My attestations
- My evidence requests
- My risks/issues
- Help

No framework administration or specialist registers by default.

### 2. Practitioner shell

- My Work
- Assurance Map
- Domain workspace
- Calendar
- Reports

### 3. Executive shell

- Enterprise posture
- Decisions required
- Exceptions and trends
- Business-unit comparison
- Board packs

No operational CRUD tables on the landing page.

### 4. Administrator shell

- Organisation and access
- Packages and entitlements
- Workflow/configuration
- Integrations
- Regulatory content
- Audit and operations

### 5. External portal shells

Separate, narrowly scoped shells for vendors, data subjects, whistleblowers, complainants and public trust content. They must not load the internal navigation or disclose tenant metadata.

## Target information architecture

```text
My Work
  Inbox
  Approvals
  Evidence requests
  Deadlines and escalations

Assurance
  Risks
  Obligations
  Controls
  Evidence
  Issues and actions
  Testing and assessments

Workspaces
  [only installed and permitted packages]

Executive
  Posture
  Decisions
  Trends
  Board and committee packs

More
  Calendar
  Reports
  Knowledge
  Administration
```

Regulator names appear as filters/content packs, not as duplicated top-level products unless the purchased package is a true vertical workflow.

## Core page patterns

### Work queue

Required columns:

- task and business object;
- reason assigned;
- owner and delegator;
- due/SLA status;
- risk/criticality;
- next action.

Supports saved views, bulk reassignment where safe, keyboard operation and explicit empty-state onboarding.

### Object workspace

Every significant object uses:

1. identity: reference, bilingual title, owner, status;
2. next action and blockers;
3. summary and current assessment;
4. relationships: risks, controls, obligations, parties and assets;
5. evidence and freshness;
6. decisions and rationale;
7. activity/history;
8. contextual help.

### Guided decision page

High-consequence decisions must not be a small modal. The page shows:

- requested decision and requester;
- authority and SoD result;
- facts, risks and exceptions;
- required evidence and missing evidence;
- alternatives;
- conditions and expiry;
- approve/reject/return actions;
- mandatory rationale;
- immutable confirmation summary.

### Executive metric

Every metric supports:

`metric → scope and definition → trend → exceptions → affected objects → evidence/test lineage`

If lineage is unavailable, the metric is labelled “indicative”.

## Form architecture

- Group by decision sequence, not entity fields.
- Prefer 5–9 visible fields per step.
- Explain regulatory purpose beside uncommon fields.
- Save draft automatically.
- Validate at field, step and submission levels.
- Preserve entered values after failure.
- Use reference selectors rather than raw numeric IDs.
- Require Arabic/English together only for material content; explain exceptions.
- Show inherited values and source.
- Warn before changing a value that invalidates downstream approvals.

## Navigation rules

- Maximum seven primary items per shell.
- Maximum two persistent navigation levels.
- Tabs represent sibling views, not entire workflows.
- No hidden route may bypass permission or entitlement.
- Deep links retain context and return destination.
- Search spans installed packages but respects tenant, permission, classification and package entitlement.

## Notifications

Notifications are not a second inbox. Aggregate by business object and urgency. Each notification contains reason, due date and direct action. Users control channel and digest frequency subject to mandatory incident/regulatory notices.

## Accessibility target

WCAG 2.2 AA:

- complete keyboard journeys;
- visible focus;
- semantic headings and landmarks;
- screen-reader names and status announcements;
- 4.5:1 normal-text contrast;
- no colour-only meaning;
- 200% zoom and reflow;
- accessible charts with data tables;
- error summaries and field association;
- sufficient target size;
- reduced-motion support;
- Arabic screen-reader and mixed-direction testing.

## UX telemetry

Collect privacy-aware product analytics:

- journey completion and abandonment;
- time to first value;
- error and retry rate;
- evidence-request rework;
- approval return reasons;
- search success;
- Arabic/English switching;
- accessibility preference;
- help usage;
- module adoption by persona.

Telemetry must not capture sensitive field content.

## UX acceptance

A workflow is ready only after:

- five target users complete it without facilitator rescue;
- Arabic and English success rates are measured;
- critical errors are zero;
- median completion time meets the journey target;
- keyboard and screen-reader paths pass;
- error recovery is demonstrated;
- audit evidence matches what the user believed occurred.

