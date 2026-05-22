-- V156 — Committee forms library (the bilingual templates that turn the
--        module from a "register" into a working operational platform).
--
-- 8 core forms cover the full meeting lifecycle:
--   1. CHARTER                — constitution of a committee
--   2. AGENDA                 — standard meeting agenda
--   3. PRE_READ_COVERSHEET    — circulating papers in advance
--   4. ATTENDANCE             — attendance + quorum register
--   5. MOTION                 — formal motion / resolution form
--   6. MINUTES                — SAMA/CMA-aligned minutes template
--   7. ACTION_REGISTER        — post-meeting action tracker
--   8. COI_DECLARATION        — conflict-of-interest declaration
--
-- Two more for governance committees:
--   9. SKILLS_MATRIX          — board / audit-committee skills matrix
--  10. ANNUAL_EVALUATION      — annual self-evaluation per CMA Article 26
--
-- Each form's content_md_* is real bilingual markdown that an org can copy
-- and adapt directly — not Lorem-Ipsum placeholders. Field placeholders
-- use the [BRACKETED] convention so they can be auto-filled by the agenda
-- builder + meeting service.

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM admin.committee_form_templates WHERE form_code = N'FORM-CHARTER-001')
BEGIN

-- ═════════════════════════════════════════════════════════════════════
-- 1. CHARTER template
-- ═════════════════════════════════════════════════════════════════════
INSERT INTO admin.committee_form_templates
    (form_code, form_type, applicable_to, regulator_anchors,
     title_en, title_ar, purpose_en, purpose_ar,
     content_md_en, content_md_ar,
     completion_guide_en, completion_guide_ar, version)
VALUES
(N'FORM-CHARTER-001', N'CHARTER', N'ALL', N'CMA,SAMA',
 N'Committee Charter Template', N'نموذج ميثاق اللجنة',
 N'Standard committee-charter template covering mandate, authority, composition, quorum, decision-making, escalation, regulator anchor, and annual-review process. Adapt for the specific committee per its regulatory context.',
 N'نموذج موحّد لميثاق اللجنة يغطي الولاية، الصلاحيات، التشكيل، النصاب، اتخاذ القرار، التصعيد، المرجع التنظيمي، وعملية المراجعة السنوية. يُكيَّف لكل لجنة وفق سياقها التنظيمي.',

 N'# Committee Charter — [Committee Name]

**Charter version:** [1.0]   **Approved on:** [YYYY-MM-DD]   **Approving body:** [Board Resolution #]
**Next review due:** [YYYY-MM-DD]   **Custodian:** [Company Secretary]

## 1. Establishment & Authority
This Committee is established by resolution of [the Board of Directors / Executive Committee] on [date]. Its authority derives from [Article X of CMA Corporate Governance Regulations / SAMA RGov 2017 / NCA ECC-2 control 1-2-1 / specific Board resolution]. The Committee operates under delegated authority from [parent body] and reports to [reporting body].

## 2. Mandate
The Committee is responsible for:
- [Primary mandate item 1]
- [Primary mandate item 2]
- [Primary mandate item 3]

## 3. Authority & Decision Rights
The Committee is authorised to:
- Approve [decision type] up to [threshold].
- Recommend to [parent body] all matters above the above threshold.
- Engage external advisors (legal, technical, financial) as needed, with budget ceiling SAR [amount] per engagement.
- Request information, documents, and personnel time from any function in the company.
- Convene executive (in-camera) sessions without management present.

## 4. Composition
- **Chair:** [Title / role expectation — independence / financial-expert / sector qualification].
- **Members:** [N] members in total. [Description of expected mix: independent non-executives, executives, standing invitees].
- **Secretary:** [Role — typically Company Secretary, Head of Risk, or function-aligned officer].
- **Term of office:** [3 years for governance committees / aligned with directorship term / indefinite for management committees].
- **Independence:** [Specify which seats must be held by independent directors per regulator requirements].
- **Standing invitees:** [Roles invited to all meetings without voting rights].

## 5. Quorum & Voting
- **Quorum:** [X]% of voting members present (minimum [N] members).
- **Voting:** One vote per voting member. Chair has a casting vote in the event of a tie.
- **Conflicts of interest:** Members must declare any conflict at the start of each meeting and recuse themselves from the relevant item; the recusal is recorded in the minutes.
- **Confidentiality:** All Committee deliberations are confidential. Members are bound by the company''s confidentiality undertakings.

## 6. Meetings
- **Cadence:** [Frequency, e.g. Monthly / Quarterly / Semi-annual / Annual].
- **Annual calendar:** Published by the Secretary by [date] each year, reviewed by the Chair, and approved by the parent body.
- **Notice:** [10] business days advance notice for regular meetings; [24] hours for emergency meetings.
- **Pre-read papers:** Circulated at least [7] business days before the meeting. Late papers require Chair approval.
- **Minutes:** Drafted by the Secretary within [3] business days, circulated for member comments within [7] business days, finalised and approved at the next meeting (or by electronic vote for time-sensitive items).

## 7. Reporting & Escalation
- The Committee reports to [parent body] [quarterly / per material event].
- Material matters escalated upwards include: [list per committee type].
- Annual report on the Committee''s work is included in the [Corporate Governance Report / Annual Report].

## 8. Regulator Anchor
This Committee''s mandate aligns with the following regulator requirements:
- [Regulator name + specific article / control reference]
- [Regulator name + specific article / control reference]

## 9. Annual Review
- The Committee shall review this Charter annually.
- Material changes require approval by [parent body].
- Performance of the Committee against this Charter shall be assessed annually as part of the [Board / Committee] evaluation process.

---

**Approved:**
[Chair Name]   [Date]
[Parent Body Chair Name]   [Date]
',

 N'# ميثاق اللجنة — [اسم اللجنة]

**نسخة الميثاق:** [1.0]   **اعتُمد في:** [YYYY-MM-DD]   **الجهة المعتمدة:** [قرار المجلس رقم]
**المراجعة القادمة:** [YYYY-MM-DD]   **الحارس:** [أمين سر الشركة]

## 1. التأسيس والصلاحية
أُنشئت هذه اللجنة بقرار من [مجلس الإدارة / اللجنة التنفيذية] بتاريخ [التاريخ]. تستمد صلاحيتها من [المادة X من لائحة حوكمة الشركات الصادرة عن هيئة السوق المالية / إطار حوكمة ساما 2017 / الضابط 1-2-1 من ECC-2 الصادر عن الهيئة الوطنية للأمن السيبراني / قرار محدد من المجلس]. تعمل اللجنة بصلاحية مفوّضة من [الجهة الأم] وترفع تقاريرها إلى [جهة الرفع].

## 2. الولاية
اللجنة مسؤولة عن:
- [بند الولاية الأول]
- [بند الولاية الثاني]
- [بند الولاية الثالث]

## 3. الصلاحيات وحقوق اتخاذ القرار
اللجنة مخوّلة بـ:
- اعتماد [نوع القرار] حتى [الحد].
- التوصية إلى [الجهة الأم] في جميع الأمور التي تتجاوز الحد المذكور.
- الاستعانة بمستشارين خارجيين (قانونيين، فنيين، ماليين) عند الحاجة، بسقف ميزانية [مبلغ] ريال لكل تكليف.
- طلب المعلومات والمستندات ووقت الموظفين من أي وظيفة في الشركة.
- عقد جلسات تنفيذية (مغلقة) دون حضور الإدارة.

## 4. التشكيل
- **الرئيس:** [اللقب / متطلبات الدور — الاستقلالية / الخبير المالي / المؤهل القطاعي].
- **الأعضاء:** [العدد] أعضاء إجمالاً. [وصف المزيج المتوقع: غير تنفيذيين مستقلون، تنفيذيون، مدعوون دائمون].
- **الأمين:** [الدور — عادة أمين سر الشركة أو رئيس المخاطر أو مسؤول وظيفي].
- **مدة العضوية:** [3 سنوات للجان الحوكمة / متوافقة مع مدة العضوية / غير محددة للجان الإدارية].
- **الاستقلالية:** [حدد المقاعد التي يجب أن يشغلها أعضاء مستقلون وفق متطلبات الجهة التنظيمية].
- **مدعوون دائمون:** [الأدوار المدعوة لجميع الاجتماعات دون حق التصويت].

## 5. النصاب والتصويت
- **النصاب:** [X]% من الأعضاء المصوّتين حاضرون (الحد الأدنى [N] أعضاء).
- **التصويت:** صوت واحد لكل عضو مصوّت. للرئيس صوت مرجح في حال التعادل.
- **تعارض المصالح:** يجب على الأعضاء الإفصاح عن أي تعارض في بداية كل اجتماع والانسحاب من البند ذي الصلة؛ يُسجل الانسحاب في المحضر.
- **السرية:** جميع مداولات اللجنة سرية. يلتزم الأعضاء بتعهدات السرية الخاصة بالشركة.

## 6. الاجتماعات
- **التواتر:** [التكرار، مثل شهري / ربع سنوي / نصف سنوي / سنوي].
- **التقويم السنوي:** يصدره الأمين بحلول [التاريخ] من كل عام، يراجعه الرئيس، وتعتمده الجهة الأم.
- **الإشعار:** إشعار مسبق بـ [10] أيام عمل للاجتماعات العادية؛ [24] ساعة للاجتماعات الطارئة.
- **أوراق ما قبل القراءة:** تُعمَّم قبل الاجتماع بـ [7] أيام عمل على الأقل. الأوراق المتأخرة تتطلب موافقة الرئيس.
- **المحضر:** يُحرره الأمين خلال [3] أيام عمل، يُعمَّم لتعليقات الأعضاء خلال [7] أيام عمل، يُنهى ويُعتمد في الاجتماع التالي (أو بالتصويت الإلكتروني للبنود الحساسة زمنياً).

## 7. الرفع والتصعيد
- ترفع اللجنة تقاريرها إلى [الجهة الأم] [ربع سنوياً / لكل حدث جوهري].
- الأمور الجوهرية المصعَّدة إلى الأعلى تشمل: [قائمة حسب نوع اللجنة].
- يُدرج التقرير السنوي عن عمل اللجنة في [تقرير حوكمة الشركات / التقرير السنوي].

## 8. المرجع التنظيمي
تتوافق ولاية هذه اللجنة مع متطلبات الجهات التنظيمية التالية:
- [اسم الجهة التنظيمية + مرجع المادة / الضابط]
- [اسم الجهة التنظيمية + مرجع المادة / الضابط]

## 9. المراجعة السنوية
- تراجع اللجنة هذا الميثاق سنوياً.
- التعديلات الجوهرية تتطلب موافقة [الجهة الأم].
- يُقيَّم أداء اللجنة مقابل هذا الميثاق سنوياً كجزء من عملية تقييم [المجلس / اللجنة].

---

**معتمد:**
[اسم الرئيس]   [التاريخ]
[اسم رئيس الجهة الأم]   [التاريخ]
',

 N'Replace all [BRACKETED] placeholders with values specific to the committee. Mandate, authority, composition, and quorum should reflect the regulator anchor (CMA Article X, SAMA RGov 2017, NCA ECC-2 control). Have General Counsel review before Board approval. The annual review date drives an F8 reminder 30 days before expiry.',
 N'استبدل جميع العناصر النائبة [بين الأقواس] بقيم محددة للجنة. يجب أن يعكس النطاق والصلاحيات والتشكيل والنصاب المرجع التنظيمي (المادة X من هيئة السوق المالية، إطار ساما 2017، الضابط 1-2-1 من ECC-2). راجع المستشار العام قبل اعتماد المجلس. يُفعّل تاريخ المراجعة السنوية تذكيراً F8 قبل الانتهاء بـ 30 يوماً.',
 N'1.0'),

-- ═════════════════════════════════════════════════════════════════════
-- 2. AGENDA template
-- ═════════════════════════════════════════════════════════════════════
(N'FORM-AGENDA-001', N'AGENDA', N'ALL', N'CMA,SAMA',
 N'Meeting Agenda Template', N'نموذج جدول أعمال الاجتماع',
 N'Standard agenda template for any committee meeting. The annual calendar generator auto-populates this with the committee''s standing items + open actions + escalations from cross-module feeds. The Chair finalises the agenda 14 days before the meeting.',
 N'نموذج موحّد لجدول أعمال أي اجتماع لجنة. يقوم مولّد التقويم السنوي بتعبئة هذا النموذج بالبنود الدائمة + الإجراءات المفتوحة + التصعيدات من تكاملات الوحدات. يعتمد الرئيس جدول الأعمال قبل الاجتماع بـ 14 يوماً.',

 N'# [Committee Name] — Meeting Agenda

**Meeting code:** [MTG-YYYY-Qx-XXX-NNN]   **Type:** [Regular / Special / Annual]
**Scheduled:** [YYYY-MM-DD HH:mm Riyadh / Gregorian]   **Hijri:** [YYYY-MM-DD]
**Location:** [Boardroom / Microsoft Teams / Hybrid]
**Chair:** [Name]   **Secretary:** [Name]
**Quorum required:** [X] of [Y] voting members ([Z]%)

---

## Pre-meeting checklist
- [ ] Agenda finalised by Chair on [date — should be ≥14 days before meeting]
- [ ] Pre-read papers circulated on [date — should be ≥7 days before]
- [ ] COI declarations refreshed
- [ ] Quorum confirmed
- [ ] Audio / video / dial-in tested

---

## Agenda

| # | Topic | Presenter | Time | Decision required | Pre-read ref |
|---|---|---|---|---|---|
| 1 | Welcome, apologies, quorum check | Chair | 5 min | — | — |
| 2 | Approval of previous minutes (date) | Chair | 5 min | Yes | Paper 1 |
| 3 | Matters arising / open actions review | Secretary | 10 min | — | Paper 2 |
| 4 | [Standing item 1] | [Presenter] | [N min] | [Yes/No] | [Paper #] |
| 5 | [Standing item 2] | [Presenter] | [N min] | [Yes/No] | [Paper #] |
| 6 | [Ad-hoc item — added by Chair] | [Presenter] | [N min] | [Yes/No] | [Paper #] |
| 7 | AOB (Any Other Business) | Chair | 5 min | — | — |
| 8 | Executive session (in-camera, if applicable) | Chair | [N min] | — | — |
| 9 | Date of next meeting | Secretary | 2 min | — | — |

**Total scheduled time:** [N hours N min]

---

## Notes
- Members joining via Teams should connect at [start time minus 10 min] to test audio/video.
- Recording is [enabled / not enabled] — confidentiality settings: [Internal / Confidential / Restricted].
- Late papers (circulated < 7 days before) are flagged in the table above.
- Any item where the Chair declares an executive session will be conducted without management present, with the Secretary instructed to leave the meeting room.
',

 N'# [اسم اللجنة] — جدول أعمال الاجتماع

**رمز الاجتماع:** [MTG-YYYY-Qx-XXX-NNN]   **النوع:** [عادي / استثنائي / سنوي]
**موعد الاجتماع:** [YYYY-MM-DD HH:mm بتوقيت الرياض / ميلادي]   **هجري:** [YYYY-MM-DD]
**المكان:** [قاعة المجلس / Microsoft Teams / مختلط]
**الرئيس:** [الاسم]   **الأمين:** [الاسم]
**النصاب المطلوب:** [X] من [Y] أعضاء مصوّتين ([Z]%)

---

## قائمة فحص ما قبل الاجتماع
- [ ] اعتمد الرئيس جدول الأعمال في [التاريخ — يجب أن يكون قبل الاجتماع بـ 14 يوماً على الأقل]
- [ ] عُمّمت أوراق ما قبل القراءة في [التاريخ — يجب أن يكون قبل الاجتماع بـ 7 أيام على الأقل]
- [ ] حُدّثت إعلانات تعارض المصالح
- [ ] تأكد النصاب
- [ ] جُرّبت الصوتيات / الفيديو / الاتصال الهاتفي

---

## جدول الأعمال

| # | البند | المقدّم | الوقت | يتطلب قراراً | مرجع ورقة القراءة |
|---|---|---|---|---|---|
| 1 | الترحيب، الاعتذارات، فحص النصاب | الرئيس | 5 د | — | — |
| 2 | اعتماد محضر الاجتماع السابق (التاريخ) | الرئيس | 5 د | نعم | الورقة 1 |
| 3 | الأمور المنبثقة / مراجعة الإجراءات المفتوحة | الأمين | 10 د | — | الورقة 2 |
| 4 | [البند الدائم 1] | [المقدّم] | [N د] | [نعم/لا] | [الورقة #] |
| 5 | [البند الدائم 2] | [المقدّم] | [N د] | [نعم/لا] | [الورقة #] |
| 6 | [بند طارئ — أضافه الرئيس] | [المقدّم] | [N د] | [نعم/لا] | [الورقة #] |
| 7 | أمور أخرى | الرئيس | 5 د | — | — |
| 8 | جلسة تنفيذية (مغلقة، إن وُجدت) | الرئيس | [N د] | — | — |
| 9 | تاريخ الاجتماع القادم | الأمين | 2 د | — | — |

**إجمالي الوقت المجدول:** [N ساعات N دقيقة]

---

## ملاحظات
- على الأعضاء المنضمين عبر Teams الاتصال قبل البداية بـ 10 دقائق لاختبار الصوتيات والفيديو.
- التسجيل [مفعّل / غير مفعّل] — إعدادات السرية: [داخلي / سري / مقيّد].
- الأوراق المتأخرة (المعمَّمة قبل أقل من 7 أيام) موضوعة عليها علامة في الجدول أعلاه.
- أي بند يعلن فيه الرئيس جلسة تنفيذية يُعقد دون حضور الإدارة، مع توجيه الأمين بمغادرة قاعة الاجتماع.
',

 N'The annual-calendar generator pre-fills items 4-5+ from committee_standing_items. The Chair reviews and adds ad-hoc items. The Secretary distributes the final agenda + paper pack ≥7 days before the meeting via the platform (with read-receipt tracking). For special-purpose or emergency meetings, the chair can compress the timeline but should document the deviation.',
 N'يقوم مولّد التقويم السنوي بملء البنود من 4 فصاعداً من committee_standing_items. يراجع الرئيس ويضيف البنود الطارئة. يوزع الأمين جدول الأعمال النهائي وحزمة الأوراق قبل الاجتماع بـ 7 أيام عبر المنصة (مع تتبع إشعارات القراءة). للاجتماعات الخاصة أو الطارئة، يمكن للرئيس ضغط الجدول الزمني لكن يجب توثيق الانحراف.',
 N'1.0'),

-- ═════════════════════════════════════════════════════════════════════
-- 3. PRE-READ COVERSHEET
-- ═════════════════════════════════════════════════════════════════════
(N'FORM-PREREAD-001', N'PRE_READ_COVERSHEET', N'ALL', N'CMA',
 N'Pre-Read Paper Coversheet', N'صفحة غلاف ورقة ما قبل القراءة',
 N'Standardised coversheet attached to every paper circulated to a committee. Forces the author to declare the paper''s purpose, decisions sought, confidentiality, and reading time — so members know what they''re committing to read.',
 N'صفحة غلاف موحّدة تُرفق بكل ورقة تُعمَّم على لجنة. تُلزم المؤلف بإعلان الغرض من الورقة، القرارات المطلوبة، السرية، ووقت القراءة — حتى يعرف الأعضاء ما يلتزمون بقراءته.',

 N'# Pre-Read Paper Coversheet

| Field | Value |
|---|---|
| Paper code | [PAPER-YYYY-Qx-CMTE-NNN] |
| Title | [Paper title] |
| Author | [Name + role] |
| Committee | [Committee name] |
| Meeting | [Meeting code / date] |
| Agenda item | [#N] |
| Confidentiality | [Public / Internal / Confidential / Restricted] |
| Reading time | [N min] |
| Date circulated | [YYYY-MM-DD] |
| Late paper? | [No / Yes — reason: ___ — approved by Chair: yes/no] |

## Purpose of paper
[1-2 sentences — what is this paper for]

## Decision(s) sought
- [Decision 1 — Approve / Note / Discuss / Recommend]
- [Decision 2 — Approve / Note / Discuss / Recommend]

## Background (≤200 words)
[Context that the reader needs before reading the body]

## Key risks / issues highlighted
- [Risk 1]
- [Risk 2]

## Recommendations
[What the author recommends. If "Note only", state so explicitly.]

## Related papers / records
- [Cross-reference any related committee paper, board resolution, risk register entry, audit finding, etc.]

---

**Reviewed by Author''s Manager:** [Name]   **Date:** [YYYY-MM-DD]
**Cleared for circulation by Secretary:** [Name]   **Date:** [YYYY-MM-DD]
',

 N'# صفحة غلاف ورقة ما قبل القراءة

| الحقل | القيمة |
|---|---|
| رمز الورقة | [PAPER-YYYY-Qx-CMTE-NNN] |
| العنوان | [عنوان الورقة] |
| المؤلف | [الاسم + الدور] |
| اللجنة | [اسم اللجنة] |
| الاجتماع | [رمز / تاريخ الاجتماع] |
| بند جدول الأعمال | [#N] |
| السرية | [عام / داخلي / سري / مقيّد] |
| وقت القراءة | [N دقيقة] |
| تاريخ التعميم | [YYYY-MM-DD] |
| ورقة متأخرة؟ | [لا / نعم — السبب: ___ — اعتماد الرئيس: نعم/لا] |

## الغرض من الورقة
[جملة أو جملتان — ما الغرض من هذه الورقة]

## القرار(ات) المطلوبة
- [القرار 1 — اعتماد / علم / مناقشة / توصية]
- [القرار 2 — اعتماد / علم / مناقشة / توصية]

## الخلفية (≤200 كلمة)
[السياق الذي يحتاج القارئ معرفته قبل قراءة المتن]

## المخاطر / القضايا الرئيسية المرصودة
- [المخاطرة 1]
- [المخاطرة 2]

## التوصيات
[ما يوصي به المؤلف. إذا كانت "للعلم فقط"، اذكر ذلك صراحة.]

## الأوراق / السجلات ذات الصلة
- [الإشارة المرجعية لأي ورقة لجنة، قرار مجلس، إدخال في سجل المخاطر، ملاحظة مراجعة ذات صلة]

---

**روجعت من مدير المؤلف:** [الاسم]   **التاريخ:** [YYYY-MM-DD]
**أُجيز التعميم بواسطة الأمين:** [الاسم]   **التاريخ:** [YYYY-MM-DD]
',

 N'Authors complete this coversheet before submitting any paper. The Secretary rejects papers without a complete coversheet. "Reading time" is a self-assessment — if the body is >5 pages or has heavy data tables, allocate 15+ minutes.',
 N'يكمل المؤلفون صفحة الغلاف هذه قبل تقديم أي ورقة. يرفض الأمين الأوراق دون صفحة غلاف مكتملة. "وقت القراءة" تقدير ذاتي — إذا كان المتن > 5 صفحات أو يحوي جداول بيانات كثيفة، خصص 15 دقيقة أو أكثر.',
 N'1.0'),

-- ═════════════════════════════════════════════════════════════════════
-- 4. ATTENDANCE & QUORUM register
-- ═════════════════════════════════════════════════════════════════════
(N'FORM-ATTENDANCE-001', N'ATTENDANCE', N'ALL', N'CMA,SAMA',
 N'Attendance & Quorum Register', N'سجل الحضور والنصاب',
 N'Captured at the start of every committee meeting by the Secretary. Records who attended, who sent apologies, who joined late or left early, and whether quorum was met at each agenda item (relevant when members recuse themselves on COI grounds).',
 N'يُلتقط في بداية كل اجتماع لجنة من قبل الأمين. يسجل من حضر، ومن أرسل اعتذاراً، ومن انضم متأخراً أو غادر مبكراً، وما إذا كان النصاب متوافراً عند كل بند من جدول الأعمال (مهم عندما ينسحب الأعضاء بسبب تعارض المصالح).',

 N'# Attendance & Quorum Register

**Committee:** [Name]   **Meeting:** [Code]   **Date:** [YYYY-MM-DD]
**Scheduled start:** [HH:mm]   **Actual start:** [HH:mm]   **End:** [HH:mm]
**Quorum required:** [X]% of voting members ([N] of [M])

## Attendance

| # | Member | Role | Voting? | Present? | Joined | Left | Mode | Notes |
|---|---|---|---|---|---|---|---|---|
| 1 | [Name] | Chair | Yes | ☐ | [HH:mm] | [HH:mm] | In-person / Teams | |
| 2 | [Name] | Vice-Chair | Yes | ☐ | | | | |
| 3 | [Name] | Member | Yes | ☐ | | | | |
| 4 | [Name] | Member | Yes | ☐ | | | | Apologies received [date] |
| 5 | [Name] | Secretary | No | ☐ | | | | |
| 6 | [Name] | Standing invitee | No | ☐ | | | | |

**Total voting members present:** [N]   **Quorum met:** [Yes / No]

## Quorum at each agenda item
For items where a member recuses themselves on COI grounds:

| Item # | Recused member | Reason (COI) | Quorum after recusal | Quorum still met? |
|---|---|---|---|---|
| | | | | |

## Late arrivals / early departures
| Member | Joined at | Items missed | Left at | Items missed |
|---|---|---|---|---|
| | | | | |

## Apologies received
| Member | Reason | Received on | Acknowledged by |
|---|---|---|---|
| | | | |

---

**Confirmed by Secretary:** [Name]   **Signature:** ___   **Date:** [YYYY-MM-DD]
**Confirmed by Chair:** [Name]   **Signature:** ___   **Date:** [YYYY-MM-DD]
',

 N'# سجل الحضور والنصاب

**اللجنة:** [الاسم]   **الاجتماع:** [الرمز]   **التاريخ:** [YYYY-MM-DD]
**البداية المجدولة:** [HH:mm]   **البداية الفعلية:** [HH:mm]   **النهاية:** [HH:mm]
**النصاب المطلوب:** [X]% من الأعضاء المصوّتين ([N] من [M])

## الحضور

| # | العضو | الدور | مصوّت؟ | حاضر؟ | انضم | غادر | الوسيلة | ملاحظات |
|---|---|---|---|---|---|---|---|---|
| 1 | [الاسم] | الرئيس | نعم | ☐ | [HH:mm] | [HH:mm] | شخصياً / Teams | |
| 2 | [الاسم] | نائب الرئيس | نعم | ☐ | | | | |
| 3 | [الاسم] | عضو | نعم | ☐ | | | | |
| 4 | [الاسم] | عضو | نعم | ☐ | | | | اعتذار مستلم [التاريخ] |
| 5 | [الاسم] | الأمين | لا | ☐ | | | | |
| 6 | [الاسم] | مدعو دائم | لا | ☐ | | | | |

**إجمالي الأعضاء المصوّتين الحاضرين:** [N]   **النصاب متوافر:** [نعم / لا]

## النصاب عند كل بند من جدول الأعمال
للبنود التي ينسحب فيها عضو بسبب تعارض المصالح:

| رقم البند | العضو المنسحب | السبب (تعارض المصالح) | النصاب بعد الانسحاب | النصاب لا يزال متوافراً؟ |
|---|---|---|---|---|
| | | | | |

## الوصول المتأخر / المغادرة المبكرة
| العضو | انضم في | البنود الفائتة | غادر في | البنود الفائتة |
|---|---|---|---|---|
| | | | | |

## الاعتذارات المستلمة
| العضو | السبب | مستلم في | تم تأكيده بواسطة |
|---|---|---|---|
| | | | |

---

**تأكيد الأمين:** [الاسم]   **التوقيع:** ___   **التاريخ:** [YYYY-MM-DD]
**تأكيد الرئيس:** [الاسم]   **التوقيع:** ___   **التاريخ:** [YYYY-MM-DD]
',

 N'The Secretary completes this register live at the start of the meeting. If quorum is not met within 30 minutes of the scheduled start, the Chair may either adjourn the meeting or convert it to an informational session with no decisions taken. COI recusals can drop quorum below threshold for specific items — when that happens, the item is deferred to the next meeting.',
 N'يكمل الأمين هذا السجل مباشرة في بداية الاجتماع. إذا لم يتوافر النصاب خلال 30 دقيقة من البداية المجدولة، يمكن للرئيس إما تأجيل الاجتماع أو تحويله إلى جلسة معلوماتية دون اتخاذ قرارات. يمكن لانسحابات تعارض المصالح أن تُسقط النصاب لبنود معينة — عند حدوث ذلك، يُؤجل البند للاجتماع التالي.',
 N'1.0'),

-- ═════════════════════════════════════════════════════════════════════
-- 5. MOTION / RESOLUTION form
-- ═════════════════════════════════════════════════════════════════════
(N'FORM-MOTION-001', N'MOTION', N'ALL', N'CMA,SAMA',
 N'Motion / Resolution Form', N'نموذج اقتراح / قرار',
 N'Used during a meeting to record a formal motion. Must be proposed by one member and seconded by another before voting. The form is read aloud by the Secretary before voting, and the final wording is what enters the resolutions register.',
 N'يُستخدم أثناء الاجتماع لتسجيل اقتراح رسمي. يجب أن يقترحه عضو واحد ويؤيده عضو آخر قبل التصويت. يقرأ الأمين النموذج بصوت عالٍ قبل التصويت، وتدخل الصيغة النهائية في سجل القرارات.',

 N'# Motion / Resolution Form

**Resolution code:** [RES-YYYY-Qx-CMTE-NNN]   **Meeting:** [Code]
**Date:** [YYYY-MM-DD]   **Agenda item:** [#N]
**Confidentiality:** [Public / Internal / Confidential / Restricted]

## Motion text
> "[The full text of the motion as it should appear in the minutes and resolutions register. Use exact, unambiguous language. Avoid pronouns where possible.]"

## Proposed by
[Member name + role]

## Seconded by
[Member name + role]

## Discussion summary
[Brief summary of the debate — 3-5 lines max. Detailed discussion stays in the minutes; this is just the head-line points that led to the motion''s current wording.]

## Vote
| Outcome | Count |
|---|---|
| For | [N] |
| Against | [N] |
| Abstain | [N] |
| Recused (COI) | [N] |
| **Total voting members** | **[N]** |

**Outcome:** [APPROVED / REJECTED / DEFERRED / WITHDRAWN]

## Dissent record (if any)
[Any member who voted "Against" may have their dissent recorded with reasoning, if they so request. Per CMA Article 31 dissent on board / committee resolutions is a member''s right.]

| Dissenting member | Reasoning |
|---|---|
| | |

## Effective from
[Date the resolution takes effect — may be immediate, or a future date]

## Review required by
[If the resolution sunsets or needs renewal, the date by which it must be re-considered]

## Regulator notification
[If the resolution triggers a CMA / SAMA / NCA / SDAIA notification — specify the regulator + filing deadline]

## Linked records
- [Cross-reference: risk ID, audit finding, policy version, board resolution, etc.]

---

**Read aloud by Secretary:** [Name]   **Signed by Chair:** ___   **Date:** [YYYY-MM-DD]
',

 N'# نموذج اقتراح / قرار

**رمز القرار:** [RES-YYYY-Qx-CMTE-NNN]   **الاجتماع:** [الرمز]
**التاريخ:** [YYYY-MM-DD]   **بند جدول الأعمال:** [#N]
**السرية:** [عام / داخلي / سري / مقيّد]

## نص الاقتراح
> "[النص الكامل للاقتراح كما يجب أن يظهر في المحضر وسجل القرارات. استخدم لغة دقيقة لا لبس فيها. تجنب الضمائر حيثما أمكن.]"

## اقترحه
[اسم العضو + الدور]

## أيّده
[اسم العضو + الدور]

## ملخص المناقشة
[ملخص موجز للنقاش — 3-5 أسطر بحد أقصى. تبقى المناقشة المفصلة في المحضر؛ هذه فقط النقاط الرئيسية التي أدت إلى الصياغة الحالية للاقتراح.]

## التصويت
| النتيجة | العدد |
|---|---|
| مع | [N] |
| ضد | [N] |
| امتناع | [N] |
| منسحبون (تعارض مصالح) | [N] |
| **إجمالي الأعضاء المصوّتين** | **[N]** |

**النتيجة:** [اعتُمد / رُفض / أُجل / سُحب]

## سجل الاعتراض (إن وُجد)
[يحق لأي عضو صوّت "ضد" أن يُسجَّل اعتراضه مع التعليل، إذا طلب ذلك. وفق المادة 31 من لائحة هيئة السوق المالية، الاعتراض على قرارات المجلس / اللجان حق للعضو.]

| العضو المعترض | التعليل |
|---|---|
| | |

## ساري من
[تاريخ سريان القرار — قد يكون فورياً أو تاريخاً مستقبلياً]

## مراجعة مطلوبة بحلول
[إذا كان القرار ينتهي تلقائياً أو يحتاج تجديداً، التاريخ الذي يجب أن يُعاد فيه النظر]

## إخطار الجهات التنظيمية
[إذا كان القرار يفعّل إخطاراً لـ هيئة السوق المالية / ساما / الهيئة الوطنية للأمن السيبراني / SDAIA — حدد الجهة التنظيمية + الموعد النهائي للتقديم]

## السجلات المرتبطة
- [الإشارة المرجعية: رقم المخاطرة، ملاحظة المراجعة، نسخة السياسة، قرار المجلس، إلخ.]

---

**قرأه الأمين بصوت عالٍ:** [الاسم]   **توقيع الرئيس:** ___   **التاريخ:** [YYYY-MM-DD]
',

 N'Critical resolutions (those triggering regulator notification, or those changing the risk appetite, or those approving expenditure above delegated authority) require the dissent record to be explicit even if no member dissented — record "no dissent" rather than leave blank. The resolution code follows the pattern RES-YYYY-Qx-CMTE-NNN where CMTE is the 3-letter committee code (BRD, AUD, RSK, etc).',
 N'القرارات الحرجة (تلك التي تفعّل إخطار الجهات التنظيمية، أو تغيّر شهية المخاطر، أو تعتمد إنفاقاً يتجاوز الصلاحية المفوّضة) تتطلب سجل اعتراض صريحاً حتى لو لم يعترض أحد — سجّل "لا اعتراض" بدلاً من الترك فارغاً. يتبع رمز القرار النمط RES-YYYY-Qx-CMTE-NNN حيث CMTE هو رمز اللجنة المكوّن من ثلاثة حروف (BRD، AUD، RSK، إلخ).',
 N'1.0'),

-- ═════════════════════════════════════════════════════════════════════
-- 6. MINUTES template
-- ═════════════════════════════════════════════════════════════════════
(N'FORM-MINUTES-001', N'MINUTES', N'ALL', N'CMA,SAMA',
 N'Meeting Minutes Template (SAMA/CMA-aligned)', N'نموذج محضر الاجتماع (متوافق مع ساما وهيئة السوق المالية)',
 N'Standardised minutes format that matches what SAMA and CMA examiners expect to see. The Secretary drafts within 3 business days, circulates within 7 business days, and seeks approval at the next meeting (or by electronic vote for time-sensitive items).',
 N'صيغة محضر موحّدة تتطابق مع ما يتوقع رؤيته فاحصو ساما وهيئة السوق المالية. يحرر الأمين خلال 3 أيام عمل، يعمم خلال 7 أيام عمل، ويطلب الاعتماد في الاجتماع التالي (أو بالتصويت الإلكتروني للبنود الحساسة زمنياً).',

 N'# Minutes — [Committee Name]

**Meeting code:** [MTG-YYYY-Qx-CMTE-NNN]   **Type:** [Regular / Special]
**Date:** [YYYY-MM-DD]   **Time:** [HH:mm — HH:mm]
**Location:** [Boardroom + Teams hybrid / Microsoft Teams / Specific venue]
**Chair:** [Name]   **Secretary:** [Name]
**Quorum met:** [Yes — N of M voting members present]

## 1. Welcome, apologies, quorum check
The Chair opened the meeting at [HH:mm]. Apologies were received from [members]. Quorum was confirmed by the Secretary ([N] of [M] voting members present, [X]%).

## 2. Approval of previous minutes
The minutes of the previous meeting on [date] were [approved as circulated / approved subject to the following amendments: ___].

## 3. Matters arising / open actions
The Secretary tabled the open-actions register. [N] actions were reported complete, [N] in progress, [N] overdue. The Chair noted [specific action] for follow-up.

## 4. [Standing item — e.g. CEO report]
The [presenter] tabled [paper code]. Key points discussed:
- [Discussion point 1]
- [Discussion point 2]
- [Discussion point 3]

**Resolution [RES-YYYY-Qx-CMTE-NNN]:** "[Full text of resolution]"
**Vote:** [For] / [Against] / [Abstain] — **Outcome:** [APPROVED]

## 5. [Standing item — repeat structure for each agenda item]

## 6. AOB (Any Other Business)
[Members raised the following items: ___ / No items raised.]

## 7. Executive session (in-camera) — if held
The meeting moved into executive session at [HH:mm] for [N min]. [Confidential — content summarised separately in the executive-session minutes filed with the Chair only.]

## 8. Date of next meeting
The next [regular / special] meeting is scheduled for [YYYY-MM-DD] at [HH:mm].

## 9. Closure
The Chair closed the meeting at [HH:mm].

---

## Resolutions adopted at this meeting
| Resolution code | Title | Outcome |
|---|---|---|
| RES-YYYY-Qx-CMTE-NNN | [Title] | APPROVED |

## Action items arising
| Action code | Action | Owner | Due | Priority |
|---|---|---|---|---|
| ACT-YYYY-Qx-CMTE-NNN | [Action] | [Owner] | [YYYY-MM-DD] | [Priority] |

## Attendance
[Reference to the attendance-register form, attached as Appendix A]

## Conflicts of interest declared
[List of COI declarations made at the start of the meeting and any recusals during specific items.]

---

**Drafted by Secretary:** [Name]   **Date:** [YYYY-MM-DD]
**Circulated to members:** [Date]
**Approved at meeting on:** [Date]
**Signed by Chair:** [Name + signature]
',

 N'# محضر — [اسم اللجنة]

**رمز الاجتماع:** [MTG-YYYY-Qx-CMTE-NNN]   **النوع:** [عادي / استثنائي]
**التاريخ:** [YYYY-MM-DD]   **الوقت:** [HH:mm — HH:mm]
**المكان:** [قاعة المجلس + Teams مختلط / Microsoft Teams / مكان محدد]
**الرئيس:** [الاسم]   **الأمين:** [الاسم]
**النصاب متوافر:** [نعم — N من M أعضاء مصوّتين حاضرين]

## 1. الترحيب، الاعتذارات، فحص النصاب
افتتح الرئيس الاجتماع في [HH:mm]. وردت اعتذارات من [الأعضاء]. أكد الأمين توافر النصاب ([N] من [M] أعضاء مصوّتين حاضرين، [X]%).

## 2. اعتماد المحضر السابق
[اعتُمد محضر الاجتماع السابق بتاريخ [التاريخ] كما تم تعميمه / اعتُمد رهناً بالتعديلات التالية: ___].

## 3. الأمور المنبثقة / الإجراءات المفتوحة
عرض الأمين سجل الإجراءات المفتوحة. أُبلغ عن اكتمال [N] إجراءات، [N] قيد التنفيذ، [N] متأخرة. أشار الرئيس إلى [إجراء محدد] للمتابعة.

## 4. [البند الدائم — مثل تقرير الرئيس التنفيذي]
عرض [المقدّم] [رمز الورقة]. النقاط الرئيسية التي نوقشت:
- [نقطة المناقشة 1]
- [نقطة المناقشة 2]
- [نقطة المناقشة 3]

**قرار [RES-YYYY-Qx-CMTE-NNN]:** "[النص الكامل للقرار]"
**التصويت:** [مع] / [ضد] / [امتناع] — **النتيجة:** [اعتُمد]

## 5. [البند الدائم — كرر الهيكل لكل بند من جدول الأعمال]

## 6. أمور أخرى
[طرح الأعضاء البنود التالية: ___ / لم تُطرح أي بنود.]

## 7. الجلسة التنفيذية (المغلقة) — إن عُقدت
انتقل الاجتماع إلى جلسة تنفيذية في [HH:mm] لمدة [N دقيقة]. [سري — يُلخّص المحتوى منفصلاً في محضر الجلسة التنفيذية المحفوظ مع الرئيس فقط.]

## 8. تاريخ الاجتماع القادم
الاجتماع [العادي / الاستثنائي] القادم مجدول في [YYYY-MM-DD] في [HH:mm].

## 9. الاختتام
أغلق الرئيس الاجتماع في [HH:mm].

---

## القرارات المعتمدة في هذا الاجتماع
| رمز القرار | العنوان | النتيجة |
|---|---|---|
| RES-YYYY-Qx-CMTE-NNN | [العنوان] | اعتُمد |

## الإجراءات المنبثقة
| رمز الإجراء | الإجراء | المالك | الاستحقاق | الأولوية |
|---|---|---|---|---|
| ACT-YYYY-Qx-CMTE-NNN | [الإجراء] | [المالك] | [YYYY-MM-DD] | [الأولوية] |

## الحضور
[الإشارة إلى نموذج سجل الحضور، مرفق كملحق أ]

## تعارضات المصالح المُعلَنة
[قائمة بإعلانات تعارض المصالح في بداية الاجتماع وأي انسحابات خلال بنود معينة.]

---

**حرره الأمين:** [الاسم]   **التاريخ:** [YYYY-MM-DD]
**عُمم للأعضاء:** [التاريخ]
**اعتُمد في اجتماع:** [التاريخ]
**وقّع الرئيس:** [الاسم + التوقيع]
',

 N'Minutes are NOT a transcript. Summarise discussion, name resolutions verbatim, and capture every action item. SAMA examiners expect to see: clear quorum confirmation, every resolution with its vote breakdown, every action with owner and date, and any COI recusal explicitly recorded. Executive-session content is excluded from the main minutes and filed separately with Chair-only access.',
 N'المحضر ليس نسخة حرفية. لخّص النقاش، اذكر القرارات حرفياً، والتقط كل بند إجراء. يتوقع فاحصو ساما رؤية: تأكيد نصاب واضح، كل قرار مع تفاصيل تصويته، كل إجراء مع المالك والتاريخ، وأي انسحاب تعارض مصالح مسجل صراحة. يُستثنى محتوى الجلسة التنفيذية من المحضر الرئيسي ويُحفظ منفصلاً بوصول الرئيس فقط.',
 N'1.0'),

-- ═════════════════════════════════════════════════════════════════════
-- 7. ACTION REGISTER
-- ═════════════════════════════════════════════════════════════════════
(N'FORM-ACTREG-001', N'ACTION_REGISTER', N'ALL', N'CMA',
 N'Post-Meeting Action Register', N'سجل الإجراءات بعد الاجتماع',
 N'Master register of every action item from every meeting of the committee. Rolled forward into each subsequent meeting''s agenda under "Matters arising / open actions". The platform auto-rolls overdue items into the next agenda.',
 N'سجل رئيسي لكل بند إجراء من كل اجتماع للجنة. يُنقل إلى جدول أعمال كل اجتماع لاحق تحت "الأمور المنبثقة / الإجراءات المفتوحة". تُدوّر المنصة البنود المتأخرة تلقائياً إلى جدول الأعمال التالي.',

 N'# Action Register — [Committee Name]

**Updated:** [YYYY-MM-DD HH:mm]   **By:** [Secretary name]

| Action code | Source meeting | Action | Owner | Due | Status | Priority | Linked record | Last update |
|---|---|---|---|---|---|---|---|---|
| ACT-2026-Q1-RSK-001 | MTG-2026-Q1-RSK-001 | Submit Aramco scenario to Board Q2 | CRO | 2026-06-15 | IN_PROGRESS | HIGH | Risk RSK-2026-004 | 2026-04-20 |
| ACT-2026-Q1-RSK-002 | MTG-2026-Q1-RSK-001 | Issue enterprise Copilot RFP | CISO | 2026-05-30 | IN_PROGRESS | NORMAL | — | 2026-04-22 |
| ACT-2026-Q1-RSK-003 | MTG-2026-Q1-RSK-001 | Block consumer-LLM domains via DNS | SecOps Lead | 2026-05-20 | OPEN | NORMAL | — | — |

## Status legend
- **OPEN** — owner has not yet acknowledged
- **IN_PROGRESS** — work has started
- **COMPLETED** — closed, evidence on file
- **OVERDUE** — past due date, escalation triggered
- **CANCELLED** — no longer relevant, Chair has approved closure without completion
- **DEFERRED** — date moved with Chair approval

## Escalation
Any item that goes **OVERDUE** is automatically:
1. Re-tabled at the next meeting under "Matters arising"
2. Emailed to the owner''s line manager
3. Reported to the Chair in the next pre-meeting brief

After three meetings overdue, the Chair must explicitly decide whether to escalate to the parent committee, cancel the action, or hold the owner accountable.

## Linkage
Each action item may be linked to:
- A risk in the risk register (`Risk:DEMO-001`)
- An audit finding (`AuditFinding:FND-2026-004`)
- A CAPA record (`Capa:CAPA-2026-Q2-014`)
- An incident (`Incident:INC-2026-007`)
- A policy due for review (`Policy:POL-INF-SEC-V3`)
- A previous resolution (`Resolution:RES-2026-Q1-BRD-003`)

The platform automatically updates the linked record''s "related committee actions" when status changes here.

---

**Auto-rolled into next agenda by:** Platform (Secretary may override)
**Next review date:** [Next meeting date]
',

 N'# سجل الإجراءات — [اسم اللجنة]

**محدّث:** [YYYY-MM-DD HH:mm]   **بواسطة:** [اسم الأمين]

| رمز الإجراء | الاجتماع المصدر | الإجراء | المالك | الاستحقاق | الحالة | الأولوية | السجل المرتبط | آخر تحديث |
|---|---|---|---|---|---|---|---|---|
| ACT-2026-Q1-RSK-001 | MTG-2026-Q1-RSK-001 | تقديم سيناريو أرامكو للمجلس في الربع الثاني | مدير المخاطر | 2026-06-15 | قيد التنفيذ | عالية | المخاطرة RSK-2026-004 | 2026-04-20 |
| ACT-2026-Q1-RSK-002 | MTG-2026-Q1-RSK-001 | إصدار RFP Copilot المؤسسي | مدير أمن المعلومات | 2026-05-30 | قيد التنفيذ | عادية | — | 2026-04-22 |
| ACT-2026-Q1-RSK-003 | MTG-2026-Q1-RSK-001 | حجب نطاقات LLM الاستهلاكية عبر DNS | قائد SecOps | 2026-05-20 | مفتوح | عادية | — | — |

## دليل الحالات
- **مفتوح** — لم يقر المالك بعد
- **قيد التنفيذ** — بدأ العمل
- **مكتمل** — مغلق، الأدلة موثقة
- **متأخر** — تجاوز موعد الاستحقاق، تم تفعيل التصعيد
- **ملغى** — لم يعد ذا صلة، اعتمد الرئيس الإغلاق دون إكمال
- **مؤجل** — تم تغيير التاريخ بموافقة الرئيس

## التصعيد
أي بند يصبح **متأخراً** يتم تلقائياً:
1. إعادة إدراجه في الاجتماع التالي تحت "الأمور المنبثقة"
2. إرساله بالبريد الإلكتروني لمدير المالك المباشر
3. الإبلاغ عنه للرئيس في إيجاز ما قبل الاجتماع التالي

بعد ثلاثة اجتماعات من التأخر، يجب على الرئيس أن يقرر صراحة إما التصعيد إلى اللجنة الأم، أو إلغاء الإجراء، أو محاسبة المالك.

## الارتباط
يمكن ربط كل بند إجراء بـ:
- مخاطرة في سجل المخاطر (`Risk:DEMO-001`)
- ملاحظة مراجعة (`AuditFinding:FND-2026-004`)
- سجل CAPA (`Capa:CAPA-2026-Q2-014`)
- حادثة (`Incident:INC-2026-007`)
- سياسة مستحقة للمراجعة (`Policy:POL-INF-SEC-V3`)
- قرار سابق (`Resolution:RES-2026-Q1-BRD-003`)

تحدّث المنصة تلقائياً "الإجراءات المرتبطة باللجنة" في السجل المرتبط عندما تتغير الحالة هنا.

---

**يُدوّر تلقائياً إلى جدول الأعمال التالي بواسطة:** المنصة (يمكن للأمين التجاوز)
**تاريخ المراجعة القادم:** [تاريخ الاجتماع التالي]
',

 N'The action register is a live document — owners update status in the platform, the Secretary verifies before the next meeting. Overdue items are auto-rolled. The linked-record column makes the platform navigate from a stale risk-register entry back to the committee where its remediation was last discussed.',
 N'سجل الإجراءات وثيقة حية — يحدّث المالكون الحالة في المنصة، ويتحقق الأمين قبل الاجتماع التالي. تُدوّر البنود المتأخرة تلقائياً. يسمح عمود السجل المرتبط للمنصة بالانتقال من إدخال سجل مخاطر قديم إلى اللجنة التي نوقشت فيها معالجته آخر مرة.',
 N'1.0'),

-- ═════════════════════════════════════════════════════════════════════
-- 8. CONFLICT-OF-INTEREST DECLARATION
-- ═════════════════════════════════════════════════════════════════════
(N'FORM-COI-001', N'COI_DECLARATION', N'ALL', N'CMA',
 N'Conflict-of-Interest Declaration', N'إعلان تعارض المصالح',
 N'Required at three points: (1) when joining the committee, (2) annually, (3) at the start of every meeting where COI may exist on a specific agenda item. Per CMA Corporate Governance Article 30 and SAMA RGov 2017.',
 N'مطلوب في ثلاث نقاط: (1) عند الانضمام للجنة، (2) سنوياً، (3) في بداية كل اجتماع قد يوجد فيه تعارض مصالح على بند معين. وفق المادة 30 من لائحة حوكمة الشركات الصادرة عن هيئة السوق المالية وإطار حوكمة ساما 2017.',

 N'# Conflict-of-Interest Declaration

**Member:** [Name + role]   **Committee:** [Name]
**Type:** [On joining / Annual / Specific meeting / Specific item]
**Date:** [YYYY-MM-DD]   **Period covered:** [if Annual: YYYY]

## Section A — Material relationships
Have you, or any related party (spouse, child, parent, sibling, business partner, or controlled entity), held any of the following in the past 12 months? Tick ✔ Yes / ✘ No for each.

| # | Relationship type | Yes / No | If Yes: details |
|---|---|---|---|
| 1 | Directorship / executive role in another company in the same sector | ☐ | |
| 2 | Material shareholding (>5%) in a company that contracts with the organisation | ☐ | |
| 3 | Consultancy / advisory engagement with the organisation or a competitor | ☐ | |
| 4 | Family member employed by the organisation in a senior position | ☐ | |
| 5 | Personal financial transaction with the organisation above SAR [threshold] | ☐ | |
| 6 | Gift / hospitality received from a counterparty above SAR [threshold] | ☐ | |
| 7 | Political-office or government-board appointment | ☐ | |
| 8 | Any other relationship that could be perceived as a conflict | ☐ | |

## Section B — Specific-meeting declaration
**This declaration applies to agenda item(s):** [#N — Item title]

I declare that I have a [direct / indirect] interest in the matter under consideration arising from [describe]. I [will / will not] participate in the discussion and [will / will not] vote on this item.

If recusing: I will leave the meeting room (or the Teams call) at the start of the item and rejoin only after the Chair confirms the matter has been concluded.

## Section C — Mitigation / management
| Mitigation | Applied? |
|---|---|
| Recusal from the specific agenda item | ☐ |
| Recusal from the entire meeting | ☐ |
| Resignation from the committee | ☐ |
| Acceptance with disclosure (low materiality) | ☐ |

## Section D — Acknowledgements
- I acknowledge that I have read and understood the company''s Code of Conduct + Conflict-of-Interest Policy.
- I acknowledge that failure to declare a material conflict may be a breach of CMA Corporate Governance regulations and may result in [sanctions per company policy].
- I undertake to update this declaration immediately upon any material change in my circumstances.

---

**Member signature:** ___   **Date:** [YYYY-MM-DD]
**Acknowledged by Secretary:** ___   **Date:** [YYYY-MM-DD]
**Reviewed by Chair (if needed):** ___   **Date:** [YYYY-MM-DD]
',

 N'# إعلان تعارض المصالح

**العضو:** [الاسم + الدور]   **اللجنة:** [الاسم]
**النوع:** [عند الانضمام / سنوي / اجتماع محدد / بند محدد]
**التاريخ:** [YYYY-MM-DD]   **الفترة المغطاة:** [إذا كان سنوياً: YYYY]

## القسم أ — العلاقات الجوهرية
هل كنت أنت، أو أي طرف ذي علاقة (الزوج/الزوجة، الأبناء، الوالدان، الأشقاء، الشركاء التجاريون، أو الكيانات المسيطر عليها)، تشغل أياً مما يلي خلال الـ 12 شهراً الماضية؟ ضع ✔ نعم / ✘ لا لكل بند.

| # | نوع العلاقة | نعم / لا | إذا نعم: التفاصيل |
|---|---|---|---|
| 1 | عضوية مجلس إدارة / دور تنفيذي في شركة أخرى في القطاع نفسه | ☐ | |
| 2 | حصة جوهرية (>5%) في شركة تتعاقد مع المنظمة | ☐ | |
| 3 | تكليف استشاري مع المنظمة أو منافس | ☐ | |
| 4 | فرد من العائلة يعمل في المنظمة في منصب رفيع | ☐ | |
| 5 | معاملة مالية شخصية مع المنظمة فوق [الحد] ريال | ☐ | |
| 6 | هدية / ضيافة مستلمة من نظير فوق [الحد] ريال | ☐ | |
| 7 | منصب سياسي أو تعيين في مجلس حكومي | ☐ | |
| 8 | أي علاقة أخرى يمكن أن يُنظر إليها كتعارض | ☐ | |

## القسم ب — إعلان اجتماع محدد
**ينطبق هذا الإعلان على بند(بنود) جدول الأعمال:** [#N — عنوان البند]

أُقرّ بأن لدي مصلحة [مباشرة / غير مباشرة] في الموضوع قيد النظر ناشئة عن [الوصف]. [سأشارك / لن أشارك] في النقاش و[سأصوّت / لن أصوّت] على هذا البند.

في حال الانسحاب: سأغادر قاعة الاجتماع (أو مكالمة Teams) في بداية البند وأعود فقط بعد تأكيد الرئيس انتهاء الموضوع.

## القسم ج — التخفيف / الإدارة
| التخفيف | تم تطبيقه؟ |
|---|---|
| الانسحاب من البند المحدد | ☐ |
| الانسحاب من الاجتماع بأكمله | ☐ |
| الاستقالة من اللجنة | ☐ |
| القبول مع الإفصاح (جوهرية منخفضة) | ☐ |

## القسم د — التأكيدات
- أُقرّ بأنني قرأت وفهمت ميثاق سلوك الشركة + سياسة تعارض المصالح.
- أُقرّ بأن عدم الإفصاح عن تعارض جوهري قد يكون مخالفة للائحة حوكمة الشركات الصادرة عن هيئة السوق المالية وقد يؤدي إلى [العقوبات وفق سياسة الشركة].
- أتعهد بتحديث هذا الإعلان فور حدوث أي تغيير جوهري في ظروفي.

---

**توقيع العضو:** ___   **التاريخ:** [YYYY-MM-DD]
**تأكيد الأمين:** ___   **التاريخ:** [YYYY-MM-DD]
**مراجعة الرئيس (عند الحاجة):** ___   **التاريخ:** [YYYY-MM-DD]
',

 N'COI declarations must be on file before the first meeting. Annual refreshes are mandatory — the platform issues automatic reminders 30 days before the annual due date. Specific-item declarations are made at the start of the meeting and recorded in the attendance / minutes. CMA examiners ask for the COI register at every Corporate Governance audit.',
 N'يجب أن تكون إعلانات تعارض المصالح في الملف قبل الاجتماع الأول. التحديثات السنوية إلزامية — تصدر المنصة تذكيرات تلقائية قبل تاريخ الاستحقاق السنوي بـ 30 يوماً. تُعمل إعلانات البنود المحددة في بداية الاجتماع وتُسجل في الحضور / المحضر. يطلب فاحصو هيئة السوق المالية سجل تعارض المصالح في كل مراجعة لحوكمة الشركات.',
 N'1.0'),

-- ═════════════════════════════════════════════════════════════════════
-- 9. SKILLS MATRIX (governance committees)
-- ═════════════════════════════════════════════════════════════════════
(N'FORM-SKILLS-001', N'SKILLS_MATRIX', N'BOARD,AUDIT_COMMITTEE,NOMINATION_REMUNERATION', N'CMA',
 N'Board / Audit Committee Skills Matrix', N'مصفوفة مهارات المجلس / لجنة المراجعة',
 N'Skills-gap analysis tool. Plots each member''s expertise across 8-12 dimensions vs the committee''s required mix. Reviewed annually by the Nomination Committee, drives the next director-nomination cycle. CMA Article 16 expects this.',
 N'أداة تحليل فجوة المهارات. ترسم خبرة كل عضو عبر 8-12 بُعداً مقابل المزيج المطلوب للجنة. تُراجَع سنوياً من قبل لجنة الترشيحات، وتقود دورة ترشيح الأعضاء التالية. تتوقع المادة 16 من لائحة هيئة السوق المالية هذا.',

 N'# Skills Matrix — [Committee Name]

**Period:** [YYYY]   **Last reviewed:** [YYYY-MM-DD]   **By:** Nomination & Remuneration Committee

## Skill dimensions

Level: **0** = none, **1** = basic, **2** = intermediate, **3** = expert.

| Skill | Required min total | [Member 1] | [Member 2] | [Member 3] | [Member 4] | Total | Gap |
|---|---|---|---|---|---|---|---|
| Financial / accounting expertise (CMA "financial expert" criterion) | 3+ | 3 | 2 | 1 | 0 | 6 | ✓ |
| Sector / industry expertise (banking, insurance, healthcare, etc.) | 6+ | 3 | 3 | 2 | 1 | 9 | ✓ |
| Risk management | 4+ | 1 | 3 | 2 | 1 | 7 | ✓ |
| Cyber-security / IT governance | 3+ | 0 | 1 | 3 | 1 | 5 | ✓ |
| Legal / regulatory | 3+ | 1 | 2 | 1 | 3 | 7 | ✓ |
| ESG / sustainability | 2+ | 0 | 1 | 2 | 1 | 4 | ✓ |
| Strategy / M&A | 3+ | 3 | 1 | 1 | 2 | 7 | ✓ |
| Digital transformation | 3+ | 1 | 0 | 3 | 2 | 6 | ✓ |
| Saudi market / Vision 2030 | 5+ | 3 | 3 | 2 | 3 | 11 | ✓ |
| HR / leadership development | 2+ | 1 | 2 | 1 | 1 | 5 | ✓ |
| Diversity (gender, age, nationality) | varied | F/45/SA | M/58/SA | M/61/EG | M/52/SA | | review |

## Member detail (basis for self-attestation)

### [Member 1 name]
- **Independent:** Yes
- **Term:** 1 of 3 (appointed [date])
- **Financial expert:** Yes — [credential: e.g. CPA, 20 years audit at Big 4]
- **Other roles:** [list any concurrent directorships]
- **Conflicts:** None declared / [list]

### [Member 2 name]
- [Repeat structure]

## Gap analysis
Based on the matrix:
- **Strong areas:** [list]
- **Weak areas:** [list] — addressed via training / external advisor / next nomination
- **Independence:** [N] of [M] members are independent ([%]) — meets / exceeds / falls short of CMA one-third minimum

## Recommendations to next nomination cycle
1. [Add a director with strong [skill area]]
2. [Renew term of [member] subject to independence reassessment]
3. [Decline renewal of [member] — gap addressed elsewhere]

---

**Reviewed by NomCom Chair:** ___   **Date:** [YYYY-MM-DD]
**Endorsed by Board:** ___   **Date:** [YYYY-MM-DD]
',

 N'# مصفوفة المهارات — [اسم اللجنة]

**الفترة:** [YYYY]   **آخر مراجعة:** [YYYY-MM-DD]   **بواسطة:** لجنة الترشيحات والمكافآت

## أبعاد المهارات

المستوى: **0** = لا يوجد، **1** = أساسي، **2** = متوسط، **3** = خبير.

| المهارة | الحد الأدنى المطلوب من الإجمالي | [العضو 1] | [العضو 2] | [العضو 3] | [العضو 4] | الإجمالي | فجوة |
|---|---|---|---|---|---|---|---|
| الخبرة المالية / المحاسبية (معيار "الخبير المالي" لهيئة السوق المالية) | 3+ | 3 | 2 | 1 | 0 | 6 | ✓ |
| الخبرة القطاعية / الصناعية (مصرفي، تأمين، رعاية صحية، إلخ) | 6+ | 3 | 3 | 2 | 1 | 9 | ✓ |
| إدارة المخاطر | 4+ | 1 | 3 | 2 | 1 | 7 | ✓ |
| الأمن السيبراني / حوكمة تقنية المعلومات | 3+ | 0 | 1 | 3 | 1 | 5 | ✓ |
| القانوني / التنظيمي | 3+ | 1 | 2 | 1 | 3 | 7 | ✓ |
| الاستدامة / ESG | 2+ | 0 | 1 | 2 | 1 | 4 | ✓ |
| الاستراتيجية / الاندماج والاستحواذ | 3+ | 3 | 1 | 1 | 2 | 7 | ✓ |
| التحول الرقمي | 3+ | 1 | 0 | 3 | 2 | 6 | ✓ |
| السوق السعودي / رؤية 2030 | 5+ | 3 | 3 | 2 | 3 | 11 | ✓ |
| الموارد البشرية / تطوير القيادات | 2+ | 1 | 2 | 1 | 1 | 5 | ✓ |
| التنوع (الجنس، العمر، الجنسية) | متنوع | أ/45/سعودية | ذ/58/سعودي | ذ/61/مصري | ذ/52/سعودي | | مراجعة |

## تفاصيل العضو (أساس الإقرار الذاتي)

### [اسم العضو 1]
- **مستقل:** نعم
- **المدة:** 1 من 3 (عُيّن في [التاريخ])
- **خبير مالي:** نعم — [الاعتماد: مثل CPA، 20 سنة مراجعة في Big 4]
- **أدوار أخرى:** [قائمة أي عضوية مجلس متزامنة]
- **التعارضات:** لا توجد / [قائمة]

### [اسم العضو 2]
- [كرر الهيكل]

## تحليل الفجوة
بناءً على المصفوفة:
- **المجالات القوية:** [قائمة]
- **المجالات الضعيفة:** [قائمة] — تعالج عبر التدريب / مستشار خارجي / الترشيح القادم
- **الاستقلالية:** [N] من [M] أعضاء مستقلون ([%]) — يستوفي / يفوق / يقصر عن الحد الأدنى لهيئة السوق المالية (الثلث)

## التوصيات لدورة الترشيح القادمة
1. [إضافة عضو ذي مهارات قوية في [مجال المهارة]]
2. [تجديد مدة [العضو] رهناً بإعادة تقييم الاستقلالية]
3. [عدم تجديد [العضو] — الفجوة معالجة في موقع آخر]

---

**روجعت من رئيس لجنة الترشيحات:** ___   **التاريخ:** [YYYY-MM-DD]
**اعتمدها المجلس:** ___   **التاريخ:** [YYYY-MM-DD]
',

 N'Members self-attest their levels with evidence (certifications, years of experience, named engagements). The Nomination Committee Chair validates. The "required minimum total" column is set by Board policy and varies by sector — banks typically need 4+ financial experts; non-financial firms 2-3.',
 N'يُقرّ الأعضاء ذاتياً بمستوياتهم مع الأدلة (الاعتمادات، سنوات الخبرة، التكليفات المسماة). يتحقق رئيس لجنة الترشيحات. عمود "الحد الأدنى المطلوب من الإجمالي" تحدده سياسة المجلس ويتفاوت حسب القطاع — تحتاج البنوك عادة 4+ خبراء ماليين؛ الشركات غير المالية 2-3.',
 N'1.0'),

-- ═════════════════════════════════════════════════════════════════════
-- 10. ANNUAL SELF-EVALUATION
-- ═════════════════════════════════════════════════════════════════════
(N'FORM-EVAL-001', N'ANNUAL_EVALUATION', N'BOARD,AUDIT_COMMITTEE,RISK_COMMITTEE,NOMINATION_REMUNERATION', N'CMA',
 N'Annual Committee Self-Evaluation', N'التقييم الذاتي السنوي للجنة',
 N'CMA Corporate Governance Article 26 requires the Board and its committees to conduct an annual evaluation of their effectiveness. This form is the standard self-evaluation questionnaire — completed individually, then aggregated and discussed in a closed session.',
 N'تتطلب المادة 26 من لائحة حوكمة الشركات الصادرة عن هيئة السوق المالية أن يجري المجلس ولجانه تقييماً سنوياً لفعاليتهم. هذا النموذج هو الاستبيان الموحّد للتقييم الذاتي — يُكمل فردياً، ثم يُجمَّع ويُناقش في جلسة مغلقة.',

 N'# Annual Self-Evaluation — [Committee Name]

**Period under evaluation:** [YYYY]   **Submitting member:** [Name]
**Submission date:** [YYYY-MM-DD]
**Anonymity:** [Anonymous to other members / Identified — Secretary aggregates]

For each statement, rate **1 = strongly disagree** to **5 = strongly agree**.

## A. Composition & independence
| # | Statement | Rating | Comment |
|---|---|---|---|
| A1 | The committee has the right mix of skills and experience to discharge its mandate. | _ | |
| A2 | The independence of independent members is genuine — they question management robustly. | _ | |
| A3 | The diversity (gender, nationality, age, professional background) of the committee is adequate. | _ | |
| A4 | The committee meets the regulator-mandated minimum independence threshold. | _ | |

## B. Mandate, agenda & meetings
| # | Statement | Rating | Comment |
|---|---|---|---|
| B1 | The committee charter clearly defines the mandate and authority. | _ | |
| B2 | The annual agenda covers all charter responsibilities (no gaps). | _ | |
| B3 | Pre-read papers are received on time, are of high quality, and are appropriately concise. | _ | |
| B4 | Meeting time is well allocated — important items get sufficient discussion. | _ | |
| B5 | Decisions are explicit, recorded clearly, with clear ownership for follow-up. | _ | |

## C. Information & decision-making
| # | Statement | Rating | Comment |
|---|---|---|---|
| C1 | The committee receives the information it needs from management in a timely way. | _ | |
| C2 | Risks are properly identified, debated, and acted upon. | _ | |
| C3 | Dissenting opinions are heard and recorded where members request it. | _ | |
| C4 | The committee challenges management constructively rather than rubber-stamping. | _ | |

## D. Effectiveness of chair & secretary
| # | Statement | Rating | Comment |
|---|---|---|---|
| D1 | The chair runs effective meetings — agenda is followed, all members contribute. | _ | |
| D2 | The chair ensures executive sessions are held without management when appropriate. | _ | |
| D3 | The secretary produces accurate minutes promptly; the action register is well-maintained. | _ | |

## E. Performance against responsibilities
For each major responsibility in the charter, rate effectiveness:

| # | Responsibility | Rating | Comment |
|---|---|---|---|
| E1 | [Mandate item 1] | _ | |
| E2 | [Mandate item 2] | _ | |
| E3 | [Mandate item 3] | _ | |

## F. Open questions
1. What is the **one** thing this committee does best?
2. What is the **one** thing it should change in the year ahead?
3. Are there any topics the committee should be discussing but is not?
4. Any other comments?

---

**Submitting member signature:** ___   **Date:** [YYYY-MM-DD]
**(For Secretary use)** Aggregation completed on: [Date]   By: [Name]
',

 N'# التقييم الذاتي السنوي — [اسم اللجنة]

**الفترة قيد التقييم:** [YYYY]   **العضو المقدّم:** [الاسم]
**تاريخ التقديم:** [YYYY-MM-DD]
**السرية:** [مجهول الهوية للأعضاء الآخرين / مُعرَّف — يجمّعه الأمين]

لكل عبارة، صنّف **1 = لا أوافق بشدة** إلى **5 = أوافق بشدة**.

## أ. التشكيل والاستقلالية
| # | العبارة | التصنيف | التعليق |
|---|---|---|---|
| أ1 | لدى اللجنة المزيج الصحيح من المهارات والخبرة لأداء ولايتها. | _ | |
| أ2 | استقلالية الأعضاء المستقلين حقيقية — يستجوبون الإدارة بقوة. | _ | |
| أ3 | تنوع اللجنة (الجنس، الجنسية، العمر، الخلفية المهنية) كافٍ. | _ | |
| أ4 | تستوفي اللجنة الحد الأدنى للاستقلالية المفروض من الجهات التنظيمية. | _ | |

## ب. الولاية وجدول الأعمال والاجتماعات
| # | العبارة | التصنيف | التعليق |
|---|---|---|---|
| ب1 | يحدد ميثاق اللجنة الولاية والصلاحيات بوضوح. | _ | |
| ب2 | يغطي جدول الأعمال السنوي جميع مسؤوليات الميثاق (لا توجد فجوات). | _ | |
| ب3 | تُستلم أوراق ما قبل القراءة في الوقت المحدد، وتكون عالية الجودة، وموجزة بشكل مناسب. | _ | |
| ب4 | يُخصص وقت الاجتماع بشكل جيد — تحظى البنود المهمة بنقاش كافٍ. | _ | |
| ب5 | القرارات صريحة، مسجلة بوضوح، مع ملكية واضحة للمتابعة. | _ | |

## ج. المعلومات واتخاذ القرار
| # | العبارة | التصنيف | التعليق |
|---|---|---|---|
| ج1 | تستلم اللجنة المعلومات التي تحتاجها من الإدارة في وقت مناسب. | _ | |
| ج2 | تُحدد المخاطر بشكل صحيح، وتُناقش، وتُتخذ إجراءات بشأنها. | _ | |
| ج3 | تُسمع الآراء المعترضة وتُسجل عندما يطلبها الأعضاء. | _ | |
| ج4 | تتحدى اللجنة الإدارة بشكل بناء بدلاً من الموافقة الشكلية. | _ | |

## د. فعالية الرئيس والأمين
| # | العبارة | التصنيف | التعليق |
|---|---|---|---|
| د1 | يدير الرئيس اجتماعات فعالة — يُتَّبع جدول الأعمال، يساهم جميع الأعضاء. | _ | |
| د2 | يضمن الرئيس عقد جلسات تنفيذية دون الإدارة عند الاقتضاء. | _ | |
| د3 | ينتج الأمين محاضر دقيقة على الفور؛ سجل الإجراءات مُحافظ عليه جيداً. | _ | |

## هـ. الأداء مقابل المسؤوليات
لكل مسؤولية رئيسية في الميثاق، صنّف الفعالية:

| # | المسؤولية | التصنيف | التعليق |
|---|---|---|---|
| هـ1 | [بند الولاية 1] | _ | |
| هـ2 | [بند الولاية 2] | _ | |
| هـ3 | [بند الولاية 3] | _ | |

## و. أسئلة مفتوحة
1. ما **الشيء الوحيد** الذي تتقنه هذه اللجنة؟
2. ما **الشيء الوحيد** الذي يجب أن تغيره في العام القادم؟
3. هل توجد موضوعات يجب أن تناقشها اللجنة لكنها لا تفعل؟
4. أي تعليقات أخرى؟

---

**توقيع العضو المقدّم:** ___   **التاريخ:** [YYYY-MM-DD]
**(لاستخدام الأمين)** اكتمل التجميع في: [التاريخ]   بواسطة: [الاسم]
',

 N'Each member completes anonymously; the Secretary aggregates and presents the heat-map at the next meeting in a closed session (no management present). Material findings feed into the next year''s committee plan + into the Nomination Committee''s skills-gap analysis. CMA examiners ask to see the evaluation outcome — not the individual responses, but the aggregated themes and the action plan that resulted.',
 N'يكمل كل عضو بشكل مجهول؛ يجمّع الأمين ويعرض الخريطة الحرارية في الاجتماع التالي في جلسة مغلقة (دون حضور الإدارة). تُغذي الملاحظات الجوهرية خطة اللجنة للعام التالي + تحليل فجوة المهارات للجنة الترشيحات. يطلب فاحصو هيئة السوق المالية رؤية نتائج التقييم — ليس الردود الفردية، بل الموضوعات المجمّعة وخطة العمل الناتجة.',
 N'1.0');

END
GO

PRINT N'V156 — admin.committee_form_templates seeded with 10 forms covering the full meeting lifecycle.';
GO
