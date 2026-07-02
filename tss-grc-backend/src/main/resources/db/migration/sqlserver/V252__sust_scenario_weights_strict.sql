-- S-E (Climate scenario loss engine) — config toggle for probability-weight validation.
--
-- The per-scenario expected loss + portfolio-at-risk are now DERIVED from the
-- sector-impact rows (Σ exposure × PD-uplift × scenario factor), never free-typed.
-- Board-approved scenarios are meant to collectively partition the future, so
-- their probability weights should sum to ~1.0. This flag decides what happens
-- when an approval would push the board-approved weight budget over 1.0:
--   0 (default) → soft: allow, and the weighted-EL read surfaces a warning
--   1           → strict: reject the approval with 400 (over-allocation)
--
-- Idempotent (COL_LENGTH guard) — dev DB is Flyway-OFF and applied by hand.
IF COL_LENGTH('sust.organisation_settings', 'climate_scenario_weights_strict') IS NULL
BEGIN
    ALTER TABLE sust.organisation_settings
        ADD climate_scenario_weights_strict BIT NOT NULL
            CONSTRAINT df_org_settings_scenario_weights_strict DEFAULT 0;
END;
GO
