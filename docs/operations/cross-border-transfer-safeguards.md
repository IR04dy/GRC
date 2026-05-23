# Cross-border data-transfer safeguards — template wording

Closes Open Issue **#012** (template legal language for `processing_activities.transfer_safeguards_en` and the matching `_ar` field).

Saudi PDPL **Article 29** requires documented safeguards for any transfer of personal data outside the Kingdom of Saudi Arabia. SDAIA issued the Cross-Border Data Transfer Regulations in 2023 with three permitted mechanisms — adequacy decisions, explicit data-subject consent (limited cases), and binding contractual safeguards. The most common in practice is the contractual route, using SDAIA's Standard Contractual Clauses (SCCs) plus a Transfer Impact Assessment (TIA).

The template wording below is what platform users should select / adapt when filling out the `transfer_safeguards_en` / `transfer_safeguards_ar` field on a Processing Activity (RoPA) or a Cross-Border Transfer record.

> **Disclaimer:** these are starter templates only. They are not legal advice. Each transfer must be reviewed by Legal / DPO before the record goes live.

---

## Template 1 — SDAIA Standard Contractual Clauses + TIA

### English

> Transfer is governed by the Standard Contractual Clauses (SCCs) issued by the Saudi Data and Artificial Intelligence Authority (SDAIA) under the Personal Data Protection Law (Royal Decree M/19, 1443H). The SCCs are executed between the data exporter (Trusted Security Solutions, KSA) and the data importer prior to any transfer. A Transfer Impact Assessment (TIA) covering the destination country's data-protection regime, government-access laws, onward-transfer risk, and technical-organisational measures has been completed and approved by the DPO. The TIA is retained as evidence in the platform under reference `{{tia_ref}}` and is reviewed annually or when a material change occurs. Encryption in transit (TLS 1.3) and at rest (AES-256) is enforced. The transfer is limited to the categories of personal data and processing purposes described in this Record of Processing Activity (RoPA).

### Arabic

> يتم نقل البيانات وفق الشروط التعاقدية النموذجية الصادرة عن الهيئة السعودية للبيانات والذكاء الاصطناعي (سدايا) بموجب نظام حماية البيانات الشخصية (المرسوم الملكي م/19، 1443هـ)، وتُبرَم هذه الشروط بين المصدِّر (شركة الحلول الأمنية الموثوقة، المملكة العربية السعودية) والمستورد قبل أي نقل. وقد تم إعداد تقييم أثر النقل (TIA) ويغطي منظومة حماية البيانات في الدولة المستقبِلة، وقوانين وصول الجهات الحكومية، ومخاطر إعادة النقل، والضوابط التقنية والتنظيمية، واعتمده مسؤول حماية البيانات. ويُحفظ التقييم في المنصة تحت المرجع `{{tia_ref}}` ويُراجع سنوياً أو عند طروء تغيير جوهري. ويُطبَّق التشفير أثناء النقل (TLS 1.3) وفي حالة السكون (AES-256). ويقتصر النقل على فئات البيانات الشخصية وأغراض المعالجة الموصوفة في سجل أنشطة المعالجة (RoPA).

---

## Template 2 — Adequacy decision (post-2025 list)

Use only when the destination country is on SDAIA's adequacy list (consult the latest published list before relying on this).

### English

> The destination country `{{destination_country}}` is recognised by SDAIA as providing an adequate level of protection for personal data under PDPL Article 29(1)(a), per SDAIA's adequacy decision dated `{{adequacy_date}}` (reference `{{adequacy_ref}}`). The platform retains a copy of the adequacy decision as evidence. No additional contractual safeguards are required for this transfer; however, encryption in transit (TLS 1.3) and at rest (AES-256) is enforced as a baseline technical measure. The data exporter monitors the adequacy decision for any revocation or restriction notice and will suspend the transfer within five business days if such notice is published.

### Arabic

> تُعدّ الدولة المستقبِلة `{{destination_country}}` من قِبَل سدايا دولة توفر مستوى ملائماً لحماية البيانات الشخصية وفق المادة 29(1)(أ) من نظام حماية البيانات الشخصية، بموجب قرار الملاءمة الصادر عن سدايا بتاريخ `{{adequacy_date}}` (المرجع `{{adequacy_ref}}`). وتحتفظ المنصة بنسخة من قرار الملاءمة كدليل. لا تُشترط ضمانات تعاقدية إضافية لهذا النقل، غير أنه يُطبَّق التشفير أثناء النقل (TLS 1.3) وفي حالة السكون (AES-256) كإجراء تقني أساسي. ويراقب المصدِّر قرار الملاءمة لرصد أي إلغاء أو تقييد، وسيُوقَف النقل خلال خمسة أيام عمل من تاريخ نشر مثل هذا الإشعار.

---

## Template 3 — Explicit data-subject consent (narrow use)

Use only where (a) the data subject has been informed of the absence of safeguards, (b) consent is genuinely free, specific, informed, and (c) the transfer is occasional, not bulk / systematic. Not appropriate for ongoing operational data flows.

### English

> Transfer relies on the explicit, informed consent of each affected data subject under PDPL Article 29(2). Before transfer, the data subject is presented with: (i) the destination country and recipient, (ii) the categories of personal data involved, (iii) the purpose of the transfer, (iv) a clear statement that the destination country may not provide a level of protection equivalent to PDPL, and (v) the right to withdraw consent at any time. Consent records are stored in `privacy.consents` with the relevant purpose and timestamp. The transfer is limited to the consenting subjects and stops immediately on withdrawal. This mechanism is used only for occasional transfers and is not the lawful basis for ongoing bulk transfers.

### Arabic

> يستند النقل إلى الموافقة الصريحة والمستنيرة لكل صاحب بيانات متأثر بموجب المادة 29(2) من نظام حماية البيانات الشخصية. ويُعرَض على صاحب البيانات قبل النقل: (1) الدولة المستقبِلة والمستلم، (2) فئات البيانات الشخصية، (3) غرض النقل، (4) بيان واضح بأن الدولة المستقبِلة قد لا توفر مستوى حماية مكافئاً لنظام حماية البيانات، (5) حق سحب الموافقة في أي وقت. وتُحفَظ سجلات الموافقة في جدول `privacy.consents` مع بيان الغرض والوقت. ويقتصر النقل على من وافقوا، ويتوقف فوراً عند سحب الموافقة. تُستخدم هذه الآلية للنقل العَرَضي فقط، ولا تُعدّ أساساً مشروعاً لعمليات النقل الجماعي المستمرة.

---

## How the platform records the choice

The `processing_activities` table has two text columns: `transfer_safeguards_en` and `transfer_safeguards_ar`. The Privacy module RoPA wizard offers a dropdown:

1. **SDAIA SCCs + TIA** — pastes Template 1 into both columns, prompts for `{{tia_ref}}`.
2. **Adequacy decision** — pastes Template 2, prompts for destination country + adequacy decision reference.
3. **Explicit consent** — pastes Template 3.
4. **Custom** — free-text (DPO review required).

Choice + completed text is captured in the audit log against the RoPA record (existing `platform.audit_log` table). The DPO must approve the RoPA before it can be marked ACTIVE — the standard CFO-style sign-off workflow.

## References

- Saudi PDPL — Royal Decree M/19, 1443H, Article 29 (cross-border transfers)
- SDAIA Cross-Border Data Transfer Regulations (2023)
- SDAIA Standard Contractual Clauses (SCCs) — published April 2024
- SDAIA published adequacy list — review monthly for updates
