"""Seed Saudi-realistic fraud cases across the 3 demo tenants.

Sprint 14 PR #7. The fraud dashboard already lists the schema +
typology catalog (from setup_fraud_db.py), but until you fire actual
investigation cases the dashboard shows 0 in every KPI. This script
inserts ~15 cases — a mix of DETECTED / INVESTIGATING / CONFIRMED /
CLOSED — across the 3 tenants so the platform looks like it's been
in production for a few months.

Tenants:
  TSS GRC (org=1)        — Saudi bank archetype (5 cases)
  Aramco Trading (org=2) — energy + trading desk (5 cases)
  STC Bank (org=3)       — telco-bank, USSD + mobile-money (5 cases)

Each case uses a real Saudi-bank fraud pattern, bilingual EN+AR
subject + narrative + closure notes, realistic SAR amounts, and
proper status transitions (with the timestamp columns set so the
timeline UI renders correctly).

Idempotent: every INSERT is guarded by IF NOT EXISTS on
(organisation_id, external_reference). Safe to re-run.

Usage:
  python setup_fraud_seed_cases.py
"""
from __future__ import annotations

import sys
from datetime import datetime, timedelta, timezone

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = True
cur = conn.cursor()


# ── Load typology id → code map ───────────────────────────────────────
def load_typology_ids() -> dict[str, int]:
    cur.execute(
        "SELECT id, code FROM fraud.fraud_typology "
        "WHERE organisation_id IS NULL AND is_deleted = 0"
    )
    return {row.code: row.id for row in cur.fetchall()}


# ── Case definitions ──────────────────────────────────────────────────
# Tuple: (org_id, external_ref, typology_code, status,
#         subject_en, subject_ar, narrative_en, narrative_ar,
#         detection_source, detection_ref, severity,
#         days_ago, estimated_loss, confirmed_loss, recovered_amount,
#         affected_customers, str_filed, str_ref, le_engaged, le_ref,
#         closure_notes_en, closure_notes_ar)

CASES = [
    # ─── TSS GRC (org=1) — Saudi bank archetype ───────────────────
    (1, 'FR-2026-04-0001', 'BEC', 'CLOSED',
     'CFO impersonation wire request — SAR 2.8M caught at call-back',
     'انتحال هوية المدير المالي — تحويل 2.8 مليون ريال أوقفه ضابط إعادة الاتصال',
     'Accounts payable received an email impersonating CFO Tariq Al-Otaibi at 22:14 on '
     '2026-03-29 instructing an urgent SAR 2.8M wire to a UAE supplier "Crescent Trade FZE". '
     'AP analyst Lina Al-Subaie escalated to the call-back control before execution. CFO confirmed '
     'no such request was made. Email source IP geo-located Lagos, NG.',
     'تلقّى قسم الذمم الدائنة بريداً ينتحل شخصية المدير المالي طارق العتيبي في 22:14 يوم 2026-03-29 '
     'يطلب تحويلاً عاجلاً بقيمة 2.8 مليون ريال إلى مورّد إماراتي "Crescent Trade FZE". صعّدت '
     'المحلّلة لينا الصبيعي إلى ضابط إعادة الاتصال قبل التنفيذ. أكّد المدير المالي عدم تقديم الطلب. '
     'عنوان IP لمصدر البريد محدّد جغرافياً في لاغوس، نيجيريا.',
     'INTERNAL_REPORT', 'AP-CB-2026-0042', 'HIGH',
     56, 2800000.00, 0.00, 0.00, 0, True, 'STR-2026-03-0089', False, None,
     'No funds left the bank — call-back control held the line. CAPA opened to lower '
     'mandatory call-back threshold from SAR 5M to SAR 1M, target Q3-2026. Awareness training '
     'pushed to all AP staff. Closed clean.',
     'لم تخرج أي أموال من البنك — أوقف ضابط إعادة الاتصال العملية. أُنشئ إجراء تصحيحي لخفض '
     'حد إلزام إعادة الاتصال من 5 ملايين إلى مليون ريال، الهدف الربع الثالث 2026. تدريب توعية لجميع '
     'موظفي الذمم الدائنة. أُغلقت بدون خسائر.'),

    (1, 'FR-2026-04-0002', 'ATM_SKIMMING', 'CLOSED',
     'ATM skimming device — Olaya Street ATM #4421',
     'جهاز نسخ بطاقات — صرّاف العليا رقم 4421',
     'Customer reported card cloning after using ATM #4421 at Olaya Street branch on 2026-04-12. '
     'Physical inspection found skimmer module + pinhole camera installed during overnight '
     'maintenance window. 47 customer cards compromised; 18 reported unauthorized withdrawals '
     'totalling SAR 184,300 — primarily at Dubai and Manama ATMs over a 36-hour window.',
     'أبلغ عميل عن استنساخ بطاقته بعد استخدامه صرّاف رقم 4421 في فرع العليا يوم 2026-04-12. '
     'كشف الفحص الفعلي عن جهاز نسخ وكاميرا دقيقة مُركّبة أثناء نافذة الصيانة الليلية. '
     'تأثّرت 47 بطاقة عميل؛ أبلغ 18 عميلاً عن سحوبات غير مصرّح بها بإجمالي 184,300 ريال — '
     'معظمها من صرّافات دبي والمنامة خلال نافذة 36 ساعة.',
     'CUSTOMER_REPORT', 'CASE-2026-OLA-001', 'HIGH',
     43, 200000.00, 184300.00, 184300.00, 47, True, 'STR-2026-04-0112', True, 'CID-RYD-26-0418',
     'All 47 affected cards reissued at no charge; full SAR 184,300 refunded to customers under '
     'SAMA consumer protection rules. CID coordinated with regional banks; 2 arrests in Dubai. '
     'CAPA: ATM tamper-detection sensors retrofitted across all 312 branches, completed 2026-05-08.',
     'أُعيد إصدار جميع البطاقات الـ47 المتأثّرة دون رسوم؛ ورُدّ كامل مبلغ 184,300 ريال للعملاء '
     'بموجب قواعد حماية عملاء ساما. نسّقت المباحث مع البنوك الإقليمية؛ تم اعتقالان في دبي. '
     'إجراء تصحيحي: تركيب أجهزة استشعار العبث على 312 صرّافاً مكتمل في 2026-05-08.'),

    (1, 'FR-2026-05-0010', 'PUSH_PAYMENT', 'INVESTIGATING',
     'SADAD push-payment fraud — elderly customer, SAR 87k',
     'احتيال دفع فوري عبر سداد — عميل مسنّ، 87 ألف ريال',
     'Customer (age 71) called branch on 2026-05-21 reporting he was deceived by phone caller '
     'claiming to be from "SAMA Anti-Fraud Department" — instructed to send SAR 87,000 via SADAD '
     'to a "secure holding account" to prevent his savings from being frozen. Funds went to a '
     'mule account at another Saudi bank, then on to a money-exchange in Riyadh.',
     'اتّصل العميل (71 عاماً) بالفرع يوم 2026-05-21 وأبلغ أنّه خُدع من قِبل متّصل ادّعى أنّه من '
     '"إدارة مكافحة الاحتيال في ساما" — وُجِّه لإرسال 87,000 ريال عبر سداد إلى "حساب حفظ آمن" '
     'لمنع تجميد مدّخراته. ذهبت الأموال إلى حساب وسيط في بنك سعودي آخر، ثم إلى صرّاف عملات بالرياض.',
     'CUSTOMER_REPORT', 'BR-OLA-2026-0521-014', 'HIGH',
     4, 87000.00, None, 12500.00, 1, False, None, True, 'CID-RYD-26-0521',
     None, None),

    (1, 'FR-2026-05-0011', 'ACCOUNT_TAKEOVER', 'CONFIRMED',
     'IB account takeover via SIM-swap — corporate customer, SAR 412k',
     'الاستيلاء على حساب إنترنت بنكي عبر استبدال شريحة — عميل شركات، 412 ألف ريال',
     'Corporate customer "Najd Trading Co." reported on 2026-05-22 that 4 unauthorized wires '
     'totalling SAR 412,000 were executed on 2026-05-19. Investigation found the customer''s '
     'authorised signer''s SIM was port-swapped at his telco that morning (social engineering — '
     'fake police report submitted by attacker). OTPs went to attacker''s device. MFA was satisfied '
     'because both factors were on the same compromised phone.',
     'أبلغ العميل الشركة "نجد للتجارة" يوم 2026-05-22 عن تنفيذ 4 تحويلات غير مصرّح بها بإجمالي '
     '412,000 ريال يوم 2026-05-19. كشف التحقيق أنّ شريحة المفوّض بالتوقيع نُقلت في شركة الاتصال '
     'صباح ذلك اليوم (هندسة اجتماعية — بلاغ شرطة مزيّف قدّمه المهاجم). ذهبت كلمات المرور لمرّة '
     'واحدة إلى جهاز المهاجم. تحقّقت المصادقة المتعدّدة لأنّ كلا العاملين على الجهاز نفسه المخترق.',
     'CUSTOMER_REPORT', 'NJD-2026-IB-022', 'CRITICAL',
     3, 412000.00, 412000.00, 0.00, 1, True, 'STR-2026-05-0145', True, 'CID-RYD-26-0522', None, None),

    (1, 'FR-2026-05-0012', 'INTERNAL_EMBEZZLEMENT', 'INVESTIGATING',
     'Branch teller — diverted dormant-account funds, SAR 326k over 14 months',
     'موظف فرع — تحويل أموال حسابات خاملة، 326 ألف ريال خلال 14 شهراً',
     'GL reconciliation team flagged 23 unusual debit-credit pairs between dormant retail accounts '
     'and a "branch suspense" sub-ledger during the 2026 Q1 close. Trace pointed to a single teller '
     'at Khobar branch — Ahmad K. — who had moved SAR 326,000 in 23 transactions between '
     '2025-03 and 2026-05. HR + Internal Audit + Compliance running parallel tracks per AR-188.',
     'رصد فريق مطابقة دفتر الأستاذ العام 23 زوجاً غير مألوف من القيود المدينة والدائنة بين حسابات '
     'أفراد خاملة وحساب فرعي "معلّق" خلال إقفال الربع الأول من 2026. تشير القرائن إلى موظف صرّاف '
     'واحد في فرع الخبر — أحمد ك. — حوّل 326,000 ريال في 23 معاملة بين 2025-03 و2026-05. '
     'الموارد البشرية والتدقيق الداخلي والالتزام تعمل بمسارات متوازية وفق القاعدة AR-188.',
     'AUDIT_FINDING', 'IA-2026-Q1-RECON-007', 'CRITICAL',
     7, 326000.00, None, 0.00, 23, False, None, False, None, None, None),

    # ─── Aramco Trading (org=2) — energy + trading ────────────────
    (2, 'FR-2026-04-0001', 'WIRE_TRANSFER', 'CLOSED',
     'SWIFT MT103 fraud attempt — fake Murex order confirmation',
     'محاولة احتيال تحويل برقي سويفت — تأكيد أمر مزيّف من Murex',
     'Treasury front-office received a SWIFT MT103 instruction at 14:22 on 2026-04-08 referencing '
     'a Murex order that did not exist in the trading system. SWIFT operator caught the discrepancy '
     'during the standard pre-release reconciliation. Attacker had compromised a Murex export-only '
     'user account via phishing the prior week.',
     'تلقّى المكتب الأمامي للخزينة تعليمات سويفت MT103 في 14:22 يوم 2026-04-08 تشير إلى أمر Murex '
     'غير موجود في نظام التداول. رصد مشغّل سويفت التناقض خلال المطابقة المعيارية قبل الإصدار. '
     'كان المهاجم قد اخترق حساب مستخدم Murex (للتصدير فقط) عبر التصيّد في الأسبوع السابق.',
     'INTERNAL_REPORT', 'TRS-FO-2026-0408', 'CRITICAL',
     47, 18500000.00, 0.00, 0.00, 0, True, 'STR-AR-2026-04-001', False, None,
     'No funds moved — SWIFT reconciliation caught the discrepancy. Compromised Murex user '
     'credentials rotated; YubiKey-based MFA mandated for all Murex export users (was optional).',
     'لم تتحرّك أي أموال — رصدت مطابقة سويفت التناقض. تمّ تدوير بيانات اعتماد مستخدم Murex المخترق؛ '
     'إلزام المصادقة الصلبة YubiKey لجميع مستخدمي تصدير Murex (كانت اختيارية).'),

    (2, 'FR-2026-05-0001', 'INVOICE_FRAUD', 'CONFIRMED',
     'Supplier impersonation — bank-detail change on a SAR 1.4M invoice',
     'انتحال مورّد — تغيير بيانات البنك على فاتورة 1.4 مليون ريال',
     'Finance received an email from "marketing@honeywell-controls-mena.com" on 2026-05-15 '
     'requesting an urgent update to bank details on an invoice from Honeywell. Wire was '
     'executed before the call-back was completed. Real Honeywell domain is honeywell.com.',
     'تلقّت المالية بريداً من "marketing@honeywell-controls-mena.com" يوم 2026-05-15 يطلب تحديثاً '
     'عاجلاً لبيانات البنك على فاتورة من Honeywell. نُفّذ التحويل قبل اكتمال إعادة الاتصال. النطاق '
     'الحقيقي لـHoneywell هو honeywell.com.',
     'INTERNAL_REPORT', 'AP-2026-HW-INV-0517', 'HIGH',
     10, 1400000.00, 1400000.00, 0.00, 0, True, 'STR-AR-2026-05-003', True, 'CID-DAM-26-0518',
     None, None),

    (2, 'FR-2026-05-0008', 'CARD_NOT_PRESENT', 'INVESTIGATING',
     'Corporate card CNP fraud — multiple online purchases overnight',
     'احتيال البطاقة غير الحاضرة — مشتريات إلكترونية متعدّدة ليلاً',
     'Operations team reported 14 unauthorized online purchases on a corporate card overnight '
     '2026-05-22 totalling SAR 38,420. Pattern matches a known CNP fraud ring active against '
     'Saudi corporate cards. CVV harvested likely via a compromised online merchant in February.',
     'أبلغ فريق العمليات عن 14 معاملة شراء إلكترونية غير مصرّح بها على بطاقة شركات ليلة 2026-05-22 '
     'بإجمالي 38,420 ريال. النمط يطابق عصابة احتيال CNP معروفة نشطة ضد بطاقات الشركات السعودية. '
     'الـCVV تمّ حصاده على الأرجح عبر تاجر إلكتروني مخترق في فبراير.',
     'DETECTION_SYSTEM', 'CARD-FRAUD-26-0522', 'MEDIUM',
     3, 38420.00, None, 0.00, 0, False, None, False, None, None, None),

    (2, 'FR-2026-05-0009', 'BEC', 'DETECTED',
     'CEO impersonation — urgent vendor wire from "Vienna conference"',
     'انتحال الرئيس التنفيذي — تحويل عاجل لمورّد من "مؤتمر فيينا"',
     'AP analyst received an email at 23:47 on 2026-05-23 claiming to be from CEO Faisal during '
     'a "Vienna conference" — urgent wire of EUR 240k to a "Berlin event venue". Email reply-to '
     'mismatches CEO domain. Analyst flagged it before any action. Case opened for investigation '
     'of the broader phishing campaign.',
     'تلقّى محلّل الذمم الدائنة بريداً في 23:47 يوم 2026-05-23 يدّعي أنّه من الرئيس التنفيذي '
     'فيصل خلال "مؤتمر فيينا" — تحويل عاجل بقيمة 240 ألف يورو إلى "قاعة فعاليات في برلين". '
     'لا يتطابق عنوان الردّ مع نطاق الرئيس التنفيذي. علّم المحلّل البريد قبل أي إجراء. '
     'فُتحت القضية للتحقيق في الحملة الأوسع.',
     'INTERNAL_REPORT', 'AP-CB-2026-0523-09', 'MEDIUM',
     2, 1080000.00, None, 0.00, 0, False, None, False, None, None, None),

    (2, 'FR-2026-03-0001', 'MONEY_MULE', 'CLOSED',
     'Mule account opened in branch — flagged by AML monitoring',
     'حساب وسيط فُتح في الفرع — رصد عبر رقابة غسل الأموال',
     'Student account opened 2026-02-18 received 4 inbound transfers totalling SAR 158,000 over '
     '11 days, each followed within 60 minutes by an outbound transfer to a money-exchange or '
     'another bank. AML rules flagged the rapid-in-out pattern on 2026-03-02. Account holder '
     'admitted to being paid SAR 200 by an intermediary to "let his account be used".',
     'حساب طالب فُتح في 2026-02-18 استقبل 4 تحويلات واردة بإجمالي 158,000 ريال خلال 11 يوماً، '
     'يلي كلّ منها تحويل خارج خلال 60 دقيقة إلى صرّاف عملات أو بنك آخر. رصدت قواعد رقابة غسل الأموال '
     'نمط الإيداع والسحب السريع في 2026-03-02. أقرّ صاحب الحساب بقبضه 200 ريال من وسيط مقابل '
     '"السماح باستخدام حسابه".',
     'DETECTION_SYSTEM', 'AML-VL-2026-0302', 'HIGH',
     84, 158000.00, 158000.00, 23000.00, 1, True, 'STR-AR-2026-03-001', True, 'CID-DAM-26-0305',
     'Account closed. SAR 23k recovered from money-exchange via SAFIU coordination. Customer '
     'referred for prosecution under Saudi AML/CFT regulations. Branch staff retrained on '
     'high-risk-segment account opening (low-income student profiles).',
     'أُغلق الحساب. استُرد 23 ألف ريال من صرّاف العملات عبر تنسيق الإدارة العامة للتحريات المالية. '
     'أُحيل العميل للنيابة بموجب نظام مكافحة غسل الأموال. أُعيد تدريب موظفي الفرع على فتح '
     'الحسابات في الشرائح عالية المخاطر (شرائح الطلاب محدودي الدخل).'),

    # ─── STC Bank (org=3) — telco-bank ─────────────────────────────
    (3, 'FR-2026-04-0001', 'PHISHING_VISHING', 'CLOSED',
     'Vishing campaign — 1,247 customers contacted, 23 OTPs harvested',
     'حملة تصيّد هاتفي — اتّصلت بـ1,247 عميلاً، حصدت 23 كلمة مرور لمرّة واحدة',
     'Multiple customers reported receiving phone calls from "+966 11 xxx xxxx" claiming to be '
     'STC Bank fraud team — instructing them to share their OTP to "secure their account". '
     '23 customers gave up OTPs before the campaign was traced. SAR 1.86M attempted across the '
     '23 cases; SAR 1.71M blocked by velocity checks, SAR 142,000 lost.',
     'أبلغ عملاء متعدّدون عن مكالمات من "+966 11 xxx xxxx" تدّعي أنّها فريق احتيال بنك STC — '
     'تطلب مشاركة كلمة المرور لمرّة واحدة "لتأمين حسابهم". أعطى 23 عميلاً كلمات المرور قبل تتبّع '
     'الحملة. حاول المهاجمون 1.86 مليون ريال على 23 قضية؛ حجبت فحوصات السرعة 1.71 مليون ريال، '
     'وخسرت 142,000 ريال.',
     'CUSTOMER_REPORT', 'CCC-2026-VS-098', 'HIGH',
     50, 1860000.00, 142000.00, 89000.00, 23, True, 'STR-STC-2026-04-007', True, 'CID-RYD-26-0410',
     'Velocity controls were already in place — caught 92% of the attempt value. SAR 89k recovered '
     'from mule accounts. 23 affected customers fully refunded. SAMA-mandated awareness SMS pushed '
     'to all 2.4M customers reminding them STC Bank never asks for OTPs.',
     'كانت ضوابط السرعة موضوعة مسبقاً — رصدت 92% من قيمة المحاولة. استُرد 89 ألف ريال من '
     'الحسابات الوسيطة. ردّ كامل لـ23 عميلاً متأثّراً. إرسال رسالة توعية إلزامية من ساما لـ2.4 '
     'مليون عميل تذكّرهم بأنّ بنك STC لا يطلب أبداً كلمات مرور لمرّة واحدة.'),

    (3, 'FR-2026-05-0001', 'ACCOUNT_TAKEOVER', 'INVESTIGATING',
     'mySTC Bank app ATO — credential stuffing wave from breached e-commerce DB',
     'الاستيلاء على حسابات تطبيق mySTC — موجة تكرار بيانات اعتماد من تسرّب تجارة إلكترونية',
     'Fraud Analytics flagged a spike of 412 mySTC login attempts from 38 IP addresses across '
     '6 days (2026-05-17 to 2026-05-23) using credential pairs that match a recently-breached '
     'regional e-commerce site. 14 accounts successfully accessed; SAR 67,400 in unauthorized '
     'transfers before risk-engine velocity checks froze the targets.',
     'رصد فريق تحليل الاحتيال ارتفاعاً بـ412 محاولة دخول لـmySTC من 38 عنوان IP خلال 6 أيام '
     '(2026-05-17 إلى 2026-05-23) باستخدام أزواج بيانات اعتماد تطابق موقع تجارة إلكترونية إقليمي '
     'تسرّبت بياناته مؤخّراً. اختُرق 14 حساباً؛ 67,400 ريال في تحويلات غير مصرّح بها قبل أن تجمّد '
     'فحوصات السرعة الأهداف.',
     'DETECTION_SYSTEM', 'SIEM-CRDST-2026-0517', 'HIGH',
     8, 67400.00, None, 0.00, 14, True, 'STR-STC-2026-05-012', False, None, None, None),

    (3, 'FR-2026-05-0007', 'PUSH_PAYMENT', 'CONFIRMED',
     'USSD *900# push-payment fraud — fake "BluPay" support hotline',
     'احتيال دفع فوري عبر *900# — خط دعم مزيّف "BluPay"',
     'Customer received SMS appearing to be from STC Bank with a "support" number to call '
     'about a "frozen account". The "agent" walked the customer through a USSD sequence that '
     'transferred SAR 18,500 to a fraudster''s account. 41 similar reports received this week.',
     'تلقّى العميل رسالة نصيّة تبدو من بنك STC تتضمّن رقم "دعم" للاتّصال بشأن "حساب مجمّد". '
     'أرشد "الموظف" العميل عبر تسلسل USSD حوّل 18,500 ريال إلى حساب محتال. وردت 41 شكوى '
     'مماثلة هذا الأسبوع.',
     'CUSTOMER_REPORT', 'CCC-2026-USSD-0521-018', 'HIGH',
     4, 18500.00, 18500.00, 0.00, 41, True, 'STR-STC-2026-05-018', True, 'CID-RYD-26-0522',
     None, None),

    (3, 'FR-2026-05-0008', 'LOAN_APPLICATION', 'CLEARED',
     'Suspected synthetic-identity personal loan — turned out to be data-entry error',
     'اشتباه قرض شخصي بهوية مركّبة — تبيّن أنّه خطأ إدخال بيانات',
     'AI fraud-screening flagged a SAR 280k personal-loan application from "Mohammed Al-Ali" '
     'because SIMAH score and stated salary didn''t reconcile. Manual investigation found the '
     'discrepancy was a data-entry error in the salary letter (employer wrote gross instead of '
     'net). Customer was legitimate.',
     'علّم فحص الاحتيال بالذكاء الاصطناعي طلب قرض شخصي بقيمة 280 ألف ريال من "محمد العلي" لأنّ '
     'درجة سمة والراتب المُصرَّح به لم يتطابقا. كشف التحقيق اليدوي أنّ التناقض كان خطأ إدخال '
     'بيانات في خطاب الراتب (كتب صاحب العمل الراتب الإجمالي بدلاً من الصافي). العميل شرعي.',
     'DETECTION_SYSTEM', 'LOAN-AI-2026-0519-PA', 'LOW',
     6, 280000.00, 0.00, 0.00, 0, False, None, False, None,
     'Cleared — data-entry error not fraud. Loan approved after corrected salary letter. AI '
     'screening tuning request opened to better handle gross-vs-net salary discrepancy pattern.',
     'تمت التبرئة — خطأ إدخال بيانات لا احتيال. تمّت الموافقة على القرض بعد تصحيح خطاب الراتب. '
     'فُتح طلب ضبط للذكاء الاصطناعي لمعالجة نمط تناقض الراتب الإجمالي مقابل الصافي بشكل أفضل.'),

    (3, 'FR-2026-05-0009', 'BEC', 'DETECTED',
     'Compromised vendor email — IT services SAR 95k invoice mismatch',
     'بريد مورّد مخترق — فاتورة خدمات IT بقيمة 95 ألف ريال غير مطابقة',
     'Procurement flagged that an invoice from "Smart Cloud KSA" came with bank details that '
     'don''t match the supplier master record. Email originated from a hijacked vendor mailbox '
     '(forensics ongoing). Wire was paused per supplier-master mismatch control. Investigation '
     'open to assess broader vendor-domain exposure.',
     'علّم المشتريات أنّ فاتورة من "Smart Cloud KSA" تحمل بيانات بنك لا تطابق سجل بيانات المورّد '
     'الرئيسي. صدر البريد من صندوق بريد مورّد مختطف (التحقيق الجنائي جارٍ). أُوقف التحويل وفق '
     'ضابط عدم تطابق سجل المورّد. التحقيق مفتوح لتقييم تعرّض نطاق المورّد الأوسع.',
     'INTERNAL_REPORT', 'PROC-VM-2026-0524-03', 'MEDIUM',
     1, 95000.00, None, 0.00, 0, False, None, False, None, None, None),
]


# ── Insert helpers ────────────────────────────────────────────────────
SQL_INSERT = """
IF NOT EXISTS (SELECT 1 FROM fraud.fraud_case
                WHERE organisation_id = ? AND external_reference = ?)
BEGIN
    INSERT INTO fraud.fraud_case (
        organisation_id, external_reference, typology_id, typology_code_snapshot,
        subject_en, subject_ar, narrative_en, narrative_ar,
        status, severity, detection_source, detection_reference,
        detected_at, triaged_at, investigation_started_at,
        outcome_set_at, closed_at,
        estimated_loss_sar, confirmed_loss_sar, recovered_amount_sar,
        loss_currency, affected_customer_count,
        str_filed, str_reference, str_filed_at,
        law_enforcement_engaged, law_enforcement_reference,
        closure_notes_en, closure_notes_ar
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
            ?, ?, ?, ?, ?,
            ?, ?, ?, 'SAR', ?,
            ?, ?, ?, ?, ?, ?, ?);
END
"""


def insert_case(c):
    (org, ref, code, status, sub_en, sub_ar, narr_en, narr_ar,
     det_src, det_ref, severity, days_ago,
     est_loss, conf_loss, recovered, affected,
     str_filed, str_ref, le_engaged, le_ref,
     close_en, close_ar) = c

    typo_id = TYPOLOGY_IDS.get(code)
    if not typo_id:
        print(f"! skip {ref}: typology {code} not found")
        return

    now_utc = datetime.now(timezone.utc).replace(tzinfo=None)
    detected_at = now_utc - timedelta(days=days_ago)
    triaged_at = detected_at + timedelta(hours=6) if status != 'DETECTED' else None
    invest_started_at = detected_at + timedelta(hours=6) if status in (
        'INVESTIGATING', 'CONFIRMED', 'CLEARED', 'FALSE_POSITIVE', 'CLOSED') else None
    outcome_set_at = detected_at + timedelta(days=max(1, days_ago // 2)) if status in (
        'CONFIRMED', 'CLEARED', 'FALSE_POSITIVE', 'CLOSED') else None
    closed_at = detected_at + timedelta(days=max(2, days_ago - 1)) if status == 'CLOSED' else None
    str_filed_at = outcome_set_at if str_filed else None

    cur.execute(
        SQL_INSERT,
        # IF NOT EXISTS guard
        org, ref,
        # INSERT values
        org, ref, typo_id, code,
        sub_en, sub_ar, narr_en, narr_ar,
        status, severity, det_src, det_ref,
        detected_at, triaged_at, invest_started_at,
        outcome_set_at, closed_at,
        est_loss, conf_loss, recovered, affected,
        1 if str_filed else 0, str_ref, str_filed_at,
        1 if le_engaged else 0, le_ref,
        close_en, close_ar,
    )


# ── Main ──────────────────────────────────────────────────────────────
def main():
    global TYPOLOGY_IDS
    print(">>> Seeding Saudi-realistic fraud cases (Sprint 14 PR #7)")
    TYPOLOGY_IDS = load_typology_ids()
    print(f"--- {len(TYPOLOGY_IDS)} typology codes mapped")
    inserted = 0
    for c in CASES:
        try:
            insert_case(c)
            inserted += 1
        except pyodbc.Error as e:
            print(f"! insert failed for {c[1]}: {e}")
    # Count what's now in DB
    cur.execute(
        "SELECT organisation_id, COUNT(*) FROM fraud.fraud_case "
        "WHERE is_deleted = 0 GROUP BY organisation_id ORDER BY organisation_id"
    )
    print("--- post-seed counts per org:")
    for row in cur.fetchall():
        print(f"    org={row[0]}: {row[1]} cases")
    print("✓ done")


if __name__ == '__main__':
    main()
