-- V275 (WF-8 + WF-9): reusable recipient lists (distribution lists) + committee quorum/majority.
--
-- WF-8 — recipient_lists / recipient_list_members: a named, reusable recipient set an org
-- composes once and reuses on any request ("send to a distribution list", §2.2 / §5). On
-- create, a list's members are expanded into the request's recipient rows.
--
-- WF-9 — completion_threshold + widened ck_wfr_completion: PARALLEL requests may now complete on
-- QUORUM (>= completion_threshold responses) or MAJORITY (> half), not just ALL/ANY — the basis
-- for committee/panel review (§2.3 #7). Committees can be expanded to per-member gates at create.

CREATE TABLE workflow.recipient_lists (
    id                BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    name              NVARCHAR(150) NOT NULL,
    description       NVARCHAR(500) NULL,
    is_active         SMALLINT NOT NULL CONSTRAINT df_wrl_active DEFAULT 1,
    is_deleted        BIT NOT NULL CONSTRAINT df_wrl_deleted DEFAULT 0,
    deleted_at        DATETIME2 NULL,
    deleted_by        BIGINT NULL,
    created_at        DATETIME2 NOT NULL CONSTRAINT df_wrl_created DEFAULT SYSUTCDATETIME(),
    created_by        BIGINT NULL,
    updated_at        DATETIME2 NULL,
    updated_by        BIGINT NULL,
    row_version       ROWVERSION,
    CONSTRAINT uq_wrl_org_name UNIQUE (organisation_id, name)
);
GO

CREATE TABLE workflow.recipient_list_members (
    id                BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    organisation_id   BIGINT NOT NULL,
    list_id           BIGINT NOT NULL,
    recipient_type    NVARCHAR(20) NOT NULL,
    recipient_id      BIGINT NULL,
    recipient_key     NVARCHAR(120) NULL,
    recipient_label   NVARCHAR(200) NULL,
    actor_role        NVARCHAR(30) NOT NULL CONSTRAINT df_wrlm_role DEFAULT 'ASSIGNEE',
    step_no           INT NOT NULL CONSTRAINT df_wrlm_step DEFAULT 1,
    is_deleted        BIT NOT NULL CONSTRAINT df_wrlm_deleted DEFAULT 0,
    deleted_at        DATETIME2 NULL,
    deleted_by        BIGINT NULL,
    created_at        DATETIME2 NOT NULL CONSTRAINT df_wrlm_created DEFAULT SYSUTCDATETIME(),
    created_by        BIGINT NULL,
    updated_at        DATETIME2 NULL,
    updated_by        BIGINT NULL,
    row_version       ROWVERSION,
    CONSTRAINT fk_wrlm_list FOREIGN KEY (list_id) REFERENCES workflow.recipient_lists (id),
    CONSTRAINT ck_wrlm_type CHECK (recipient_type IN ('USER','ROLE','DEPARTMENT','COMMITTEE','EXTERNAL')),
    CONSTRAINT ck_wrlm_role CHECK (actor_role IN ('ASSIGNEE','REVIEWER','APPROVER','OBSERVER','ESCALATION'))
);
GO

CREATE INDEX ix_wrl_org        ON workflow.recipient_lists (organisation_id) WHERE is_deleted = 0;
CREATE INDEX ix_wrlm_list      ON workflow.recipient_list_members (list_id) WHERE is_deleted = 0;
GO

-- WF-9 — QUORUM threshold + widened completion rule.
IF COL_LENGTH('workflow.requests', 'completion_threshold') IS NULL
    ALTER TABLE workflow.requests ADD completion_threshold INT NULL;
GO

ALTER TABLE workflow.requests DROP CONSTRAINT ck_wfr_completion;
GO
ALTER TABLE workflow.requests ADD CONSTRAINT ck_wfr_completion
    CHECK (completion_rule IN ('ALL','ANY','QUORUM','MAJORITY'));
GO
