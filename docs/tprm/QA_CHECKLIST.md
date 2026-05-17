# TPRM Questionnaire Engine — QA Checklist

Manual-QA gate for the V81 questionnaire engine on top of the existing
TPRM module — templates, questions, responses, and weighted scoring
into `tprm.vendor_assessments` (`module.tprm.service.QuestionnaireService`).

This document is walkable without source access — every server-side
rule has a checklist item.

---

## Templates

Template lifecycle: `DRAFT → ACTIVE → ARCHIVED`.

### Creation
- [ ] `POST /api/v1/tprm/questionnaire-templates` requires
      `templateCode` and `nameEn`; missing either returns 400.
- [ ] `version` defaults to `1.0`; `status` defaults to `DRAFT`.
- [ ] Re-using an existing `(templateCode, version)` pair within the
      same tenant returns 400 with "Template already exists".
- [ ] Thresholds default sanely (`low_max ≤ medium_max ≤ high_max`)
      and are editable while DRAFT.

### Edit guard
- [ ] PUT on a DRAFT template updates every metadata field plus
      thresholds.
- [ ] PUT on an ACTIVE or ARCHIVED template only updates `nameEn`,
      `nameAr`, `descriptionEn`, `descriptionAr`, `notes` — thresholds
      and category stay frozen.

### Transitions
- [ ] DRAFT → ACTIVE with zero questions returns 400 with "Cannot
      activate a template with no questions".
- [ ] DRAFT → ACTIVE with at least one question stamps `activated_at`.
- [ ] DRAFT → ARCHIVED stamps `archived_at` and bypasses ACTIVE.
- [ ] ACTIVE → ARCHIVED stamps `archived_at`.
- [ ] No other transition is permitted (ARCHIVED → DRAFT, ACTIVE →
      DRAFT, etc.) — each returns 400 with "Illegal template
      transition".

## Questions

Answer types: SCALE_1_5 / BOOLEAN / SELECT / TEXT.

### Add / edit / delete
- [ ] `POST /:templateId/questions` only works while the parent
      template is DRAFT. Calling on ACTIVE returns 400 with "Cannot
      add questions to a ACTIVE template; clone to a new version
      first".
- [ ] PUT on a question fails the same way if the template is not
      DRAFT.
- [ ] DELETE on a question of a non-DRAFT template returns 400.
- [ ] `questionCode` and `questionEn` are required; `answerType`
      defaults to SCALE_1_5.
- [ ] Invalid `answerType` (e.g. "RATING") returns 400 with "Invalid
      answerType".

### Schema fields
- [ ] `sectionCode` + bilingual section names roll up sensibly in the
      UI sections.
- [ ] `weight` is a positive decimal (1.0 — 5.0 typical).
- [ ] `invert_score` flag flips contribution at scoring time
      (verified in the scoring tests).
- [ ] `mandatory` flag blocks `markComplete` (see Workflow below).

## Vendor assessment assignment + responses

### Assigning a template
- [ ] `POST /assessments/:id/assign-template` only accepts an ACTIVE
      template — DRAFT/ARCHIVED returns 400 with "Can only assign an
      ACTIVE template".
- [ ] Successful assign sets `template_id`, copies `total_questions`,
      resets `answered_count = 0`, sets workflow_status =
      IN_PROGRESS, clears `computed_score_pct`.

### Saving responses
- [ ] `POST /assessments/:id/responses` with `{questionId,
      answerValue}` upserts the row (no duplicate per
      (assessmentId, questionId)).
- [ ] Question on a different template than the assessment returns
      400 with "Question N is on a different template than the
      assessment".
- [ ] BOOLEAN accepts YES/NO/TRUE/FALSE/1/0 (case-insensitive); any
      other string returns 400.
- [ ] SCALE_1_5 accepts integers 1..5; values outside that range
      return 400 with "SCALE_1_5 answer must be 1..5".
- [ ] SELECT looks up the value in `options_json` and reads back its
      numeric points; missing option returns 400 with "SELECT option
      not found".
- [ ] TEXT does **not** auto-score — `scored_points` only updates
      when the reviewer sets it explicitly via the body's
      `scoredPoints` field.

### NA responses
- [ ] Marking `notApplicable = true` without `naJustification` returns
      400 with "naJustification is required when notApplicable =
      true".
- [ ] NA responses contribute neither to the numerator nor the
      denominator of the score — flipping a previously-answered
      question to NA recomputes the risk %.

### Scoring math
- [ ] Posture % = `Σ(effective × weight) / Σ(max × weight) × 100`,
      rounded HALF_UP to 2 decimals.
- [ ] Risk % = `100 − posture %`.
- [ ] `invert_score = true` on a question replaces `effective` with
      `max − scored` (reverse-coded items).
- [ ] Rating band derived from the template thresholds:
      `risk% ≤ low_max → LOW`, `≤ medium_max → MEDIUM`,
      `≤ high_max → HIGH`, otherwise CRITICAL.
- [ ] Worked example (verified live): 8 questions, weighted sum
      21.5 / 26.0 = posture 82.69 %, risk 17.31 %, band LOW.

### Workflow
- [ ] `POST /assessments/:id/complete` refuses if any mandatory
      question is unanswered (no scored response and not NA) — 400
      with "Mandatory question {code} is unanswered".
- [ ] Successful complete sets workflow_status = COMPLETED and
      re-runs the score / rating computation.
- [ ] `POST /assessments/:id/review` only works from COMPLETED;
      calling on IN_PROGRESS returns 400 with "Only COMPLETED
      assessments can be REVIEWED".
- [ ] Successful review sets workflow_status = REVIEWED. No further
      response writes should mutate the score (UI hides the fill-out
      controls).

## Frontend response-filling UI

- [ ] TPRM page → "Questionnaires" tab shows three cards: Template
      library, Questions for selected template, Vendor assessments.
- [ ] Vendor assessment row shows Vendor name, date, template
      assignment, progress (answered/total + %), workflow_status,
      computed risk %, rating badge, and an action button.
- [ ] When `template_id` is null, the row offers "Assign template"
      with a prompt listing ACTIVE templates.
- [ ] When `template_id` is set and status ≠ REVIEWED, the row offers
      "Fill out" (or "Review" if already COMPLETED).
- [ ] The fill-out modal shows each question with the right answer
      input type:
        BOOLEAN → Yes / No buttons,
        SCALE_1_5 → 1..5 buttons,
        SELECT → buttons per option,
        TEXT → textarea with explicit Save button.
- [ ] Each question shows section, mandatory badge, inverted badge,
      and weight.
- [ ] N/A button on each question prompts for a justification before
      saving.
- [ ] Footer shows the live risk % and rating after each save.
- [ ] "Mark complete" button is only visible when status is
      IN_PROGRESS; "Mark reviewed" appears when status is COMPLETED.
- [ ] Locked state (COMPLETED / REVIEWED) disables all answer
      controls; users can still re-open the modal to view answers.

---

## Cross-cutting checks

### Tenant isolation
- [ ] Every endpoint filters by `organisation_id` from the JWT.
- [ ] Soft-delete columns + `@Where` annotations are in place on
      templates, questions, and responses.

### Bilingual content
- [ ] Templates / questions / sections all support EN + AR text and
      render the right language based on the user's locale.
- [ ] AR phrasing follows Saudi-native conventions per MEMORY.md.
- [ ] The fill-out modal renders RTL when language is Arabic and the
      Yes/No buttons read نعم / لا.

### Tests
- [ ] `mvn test -Dtest='com.tss.grc.module.tprm.service.QuestionnaireServiceTest'`
      passes. The test class locks:
        - Template activation requires ≥ 1 question
        - Add/edit on ACTIVE template refused
        - Assignment requires ACTIVE template
        - Scoring math (posture, risk, rating)
        - invert_score behaviour
        - NA requires justification
        - SCALE out-of-range rejected
        - markComplete refuses mandatory gaps
        - markReviewed only from COMPLETED

### Documentation
- [ ] STATUS.md "TPRM Questionnaire Engine" section is current.
- [ ] CLAUDE.md captures the `tprm.questionnaire_*` tables and V81
      ALTER additions on `tprm.vendor_assessments`.

---

Last updated: alongside Sprint 2 close-out + Questionnaire unit tests.
