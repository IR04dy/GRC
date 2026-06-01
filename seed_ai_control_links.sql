-- Pre-map AI/ML models to NCA + AI-Governance sub-controls (review item #3).
-- The detail page exposes a control picker (entity_type='AI_MODEL') but no
-- links were seeded, so every model showed "0 linked controls". This wires
-- each model to a profile-appropriate set across:
--   • framework 17  AI Governance (ISO 42001 + NIST AI RMF + EU AI Act + SDAIA)
--   • framework 1   NCA ECC-2:2024
--   • framework 2   NCA DCC-1:2022 (Data Cybersecurity Controls)
--   • framework 3   NCA CSCC-1:2020 (Cloud Cybersecurity Controls)
-- Idempotent (NOT EXISTS guarded). Dev only.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

DECLARE @AI_MODEL NVARCHAR(20) = N'AI_MODEL';

-- Helper macro pattern: each block inserts (model × sub-control) pairs that
-- match the WHERE predicate, skipping any link that already exists.

-- 1) Baseline — every AI model (governance backbone).
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT m.organisation_id, @AI_MODEL, m.id, v.scid, 1, SYSUTCDATETIME(), v.sat
FROM ai_governance.ml_model m
CROSS JOIN (VALUES
    (630,'FULL'),   -- AI-1-3  AI Risk Appetite & Inventory
    (631,'FULL'),   -- AI-2-1  AI Risk Tiering (EU AI Act categories)
    (637,'FULL'),   -- AI-4-1  Model Documentation / Model Card
    (638,'PARTIAL'),-- AI-4-2  Independent Model Validation
    (652,'FULL'),   -- AI-9-1  AI Register / Transparency Record
    (104,'FULL'),   -- ECC 2-3-4  Secure Software Development Lifecycle
    (72 ,'FULL')    -- ECC 1-5-1  Cybersecurity Risk Management Framework
) AS v(scid, sat)
WHERE m.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=@AI_MODEL AND cl.entity_id=m.id AND cl.sub_control_id=v.scid);

-- 2) HIGH / CRITICAL tier — impact assessment, fairness, explainability, AI security.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT m.organisation_id, @AI_MODEL, m.id, v.scid, 1, SYSUTCDATETIME(), v.sat
FROM ai_governance.ml_model m
CROSS JOIN (VALUES
    (632,'PARTIAL'),-- AI-2-2  AI Impact Assessment (AIA / fundamental rights)
    (640,'FULL'),   -- AI-5-1  Fairness / Bias Testing
    (641,'FULL'),   -- AI-5-2  Explainability & Reason Codes
    (645,'PARTIAL') -- AI-6-3  AI Cybersecurity Controls
) AS v(scid, sat)
WHERE m.is_deleted = 0 AND m.risk_tier IN (N'CRITICAL', N'HIGH')
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=@AI_MODEL AND cl.entity_id=m.id AND cl.sub_control_id=v.scid);

-- 3) Personal / financial-data domains — PDPL alignment + data classification + DPIA.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT m.organisation_id, @AI_MODEL, m.id, v.scid, 1, SYSUTCDATETIME(), v.sat
FROM ai_governance.ml_model m
CROSS JOIN (VALUES
    (119,'FULL'),   -- ECC 2-7-4  Personal Data Protection (PDPL Alignment)
    (246,'FULL'),   -- DCC 2-1-1  Classification Scheme Definition
    (251,'FULL'),   -- DCC 3-1-1  Encryption at Rest for Sensitive Data
    (633,'FULL')    -- AI-2-3  Privacy / PDPL DPIA for AI
) AS v(scid, sat)
WHERE m.is_deleted = 0
  AND m.business_domain IN (N'CREDIT_DECISION', N'AML_MONITORING', N'KYC_OCR',
                            N'FRAUD_DETECTION', N'CUSTOMER_CHURN', N'RISK_SCORING')
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=@AI_MODEL AND cl.entity_id=m.id AND cl.sub_control_id=v.scid);

-- 4) Cloud-hosted models — NCA Cloud Cybersecurity Controls.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT m.organisation_id, @AI_MODEL, m.id, v.scid, 1, SYSUTCDATETIME(), v.sat
FROM ai_governance.ml_model m
CROSS JOIN (VALUES
    (155,'FULL'),   -- ECC 4-2-1  Cloud Security Risk Assessment & Approval
    (288,'FULL'),   -- CSCC 2-1-2 Data Residency Enforcement - KSA Sovereign Data
    (301,'FULL')    -- CSCC 5-1-3 Encryption of Data at Rest in Cloud Storage
) AS v(scid, sat)
WHERE m.is_deleted = 0 AND m.hosting IS NOT NULL AND m.hosting NOT IN (N'ON_PREM', N'EDGE')
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=@AI_MODEL AND cl.entity_id=m.id AND cl.sub_control_id=v.scid);

-- 5) Cross-border residency — PDPL Art.29 transfer controls.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT m.organisation_id, @AI_MODEL, m.id, v.scid, 1, SYSUTCDATETIME(), v.sat
FROM ai_governance.ml_model m
CROSS JOIN (VALUES
    (263,'PARTIAL'),-- DCC 5-1-2  Cross-Border Data Transfer Controls
    (264,'FULL')    -- DCC 5-1-3  Data Residency Classification & Mapping
) AS v(scid, sat)
WHERE m.is_deleted = 0 AND m.data_residency IN (N'GCC', N'GLOBAL')
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=@AI_MODEL AND cl.entity_id=m.id AND cl.sub_control_id=v.scid);

-- 6) GenAI / LLM models — GenAI safety, logging/human-handoff, user disclosure.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT m.organisation_id, @AI_MODEL, m.id, v.scid, 1, SYSUTCDATETIME(), v.sat
FROM ai_governance.ml_model m
CROSS JOIN (VALUES
    (650,'PARTIAL'),-- AI-8-2  GenAI/LLM Safety Controls
    (651,'FULL'),   -- AI-8-3  GenAI Usage Logging & Human Handoff
    (642,'FULL')    -- AI-5-3  User Transparency & AI Disclosure
) AS v(scid, sat)
WHERE m.is_deleted = 0 AND (m.model_type IN (N'LLM', N'GENERATIVE') OR m.business_domain = N'CHATBOT')
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=@AI_MODEL AND cl.entity_id=m.id AND cl.sub_control_id=v.scid);

SELECT m.model_code, COUNT(*) AS linked_controls
FROM evidence.control_links cl
JOIN ai_governance.ml_model m ON m.id = cl.entity_id
WHERE cl.entity_type = N'AI_MODEL' AND m.organisation_id = 1
GROUP BY m.model_code
ORDER BY m.model_code;
