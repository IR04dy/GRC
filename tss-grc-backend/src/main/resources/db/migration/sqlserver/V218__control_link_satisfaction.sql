-- V218 — per-link satisfaction level on generic control mappings.
-- How fully a mapped entity satisfies the control: FULL / PARTIAL / PLANNED.
-- Idempotent; defaults existing rows to FULL.

IF COL_LENGTH('evidence.control_links', 'satisfaction') IS NULL
    ALTER TABLE evidence.control_links
        ADD satisfaction NVARCHAR(20) NULL
            CONSTRAINT DF_control_links_satisfaction DEFAULT 'FULL';
