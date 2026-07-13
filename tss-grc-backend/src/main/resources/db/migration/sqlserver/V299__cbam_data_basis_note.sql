-- V299 — GF-6: CBAM derive-from-GHG helper. When the embedded factor is derived
-- from the GHG inventory (Σ VERIFIED/APPROVED S1+S2 of a year × line share /
-- production tonnes), the derivation inputs are persisted next to the figure so
-- an auditor can reproduce it. Manual entry stays the default and data_basis
-- (MEASURED | DEFAULT_VALUES) is unchanged.

IF COL_LENGTH('sust.cbam_lines', 'data_basis_note') IS NULL
    ALTER TABLE sust.cbam_lines ADD data_basis_note NVARCHAR(500) NULL;
GO
