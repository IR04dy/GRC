# Design System Specification

## Component hierarchy

1. tokens: colour, typography, spacing, elevation, motion and direction;
2. primitives: text, icon, button, input, link and badge;
3. form patterns: field, bilingual pair, reference selector, evidence upload and decision rationale;
4. data patterns: table, timeline, relationship graph, metric and assurance status;
5. workflow patterns: task queue, state indicator, approval panel and SLA;
6. page patterns: dashboard, register, workspace, wizard, decision and public portal.

## Status semantics

Use a consistent vocabulary:

| Semantic | Meaning |
|---|---|
| Neutral | Not started or informational |
| Active | Work is underway |
| Attention | Due soon, exception or incomplete |
| Critical | Breach, overdue material item or failed control |
| Positive | Completed/effective, with scope and freshness |
| Restricted | Permission, conflict or legal hold |

Never label a control “effective” without test scope and date.

## Assurance badge

Every evidence-derived result displays:

- source type;
- status: declared/tested/observed/assured;
- valid-from and expiry;
- scope;
- reviewer;
- exceptions.

## Charts

- state the question answered in the title;
- include period, scope and denominator;
- show data table alternative;
- avoid pie charts beyond five categories;
- support RTL labels without reversing quantitative meaning;
- permit drill-down;
- expose “last updated”.

## Responsive priority

Internal specialist configuration is desktop-first but reflows accessibly. Crisis, executive approval and external portals are fully mobile-capable.

## Content voice

Concise, professional and accountable. Buttons use verbs: “Submit for review”, “Approve with conditions”, “Return for evidence”. Avoid “OK”, “Process” and unexplained acronyms.

