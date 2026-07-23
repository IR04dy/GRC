-- V304 — CH-10: inline structured policy content.
-- A policy's body was only ever a body_url (a link to an external file) plus a
-- short summary — there was no way to author the controlled text IN the platform.
-- content_md holds the structured policy body (markdown: templated section
-- headings + text) so a policy can be authored in-app and rendered to a
-- controlled PDF/Word (CH-10 controlled-output generation). Inline content also
-- satisfies the require-a-document publish gate (CH-10a). Nullable; no backfill.
-- Idempotent; applied to dev twice via pyodbc (Flyway off in dev), prod via Flyway.

IF COL_LENGTH('policy.documents', 'content_md') IS NULL
    ALTER TABLE policy.documents ADD content_md NVARCHAR(MAX) NULL;
GO
