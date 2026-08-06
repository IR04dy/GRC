/* =====================================================================================
   V360 — disable the automation rules whose condition uses an operator object (F2-118)

   WHY
   ---
   F2-118: `AutomationRuleEngine` compared only scalars. An operator object such as
   {"severity":{"eq":"HIGH"}} is an ObjectNode, which failed all three type tests, and
   ObjectNode.asText() returns the EMPTY STRING — so the comparison was "" == "HIGH" and the
   rule could never match, for any payload. Measured: 214 rules, 43 operator-object, ALL
   ENABLED, ZERO ever fired. Among them: fraud loss > SAR 1M, confirmed loss > SAR 500k,
   >100 affected customers, NCA and CMA inspection notices, material SNCI > SAR 500k.

   The jar this migration ships with implements the operator vocabulary, so those 43 rules
   become live the moment it starts.

   ⚠ WHICH IS WHY THIS MIGRATION DISABLES THEM.
   F2-118's own recommendation: "implement the evaluator, ship it with the rules disabled,
   then enable per rule after checking each audience resolves. Do not ship the evaluator and
   the arming in one commit." Arming 43 dormant escalations across five modules at deploy —
   acting on work already under way — is the S6c / S8e situation, and F2-94 separately
   measured that most escalation audiences do not resolve anyway (69 of 84 addressed role
   names do not exist). An escalation to nobody is not an improvement on no escalation.

   So: the CAPABILITY lands here, the BEHAVIOUR does not. Each rule is re-enabled
   deliberately, after its audience is checked. Nothing is lost by waiting — these rules have
   never fired, so disabling them changes no observable behaviour at all. What it changes is
   that the register stops claiming they are on.

   ⚠ NOT A NO-OP DISGUISED AS ONE: if this migration is skipped, the 43 arm silently.

   IDEMPOTENT + SELF-CHECKING per CLAUDE.md: safe to apply twice, and THROWs rather than
   quietly doing nothing if its assumptions do not hold.
   ===================================================================================== */
SET NOCOUNT ON;

/* The predicate. `":{"` is a JSON key whose value is an object — which is exactly the shape
   the evaluator could not handle. Verified against a parsed census: this matches 43 rows and
   the JSON parse finds 43 operator-object rules, the same set. Cross-checked below against
   the operator vocabulary itself, so a false positive cannot slip through silently. */
DECLARE @population   INT;
DECLARE @wasEnabled   INT;
DECLARE @disabled     INT;
DECLARE @stillEnabled INT;
DECLARE @notOperator  INT;

SELECT @population = COUNT(*)
FROM   platform.automation_rules
WHERE  is_deleted = 0
  AND  condition_json LIKE '%":{"%';

IF @population = 0
BEGIN
    /* The shape this migration exists for is absent. Either the predicate broke or the rules
       were removed — both mean this file no longer describes the database, and passing
       silently would leave the next reader believing 43 rules were handled. */
    THROW 51360, 'V360: no operator-object automation rules found — the predicate no longer matches this schema.', 1;
END;

/* Cross-check the predicate against the vocabulary: every row it matched must actually carry
   a known operator token. If one does not, the LIKE has over-reached onto a scalar rule whose
   text merely resembles an object, and disabling it would break a working rule. */
SELECT @notOperator = COUNT(*)
FROM   platform.automation_rules
WHERE  is_deleted = 0
  AND  condition_json LIKE '%":{"%'
  AND  condition_json NOT LIKE '%"eq"%'
  AND  condition_json NOT LIKE '%"ne"%'
  AND  condition_json NOT LIKE '%"gt"%'
  AND  condition_json NOT LIKE '%"gte"%'
  AND  condition_json NOT LIKE '%"lt"%'
  AND  condition_json NOT LIKE '%"lte"%'
  AND  condition_json NOT LIKE '%"in"%'
  AND  condition_json NOT LIKE '%">"%'
  AND  condition_json NOT LIKE '%"<"%'
  AND  condition_json NOT LIKE '%">="%'
  AND  condition_json NOT LIKE '%"<="%';

IF @notOperator > 0
BEGIN
    THROW 51361, 'V360: the object-condition predicate matched a rule carrying no known operator — it would disable a working rule.', 1;
END;

SELECT @wasEnabled = COUNT(*)
FROM   platform.automation_rules
WHERE  is_deleted = 0
  AND  condition_json LIKE '%":{"%'
  AND  enabled = 1;

UPDATE platform.automation_rules
SET    enabled    = 0,
       updated_at = SYSUTCDATETIME(),
       /* Recorded in the row, so an operator who wonders why a rule is off has the answer
          without reading a migration. */
       updated_by = 'V360 (F2-118: operator condition never evaluated; re-enable after checking the audience)'
WHERE  is_deleted = 0
  AND  condition_json LIKE '%":{"%'
  AND  enabled = 1;

SET @disabled = @@ROWCOUNT;

SELECT @stillEnabled = COUNT(*)
FROM   platform.automation_rules
WHERE  is_deleted = 0
  AND  condition_json LIKE '%":{"%'
  AND  enabled = 1;

IF @stillEnabled <> 0
BEGIN
    THROW 51362, 'V360: an operator-condition rule is still enabled after the update.', 1;
END;

IF @disabled <> @wasEnabled
BEGIN
    THROW 51363, 'V360: disabled row count does not match the enabled operator-rule count.', 1;
END;

PRINT CONCAT('V360: ', @population, ' operator-condition rule(s) present, ',
             @disabled, ' disabled this run (F2-118). The evaluator now understands them; ',
             're-enable each one deliberately after checking its audience resolves.');
