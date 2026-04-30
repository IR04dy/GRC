-- ============================================================================
-- Fix Arabic charset: UPDATE all garbled Arabic text with correct Unicode values
-- Run: sqlcmd -S localhost -U sa -P "tss.123" -d TSS_GRC -i fix_arabic_charset.sql -f 65001
-- ============================================================================

USE TSS_GRC;
GO

SET NOCOUNT ON;

-- ============================================================================
-- Fix iam.users Arabic names
-- ============================================================================

UPDATE iam.users SET full_name_ar = N'مدير النظام'          WHERE username = 'admin';
UPDATE iam.users SET full_name_ar = N'خالد الرشيدي'          WHERE username = 'rmanager';
UPDATE iam.users SET full_name_ar = N'نورة الزهراني'          WHERE username = 'compliance';
UPDATE iam.users SET full_name_ar = N'فهد العتيبي'            WHERE username = 'auditor';
UPDATE iam.users SET full_name_ar = N'سارة الغامدي'           WHERE username = 'ranalyst';
UPDATE iam.users SET full_name_ar = N'محمد الحربي'            WHERE username = 'viewer';

PRINT 'User Arabic names fixed.';
GO

-- ============================================================================
-- Fix risk.risk_categories Arabic names
-- ============================================================================

UPDATE risk.risk_categories SET name_ar = N'أمن المعلومات'              WHERE name_en = 'Information Security'   AND organisation_id = 1;
UPDATE risk.risk_categories SET name_ar = N'الأمن المادي'               WHERE name_en = 'Physical Security'      AND organisation_id = 1;
UPDATE risk.risk_categories SET name_ar = N'القانوني والامتثال'          WHERE name_en = 'Legal & Compliance'     AND organisation_id = 1;
UPDATE risk.risk_categories SET name_ar = N'المالي'                      WHERE name_en = 'Financial'              AND organisation_id = 1;
UPDATE risk.risk_categories SET name_ar = N'التشغيلي'                    WHERE name_en = 'Operational'            AND organisation_id = 1;
UPDATE risk.risk_categories SET name_ar = N'الموارد البشرية'             WHERE name_en = 'Human Resources'        AND organisation_id = 1;
UPDATE risk.risk_categories SET name_ar = N'التقنية'                     WHERE name_en = 'Technology'             AND organisation_id = 1;
UPDATE risk.risk_categories SET name_ar = N'الاستراتيجي'                 WHERE name_en = 'Strategic'              AND organisation_id = 1;
UPDATE risk.risk_categories SET name_ar = N'الطرف الثالث والموردون'      WHERE name_en = 'Third Party & Vendor'   AND organisation_id = 1;
UPDATE risk.risk_categories SET name_ar = N'السمعة'                      WHERE name_en = 'Reputational'           AND organisation_id = 1;

PRINT 'Risk category Arabic names fixed.';
GO

-- ============================================================================
-- Fix risk.risks Arabic titles
-- ============================================================================

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

UPDATE risk.risks SET title_ar = N'هجوم برامج الفدية على البنية التحتية الأساسية'
WHERE title_en = 'Ransomware Attack on Core Infrastructure' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'اختراق البيانات الشخصية - بيانات هوية العملاء'
WHERE title_en = 'Personal Data Breach - Customer PII' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'عدم الامتثال لضوابط الأمن السيبراني للهيئة الوطنية للأمن السيبراني'
WHERE title_en = 'Non-Compliance with NCA ECC Controls' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'تسريب البيانات من خلال موردين خارجيين'
WHERE title_en = 'Third-Party Vendor Data Exposure' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'تعطل الأنظمة الأساسية - فشل استمرارية الأعمال'
WHERE title_en = 'Core System Downtime - Business Continuity Failure' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'هجمات التصيد الاحتيالي والهندسة الاجتماعية'
WHERE title_en = 'Phishing and Social Engineering Attacks' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'تهديد داخلي من المستخدمين المميزين - تسريب البيانات'
WHERE title_en = 'Privileged Insider Threat - Data Exfiltration' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'الإعداد الخاطئ للبنية التحتية السحابية'
WHERE title_en = 'Cloud Infrastructure Misconfiguration' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'الاعتماد على الكفاءات الرئيسية وتركيز المعرفة'
WHERE title_en = 'Key Personnel Dependency and Knowledge Concentration' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'عدم الامتثال لنظام حماية البيانات الشخصية السعودي'
WHERE title_en = 'Non-Compliance with Saudi PDPL' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'الأنظمة غير المحدّثة والتعرض للثغرات الأمنية'
WHERE title_en = 'Unpatched Systems and Vulnerability Exposure' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'الضرر بالسمعة من حوادث وسائل التواصل الاجتماعي'
WHERE title_en = 'Reputational Damage from Social Media Incidents' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'الوصول المادي غير المصرح به إلى مراكز البيانات'
WHERE title_en = 'Unauthorised Physical Access to Data Centre' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'عدم التوافق في استراتيجية التحول الرقمي'
WHERE title_en = 'Digital Transformation Strategy Misalignment' AND organisation_id = 1;

UPDATE risk.risks SET title_ar = N'الاحتيال عبر البريد الإلكتروني التجاري - تحويل المدفوعات'
WHERE title_en = 'Business Email Compromise - Payment Diversion' AND organisation_id = 1;

PRINT 'Risk Arabic titles fixed.';
GO

PRINT 'Arabic charset fix complete.';
GO
