-- V363 — F2-147: a crisis cascade row must be able to say that nothing was sent.
--
-- THE DEFECT. `bcm.crisis_comms_cascade` is the BCM evidence that people were notified during an
-- incident: one row per channel, with `status`, `recipient_count` and `success_count`. Three of the
-- four channel senders (SMS, WHATSAPP, TEAMS) have no gateway behind them — they log a line and
-- return success — and EMAIL takes the same branch whenever no `JavaMailSender` bean is configured.
-- The senders were honest about it in `response_payload` ("stub-sms: 12 recipients logged; integrate
-- STC/Mobily HTTP gateway in Sprint 12"), but a free-text English provider string is not a fact a
-- query can filter, a report can total, or an Arabic-first operator can read. The row itself said
-- COMPLETED, 12 of 12, and nothing else.
--
-- WHY A COLUMN AND NOT JUST A RESPONSE FIELD. The DTO already carried `responsePayload`, and the
-- wizard already printed it in a Details cell — so the information reached the screen ONCE, in a
-- transient React step that "Send another" discards. The reader who matters is the auditor opening
-- the cascade six months later, and they read the row. A response field cannot reach them.
--
-- ⚠ MEASURED BEFORE WRITING THIS FILE (2026-08-06), which is what makes the default safe:
--
--     dev  TSS_GRC             bcm.crisis_comms_cascade  0 rows
--     prod TSS_GRC_Deployment  bcm.crisis_comms_cascade  0 rows
--
-- So there is no backfill decision to get wrong, and no historical row whose honesty is in question:
-- NOT NULL DEFAULT 0 cannot mislabel a past dispatch as delivered, because there is no past dispatch.
-- That is measurement, not optimism — had either table held rows, the correct column would have been
-- NULLABLE with NULL meaning "written before the flag existed", because defaulting an unknown to the
-- reassuring value is the exact failure this finding is about.
--
-- ⚠ AND IT MEANS THE FINDING IS LATENT, NOT REALISED. Nobody has yet been shown 12/12 for a message
-- that was never sent — the subsystem has never been dispatched in either environment. It would have
-- fired the first time someone used it, which for a crisis-communications tool is during a crisis.
--
-- The application always sets this column explicitly (`CrisisCommsDispatchService` from
-- `SendResult.simulated()`); the DB default exists only so the ALTER can be NOT NULL. That every
-- write path sets it is pinned in Java, not here — `CrisisCommsHonestDeliveryTest`.
--
-- REVERSE RECIPE: ALTER TABLE bcm.crisis_comms_cascade DROP CONSTRAINT df_cascade_simulated;
--                 ALTER TABLE bcm.crisis_comms_cascade DROP COLUMN simulated;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── Refuse to default a population that exists ────────────────────────────────────────
-- BEFORE the ALTER, and only while the column is still absent — so this fires exactly when
-- DEFAULT (0) would be about to label real history, and is skipped on any later re-run.
-- (Placing it after the ALTER instead would make the file refuse its own second application
-- the moment a cascade had been dispatched, which is a broken guard, not a strict one.)
DECLARE @preExisting INT;
DECLARE @preExistingMsg NVARCHAR(400) =
    N'V363 refused: bcm.crisis_comms_cascade already holds rows, so DEFAULT (0) would assert that '
  + N'every historical dispatch was really delivered. Both databases held 0 rows when this migration '
  + N'was written. Re-decide the backfill (NULLABLE + per-row triage) before proceeding.';
IF COL_LENGTH('bcm.crisis_comms_cascade', 'simulated') IS NULL
BEGIN
    SELECT @preExisting = COUNT(*) FROM bcm.crisis_comms_cascade;
    IF @preExisting > 0 THROW 50000, @preExistingMsg, 1;
END
GO

-- ── The flag ──────────────────────────────────────────────────────────────────────────
-- Named constraint so the reverse recipe above can be scripted; an unnamed DEFAULT gets a
-- generated name that differs between databases.
IF COL_LENGTH('bcm.crisis_comms_cascade', 'simulated') IS NULL
BEGIN
    ALTER TABLE bcm.crisis_comms_cascade
        ADD simulated BIT NOT NULL
            CONSTRAINT df_cascade_simulated DEFAULT (0);
END
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a concatenation.
IF COL_LENGTH('bcm.crisis_comms_cascade', 'simulated') IS NULL
    THROW 50000, N'V363 failed: bcm.crisis_comms_cascade.simulated was not created.', 1;
GO

-- NOT NULL, or a row can decline to answer the question.
DECLARE @nullable INT;
DECLARE @nullableMsg NVARCHAR(400) =
    N'V363 failed: bcm.crisis_comms_cascade.simulated is nullable. A cascade row that can leave '
  + N'this NULL is a row that can be silent about whether the message was sent, which is the '
  + N'defect F2-147 is about.';
SELECT @nullable = c.is_nullable
  FROM sys.columns c
 WHERE c.object_id = OBJECT_ID('bcm.crisis_comms_cascade') AND c.name = 'simulated';
IF @nullable = 1 THROW 50000, @nullableMsg, 1;
GO

-- The default must be attached, or an INSERT from a jar that predates this column fails outright
-- instead of recording the dispatch. Scoped to THIS table's column, not matched by name alone:
-- `sys.default_constraints` is instance-wide, so a same-named constraint on any other table in
-- any other schema would satisfy a bare name check. (The same blind spot as an unqualified
-- OBJECT_ID, which resolves against dbo and returns NULL — that silently skipped a DROP in this
-- migration's own verification script.)
IF NOT EXISTS (
        SELECT 1
          FROM sys.default_constraints d
          JOIN sys.columns c ON c.object_id = d.parent_object_id
                            AND c.column_id = d.parent_column_id
         WHERE d.parent_object_id = OBJECT_ID('bcm.crisis_comms_cascade')
           AND c.name = 'simulated')
    THROW 50000, N'V363 failed: no default constraint is attached to bcm.crisis_comms_cascade.simulated.', 1;
GO
