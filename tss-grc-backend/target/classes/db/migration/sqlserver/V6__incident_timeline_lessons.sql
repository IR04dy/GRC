-- V6: Add timeline and lessons-learned columns to cyber.incidents
-- Run manually: sqlcmd -S localhost -d TSS_GRC -U sa -P "tss.123" -i V6__incident_timeline_lessons.sql

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'cyber.incidents') AND name = 'title_ar')
    ALTER TABLE cyber.incidents ADD title_ar NVARCHAR(255) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'cyber.incidents') AND name = 'timeline_json')
    ALTER TABLE cyber.incidents ADD timeline_json NVARCHAR(MAX) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'cyber.incidents') AND name = 'lessons_learned')
    ALTER TABLE cyber.incidents ADD lessons_learned NVARCHAR(MAX) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'cyber.incidents') AND name = 'lessons_learned_ar')
    ALTER TABLE cyber.incidents ADD lessons_learned_ar NVARCHAR(MAX) NULL;
GO

-- Seed timeline + lessons on existing incidents
UPDATE cyber.incidents SET
    title_ar = N'اختراق قاعدة بيانات العملاء',
    timeline_json = N'[{"action":"Incident reported by security team","actionAr":"تم الإبلاغ عن الحادثة من فريق الأمن","date":"2024-01-15"},{"action":"Investigation initiated","actionAr":"بدء التحقيق","date":"2024-01-15"},{"action":"Affected systems isolated","actionAr":"عزل الأنظمة المتضررة","date":"2024-01-16"},{"action":"Root cause identified: unpatched SQL injection","actionAr":"تحديد السبب الجذري: ثغرة SQL غير مُصلحة","date":"2024-01-17"},{"action":"Patch applied and systems restored","actionAr":"تطبيق الإصلاح واستعادة الأنظمة","date":"2024-01-18"}]',
    lessons_learned = N'Implement automated patch management. Conduct quarterly penetration testing. Enforce WAF rules for all customer-facing APIs.',
    lessons_learned_ar = N'تطبيق إدارة التصحيحات الآلية. إجراء اختبارات اختراق فصلية. تطبيق قواعد WAF على جميع واجهات برمجة التطبيقات.'
WHERE id = 1;

UPDATE cyber.incidents SET
    title_ar = N'هجوم تصيد احتيالي على الموظفين',
    timeline_json = N'[{"action":"Phishing emails detected by email gateway","actionAr":"اكتشاف رسائل التصيد بواسطة بوابة البريد الإلكتروني","date":"2024-02-10"},{"action":"3 employee accounts compromised","actionAr":"اختراق 3 حسابات موظفين","date":"2024-02-10"},{"action":"Accounts suspended and passwords reset","actionAr":"تعليق الحسابات وإعادة تعيين كلمات المرور","date":"2024-02-11"},{"action":"MFA enforced organization-wide","actionAr":"تطبيق MFA على مستوى المنظمة","date":"2024-02-12"}]',
    lessons_learned = N'Enforce MFA for all accounts. Conduct phishing simulation training quarterly. Implement email authentication (DMARC, SPF, DKIM).',
    lessons_learned_ar = N'تطبيق المصادقة الثنائية لجميع الحسابات. إجراء تدريب محاكاة التصيد الاحتيالي ربع سنوياً.'
WHERE id = 2;

UPDATE cyber.incidents SET
    title_ar = N'انقطاع الخادم الحيوي',
    timeline_json = N'[{"action":"Server downtime detected by monitoring","actionAr":"اكتشاف توقف الخادم بواسطة المراقبة","date":"2024-03-05"},{"action":"Root cause: hardware failure in power supply","actionAr":"السبب الجذري: عطل في وحدة الطاقة","date":"2024-03-05"},{"action":"Backup power engaged","actionAr":"تشغيل الطاقة الاحتياطية","date":"2024-03-05"},{"action":"Server restored after hardware replacement","actionAr":"استعادة الخادم بعد استبدال الأجهزة","date":"2024-03-06"}]'
WHERE id = 3;
