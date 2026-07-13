-- V297 — CT-3: CAR/PAR classification on the sustainability CAPA register.
-- One register, one lifecycle (ISO 14001 §10.2 practice): a type field, not a
-- second table. Auto-opened CAPAs are always CORRECTIVE; PREVENTIVE arrives via
-- manual creation only (remediation plan §5 decision #1 — no auto-suggestion yet).

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('sust.corrective_action') AND name = 'action_type')
BEGIN
    ALTER TABLE sust.corrective_action
        ADD action_type VARCHAR(20) NOT NULL
            CONSTRAINT df_ca_action_type DEFAULT 'CORRECTIVE';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_ca_action_type')
BEGIN
    ALTER TABLE sust.corrective_action WITH CHECK ADD CONSTRAINT ck_ca_action_type
        CHECK (action_type IN ('CORRECTIVE', 'PREVENTIVE'));
END
GO
