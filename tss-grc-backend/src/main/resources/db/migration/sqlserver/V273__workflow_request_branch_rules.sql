-- V273 (WF-6): conditional routing for the workflow request engine (§7 case #5).
--
-- branch_rules — JSON array of conditional-routing rules on the request:
--   [{"stepNo":1,"onOutcome":"REJECTED","action":"RETURN"},
--    {"stepNo":1,"onOutcome":"HIGH_RISK","action":"GOTO_STEP","targetStep":3}]
-- Actions: GOTO_STEP (forward-only — keeps the §7 #19 no-cycles guarantee),
-- RETURN (back to the initiator for rework), CLOSE (complete now),
-- ESCALATE (fire the request's escalation path). Evaluated in the service when a
-- recipient responds with an outcome; the matching rule overrides default routing.
--
-- response_outcome — the outcome token the responder chose, kept on their
-- recipient row for the timeline/preview ("what did they answer?").

IF COL_LENGTH('workflow.requests', 'branch_rules') IS NULL
    ALTER TABLE workflow.requests ADD branch_rules NVARCHAR(MAX) NULL;

IF COL_LENGTH('workflow.request_recipients', 'response_outcome') IS NULL
    ALTER TABLE workflow.request_recipients ADD response_outcome NVARCHAR(40) NULL;
