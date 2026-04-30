# -*- coding: utf-8 -*-
"""Fix Arabic data encoding in TSS_GRC database.
Run: py fix_arabic_db.py
"""
import sys, pyodbc
sys.stdout.reconfigure(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};'
    'SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;'
    'Unicode_Results=Yes;'
)
cursor = conn.cursor()

# ── Compliance Controls: Arabic names ────────────────────────────────────────
controls = {
    'ISO27001-A.5.1':  'سياسات أمن المعلومات',
    'ISO27001-A.6.1':  'تنظيم أمن المعلومات',
    'ISO27001-A.8.1':  'إدارة الأصول',
    'ISO27001-A.9.1':  'سياسة التحكم في الوصول',
    'ISO27001-A.12.1': 'إجراءات التشغيل والمسؤوليات',
    'ISO27001-A.16.1': 'إدارة حوادث أمن المعلومات',
    'NCA-ECC-1-1':     'حوكمة الأمن السيبراني',
    'NCA-ECC-1-2':     'سياسة الأمن السيبراني',
    'NCA-ECC-2-1':     'إدارة مخاطر الأمن السيبراني',
    'PDPL-ART-5':      'الأساس القانوني للمعالجة',
    'PDPL-ART-15':     'حقوق صاحب البيانات',
    'ISO22301-8.2':    'تحليل أثر الأعمال',
}
for code, name_ar in controls.items():
    cursor.execute("UPDATE compliance.controls SET name_ar=? WHERE code=?", name_ar, code)
    print(f"Control {code}: {name_ar}")

# ── Incidents: title_ar ───────────────────────────────────────────────────────
incidents = {
    1: 'اختراق قاعدة بيانات العملاء',
    2: 'هجوم تصيد احتيالي على الموظفين',
    3: 'انقطاع الخادم الحيوي',
}
for inc_id, title_ar in incidents.items():
    cursor.execute("UPDATE cyber.incidents SET title_ar=? WHERE id=?", title_ar, inc_id)
    print(f"Incident {inc_id} title_ar: {title_ar}")

# ── Incidents: timeline_json ──────────────────────────────────────────────────
timelines = {
    1: '[{"action":"Incident reported by security team","actionAr":"تم الإبلاغ عن الحادثة من فريق الأمن","date":"2024-01-15"},{"action":"Investigation initiated","actionAr":"بدء التحقيق","date":"2024-01-15"},{"action":"Affected systems isolated","actionAr":"عزل الأنظمة المتضررة","date":"2024-01-16"},{"action":"Root cause identified: unpatched SQL injection","actionAr":"تحديد السبب الجذري: ثغرة SQL غير مصلحة","date":"2024-01-17"},{"action":"Patch applied and systems restored","actionAr":"تطبيق الإصلاح واستعادة الأنظمة","date":"2024-01-18"}]',
    2: '[{"action":"Phishing emails detected by email gateway","actionAr":"اكتشاف رسائل التصيد بواسطة بوابة البريد","date":"2024-02-10"},{"action":"3 employee accounts compromised","actionAr":"اختراق 3 حسابات موظفين","date":"2024-02-10"},{"action":"Accounts suspended and passwords reset","actionAr":"تعليق الحسابات وإعادة تعيين كلمات المرور","date":"2024-02-11"},{"action":"MFA enforced organization-wide","actionAr":"تطبيق المصادقة الثنائية على مستوى المنظمة","date":"2024-02-12"}]',
    3: '[{"action":"Server downtime detected by monitoring","actionAr":"اكتشاف توقف الخادم بالمراقبة","date":"2024-03-05"},{"action":"Root cause: hardware failure in power supply","actionAr":"السبب: عطل في وحدة الطاقة","date":"2024-03-05"},{"action":"Backup power engaged","actionAr":"تشغيل الطاقة الاحتياطية","date":"2024-03-05"},{"action":"Server restored after hardware replacement","actionAr":"استعادة الخادم بعد استبدال الأجهزة","date":"2024-03-06"}]',
}
for inc_id, timeline in timelines.items():
    cursor.execute("UPDATE cyber.incidents SET timeline_json=? WHERE id=?", timeline, inc_id)
    print(f"Incident {inc_id} timeline updated")

# ── Incidents: lessons_learned_ar ────────────────────────────────────────────
lessons = {
    1: 'تطبيق إدارة التصحيحات الآلية. إجراء اختبارات اختراق فصلية. تطبيق قواعد WAF على جميع واجهات برمجة التطبيقات.',
    2: 'تطبيق المصادقة الثنائية لجميع الحسابات. إجراء تدريب محاكاة التصيد الاحتيالي ربع سنوياً. تطبيق DMARC وSPF وDKIM.',
}
for inc_id, lesson_ar in lessons.items():
    cursor.execute("UPDATE cyber.incidents SET lessons_learned_ar=? WHERE id=?", lesson_ar, inc_id)
    print(f"Incident {inc_id} lessons_learned_ar updated")

conn.commit()
conn.close()
print("\n✓ All Arabic data fixed successfully!")
