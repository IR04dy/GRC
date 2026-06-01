"""
SME catalog — AI Governance framework (ISO/IEC 42001 + NIST AI RMF + EU AI Act + SDAIA AI Ethics).

Adds a bilingual AI-governance control framework so AI becomes attestable through
the GRC Expert surface, alongside NCA/NDI/PDPL/BCM/ERM/ESG. 9 controls / 27
sub-controls with EN + Saudi-native AR recommended evidence. Practitioner SME
mapping; structure aligned to the published standards, control text paraphrased.

Idempotent: re-running deactivates then re-seeds + re-activates the framework.
ERM/ESG-style data-only seed (the AI family must be added to classifyFamily).
"""
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


def sub(cid, code, name_en, name_ar, g_en, g_ar, desc_en=None):
    cur.execute("SELECT id FROM evidence.sub_controls WHERE control_id=? AND sub_control_code=?", cid, code)
    r = cur.fetchone()
    if r:
        sid = int(r[0]); cur.execute("UPDATE evidence.sub_controls SET name_en=?, name_ar=?, description_en=?, evidence_guidance=?, evidence_guidance_ar=?, is_active=1 WHERE id=?",
                                      name_en, name_ar, desc_en, g_en, g_ar, sid)
    else:
        cur.execute("INSERT INTO evidence.sub_controls (control_id,sub_control_code,name_en,name_ar,description_en,evidence_guidance,evidence_guidance_ar,is_active) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,1)",
                    cid, code, name_en, name_ar, desc_en, g_en, g_ar)
        sid = int(cur.fetchone()[0])
    return sid


fid = framework(
    "AI-ISO42001-NIST-1",
    "AI Governance (ISO/IEC 42001 + NIST AI RMF + EU AI Act + SDAIA AI Ethics)",
    "حوكمة الذكاء الاصطناعي (الأيزو 42001 وإطار NIST لإدارة مخاطر الذكاء الاصطناعي وقانون الاتحاد الأوروبي للذكاء الاصطناعي ومبادئ سدايا لأخلاقيات الذكاء الاصطناعي)",
    "ISO 42001 / NIST AI RMF / EU AI Act",
    "ISO / NIST / EU / SDAIA",
    "AI governance & model-risk framework integrating ISO/IEC 42001 (AI management system), NIST AI RMF (Govern/Map/Measure/Manage), the EU AI Act (risk tiering + high-risk obligations), and SDAIA's AI Ethics Principles for Saudi Arabia. Covers governance, AI risk & impact assessment, data governance, model development & validation, fairness/transparency/explainability, human oversight & safety, monitoring & incident management, third-party & GenAI controls, and transparency/conformity. Practitioner SME mapping; paraphrased, not verbatim."
)

# 1. Governance & Accountability
c1 = control(fid, "AI-1", "AI Governance & Accountability", "حوكمة الذكاء الاصطناعي والمساءلة",
             "Board-approved AI policy, defined roles, AI risk appetite and oversight (ISO 42001 / NIST GOVERN).")
sub(c1, "AI-1-1", "AI Policy & Management System", "سياسة الذكاء الاصطناعي ونظام الإدارة",
    "Board-approved AI/ML policy and AI management system scope per ISO 42001; objectives, principles (incl. SDAIA AI Ethics), review cycle; published and accessible.",
    "سياسة الذكاء الاصطناعي/التعلّم الآلي ونطاق نظام إدارة الذكاء الاصطناعي معتمدة من مجلس الإدارة وفق الأيزو 42001؛ الأهداف والمبادئ (بما في ذلك مبادئ سدايا للأخلاقيات) ودورة المراجعة؛ منشورة ومتاحة.")
sub(c1, "AI-1-2", "AI Roles, Accountability & Oversight", "أدوار الذكاء الاصطناعي والمساءلة والإشراف",
    "RACI for AI roles (model owner, validator, AI risk officer, business sponsor); board/committee AI oversight minutes; documented accountability for AI outcomes.",
    "مصفوفة مسؤوليات لأدوار الذكاء الاصطناعي (مالك النموذج والمدقّق ومسؤول مخاطر الذكاء الاصطناعي والراعي)؛ محاضر إشراف المجلس/اللجنة على الذكاء الاصطناعي؛ توثيق المساءلة عن نتائج الذكاء الاصطناعي.")
sub(c1, "AI-1-3", "AI Risk Appetite & Inventory", "شهية مخاطر الذكاء الاصطناعي وسجل النماذج",
    "Documented AI risk appetite; complete AI/ML model inventory/registry with owner, purpose, risk tier, and lifecycle stage; reviewed periodically.",
    "شهية مخاطر موثقة للذكاء الاصطناعي؛ سجل/جرد كامل لنماذج الذكاء الاصطناعي والتعلّم الآلي يتضمّن المالك والغرض ومستوى المخاطر ومرحلة دورة الحياة؛ يُراجع دوريًا.")

# 2. AI Risk & Impact Assessment
c2 = control(fid, "AI-2", "AI Risk & Impact Assessment", "تقييم مخاطر الذكاء الاصطناعي والأثر",
             "Risk tiering, AI impact assessment and fundamental-rights review (NIST MAP / EU AI Act).")
sub(c2, "AI-2-1", "AI Risk Tiering (EU AI Act categories)", "تصنيف مخاطر الذكاء الاصطناعي (فئات قانون الاتحاد الأوروبي)",
    "Risk classification of each AI system into prohibited / high-risk / limited / minimal with rationale; high-risk systems flagged for the full obligation set.",
    "تصنيف مخاطر كل نظام ذكاء اصطناعي إلى محظور/عالي الخطورة/محدود/أدنى مع المبرّرات؛ وتمييز الأنظمة عالية الخطورة لتطبيق كامل الالتزامات عليها.")
sub(c2, "AI-2-2", "AI Impact Assessment (AIA / fundamental rights)", "تقييم أثر الذكاء الاصطناعي (الحقوق الأساسية)",
    "Completed AI impact assessment covering affected individuals, potential harms, fundamental-rights/fairness impact, and mitigations; approved before deployment.",
    "تقييم أثر للذكاء الاصطناعي يغطّي الأفراد المتأثّرين والأضرار المحتملة وأثر الحقوق الأساسية/العدالة والإجراءات التخفيفية؛ معتمد قبل النشر.")
sub(c2, "AI-2-3", "Privacy / PDPL DPIA for AI", "تقييم أثر حماية البيانات (PDPL) للذكاء الاصطناعي",
    "PDPL DPIA where the model processes personal data; lawful basis, automated-decision safeguards (PDPL Art.20 right to explanation), and minimisation evidenced.",
    "تقييم أثر حماية البيانات وفق نظام حماية البيانات الشخصية عند معالجة بيانات شخصية؛ الأساس النظامي وضمانات القرار الآلي (المادة 20 — الحق في التفسير) والتقليل من البيانات.")

# 3. Data Governance for AI
c3 = control(fid, "AI-3", "Data Governance for AI", "حوكمة البيانات للذكاء الاصطناعي",
             "Training-data provenance, quality and bias controls (EU AI Act data governance / ISO 42001).")
sub(c3, "AI-3-1", "Training-Data Provenance & Lineage", "مصدر بيانات التدريب وسلسلة المنشأ",
    "Documented training/validation/test data provenance, lineage, licensing, and consent basis; record of data sources (incl. synthetic/external) per model.",
    "توثيق مصدر بيانات التدريب/التحقّق/الاختبار وسلسلة المنشأ والتراخيص وأساس الموافقة؛ سجل لمصادر البيانات (بما في ذلك الاصطناعية/الخارجية) لكل نموذج.")
sub(c3, "AI-3-2", "Data Quality & Representativeness Gate", "بوّابة جودة البيانات وتمثيلها",
    "Data-quality criteria (completeness, accuracy, deduplication, representativeness) checked before training; results retained; gaps remediated.",
    "معايير جودة البيانات (الاكتمال والدقة وإزالة التكرار والتمثيل) تُفحص قبل التدريب؛ مع الاحتفاظ بالنتائج ومعالجة الفجوات.")
sub(c3, "AI-3-3", "Bias in Data Assessment", "تقييم التحيّز في البيانات",
    "Assessment of bias/skew in training data across protected attributes; mitigation (re-sampling, re-weighting) and residual-bias acceptance documented.",
    "تقييم التحيّز/الانحراف في بيانات التدريب عبر الخصائص المحمية؛ مع توثيق إجراءات التخفيف (إعادة المعاينة/الترجيح) وقبول التحيّز المتبقّي.")

# 4. Model Development & Validation
c4 = control(fid, "AI-4", "Model Development & Independent Validation", "تطوير النماذج والتحقّق المستقل",
             "Model documentation, independent validation, and pre-deployment testing (model risk management).")
sub(c4, "AI-4-1", "Model Documentation / Model Card", "توثيق النموذج / بطاقة النموذج",
    "Model card / technical documentation per EU AI Act Annex IV / NIST: purpose, data, architecture, metrics, limitations, intended use; version-controlled.",
    "بطاقة النموذج/التوثيق الفنّي وفق الملحق الرابع لقانون الاتحاد الأوروبي وNIST: الغرض والبيانات والبنية والمقاييس والقيود والاستخدام المقصود؛ مع ضبط الإصدارات.")
sub(c4, "AI-4-2", "Independent Model Validation", "التحقّق المستقل من النموذج",
    "Independent validation report (party separate from development): performance, stability, fairness, conceptual soundness; pass/conditions/fail outcome with sign-off.",
    "تقرير تحقّق مستقل (جهة منفصلة عن التطوير): الأداء والاستقرار والعدالة والسلامة المفاهيمية؛ مع نتيجة (اجتياز/بشروط/إخفاق) واعتماد.")
sub(c4, "AI-4-3", "Pre-Deployment Testing & Approval Gate", "اختبار ما قبل النشر وبوّابة الاعتماد",
    "Pre-deployment testing (incl. shadow/canary where applicable) and a documented approval gate; HIGH/CRITICAL models require validator + risk sign-off before production.",
    "اختبار ما قبل النشر (بما في ذلك الوضع الظلّي/التجريبي عند الاقتضاء) وبوّابة اعتماد موثقة؛ تتطلّب النماذج عالية/حرجة الخطورة اعتماد المدقّق والمخاطر قبل الإنتاج.")

# 5. Fairness, Transparency & Explainability
c5 = control(fid, "AI-5", "Fairness, Transparency & Explainability", "العدالة والشفافية وقابلية التفسير",
             "Bias testing, explainability and reason codes (SDAIA fairness/transparency / NIST MEASURE).")
sub(c5, "AI-5-1", "Fairness / Bias Testing", "اختبار العدالة/التحيّز",
    "Fairness testing across protected attributes (demographic parity, equalised odds, etc.) with thresholds; results and remediation retained; periodic re-test.",
    "اختبار العدالة عبر الخصائص المحمية (تكافؤ الفئات، تكافؤ الفرص، إلخ) مع حدود؛ مع الاحتفاظ بالنتائج والمعالجة وإعادة الاختبار الدوري.")
sub(c5, "AI-5-2", "Explainability & Reason Codes", "قابلية التفسير ورموز الأسباب",
    "Explainability evidence (e.g., SHAP/feature-importance) and adverse-action reason codes mapped to PDPL Art.20; explanation available for affected decisions.",
    "أدلة قابلية التفسير (مثل SHAP/أهمية الخصائص) ورموز أسباب الإجراءات السلبية مرتبطة بالمادة 20 من نظام حماية البيانات؛ مع إتاحة التفسير للقرارات المؤثّرة.")
sub(c5, "AI-5-3", "User Transparency & AI Disclosure", "شفافية المستخدم والإفصاح عن الذكاء الاصطناعي",
    "Evidence that users are informed when interacting with AI / subject to automated decisions (EU AI Act transparency + SDAIA); disclosure text retained.",
    "أدلة على إعلام المستخدمين عند التعامل مع الذكاء الاصطناعي/الخضوع لقرارات آلية (شفافية قانون الاتحاد الأوروبي ومبادئ سدايا)؛ مع الاحتفاظ بنص الإفصاح.")

# 6. Human Oversight & Safety
c6 = control(fid, "AI-6", "Human Oversight, Robustness & Safety", "الإشراف البشري والمتانة والسلامة",
             "Human-in-the-loop, override, and accuracy/robustness/cybersecurity (EU AI Act high-risk / SDAIA).")
sub(c6, "AI-6-1", "Human Oversight & Override", "الإشراف البشري وإمكانية التجاوز",
    "Defined human-in-the-loop / human-on-the-loop controls and a documented override/countermand mechanism for AI decisions; override events logged.",
    "ضوابط محدّدة للإشراف البشري (داخل/على الحلقة) وآلية موثقة لتجاوز/إلغاء قرارات الذكاء الاصطناعي؛ مع تسجيل أحداث التجاوز.")
sub(c6, "AI-6-2", "Accuracy, Robustness & Adversarial Testing", "الدقة والمتانة واختبار الهجمات العدائية",
    "Accuracy/robustness thresholds and adversarial/stress testing results (incl. evasion/poisoning where relevant); remediation of failures evidenced.",
    "حدود الدقة/المتانة ونتائج اختبارات الهجمات العدائية/الضغط (بما في ذلك التهرّب/التسميم عند الاقتضاء)؛ مع إثبات معالجة الإخفاقات.")
sub(c6, "AI-6-3", "AI Cybersecurity Controls", "ضوابط الأمن السيبراني للذكاء الاصطناعي",
    "Security controls over the model/pipeline (access control, model integrity, secrets, supply-chain) aligned to NCA ECC; evidence of testing.",
    "ضوابط أمنية على النموذج/خط الإنتاج (التحكّم بالوصول وسلامة النموذج والأسرار وسلسلة التوريد) متوافقة مع الضوابط الأساسية للهيئة الوطنية للأمن السيبراني؛ مع أدلة الاختبار.")

# 7. Monitoring, Logging & Incident Management
c7 = control(fid, "AI-7", "Monitoring, Logging & Incident Management", "المراقبة والتسجيل وإدارة الحوادث",
             "Drift/performance monitoring, record-keeping/logging, and AI incident response (NIST MANAGE / EU AI Act).")
sub(c7, "AI-7-1", "Performance & Drift Monitoring", "مراقبة الأداء والانحراف",
    "Post-deployment monitoring of performance, data/concept drift, and fairness with thresholds and alerting; observations reviewed and acted upon.",
    "مراقبة ما بعد النشر للأداء وانحراف البيانات/المفاهيم والعدالة مع حدود وتنبيهات؛ مع مراجعة الملاحظات واتخاذ الإجراءات.")
sub(c7, "AI-7-2", "Record-Keeping & Inference Logging", "حفظ السجلات وتسجيل الاستدلال",
    "Automatic logging/record-keeping over the AI system's lifecycle (EU AI Act); inference/usage logs retained per policy with retention period defined.",
    "تسجيل/حفظ تلقائي للسجلات عبر دورة حياة نظام الذكاء الاصطناعي (قانون الاتحاد الأوروبي)؛ مع الاحتفاظ بسجلات الاستدلال/الاستخدام وفق السياسة وتحديد مدة الاحتفاظ.")
sub(c7, "AI-7-3", "AI Incident & Failure Management", "إدارة حوادث وإخفاقات الذكaء الاصطناعي",
    "AI incident process with a failure-mode taxonomy (drift/concept-drift/adversarial/regression), root-cause, CAPA, and regulator notification where required.",
    "عملية لحوادث الذكاء الاصطناعي مع تصنيف لأنماط الإخفاق (انحراف/انحراف مفاهيمي/هجوم عدائي/تراجع) وتحليل الأسباب الجذرية والإجراءات التصحيحية وإخطار الجهة التنظيمية عند الاقتضاء.")

# 8. Third-Party & GenAI Controls
c8 = control(fid, "AI-8", "Third-Party & GenAI / LLM Controls", "ضوابط الأطراف الثالثة والذكاء الاصطناعي التوليدي",
             "Vendor AI governance and GenAI/LLM safety (ISO 42001 §8.4 supplier mgmt + GenAI risk).")
sub(c8, "AI-8-1", "Third-Party / Vendor AI Governance", "حوكمة الذكاء الاصطناعي للأطراف الثالثة",
    "Vendor-AI risk assessment, contractual audit rights/SLAs, incident-notification terms, and annual re-assessment for externally-provided AI (e.g., API/LLM vendors).",
    "تقييم مخاطر الذكاء الاصطناعي للموردين وحقوق التدقيق التعاقدية/اتفاقيات مستوى الخدمة وبنود الإخطار بالحوادث وإعادة التقييم السنوي للذكاء الاصطناعي المقدّم خارجيًا (مثل موردي واجهات البرمجة/النماذج اللغوية).")
sub(c8, "AI-8-2", "GenAI/LLM Safety Controls", "ضوابط سلامة الذكاء الاصطناعي التوليدي/النماذج اللغوية",
    "For LLM/GenAI: prompt-injection/jailbreak detection, content-safety moderation, PII/data-leakage prevention, and groundedness/hallucination checks; configuration evidenced.",
    "للنماذج اللغوية/التوليدية: كشف حقن الأوامر/كسر الحماية، وتهذيب سلامة المحتوى، ومنع تسرّب البيانات الشخصية، وفحوص الترابط/الهلوسة؛ مع إثبات الإعداد.")
sub(c8, "AI-8-3", "GenAI Usage Logging & Human Handoff", "تسجيل استخدام الذكاء الاصطناعي التوليدي والتحويل البشري",
    "GenAI usage/inference logging with safety flags, and a human-handoff path for sensitive actions (e.g., account/transaction) instead of autonomous execution.",
    "تسجيل استخدام/استدلال الذكاء الاصطناعي التوليدي مع مؤشّرات السلامة، ومسار تحويل بشري للإجراءات الحساسة (مثل الحسابات/المعاملات) بدلًا من التنفيذ الذاتي.")

# 9. Transparency, Disclosure & Conformity
c9 = control(fid, "AI-9", "Transparency, Disclosure & Conformity", "الشفافية والإفصاح والمطابقة",
             "AI register, model cards as artifacts, and conformity attestation (EU AI Act / SDAIA / ISO 42001).")
sub(c9, "AI-9-1", "AI Register / Transparency Record", "سجل الذكاء الاصطناعي/سجل الشفافية",
    "Maintained AI register (purpose, risk tier, owner, status) and, where applicable, public/regulator transparency disclosures; kept current.",
    "سجل ذكاء اصطناعي محدّث (الغرض ومستوى المخاطر والمالك والحالة) و—عند الاقتضاء—إفصاحات شفافية للعموم/الجهة التنظيمية؛ يُبقى محدّثًا.")
sub(c9, "AI-9-2", "Conformity / Ethics Attestation", "إقرار المطابقة/الأخلاقيات",
    "Annual conformity / AI-ethics attestation mapping the system to SDAIA AI Ethics principles (and EU AI Act high-risk obligations where applicable); board/officer sign-off.",
    "إقرار سنوي بالمطابقة/أخلاقيات الذكاء الاصطناعي يربط النظام بمبادئ سدايا للأخلاقيات (والتزامات قانون الاتحاد الأوروبي عالية الخطورة عند الاقتضاء)؛ معتمد من المجلس/المسؤول.")
sub(c9, "AI-9-3", "Recertification & Periodic Review", "إعادة الاعتماد والمراجعة الدورية",
    "Periodic (at least annual) recertification/review of deployed AI: re-validation, fairness re-audit, documentation refresh; recertify or retire decision recorded.",
    "إعادة اعتماد/مراجعة دورية (سنوية على الأقل) للذكاء الاصطناعي المنشور: إعادة التحقّق وإعادة تدقيق العدالة وتحديث التوثيق؛ مع تسجيل قرار إعادة الاعتماد أو الإيقاف.")

conn.commit()
cur.execute("SELECT COUNT(*) FROM evidence.controls WHERE framework_id=? AND is_active=1", fid)
nc = int(cur.fetchone()[0])
cur.execute("""SELECT COUNT(*), SUM(CASE WHEN evidence_guidance IS NOT NULL AND evidence_guidance_ar IS NOT NULL THEN 1 ELSE 0 END)
               FROM evidence.sub_controls WHERE control_id IN (SELECT id FROM evidence.controls WHERE framework_id=?) AND is_active=1""", fid)
ns, nbil = cur.fetchone()
print(f"[AI-ISO42001-NIST-1] Seeded OK — fid={fid}, {nc} controls, {ns} sub-controls, {nbil}/{ns} fully bilingual (EN+AR)")
conn.close()
