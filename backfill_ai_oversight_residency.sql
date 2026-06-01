-- Backfill structured human-oversight + data-residency on seeded AI models
-- (dev only — realistic Saudi-bank posture for the registry demo).
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

-- 1) Baseline: KSA-resident, human-in-the-loop, no cross-border transfer.
UPDATE ai_governance.ml_model
   SET human_oversight             = N'HUMAN_IN_THE_LOOP',
       data_residency              = N'KSA',
       cross_border_transfer_basis = N'NONE_KSA_RESIDENT'
 WHERE human_oversight IS NULL;

-- 2) Chatbots run on a foreign-hosted LLM → GLOBAL residency, transfer on SCC,
--    human-on-the-loop (agent monitors, escalates account/transaction actions).
UPDATE ai_governance.ml_model
   SET human_oversight             = N'HUMAN_ON_THE_LOOP',
       data_residency              = N'GLOBAL',
       cross_border_transfer_basis = N'BCR_SCC',
       human_oversight_notes_en    = N'Conversational only; any account or transaction action is handed off to a human agent. PII minimised before the prompt leaves the Kingdom.',
       human_oversight_notes_ar    = N'محادثة فقط؛ أي إجراء على الحساب أو المعاملات يُحوَّل إلى موظّف بشري. تُقلَّص البيانات الشخصية قبل خروج الطلب من المملكة.'
 WHERE business_domain = N'CHATBOT';

-- 3) Real-time fraud auto-blocks but an analyst reviews every block → on-the-loop.
UPDATE ai_governance.ml_model
   SET human_oversight          = N'HUMAN_ON_THE_LOOP',
       human_oversight_notes_en = N'Auto-blocks at score > 0.92; a fraud analyst reviews every auto-block within SLA and can reverse it.',
       human_oversight_notes_ar = N'حظر تلقائي عند التسجيل > 0.92؛ يراجع محلّل الاحتيال كل حظر تلقائي ضمن اتفاقية مستوى الخدمة ويمكنه التراجع عنه.'
 WHERE business_domain = N'FRAUD_DETECTION';

-- 4) Credit decisioning — human reviews every decline above SAR 50,000.
UPDATE ai_governance.ml_model
   SET human_oversight_notes_en = N'Human-in-the-loop for every financing decline above SAR 50,000; the model output is advisory and recorded with a reason code.',
       human_oversight_notes_ar = N'وجود الإنسان في حلقة القرار لكل رفض تمويل يتجاوز 50,000 ريال؛ مخرجات النموذج استرشادية وتُسجَّل مع رمز سبب.'
 WHERE business_domain = N'CREDIT_DECISION';

SELECT business_domain, human_oversight, data_residency, cross_border_transfer_basis
  FROM ai_governance.ml_model
 ORDER BY business_domain;
