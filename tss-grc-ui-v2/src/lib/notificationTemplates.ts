// Bilingual communication templates per notification spec.
//
// ServiceNow IRM equivalent: "Notification Template Library". Each
// template is a structured message body with `{placeholders}` that the
// Compose modal substitutes at runtime so the auditor sees a pre-filled
// draft they can review and tweak before "sending."
//
// Coverage strategy: hand-write templates for the most-visible
// regulator + internal flows; other specs fall through to a generic
// template that surfaces the spec description + reg ref so the auditor
// has a starting point.

import type { NotificationSpec, CommLogEntry } from './notificationMatrix';
import { STAKEHOLDERS } from './stakeholders';

export interface NotificationTemplate {
  /** Joins to NotificationSpec.id. Pattern "*" → fallback. */
  specId: string;
  subjectEn: string;
  subjectAr: string;
  bodyEn: string;
  bodyAr: string;
}

// Placeholder syntax: {placeholderName}
// Supported placeholders:
//   {recordKey}        — composite "KIND:ID" of the record triggering the comm
//   {recordTitle}      — periodic-filing title or spec description (best-available)
//   {eventDate}        — yyyy-mm-dd of the underlying event (eventDetectedAt)
//   {deadline}         — yyyy-mm-dd of the SLA deadline
//   {stakeholderName}  — receiving stakeholder display name
//   {regulatoryRef}    — citation (e.g. "PDPL Art.20")
//   {senderName}       — internal sender (defaults to "GRC Platform Operator")
//   {senderRole}       — sender role (defaults to "DPO" / "CISO" depending on flow)
//   {orgName}          — defaults to "Tatweer Saudi Solutions"
//   {todayIso}         — ISO date for "today"

export const TEMPLATES: NotificationTemplate[] = [
  // ── NCA HEY 24h cyber-incident notification ─────────────────────────────
  { specId: 'CYB-NCA-24H',
    subjectEn: 'Cybersecurity incident notification — {recordKey}',
    subjectAr: 'إشعار حادثة سيبرانية — {recordKey}',
    bodyEn: `To: National Cybersecurity Authority — HEY portal
Reference: {regulatoryRef}
Reporting entity: {orgName}
Incident reference: {recordKey}
Detection time (UTC+3): {eventDate}
Notification deadline: {deadline} (within 24 hours of detection)

Summary
{recordTitle}

Initial classification
- Severity: HIGH
- Affected systems: [populate]
- Detection vector: [populate]
- Containment status: [populate]

Preliminary impact
[Populate — affected services, customers, data classes]

Actions in flight
1. Containment activated
2. Forensic preservation in progress
3. Internal CSIRT escalation completed

Sender: {senderName}, {senderRole}
{orgName}
{todayIso}`,
    bodyAr: `إلى: الهيئة الوطنية للأمن السيبراني — بوابة "حصين"
المرجع التنظيمي: {regulatoryRef}
الجهة المُبلِّغة: {orgName}
رقم الحادثة الداخلي: {recordKey}
وقت الكشف (توقيت السعودية): {eventDate}
الموعد النهائي للإشعار: {deadline} (خلال 24 ساعة من الكشف)

الملخّص
{recordTitle}

التصنيف الأولي
- درجة الخطورة: عالية
- الأنظمة المتأثرة: [استكمال]
- منصة الكشف: [استكمال]
- حالة الاحتواء: [استكمال]

الأثر الأوّلي
[استكمال — الخدمات المتأثرة، العملاء، فئات البيانات]

الإجراءات الجارية
1. تفعيل خطة الاحتواء
2. حفظ الأدلة الجنائية الرقمية
3. تصعيد فريق الاستجابة الداخلي

المُرسِل: {senderName}، {senderRole}
{orgName}
{todayIso}` },

  // ── SDAIA 72h privacy-breach notification ───────────────────────────────
  { specId: 'PDPL-SDAIA-72H',
    subjectEn: 'Personal data breach notification — {recordKey}',
    subjectAr: 'إشعار اختراق بيانات شخصية — {recordKey}',
    bodyEn: `To: Saudi Data & AI Authority
Reference: {regulatoryRef}
Controller: {orgName}
Breach reference: {recordKey}
Detection: {eventDate}
Notification deadline: {deadline} (PDPL Art.20 — 72-hour window)

1. Nature of breach
{recordTitle}

2. Categories + approximate number of data subjects affected
[Populate]

3. Categories + approximate number of records affected
[Populate]

4. Likely consequences
[Populate]

5. Measures taken / proposed
- Containment: [populate]
- Notification of affected subjects: [planned/completed/not-required]
- Mitigations to prevent recurrence: [populate]

6. DPO contact
{senderName} ({senderRole}) — dpo@{orgName}
{todayIso}`,
    bodyAr: `إلى: الهيئة السعودية للبيانات والذكاء الاصطناعي
المرجع التنظيمي: {regulatoryRef}
المتحكم في البيانات: {orgName}
رقم الاختراق الداخلي: {recordKey}
الكشف: {eventDate}
الموعد النهائي للإشعار: {deadline} (المادة 20 من نظام حماية البيانات — خلال 72 ساعة)

1. طبيعة الاختراق
{recordTitle}

2. فئات أصحاب البيانات والعدد التقريبي للمتأثرين
[استكمال]

3. فئات السجلات والعدد التقريبي للمتأثر
[استكمال]

4. الآثار المحتملة
[استكمال]

5. الإجراءات المتخذة / المقترحة
- الاحتواء: [استكمال]
- إشعار أصحاب البيانات المتأثرين: [مخطط / مكتمل / غير مطلوب]
- إجراءات منع التكرار: [استكمال]

6. مسؤول حماية البيانات
{senderName} ({senderRole}) — dpo@{orgName}
{todayIso}` },

  // ── PDPL — affected data subject notification ───────────────────────────
  { specId: 'PDPL-SUBJECTS',
    subjectEn: 'Important — notification regarding your personal data',
    subjectAr: 'مهم — إشعار بشأن بياناتك الشخصية',
    bodyEn: `Dear customer,

We are writing to inform you of a personal-data incident that may have affected your information.

What happened
{recordTitle}

When
The incident was detected on {eventDate}.

Information potentially affected
[Populate — types of data]

What we are doing
- The incident has been contained.
- We have notified the Saudi Data & AI Authority as required under PDPL Article 20.
- We are reinforcing our security controls to prevent recurrence.

What you can do
- Monitor your accounts for any unusual activity.
- If you spot anything suspicious, contact us immediately.

Contact
Data Protection Officer: dpo@{orgName}

Sincerely,
{orgName}`,
    bodyAr: `عزيزي العميل،

نكتب إليك لإبلاغك بحادثة تتعلق بالبيانات الشخصية وقد تؤثر على معلوماتك.

ما الذي حدث
{recordTitle}

متى
تم كشف الحادثة بتاريخ {eventDate}.

المعلومات المحتمل تأثرها
[استكمال — أنواع البيانات]

ما نقوم به
- تم احتواء الحادثة.
- أبلغنا الهيئة السعودية للبيانات والذكاء الاصطناعي وفقاً للمادة 20 من نظام حماية البيانات.
- نعمل على تعزيز ضوابط الأمن لمنع التكرار.

ما يمكنك فعله
- تابع حساباتك لرصد أي نشاط غير طبيعي.
- في حال ملاحظة أي أمر مريب، تواصل معنا فوراً.

التواصل
مسؤول حماية البيانات: dpo@{orgName}

مع التحيات،
{orgName}` },

  // ── CMA material-event disclosure ───────────────────────────────────────
  { specId: 'CMA-MATERIAL-4H',
    subjectEn: 'Material event disclosure — {recordKey}',
    subjectAr: 'إفصاح عن حدث جوهري — {recordKey}',
    bodyEn: `To: Capital Market Authority + Tadawul
Issuer: {orgName}
Reference: {regulatoryRef}
Event reference: {recordKey}
Event time: {eventDate}
Disclosure deadline: {deadline} (CMA Listing Rules §53 — 4 business hours)

Nature of event
{recordTitle}

Material impact assessment
[Populate]

Action taken / planned
[Populate]

Investor implications
[Populate]

Authorised by: {senderName}, {senderRole}
{orgName}
{todayIso}`,
    bodyAr: `إلى: هيئة السوق المالية + منصة تداول
المُصدِر: {orgName}
المرجع التنظيمي: {regulatoryRef}
رقم الحدث: {recordKey}
وقت الحدث: {eventDate}
الموعد النهائي للإفصاح: {deadline} (قواعد إدراج هيئة السوق — خلال 4 ساعات عمل)

طبيعة الحدث
{recordTitle}

تقييم الأثر الجوهري
[استكمال]

الإجراءات المتخذة / المخطط لها
[استكمال]

الآثار على المساهمين
[استكمال]

اعتماد: {senderName}، {senderRole}
{orgName}
{todayIso}` },

  // ── AML SAR — SAFIU ─────────────────────────────────────────────────────
  { specId: 'AML-SAFIU',
    subjectEn: 'Suspicious Activity Report — {recordKey}',
    subjectAr: 'بلاغ نشاط مشبوه — {recordKey}',
    bodyEn: `To: Saudi Arabia Financial Intelligence Unit (SAFIU)
Reference: {regulatoryRef}
Reporting entity: {orgName}
SAR reference: {recordKey}
Detection date: {eventDate}

⚠ Tipping-off prohibited per AML Law Art.18.

Subject of report
[Populate — counterparty, transaction(s), pattern]

Reasoning
[Populate — why suspicious]

Supporting evidence
[List attached]

MLRO sign-off
{senderName} ({senderRole})
{orgName}
{todayIso}`,
    bodyAr: `إلى: وحدة التحريات المالية السعودية
المرجع التنظيمي: {regulatoryRef}
الجهة المُبلِّغة: {orgName}
رقم البلاغ الداخلي: {recordKey}
تاريخ الكشف: {eventDate}

⚠ يُحظر إخبار العميل وفقاً للمادة 18 من نظام مكافحة غسل الأموال.

موضوع البلاغ
[استكمال — الطرف المقابل، المعاملة/المعاملات، النمط]

الأسباب
[استكمال — أسباب الاشتباه]

الأدلة الداعمة
[قائمة المرفقات]

اعتماد مسؤول الإبلاغ عن غسل الأموال (MLRO)
{senderName} ({senderRole})
{orgName}
{todayIso}` },

  // ── NCEMA crisis notification ───────────────────────────────────────────
  { specId: 'BCP-NCEMA',
    subjectEn: 'Crisis-level disruption — BCP invocation — {recordKey}',
    subjectAr: 'اضطراب جسيم — تفعيل خطة الاستمرارية — {recordKey}',
    bodyEn: `To: National Center for Emergency Management (NCEMA)
Reporting entity: {orgName}
Reference: {regulatoryRef}
Disruption reference: {recordKey}
Onset: {eventDate}

Nature of disruption
{recordTitle}

Critical services affected
[Populate]

BCP invocation status
- Crisis Management Team: ACTIVATED
- BCP plan: invoked
- Estimated time to recovery (RTO): [populate]

Authority assistance requested
[Populate or "none at this stage"]

Escalation contact
{senderName} ({senderRole})
{orgName} — 24/7 ops centre`,
    bodyAr: `إلى: المركز الوطني لإدارة الكوارث والأزمات
الجهة المُبلِّغة: {orgName}
المرجع التنظيمي: {regulatoryRef}
رقم الاضطراب: {recordKey}
وقت البدء: {eventDate}

طبيعة الاضطراب
{recordTitle}

الخدمات الحرجة المتأثرة
[استكمال]

حالة تفعيل خطة الاستمرارية
- فريق إدارة الأزمات: مُفعَّل
- خطة الاستمرارية: مُفعَّلة
- الوقت التقديري للاستعادة (RTO): [استكمال]

طلبات الدعم من الجهة
[استكمال أو "لا يوجد في هذه المرحلة"]

جهة الاتصال للتصعيد
{senderName} ({senderRole})
{orgName} — مركز العمليات على مدار الساعة` },

  // ── Internal — Board CRITICAL incident briefing ─────────────────────────
  { specId: 'CYB-BOARD',
    subjectEn: 'CRITICAL cyber incident — Board notification — {recordKey}',
    subjectAr: 'حادثة سيبرانية حرجة — إبلاغ المجلس — {recordKey}',
    bodyEn: `Board members,

A CRITICAL-severity cyber incident has been detected and is being managed under the IR runbook.

Incident: {recordKey}
Detection: {eventDate}
Summary: {recordTitle}

Current state
- Crisis Management Team activated
- NCA HEY notification submitted within 24-hour window
- Forensic preservation in progress

Decisions required from the Board
[Populate — only if a decision is needed]

Next briefing
A full update will be circulated within 24 hours, or sooner if material developments occur.

{senderName} ({senderRole})
{orgName}`,
    bodyAr: `أعضاء المجلس،

تم كشف حادثة سيبرانية بدرجة خطورة حرجة، ويتم التعامل معها وفق دليل الاستجابة.

الحادثة: {recordKey}
الكشف: {eventDate}
الملخّص: {recordTitle}

الحالة الراهنة
- تم تفعيل فريق إدارة الأزمات
- تم إشعار "حصين" خلال نافذة الـ 24 ساعة
- تتم عمليات الحفظ الجنائي

قرارات مطلوبة من المجلس
[استكمال — فقط في حال احتياج قرار]

الإحاطة التالية
سيتم تعميم تحديث شامل خلال 24 ساعة أو قبل ذلك في حال حدوث تطوُّرات جوهرية.

{senderName} ({senderRole})
{orgName}` },

  // ── Internal — quarterly Audit Committee briefing ───────────────────────
  { specId: 'AUDIT-CTE',
    subjectEn: 'Quarterly Audit Committee briefing — {recordTitle}',
    subjectAr: 'الإحاطة الفصلية للجنة المراجعة — {recordTitle}',
    bodyEn: `Audit Committee,

This is the standing quarterly briefing covering:

1. Open critical / high findings — aging + ownership
2. CAPA status — implementation + independent-test outcomes
3. External audit engagements in flight
4. Cross-authority compliance posture
5. Material control failures (if any)
6. Forward look for the next quarter

Briefing reference: {recordKey}
Period: ending {eventDate}

Pre-read attached. Discussion items flagged in the agenda.

{senderName} ({senderRole})
Internal Audit
{orgName}`,
    bodyAr: `لجنة المراجعة الموقَّرة،

هذه الإحاطة الفصلية الدورية وتتناول:

1. الملاحظات الحرجة/العالية المفتوحة — التقادم والمسؤولية
2. حالة الإجراءات التصحيحية — التنفيذ ونتائج الاختبار المستقل
3. ارتباطات المراجعة الخارجية الجارية
4. موقف الامتثال عبر الجهات المختلفة
5. إخفاقات الضوابط الجوهرية (إن وُجدت)
6. التطلُّع للربع القادم

مرجع الإحاطة: {recordKey}
الفترة: تنتهي بتاريخ {eventDate}

المرفقات للقراءة المسبقة. عناصر النقاش مُعلَّمة في جدول الأعمال.

{senderName} ({senderRole})
المراجعة الداخلية
{orgName}` },

  // ── Generic fallback — shown for any spec without a hand-written template
  { specId: '*',
    subjectEn: 'Notification — {recordTitle}',
    subjectAr: 'إشعار — {recordTitle}',
    bodyEn: `To: {stakeholderName}
Reference: {regulatoryRef}
Record: {recordKey}
Event date: {eventDate}
Deadline: {deadline}

{recordTitle}

[Populate notification body — facts, impact, planned actions, follow-up.]

{senderName} ({senderRole})
{orgName}
{todayIso}`,
    bodyAr: `إلى: {stakeholderName}
المرجع التنظيمي: {regulatoryRef}
السجل: {recordKey}
تاريخ الحدث: {eventDate}
الموعد النهائي: {deadline}

{recordTitle}

[استكمال نص الإشعار — الوقائع، الأثر، الإجراءات المخططة، المتابعة.]

{senderName} ({senderRole})
{orgName}
{todayIso}` },
];

export interface TemplateContext {
  spec: NotificationSpec;
  entry: CommLogEntry;
  recordKey: string;
  recordTitle?: string;
  stakeholderName?: string;
  senderName?: string;
  senderRole?: string;
  orgName?: string;
}

/** Look up a template by spec id, with fallback to the wildcard. */
export function templateFor(specId: string): NotificationTemplate {
  return TEMPLATES.find(tpl => tpl.specId === specId)
    ?? TEMPLATES.find(tpl => tpl.specId === '*')!;
}

/** Render a template — substitute all `{placeholder}` tokens. */
export function renderTemplate(
  ctx: TemplateContext,
  lang: 'en' | 'ar',
): { subject: string; body: string } {
  const tpl = templateFor(ctx.spec.id);
  const stakeholder = STAKEHOLDERS.find(s => s.code === ctx.spec.toStakeholderCode);
  const detectedMs = new Date(ctx.entry.eventDetectedAt).getTime();
  const deadlineMs = detectedMs + ctx.spec.withinHours * 3_600_000;
  const todayIso = new Date().toISOString().slice(0, 10);

  const values: Record<string, string> = {
    recordKey:       ctx.recordKey,
    recordTitle:     ctx.recordTitle ?? (lang === 'ar' ? ctx.spec.descriptionAr : ctx.spec.descriptionEn),
    eventDate:       new Date(detectedMs).toISOString().slice(0, 10),
    deadline:        new Date(deadlineMs).toISOString().slice(0, 10),
    stakeholderName: ctx.stakeholderName ?? (lang === 'ar' ? (stakeholder?.nameAr ?? '') : (stakeholder?.nameEn ?? '')),
    regulatoryRef:   ctx.spec.regulatoryRef,
    senderName:      ctx.senderName ?? (lang === 'ar' ? 'مسؤول منصة الحوكمة' : 'GRC Platform Operator'),
    senderRole:      ctx.senderRole ?? (lang === 'ar' ? 'مسؤول الامتثال' : 'Compliance Lead'),
    orgName:         ctx.orgName ?? 'Tatweer Saudi Solutions',
    todayIso,
  };

  const sub = (lang === 'ar' ? tpl.subjectAr : tpl.subjectEn);
  const body = (lang === 'ar' ? tpl.bodyAr : tpl.bodyEn);
  const fill = (s: string) => s.replace(/\{(\w+)\}/g, (_, k) => values[k] ?? `{${k}}`);

  return { subject: fill(sub), body: fill(body) };
}
