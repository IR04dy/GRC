/* ============================================================================================
   V399 — F2-453: the existence backstop for a policy reference that was never resolved.

   `PolicyManagementService.createAttestation` is the SINGLE writer of `policy.attestations` —
   the register that evidences that a named individual read and accepted a named policy — and
   until this commit its entire validation of the reference was

       if (body.getPolicyId() == null) throw new IllegalArgumentException("policyId is required");

   A null check, and then `attestations.save(body)`. There was no FOREIGN KEY on
   `policy.attestations.policy_id` either (0 rows in sys.foreign_keys for that table, checked
   rather than assumed), so nothing downstream refused what the service accepted.

   Probed live as `ranalyst`, who holds COMPLIANCE:READ and nothing else — 32 roles hold it, and
   it is the permission this endpoint is guarded by — every one of these answered 200 and stored
   the row: a `policyId` of 999999 that exists nowhere, another tenant's policy document, and
   `policyVersion: "9.9-FORGED"` against a document whose only version is 2.1.

   🔑 WHAT THIS CONSTRAINT DOES AND DOES NOT DO, because conflating the two is how a reference
   comes to look protected when it is not (F2-242, F2-243). A foreign key refuses a NONEXISTENT
   id and knows NOTHING ABOUT TENANCY: another tenant's policy id is a perfectly valid reference
   to it. So the constraint that looks like it protects this reference is the one that cannot.
   The tenant half is closed in the same commit, in the service, by resolving through the
   module's one tenant-scoped resolver — which is where a reference must be verified, at the
   point it is STORED rather than by whoever dereferences it later.

   This file is the half that survives a future writer forgetting the guard.

   ⚠ NO `ON DELETE CASCADE`, deliberately. An attestation is evidence that a person accepted a
   policy; it must not disappear because the policy row did. And it cannot block anything today:
   both delete paths in this module are SOFT (`setIsDeleted(true)` at two sites; there is no hard
   delete of a policy document anywhere in the tree), so the parent row is never removed and the
   restrict semantics have nothing to refuse.

   ⚠ NOT `WITH NOCHECK`. Measured in BOTH databases before writing this (F2-183 — an
   ADD CONSTRAINT verifies the rows already present, soft-deleted ones included, so one
   violation fails Flyway and blocks the whole next jar): 18 attestation rows each, and ZERO
   dangling `policy_id` values over ALL rows. Both columns are `bigint NOT NULL` and
   `policy.documents` has a primary key on `id`, so there is nothing to reconcile first.

   ⚠ This constraint can only refuse a request that TODAY writes a row referencing a policy that
   does not exist. Every create naming a real document — the whole purpose of the route — is
   untouched, which is what makes it safe to add rather than merely strict.
   ============================================================================================ */

SET NOCOUNT ON;
GO

/* ── check 0: both tables must exist, or this is being applied to the wrong schema ── */
IF OBJECT_ID('policy.attestations') IS NULL OR OBJECT_ID('policy.documents') IS NULL
BEGIN
    THROW 51399, 'V399: policy.attestations or policy.documents does not exist. This migration belongs after the policy module''s tables; do not skip it.', 1;
END
GO

/* ── check 1: refuse rather than half-apply, and NAME the offending rows. A bare "conflicted ──
   ── with the FOREIGN KEY constraint" sends the reader nowhere, and a failure here blocks     ──
   ── every later migration until it is resolved.                                              ── */
IF EXISTS (
    SELECT 1
      FROM policy.attestations a
     WHERE NOT EXISTS (SELECT 1 FROM policy.documents d WHERE d.id = a.policy_id))
BEGIN
    THROW 51399, 'V399: policy.attestations already holds rows whose policy_id matches no policy.documents row — exactly what an unresolved reference produces. Decide per row whether the attestation or the reference was the intent before applying this key; that is an owner decision, because an attestation is compliance evidence. Query: SELECT a.id, a.organisation_id, a.policy_id, a.user_username, a.status FROM policy.attestations a WHERE NOT EXISTS (SELECT 1 FROM policy.documents d WHERE d.id = a.policy_id);', 1;
END
GO

/* ── the key, idempotent so a re-run is a no-op rather than an error ── */
IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
     WHERE name = 'FK_policy_att_document'
       AND parent_object_id = OBJECT_ID('policy.attestations'))
BEGIN
    ALTER TABLE policy.attestations
        ADD CONSTRAINT FK_policy_att_document
        FOREIGN KEY (policy_id) REFERENCES policy.documents (id);
END
GO

/* ── check 2: assert what this migration claims to have done rather than assuming it. ──
   ── `is_not_trusted` is what a WITH NOCHECK add leaves behind, and `is_disabled` is what a  ──
   ── later NOCHECK disable leaves behind — BOTH look identical to a live key in any listing   ──
   ── that only checks the name (F2-299). A key that is present and not enforcing is worse     ──
   ── than an absent one, because it answers the question a reader was about to ask.           ── */
IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
     WHERE name = 'FK_policy_att_document'
       AND parent_object_id = OBJECT_ID('policy.attestations')
       AND referenced_object_id = OBJECT_ID('policy.documents')
       AND is_disabled = 0
       AND is_not_trusted = 0)
BEGIN
    THROW 51399, 'V399: FK_policy_att_document is missing, points at the wrong table, is disabled, or is untrusted. An untrusted key has not verified the existing rows, so it says nothing about the data already in the register.', 1;
END
GO

/* ── check 3: exactly one column, and it is the one the service resolves. If the entity is ever ──
   ── remapped this fails here rather than silently keying something the create does not read.   ── */
IF (SELECT COUNT(*)
      FROM sys.foreign_key_columns fkc
      JOIN sys.foreign_keys fk ON fk.object_id = fkc.constraint_object_id
     WHERE fk.name = 'FK_policy_att_document') <> 1
BEGIN
    THROW 51399, 'V399: FK_policy_att_document must have exactly one key column.', 1;
END
GO

IF NOT EXISTS (
    SELECT 1
      FROM sys.foreign_key_columns fkc
      JOIN sys.foreign_keys fk ON fk.object_id = fkc.constraint_object_id
      JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id     AND pc.column_id = fkc.parent_column_id
      JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
     WHERE fk.name = 'FK_policy_att_document'
       AND pc.name = 'policy_id'
       AND rc.name = 'id')
BEGIN
    THROW 51399, 'V399: FK_policy_att_document must map policy.attestations.policy_id to policy.documents.id.', 1;
END
GO

/* ── check 4: NO cascade. An attestation is evidence a person accepted a policy and must not be ──
   ── deleted because the policy row was. delete_referential_action 0 = NO ACTION.               ── */
IF EXISTS (
    SELECT 1 FROM sys.foreign_keys
     WHERE name = 'FK_policy_att_document'
       AND (delete_referential_action <> 0 OR update_referential_action <> 0))
BEGIN
    THROW 51399, 'V399: FK_policy_att_document must not cascade. Compliance evidence does not disappear because its parent row did.', 1;
END
GO

/* ── check 5: prove the key REFUSES, rather than describing it. ──
   ── ⚠ Conditional on a row existing to clone: a fresh install runs this against an empty     ──
   ── table where the probe would verify nothing, and a migration branch that is a no-op        ──
   ── everywhere you can measure is untested code (F2-183). Both real databases hold 18 rows.  ── */
IF EXISTS (SELECT 1 FROM policy.attestations)
BEGIN
    DECLARE @refused BIT = 0;

    BEGIN TRANSACTION;
    BEGIN TRY
        INSERT INTO policy.attestations
            (organisation_id, policy_id, user_username, status, is_deleted, created_at)
        SELECT TOP 1
             organisation_id,
             2147483647,          /* no policy.documents row can have this id */
             user_username,
             status,
             is_deleted,
             created_at
          FROM policy.attestations;
    END TRY
    BEGIN CATCH
        SET @refused = 1;
    END CATCH
    ROLLBACK TRANSACTION;

    IF @refused = 0
    BEGIN
        THROW 51399, 'V399: an attestation referencing a nonexistent policy was ACCEPTED. The key exists but is not enforcing, so the register can still record that somebody accepted a policy that does not exist.', 1;
    END
END
GO
