-- V221 — attachment versioning: replace-with-new-version + history chain.
-- version: 1 for the first upload, bumped on each replace.
-- version_group_id: id of the v1 row; all versions of one document share it.
-- superseded: a newer version exists (hidden from the current list).
-- Idempotent; backfills existing rows to anchor their own single-version chain.

IF COL_LENGTH('platform.attachments', 'version') IS NULL
    ALTER TABLE platform.attachments ADD version INT NOT NULL
        CONSTRAINT DF_attach_version DEFAULT 1;

IF COL_LENGTH('platform.attachments', 'superseded') IS NULL
    ALTER TABLE platform.attachments ADD superseded BIT NOT NULL
        CONSTRAINT DF_attach_superseded DEFAULT 0;

IF COL_LENGTH('platform.attachments', 'version_group_id') IS NULL
    ALTER TABLE platform.attachments ADD version_group_id BIGINT NULL;

UPDATE platform.attachments SET version_group_id = id WHERE version_group_id IS NULL;
