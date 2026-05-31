import sys, pyodbc
sys.stdout.reconfigure(encoding='utf-8')
CONN = "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;"
conn = pyodbc.connect(CONN); conn.autocommit = False
cur = conn.cursor()

def framework(code, name_en, name_ar, version, issuer, desc_en):
    cur.execute("SELECT id FROM evidence.frameworks WHERE code=?", code)
    r = cur.fetchone()
    if r:
        fid = int(r[0])
        cur.execute("UPDATE evidence.frameworks SET name_en=?, name_ar=?, version=?, issuer=?, description_en=?, is_active=1 WHERE id=?",
                    name_en, name_ar, version, issuer, desc_en, fid)
    else:
        cur.execute("INSERT INTO evidence.frameworks (code,name_en,name_ar,version,issuer,description_en,is_active) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,1)",
                    code, name_en, name_ar, version, issuer, desc_en)
        fid = int(cur.fetchone()[0])
    cur.execute("UPDATE evidence.sub_controls SET is_active=0 WHERE control_id IN (SELECT id FROM evidence.controls WHERE framework_id=?)", fid)
    cur.execute("UPDATE evidence.controls SET is_active=0 WHERE framework_id=?", fid)
    return fid

def control(fid, code, name_en, name_ar, desc_en=None):
    cur.execute("SELECT id FROM evidence.controls WHERE framework_id=? AND control_code=?", fid, code)
    r = cur.fetchone()
    if r:
        cid = int(r[0]); cur.execute("UPDATE evidence.controls SET name_en=?, name_ar=?, description_en=?, is_active=1 WHERE id=?", name_en, name_ar, desc_en, cid)
    else:
        cur.execute("INSERT INTO evidence.controls (framework_id,control_code,name_en,name_ar,description_en,is_active) OUTPUT INSERTED.id VALUES (?,?,?,?,?,1)", fid, code, name_en, name_ar, desc_en)
        cid = int(cur.fetchone()[0])
    return cid

def sub(cid, code, name_en, name_ar, guidance, desc_en=None):
    cur.execute("SELECT id FROM evidence.sub_controls WHERE control_id=? AND sub_control_code=?", cid, code)
    r = cur.fetchone()
    if r:
        sid = int(r[0]); cur.execute("UPDATE evidence.sub_controls SET name_en=?, name_ar=?, description_en=?, evidence_guidance=?, is_active=1 WHERE id=?", name_en, name_ar, desc_en, guidance, sid)
    else:
        cur.execute("INSERT INTO evidence.sub_controls (control_id,sub_control_code,name_en,name_ar,description_en,evidence_guidance,is_active) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,1)", cid, code, name_en, name_ar, desc_en, guidance)
        sid = int(cur.fetchone()[0])
    return sid

# ---------------------------------------------------------------------------
# FRAMEWORK
# ---------------------------------------------------------------------------
fid = framework(
    "PDPL-1:2023",
    "Personal Data Protection Law (PDPL) & Implementing Regulations",
    "نظام حماية البيانات الشخصية ولوائحه التنفيذية",
    "2023",
    "SDAIA",
    "Saudi Arabia's Personal Data Protection Law (Royal Decree M/19, 1443H) together with "
    "the Implementing Regulations issued by SDAIA and the Data Transfer Regulation, governing "
    "collection, processing, disclosure, retention, transfer, and security of personal data by "
    "controllers and processors operating in the Kingdom. Covers lawful-basis requirements, data "
    "subject rights, cross-border transfer controls, breach notification timelines, special-category "
    "data, DPO obligations, and accountability mechanisms applicable to private-sector entities. "
    "— practitioner SME mapping; structure aligned to the published law/regulation, "
    "provisions paraphrased (not verbatim legal text)."
)

control_count = 0
sub_count = 0

# ---------------------------------------------------------------------------
# CONTROL 1 — Lawful Basis & Consent
# ---------------------------------------------------------------------------
c1 = control(fid, "1", "Lawful Basis & Consent",
             "الأساس القانوني وموافقة صاحب البيانات",
             "Controllers must establish and document a valid lawful basis for every processing activity. "
             "Consent, where relied upon, must be freely given, specific, informed, unambiguous, and "
             "revocable. Processing without a recognised basis is prohibited.")
control_count += 1

sub(c1, "1-1",
    "Identify and document a lawful basis for each processing purpose",
    "تحديد الأساس القانوني لكل غرض من أغراض المعالجة وتوثيقه",
    "ROPA register showing lawful-basis column per processing activity; legal-basis assessment memo signed by DPO or legal counsel; "
    "board/management approval record where consent is not the chosen basis",
    "For every distinct processing purpose the controller must identify one of the recognised lawful bases "
    "(consent, contractual necessity, legal obligation, vital interests, public task, or legitimate interest) "
    "and record it before processing commences.")
sub_count += 1

sub(c1, "1-2",
    "Obtain and record valid, granular consent",
    "الحصول على الموافقة الصريحة والمحددة وتسجيلها",
    "Time-stamped consent capture logs (digital form, click-to-accept records, or signed paper forms); "
    "consent version linked to privacy notice version; separate consent tick-boxes per processing purpose; "
    "evidence that consent was not bundled with terms of service",
    "Where consent is the chosen lawful basis, it must be obtained before processing, be purpose-specific, "
    "freely given, and documented so that the controller can demonstrate its existence and scope at any time.")
sub_count += 1

sub(c1, "1-3",
    "Enable withdrawal of consent and cease processing upon request",
    "إتاحة سحب الموافقة ووقف المعالجة فور الطلب",
    "Withdrawal-of-consent records with timestamps; self-service opt-out mechanism screenshot or audit trail; "
    "evidence of processing cessation within the prescribed period; updated consent log reflecting withdrawal",
    "Data subjects must be able to withdraw consent at any time through a mechanism as easy as giving it, "
    "and processing must stop promptly after withdrawal unless another lawful basis applies.")
sub_count += 1

sub(c1, "1-4",
    "Legitimate-interest assessment (LIA) where that basis is relied upon",
    "تقييم المصلحة المشروعة عند الاستناد إليها أساساً للمعالجة",
    "Completed LIA template covering purpose, necessity, and balancing test; sign-off record by DPO; "
    "documentation confirming data subject interests were weighed and not overridden",
    "Where legitimate interest is the lawful basis, the controller must conduct and document a three-part "
    "test (purpose, necessity, balancing) before commencing processing.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 2 — Privacy Notice & Transparency
# ---------------------------------------------------------------------------
c2 = control(fid, "2", "Privacy Notice & Transparency",
             "إشعار الخصوصية والشفافية",
             "Controllers must provide clear, accessible, bilingual privacy notices at or before the point "
             "of collection, disclosing controller identity, purposes, legal bases, retention periods, "
             "data subject rights, and cross-border transfer information.")
control_count += 1

sub(c2, "2-1",
    "Publish a comprehensive privacy notice covering all PDPL-required elements",
    "نشر إشعار خصوصية شامل يغطي جميع العناصر المطلوبة نظاماً",
    "Current published privacy notice (Arabic primary + English); version history and effective dates; "
    "checklist confirming coverage of: controller identity, DPO contact, purposes, legal bases, "
    "retention schedule, data subject rights, cross-border transfer details, complaint mechanism",
    "The privacy notice must be written in plain language, be available in Arabic (primary), cover every "
    "required disclosure element, and be kept current whenever processing activities materially change.")
sub_count += 1

sub(c2, "2-2",
    "Deliver layered, just-in-time privacy information at point of collection",
    "تقديم معلومات الخصوصية الطبقية في لحظة جمع البيانات",
    "Screenshots or design specifications showing layered notice implementation (short notice + link to full notice); "
    "user-journey documentation confirming notice is presented before or at point of data collection; "
    "A/B or UX test records if available",
    "Where the full notice would be impractical to display in-context, a concise just-in-time notice "
    "directing subjects to the full notice satisfies the transparency obligation.")
sub_count += 1

sub(c2, "2-3",
    "Maintain a notice version log and notify subjects of material changes",
    "الاحتفاظ بسجل إصدارات الإشعار وإبلاغ أصحاب البيانات بالتغييرات الجوهرية",
    "Privacy-notice version control log with change descriptions and publication dates; "
    "evidence of subject notification (email, in-app message, re-consent prompt) for material changes; "
    "sign-off record confirming legal/DPO review before each new version",
    "Material changes to processing purposes or data types require proactive notification to affected "
    "data subjects before the changes take effect, and re-consent where consent was the original basis.")
sub_count += 1

sub(c2, "2-4",
    "Provide privacy information in Arabic as the primary language",
    "توفير معلومات الخصوصية باللغة العربية بوصفها اللغة الأساسية",
    "Arabic-language version of all public-facing privacy notices and consent forms; "
    "linguistic review certification by a qualified Arabic legal-language reviewer; "
    "evidence that the Arabic version is at least as prominent as any English version",
    "All privacy disclosures directed at residents of the Kingdom must be available in Arabic, with "
    "Arabic being the controlling language for legal purposes under Saudi law.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 3 — Data Subject Rights
# ---------------------------------------------------------------------------
c3 = control(fid, "3", "Data Subject Rights",
             "حقوق أصحاب البيانات الشخصية",
             "Controllers must maintain effective, auditable mechanisms enabling data subjects to exercise "
             "their rights: access, rectification, erasure, portability, objection, and consent withdrawal, "
             "within statutory response periods.")
control_count += 1

sub(c3, "3-1",
    "Right of access — respond to subject access requests (SARs) within statutory period",
    "حق الاطلاع — الاستجابة لطلبات الوصول خلال المدة النظامية",
    "DSR intake register with date-received and date-closed columns; SLA compliance report showing response "
    "within 30 days (extendable to 60 days with notification); sample redacted SAR responses; identity-verification "
    "procedure documentation; log of requests refused and stated reasons",
    "Data subjects have the right to obtain confirmation of whether their personal data is being processed "
    "and to receive a copy, together with prescribed supplementary information, within 30 days of request.")
sub_count += 1

sub(c3, "3-2",
    "Right of rectification — correct inaccurate or incomplete personal data",
    "حق التصحيح — تصحيح البيانات غير الدقيقة أو الناقصة",
    "DSR register entries marked as rectification requests; evidence of data correction in source systems "
    "with before/after comparison (where permissible to share); notification records sent to third parties "
    "where data was disclosed prior to correction; response-time SLA report",
    "Controllers must correct or complete inaccurate or incomplete personal data upon verified request "
    "from the data subject and, where applicable, notify downstream recipients.")
sub_count += 1

sub(c3, "3-3",
    "Right of erasure — delete personal data upon request where no retention obligation applies",
    "حق الحذف — مسح البيانات الشخصية عند انتفاء المسوّغ القانوني للاحتفاظ بها",
    "DSR register entries marked as erasure requests; evidence of data deletion or anonymisation in source systems; "
    "retention-obligation assessment memo where erasure is refused; notification records to processors instructed "
    "to delete; erasure certificates where available",
    "Data subjects may request erasure when the original purpose no longer applies, consent is withdrawn, "
    "or processing is unlawful. Erasure may be declined where a statutory retention obligation overrides.")
sub_count += 1

sub(c3, "3-4",
    "Right of portability — provide personal data in machine-readable format",
    "حق نقل البيانات — تسليم البيانات الشخصية بصيغة قابلة للقراءة الآلية",
    "DSR register entries marked as portability requests; exported data package samples (JSON or CSV); "
    "evidence of response within 30 days; procedure documentation for portability workflow including "
    "identity verification and format options offered",
    "Where processing is based on consent or contract and carried out by automated means, data subjects "
    "may request their data in a structured, commonly used, machine-readable format.")
sub_count += 1

sub(c3, "3-5",
    "Right to object to processing and to opt out of direct marketing",
    "حق الاعتراض على المعالجة والرفض للتسويق المباشر",
    "Objection-request log with grounds stated and outcomes recorded; marketing opt-out mechanism screenshots "
    "and confirmation emails; evidence that processing ceased following upheld objection; "
    "procedure for evaluating compelling legitimate-interest grounds where raised",
    "Data subjects may object to processing at any time, in particular where processing is based on "
    "legitimate interest or is for direct marketing purposes. Objections to marketing must always be honoured.")
sub_count += 1

sub(c3, "3-6",
    "DSR process governance — intake, verification, tracking, and escalation",
    "حوكمة معالجة طلبات أصحاب البيانات — الاستقبال والتحقق والتتبع والتصعيد",
    "Published DSR submission channel (web form, email, or portal) with Arabic-language option; "
    "identity-verification procedure documentation; DSR tracker showing open, in-progress, and closed "
    "status with responsible owner; escalation matrix for complex or disputed requests; "
    "annual DSR volume and SLA-compliance management report",
    "Controllers must implement a documented, accessible process for receiving, verifying, tracking, "
    "and responding to all data subject rights requests within statutory timeframes.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 4 — Records of Processing Activities (ROPA)
# ---------------------------------------------------------------------------
c4 = control(fid, "4", "Records of Processing Activities (ROPA)",
             "سجل أنشطة المعالجة",
             "Controllers and processors must maintain up-to-date records of all processing activities "
             "under their responsibility, covering mandatory PDPL fields and accessible to SDAIA on request.")
control_count += 1

sub(c4, "4-1",
    "Maintain a comprehensive ROPA covering all mandatory fields",
    "الاحتفاظ بسجل شامل لأنشطة المعالجة يغطي جميع الحقول الإلزامية",
    "ROPA register export (spreadsheet or GRC system report) including: processing purpose, lawful basis, "
    "data categories, data subject categories, recipients, cross-border transfers, retention period, "
    "security measures reference, processor details; dated and version-controlled",
    "The ROPA must cover every processing activity, be kept in writing (including electronic form), "
    "and include all elements prescribed by the PDPL Implementing Regulations.")
sub_count += 1

sub(c4, "4-2",
    "Keep the ROPA current — review upon new processing activity or material change",
    "تحديث سجل أنشطة المعالجة باستمرار عند بدء نشاط جديد أو إجراء تغيير جوهري",
    "ROPA change-management log showing date of update, nature of change, and approver; "
    "new-system intake checklist requiring ROPA update before go-live; periodic ROPA completeness review "
    "record signed by DPO or privacy lead; version history showing at least annual formal review",
    "The ROPA must be updated whenever a new processing activity is initiated or an existing activity "
    "is materially changed. A formal review should occur at minimum annually.")
sub_count += 1

sub(c4, "4-3",
    "Make the ROPA available to SDAIA upon request",
    "إتاحة سجل أنشطة المعالجة لهيئة البيانات والذكاء الاصطناعي عند الطلب",
    "Designated ROPA custodian and access procedure documentation; evidence that the ROPA is held in "
    "a format exportable within a reasonable timeframe; prior SDAIA inspection response record if applicable; "
    "access-control log confirming DPO has read/export rights to the ROPA",
    "Controllers must be able to produce the ROPA for regulatory inspection without undue delay. "
    "The ROPA is a key accountability document and its unavailability constitutes a compliance failure.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 5 — Data Protection Impact Assessment (DPIA)
# ---------------------------------------------------------------------------
c5 = control(fid, "5", "Data Protection Impact Assessment (DPIA)",
             "تقييم أثر حماية البيانات الشخصية",
             "A DPIA must be conducted before commencing any processing likely to result in high risk to "
             "data subjects' rights, including large-scale sensitive data processing, systematic monitoring, "
             "and automated decision-making with significant effects.")
control_count += 1

sub(c5, "5-1",
    "Conduct a DPIA for all high-risk processing activities before commencement",
    "إجراء تقييم أثر حماية البيانات قبل البدء في أي نشاط معالجة عالي الخطورة",
    "DPIA screening register identifying all processing activities assessed for high-risk triggers; "
    "completed DPIA reports for triggered activities covering: systematic description, necessity/proportionality "
    "assessment, risk identification, mitigating measures; DPO sign-off records; "
    "board or senior management approval where residual risk is high",
    "High-risk triggers include: profiling with significant effects, large-scale sensitive data, "
    "systematic CCTV/employee monitoring, new technologies with significant rights impact, and "
    "cross-border transfers to jurisdictions lacking adequate protection.")
sub_count += 1

sub(c5, "5-2",
    "Document DPIA outcomes, residual risks, and agreed mitigating measures",
    "توثيق نتائج تقييم الأثر والمخاطر المتبقية وتدابير التخفيف المعتمدة",
    "DPIA final report with residual-risk rating and treatment plan; risk register entries cross-referenced "
    "to DPIA findings; evidence of implemented controls (e.g., pseudonymisation, access restriction); "
    "post-implementation review record confirming control effectiveness",
    "The DPIA report must clearly state residual risks and the measures committed to address them, "
    "with implementation tracked and verified before or shortly after processing commences.")
sub_count += 1

sub(c5, "5-3",
    "Consult SDAIA prior to processing where high residual risk cannot be mitigated",
    "التشاور مع الهيئة قبل المعالجة حين لا يمكن تخفيف المخاطر العالية المتبقية",
    "Prior-consultation request to SDAIA with DPIA report attached; SDAIA response or acknowledgement record; "
    "evidence that processing was delayed pending regulatory consultation outcome; "
    "board decision record on whether to proceed following SDAIA guidance",
    "Where a DPIA concludes that high residual risk cannot be adequately mitigated, the controller "
    "must consult SDAIA before commencing the processing activity.")
sub_count += 1

sub(c5, "5-4",
    "Maintain a DPIA register and schedule periodic reviews for ongoing high-risk processing",
    "الاحتفاظ بسجل تقييمات الأثر وجدولة مراجعات دورية للأنشطة العالية الخطورة المستمرة",
    "DPIA register index with processing activity, DPIA date, next-review date, and status; "
    "review records for DPIAs older than two years or where processing has materially changed; "
    "DPO attestation that the DPIA register is complete and current",
    "DPIAs are not one-time exercises; they must be reviewed when the processing changes materially "
    "or periodically (recommended every two years) for ongoing high-risk activities.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 6 — Personal Data Breach Notification
# ---------------------------------------------------------------------------
c6 = control(fid, "6", "Personal Data Breach Notification",
             "الإخطار بخرق البيانات الشخصية",
             "Controllers must notify SDAIA of personal data breaches without undue delay and no later than "
             "72 hours of becoming aware where the breach is likely to result in risk to data subjects. "
             "High-risk breaches require notification to affected data subjects.")
control_count += 1

sub(c6, "6-1",
    "Detect, log, and assess all actual and suspected personal data breaches",
    "اكتشاف جميع خروقات البيانات الشخصية الفعلية والمشتبه بها وتسجيلها وتقييمها",
    "Breach register with: incident date, discovery date, data types affected, number of subjects, "
    "breach category, and initial risk assessment; detection-to-awareness timeline evidence; "
    "internal escalation records to DPO and legal counsel within 24 hours of discovery; "
    "investigation report documenting cause and scope",
    "A breach occurs whenever personal data is accidentally or unlawfully destroyed, lost, altered, "
    "or disclosed to unauthorised parties. Every potential breach must be logged and assessed "
    "even if ultimately classified as low-risk.")
sub_count += 1

sub(c6, "6-2",
    "Notify SDAIA within 72 hours for breaches posing risk to data subjects",
    "إخطار الهيئة خلال 72 ساعة للخروقات التي تشكّل خطراً على أصحاب البيانات",
    "SDAIA breach notification submission record (portal reference number or email acknowledgement); "
    "notification timestamp proving 72-hour compliance; notification content covering: nature of breach, "
    "categories and approximate number of affected records and subjects, DPO contact, likely consequences, "
    "measures taken or proposed; where notification was delayed beyond 72 hours, documented reasons",
    "Breach notification to SDAIA is mandatory within 72 hours of the controller becoming aware, "
    "unless the breach is unlikely to result in risk. Late notification requires a documented justification.")
sub_count += 1

sub(c6, "6-3",
    "Notify affected data subjects without undue delay for high-risk breaches",
    "إخطار أصحاب البيانات المتضررين دون تأخير غير مبرر للخروقات عالية الخطورة",
    "Data subject notification records (email send logs, SMS delivery receipts, or posted-letter proof); "
    "notification content evidence covering: plain-language description, DPO contact, likely consequences, "
    "protective steps subjects should take; SDAIA consultation record where notification was waived; "
    "subject-notification timeline evidence",
    "Where a breach is likely to result in high risk to data subjects, those individuals must be "
    "notified directly and promptly in plain, accessible language describing what happened and "
    "what steps they should take to protect themselves.")
sub_count += 1

sub(c6, "6-4",
    "Maintain a breach register and conduct post-incident review",
    "الاحتفاظ بسجل الخروقات وإجراء مراجعة ما بعد الحادثة",
    "Cumulative breach register accessible to DPO and senior management; post-incident review report "
    "for each significant breach covering root cause, lessons learned, and preventive actions; "
    "evidence of preventive actions implemented; annual breach trend analysis report",
    "All breaches, including near-misses and low-risk events, must be logged in a central breach register. "
    "Significant breaches require formal post-incident review to prevent recurrence.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 7 — Cross-Border Transfer & Adequacy
# ---------------------------------------------------------------------------
c7 = control(fid, "7", "Cross-Border Transfer & Adequacy",
             "نقل البيانات الشخصية عبر الحدود وآليات الكفاءة",
             "Personal data may be transferred outside Saudi Arabia only to jurisdictions with adequate "
             "protection, or under approved safeguards, or within recognised exceptions. The Data Transfer "
             "Regulation sets out the applicable mechanisms and risk-assessment requirements.")
control_count += 1

sub(c7, "7-1",
    "Identify and document all cross-border personal data transfers",
    "تحديد جميع عمليات نقل البيانات الشخصية عبر الحدود وتوثيقها",
    "Cross-border transfer inventory mapped from ROPA; destination country list with adequacy status "
    "classification; processor/sub-processor country-of-operation records; data-flow diagram showing "
    "international flows; annual transfer inventory review record",
    "Before any transfer mechanism can be chosen, the controller must know precisely where personal data "
    "is going, what categories are involved, and for what purpose.")
sub_count += 1

sub(c7, "7-2",
    "Conduct a transfer risk assessment (TRA) for each destination jurisdiction",
    "إجراء تقييم مخاطر النقل لكل ولاية قضائية مقصودة",
    "Completed TRA per destination country or processor covering: local legal framework adequacy, "
    "government-access risk, technical safeguards in place, and overall risk rating; "
    "SDAIA adequacy determination check record (published list); DPO approval memo for each TRA; "
    "date of last review and trigger for next review",
    "The Data Transfer Regulation requires a documented risk assessment for every transfer destination, "
    "evaluating the legal environment's adequacy for protecting personal data against the PDPL standard.")
sub_count += 1

sub(c7, "7-3",
    "Implement and document an approved transfer mechanism for non-adequate destinations",
    "تطبيق آلية نقل معتمدة وتوثيقها للوجهات غير الكافية",
    "Signed data transfer agreements (standard contractual clauses approved by SDAIA, or binding corporate rules); "
    "SDAIA approval or registration records where required; evidence that contractual clauses are "
    "reflected in operational practice; supplementary technical measures documentation (encryption, pseudonymisation)",
    "Where the destination country does not appear on the SDAIA adequacy list, an approved safeguard "
    "must be in place before any transfer occurs. Transfers without a mechanism constitute a PDPL violation.")
sub_count += 1

sub(c7, "7-4",
    "Apply and document recognised transfer exceptions where relied upon",
    "تطبيق استثناءات النقل المعترف بها وتوثيقها عند الاستناد إليها",
    "Exception-reliance register documenting which exception applies (explicit consent, contractual necessity, "
    "vital interests, public interest), the factual basis, and frequency of use; "
    "legal opinion or DPO assessment confirming exception conditions are met; evidence that exceptions "
    "are not used systematically as a substitute for a proper transfer mechanism",
    "Exceptions (e.g., explicit consent, necessary for contract with the data subject, vital interests, "
    "or one-off public interest) may be relied upon only where they genuinely apply and are not used "
    "as a routine workaround for the adequacy/safeguards requirement.")
sub_count += 1

sub(c7, "7-5",
    "Monitor changes to SDAIA adequacy determinations and update transfer arrangements accordingly",
    "متابعة تحديثات قرارات الكفاءة الصادرة عن الهيئة وتحديث ترتيبات النقل وفقاً لذلك",
    "Documented process for monitoring SDAIA adequacy-list updates; change log showing transfer-mechanism "
    "updates triggered by adequacy decisions; evidence of processor notification where transfer arrangements "
    "are affected; annual transfer compliance review signed by DPO",
    "Adequacy determinations can be withdrawn or modified. Controllers must monitor SDAIA publications "
    "and promptly update their transfer arrangements whenever a relevant change occurs.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 8 — Data Minimisation, Accuracy, Retention & Disposal
# ---------------------------------------------------------------------------
c8 = control(fid, "8", "Data Minimisation, Accuracy, Retention & Disposal",
             "الحد من البيانات ودقتها والاحتفاظ بها والتخلص منها",
             "Controllers must collect only personal data that is adequate, relevant, and limited to what "
             "is necessary. Data must be kept accurate, up to date, and deleted or anonymised once the "
             "retention period expires unless a statutory obligation requires longer retention.")
control_count += 1

sub(c8, "8-1",
    "Apply data minimisation — collect only data necessary for the specified purpose",
    "تطبيق مبدأ تقليل البيانات — جمع ما هو ضروري فحسب للغرض المحدد",
    "Data-collection field inventory mapped to processing purpose with necessity justification per field; "
    "privacy-by-design review record for new systems confirming minimisation applied; "
    "periodic data-catalogue audit showing redundant or excess fields identified and removed",
    "Each data field collected must be demonstrably necessary for the stated processing purpose. "
    "Fields that are 'nice to have' without a clear purpose link must not be collected.")
sub_count += 1

sub(c8, "8-2",
    "Maintain data accuracy and provide correction mechanisms",
    "الحفاظ على دقة البيانات وتوفير آليات التصحيح",
    "Data-quality policy and periodic data-quality audit reports; evidence of proactive accuracy checks "
    "(e.g., annual profile update prompts sent to customers); "
    "DSR rectification-request log demonstrating operational correction process; "
    "data-quality KPI dashboard extract",
    "Controllers must take reasonable steps to ensure personal data is accurate and kept up to date, "
    "and must have mechanisms for data subjects and internal teams to flag and correct inaccuracies.")
sub_count += 1

sub(c8, "8-3",
    "Maintain and implement a formal retention schedule for all personal data categories",
    "وضع جدول احتفاظ رسمي بجميع فئات البيانات الشخصية وتطبيقه",
    "Approved retention schedule document covering all personal data categories with retention periods, "
    "legal/regulatory basis for each period, and deletion trigger; "
    "evidence of annual retention schedule review and DPO/legal sign-off; "
    "record of retention schedule being communicated to data custodians",
    "Retention periods must be determined per data category, based on purpose fulfilment and statutory "
    "obligations, and documented in a formal schedule. Indefinite retention is not permissible.")
sub_count += 1

sub(c8, "8-4",
    "Execute secure, verifiable disposal of personal data at end of retention period",
    "تنفيذ إتلاف آمن وقابل للتحقق للبيانات الشخصية عند انتهاء فترة الاحتفاظ",
    "Automated or scheduled data-deletion job run logs; secure-disposal certificates from data custodians "
    "and processors; evidence of physical media destruction (certificates from vendor); "
    "annual disposal compliance report cross-referencing retention schedule; "
    "anonymisation audit trail where deletion is replaced by anonymisation",
    "Data that has reached its retention end-date must be securely destroyed (for physical media) "
    "or irrecoverably deleted or anonymised (for digital data). The process must be auditable.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 9 — Sensitive / Special-Category Data
# ---------------------------------------------------------------------------
c9 = control(fid, "9", "Sensitive / Special-Category Personal Data",
             "البيانات الشخصية الحساسة",
             "The PDPL designates certain categories of personal data as sensitive, requiring stricter "
             "processing conditions, enhanced security measures, and in most cases explicit consent "
             "or a specific statutory permission.")
control_count += 1

sub(c9, "9-1",
    "Identify and inventory all sensitive personal data categories processed",
    "تحديد جميع فئات البيانات الشخصية الحساسة المعالَجة وحصرها",
    "Sensitive-data inventory derived from ROPA; classification matrix mapping data types to PDPL "
    "sensitive categories (health, genetic, biometric, financial, criminal, religious/political); "
    "data-classification labelling evidence in systems handling sensitive data; "
    "DPO attestation of inventory completeness",
    "PDPL-designated sensitive categories include health/genetic/biometric data, racial/ethnic origin, "
    "religious or political opinions, criminal records, financial data beyond general economic status, "
    "and data revealing private personal matters.")
sub_count += 1

sub(c9, "9-2",
    "Establish a specific lawful basis or explicit consent for each sensitive data processing activity",
    "تحديد أساس قانوني خاص أو موافقة صريحة لكل نشاط معالجة للبيانات الحساسة",
    "Sensitive-data processing register showing lawful basis or explicit-consent record per activity; "
    "explicit-consent capture logs distinct from general processing consent; "
    "legal opinion or SDAIA guidance relied upon for statutory-basis processing; "
    "DPO sign-off on all sensitive-data processing justifications",
    "Processing of sensitive data is prohibited unless explicit consent has been obtained or a "
    "specific statutory basis applies (e.g., vital interests, legal proceedings, public health). "
    "Implicit or bundled consent is insufficient.")
sub_count += 1

sub(c9, "9-3",
    "Apply enhanced security and access controls to sensitive personal data",
    "تطبيق ضمانات أمنية مشددة وضوابط وصول على البيانات الحساسة",
    "Access-control matrix showing sensitive data accessible only to role-justified users; "
    "encryption-at-rest and in-transit evidence for sensitive data stores; "
    "segregation evidence (sensitive data in separate schemas or encrypted fields); "
    "access log review records; penetration test scope confirmation that sensitive data stores are covered",
    "Sensitive data must be subject to access controls more restrictive than standard personal data, "
    "encryption both at rest and in transit, and periodic access-rights reviews.")
sub_count += 1

sub(c9, "9-4",
    "Conduct a DPIA before commencing large-scale or novel sensitive data processing",
    "إجراء تقييم أثر حماية البيانات قبل البدء في معالجة واسعة النطاق أو جديدة للبيانات الحساسة",
    "DPIA screening record confirming sensitive-data trigger was assessed; completed DPIA report "
    "for large-scale or novel sensitive processing; evidence of mitigating measures implemented; "
    "DPO and senior management approval records",
    "Large-scale processing of sensitive data is a high-risk trigger under the PDPL, mandating a DPIA "
    "before the activity commences regardless of other risk factors.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 10 — Children's, Health, Credit & Genetic Data
# ---------------------------------------------------------------------------
c10 = control(fid, "10", "Children's, Health, Credit & Genetic Data",
              "بيانات القاصرين والبيانات الصحية والائتمانية والجينية",
              "Specific sub-categories of sensitive data attract additional obligations: children's data "
              "requires verified parental consent; health, credit, and genetic data may only be processed "
              "for specified purposes with heightened safeguards.")
control_count += 1

sub(c10, "10-1",
    "Obtain verified parental or guardian consent before processing children's personal data",
    "الحصول على موافقة وليّ الأمر الموثقة قبل معالجة بيانات القاصرين",
    "Age-verification mechanism documentation and audit trail; parental/guardian consent capture logs "
    "with identity-verification step evidence; under-18 user flag in data systems; "
    "procedure for handling data collected before age verification where applicable; "
    "child-specific privacy notice in age-appropriate language",
    "Processing of a minor's personal data requires consent from a parent or legal guardian. "
    "The controller must implement age-gating or age-verification measures proportionate to the risk.")
sub_count += 1

sub(c10, "10-2",
    "Restrict health data processing to authorised medical or insurance purposes with strict access controls",
    "تقييد معالجة البيانات الصحية على الأغراض الطبية أو التأمينية المعتمدة مع ضوابط وصول صارمة",
    "Health data processing purpose register with statutory or explicit-consent basis per entry; "
    "role-based access control evidence limiting health data to clinical/insurance roles; "
    "audit logs for health data access and export; data-sharing agreement with any healthcare provider "
    "or insurer receiving health data; professional confidentiality policy",
    "Health data may only be processed for healthcare delivery, medical research, insurance underwriting, "
    "or similar purposes permitted by the PDPL. Processing for commercial profiling without a health nexus "
    "is not permitted.")
sub_count += 1

sub(c10, "10-3",
    "Process credit and financial data only under regulated-purpose basis with consumer protection safeguards",
    "معالجة البيانات الائتمانية والمالية في نطاق الأغراض النظامية مع ضمانات حماية المستهلك",
    "Credit-data processing register linked to SAMA/SIMAH authorisation or contractual necessity basis; "
    "data-sharing agreements with SIMAH or other credit bureaus; consumer dispute and correction process "
    "documentation; evidence that credit data is not used for purposes beyond credit assessment; "
    "annual credit-data audit report",
    "Credit and financial data processed under PDPL must be limited to credit-assessment and financial-service "
    "purposes, with consumers provided clear information about how their data influences credit decisions "
    "and with access to a dispute/correction mechanism.")
sub_count += 1

sub(c10, "10-4",
    "Process genetic data only for expressly permitted purposes under heightened security",
    "معالجة البيانات الجينية فقط للأغراض المصرح بها صراحةً وبأعلى مستوى من الضمانات الأمنية",
    "Genetic data processing justification memo citing specific statutory permission or explicit consent; "
    "evidence of encryption and pseudonymisation applied to genetic datasets; "
    "access restricted to qualified researchers or clinicians with documented need-to-know; "
    "ethics committee or IRB approval record where processing is for research",
    "Genetic data is among the most sensitive categories under PDPL. Processing is restricted to "
    "medical diagnosis, scientific research under ethical oversight, or other narrowly defined permitted "
    "purposes, with the highest tier of security controls applied.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 11 — Data Protection Officer (DPO) & Governance
# ---------------------------------------------------------------------------
c11 = control(fid, "11", "Data Protection Officer (DPO) & Governance",
              "مسؤول حماية البيانات والحوكمة",
              "Qualifying organisations must designate a DPO with appropriate expertise, independence, "
              "and authority. The DPO's role, tasks, reporting line, and resources must be formally "
              "established and maintained.")
control_count += 1

sub(c11, "11-1",
    "Designate a qualified DPO and register with SDAIA where required",
    "تعيين مسؤول حماية بيانات مؤهل وتسجيله لدى الهيئة حيثما اشترط النظام",
    "DPO appointment letter or board resolution; DPO qualifications and certification evidence (CIPP/ME or equivalent); "
    "SDAIA registration or notification record; DPO contact published in privacy notice; "
    "evidence that the DPO is not in a conflict of interest with other duties",
    "Organisations conducting large-scale processing of personal data, or processing sensitive data "
    "as a core activity, must designate a DPO and, where required, register the appointment with SDAIA.")
sub_count += 1

sub(c11, "11-2",
    "Ensure DPO independence, authority, and access to senior management",
    "ضمان استقلالية مسؤول حماية البيانات وصلاحياته ووصوله إلى الإدارة العليا",
    "Organisational chart showing DPO reporting line (directly to board or CEO level); "
    "DPO mandate/charter document confirming independence from business units; "
    "evidence of DPO attendance at senior management or board meetings; "
    "record of DPO advisory opinions sought and considered on high-risk decisions",
    "The DPO must be able to operate without instruction from business units on privacy matters "
    "and must have a direct reporting line to the highest management level.")
sub_count += 1

sub(c11, "11-3",
    "Assign DPO tasks formally and provide sufficient resources",
    "تحديد مهام مسؤول حماية البيانات رسمياً وتزويده بالموارد الكافية",
    "DPO job description covering all PDPL-mandated tasks (monitoring compliance, DPIA advisory, "
    "SDAIA liaison, data-subject rights, training); evidence of dedicated time allocation; "
    "DPO annual work plan and activity log; training budget allocation record; "
    "evidence of DPO participation in industry forums or continuing professional development",
    "The DPO must have the time, budget, staff support, and access to systems needed to carry out "
    "their mandatory tasks effectively. Nominal appointment without operational capacity is insufficient.")
sub_count += 1

sub(c11, "11-4",
    "Establish a privacy governance framework — policies, procedures, and accountability structure",
    "إرساء إطار حوكمة الخصوصية — السياسات والإجراءات وهيكل المساءلة",
    "Approved data protection policy suite (privacy policy, data retention policy, breach response procedure, "
    "DSR procedure, DPIA procedure, third-party management policy); board approval records; "
    "annual privacy governance review minutes; privacy risk inclusion in enterprise risk register; "
    "privacy training completion records for all staff handling personal data",
    "A functioning governance framework requires board-approved policies, operational procedures, "
    "defined accountabilities, regular reviews, and a staff-training programme.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 12 — Processor / Sub-processor Management & DPAs
# ---------------------------------------------------------------------------
c12 = control(fid, "12", "Processor / Sub-processor Management & DPAs",
              "إدارة المعالجين ومعالجي الباطن وعقود معالجة البيانات",
              "Controllers must only engage processors who provide sufficient guarantees of PDPL compliance, "
              "bind them by a written data processing agreement (DPA), and ensure sub-processor arrangements "
              "maintain equivalent protections.")
control_count += 1

sub(c12, "12-1",
    "Conduct due-diligence assessments of processors before engagement",
    "إجراء تقييم العناية الواجبة للمعالجين قبل التعاقد معهم",
    "Processor due-diligence questionnaire and completed assessment records; security certification "
    "evidence (ISO 27001, SOC 2, or equivalent) for critical processors; "
    "DPO sign-off on processor assessments; annual processor risk-review schedule and records; "
    "registry of approved processors with risk ratings",
    "Before appointing a processor the controller must verify that it has sufficient technical and "
    "organisational measures to process data securely and in compliance with PDPL.")
sub_count += 1

sub(c12, "12-2",
    "Execute a PDPL-compliant data processing agreement (DPA) with every processor",
    "إبرام اتفاقية معالجة بيانات متوافقة مع نظام حماية البيانات مع كل معالج",
    "Signed DPA inventory covering all processors; DPA template review record confirming coverage of: "
    "processing scope and purpose, data subject categories, security obligations, sub-processor approval "
    "requirement, breach notification to controller, data return/deletion at termination, audit rights; "
    "legal counsel review record of DPA template",
    "A DPA is a mandatory contract between controller and processor covering all prescribed elements. "
    "Engaging a processor without a DPA constitutes a PDPL violation regardless of the processor's "
    "own compliance posture.")
sub_count += 1

sub(c12, "12-3",
    "Manage and authorise sub-processor arrangements",
    "إدارة ترتيبات معالجي الباطن والموافقة عليها",
    "Sub-processor register derived from processor disclosures; evidence of controller authorisation "
    "(general or specific) for sub-processor use; flow-down DPA or equivalent contractual obligations "
    "with sub-processors; notification log when processors appoint new sub-processors; "
    "annual sub-processor inventory review",
    "Processors may not engage sub-processors without prior controller authorisation. "
    "The controller must ensure that sub-processors are bound by data protection obligations "
    "equivalent to those in the DPA.")
sub_count += 1

sub(c12, "12-4",
    "Monitor processor compliance and exercise audit rights periodically",
    "متابعة امتثال المعالجين وممارسة حقوق التدقيق بصفة دورية",
    "Annual processor compliance review records (questionnaire, audit, or third-party assurance report); "
    "evidence of follow-up on non-conformances identified; processor audit-right clause in DPA; "
    "record of exercised audit visits or inspection letters for high-risk processors; "
    "processor incident/breach notification log",
    "The controller's accountability obligations extend to its processors. Periodic reviews, audits, "
    "or reliance on recognised third-party assurance (e.g., ISO 27001 certification) are required "
    "to maintain ongoing visibility of processor compliance.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 13 — Direct Marketing & Automated Decision-Making
# ---------------------------------------------------------------------------
c13 = control(fid, "13", "Direct Marketing & Automated Decision-Making",
              "التسويق المباشر وصنع القرار الآلي",
              "Direct marketing communications require valid opt-in consent or a clear legitimate basis "
              "under applicable Saudi marketing regulations. Automated decisions producing significant "
              "legal or similarly significant effects on data subjects require safeguards, transparency, "
              "and a right to human review.")
control_count += 1

sub(c13, "13-1",
    "Obtain and document marketing consent; maintain suppression lists",
    "الحصول على موافقة التسويق وتوثيقها؛ الاحتفاظ بقوائم الإلغاء",
    "Marketing opt-in consent records linked to campaign CRM; suppression/do-not-contact list evidence; "
    "unsubscribe mechanism audit trail confirming removal from lists within required period; "
    "evidence that suppression list is applied across all marketing channels; "
    "annual marketing-database hygiene audit",
    "Marketing communications may only be sent to recipients who have affirmatively opted in "
    "or meet another recognised basis. Opt-out requests must be processed promptly and "
    "the suppression list enforced across all channels.")
sub_count += 1

sub(c13, "13-2",
    "Comply with the Communications and Information Technology Commission (CITC) anti-spam obligations",
    "الامتثال لاشتراطات هيئة الاتصالات وتقنية المعلومات المتعلقة بمكافحة البريد الإلكتروني غير المرغوب فيه",
    "CITC anti-spam policy compliance checklist; unsubscribe link presence in all commercial emails; "
    "sender identification in all SMS/email marketing; evidence of opt-in prior to first "
    "commercial communication; record of complaints received and handled",
    "Commercial electronic messages are subject to CITC anti-spam regulations as well as PDPL. "
    "Senders must identify themselves, include a working opt-out mechanism, and honour opt-outs promptly.")
sub_count += 1

sub(c13, "13-3",
    "Implement safeguards and transparency for automated individual decision-making",
    "اعتماد ضمانات وشفافية لصنع القرار الآلي الفردي",
    "Automated decision-making register identifying all processes producing significant effects; "
    "privacy notice disclosure covering automated decision-making logic in plain language; "
    "evidence of human-review mechanism for data subjects affected by automated decisions; "
    "DPIA record for automated decision-making activities; "
    "algorithmic bias monitoring report where applicable",
    "Where automated processing produces decisions with significant legal or similar effects, "
    "data subjects must be informed, given meaningful information about the logic, and have the right "
    "to request human review of the decision.")
sub_count += 1

sub(c13, "13-4",
    "Prohibit processing for profiling or targeted advertising without appropriate basis",
    "حظر استخدام البيانات للتحديد النوعي أو الإعلان المستهدف دون أساس قانوني مناسب",
    "Data-use purpose register confirming advertising/profiling activities have an identified lawful basis; "
    "consent records where consent is the basis for profiling; LIA documentation where legitimate interest "
    "is claimed; evidence of opt-out mechanism for interest-based advertising; "
    "periodic review of third-party advertising-technology partners' data use",
    "Profiling and targeted advertising using personal data require a lawful basis under PDPL. "
    "Broad 'personalisation' without a clear legal footing or valid consent is not acceptable.")
sub_count += 1

# ---------------------------------------------------------------------------
# CONTROL 14 — Security of Processing & Accountability
# ---------------------------------------------------------------------------
c14 = control(fid, "14", "Security of Processing & Accountability",
              "أمن معالجة البيانات الشخصية والمساءلة",
              "Controllers and processors must implement technical and organisational measures appropriate "
              "to the risk of processing, maintain accountability documentation, and be prepared to "
              "demonstrate PDPL compliance to SDAIA on request.")
control_count += 1

sub(c14, "14-1",
    "Implement risk-appropriate technical security measures for personal data",
    "اعتماد تدابير أمنية تقنية متناسبة مع المخاطر لحماية البيانات الشخصية",
    "Information security policy approved by senior management; encryption standard documentation "
    "covering data at rest and in transit; access-control matrix with least-privilege principle applied; "
    "annual penetration test report with personal-data scope confirmed; vulnerability management "
    "programme evidence; network segmentation diagram isolating personal data stores",
    "Security measures must be proportionate to the nature, scope, context, and risk of processing. "
    "Minimum expected controls include encryption, access control, vulnerability management, "
    "and regular security testing of systems handling personal data.")
sub_count += 1

sub(c14, "14-2",
    "Implement organisational security measures — training, policies, and access management",
    "اعتماد تدابير أمنية تنظيمية — التدريب والسياسات وإدارة الوصول",
    "Annual data protection training completion records for all staff; security-awareness programme "
    "evidence; background check records for staff with privileged access; access provisioning/de-provisioning "
    "log; leavers' process documentation confirming access removed promptly; "
    "clean-desk and clear-screen policy implementation evidence",
    "Organisational controls are as important as technical ones. Staff must be trained, access rights "
    "must be actively managed, and policies must be enforced through a culture of accountability.")
sub_count += 1

sub(c14, "14-3",
    "Embed privacy by design and by default in new systems and projects",
    "دمج الخصوصية بالتصميم والافتراض في الأنظمة والمشاريع الجديدة",
    "Privacy-by-design checklist integrated into SDLC or project management gate process; "
    "evidence of privacy review at design and build phases of at least two recent projects; "
    "default privacy settings documentation showing most privacy-protective settings as default; "
    "DPO sign-off at project go-live gate",
    "Privacy must be considered at the earliest stages of system design and projects, not retrofitted. "
    "Default settings must protect data subjects' privacy without requiring action from them.")
sub_count += 1

sub(c14, "14-4",
    "Maintain accountability documentation and demonstrate compliance to SDAIA on request",
    "الاحتفاظ بوثائق المساءلة وإثبات الامتثال للهيئة عند الطلب",
    "Compliance programme documentation index (policies, ROPA, DPIAs, DPO appointment, training records, "
    "breach register, processor DPAs, transfer assessments); evidence that documents are version-controlled "
    "and retrievable; record of any SDAIA inspection or enquiry and response; "
    "annual privacy compliance report presented to board or audit committee",
    "The accountability principle requires controllers to not only comply but also be able to "
    "demonstrate compliance. A well-maintained documentation library is the primary evidence of "
    "an effective PDPL programme.")
sub_count += 1

sub(c14, "14-5",
    "Conduct an annual internal privacy audit and address findings",
    "إجراء تدقيق خصوصية داخلي سنوي ومعالجة النتائج",
    "Annual internal or third-party privacy audit scope and methodology document; "
    "audit report with findings rated by severity; remediation plan with owners and target dates; "
    "evidence of findings closure; audit report presented to DPO, senior management, and audit committee; "
    "trend analysis comparing successive annual audit findings",
    "An annual audit of the privacy programme against PDPL requirements identifies gaps before "
    "regulators do and demonstrates the organisation's commitment to ongoing compliance improvement.")
sub_count += 1

# ---------------------------------------------------------------------------
# COMMIT
# ---------------------------------------------------------------------------
conn.commit()
print(f"[PDPL SME Seed] Done. Controls inserted/updated: {control_count}. Sub-controls inserted/updated: {sub_count}.")
