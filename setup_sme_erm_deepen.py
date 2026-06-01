"""
SME catalog — DEEPEN the ERM-1 framework (ISO 31000 + COSO ERM 2017 + SAMA).

ERM-1 was a thin catalog (3 controls / 8 sub-controls). This re-seeds it into a
comprehensive Enterprise Risk Management framework: 8 controls / 25 sub-controls
covering governance & framework, identification & assessment, treatment &
monitoring, risk culture, emerging-risk/horizon scanning, ICAAP/stress testing,
three-lines-of-defence assurance, and risk reporting & data aggregation.

The EXISTING 8 sub-control codes (ERM-1-1-1..ERM-1-3-2) are preserved verbatim so
their row IDs (and any evidence links) survive (UPSERT by code = UPDATE), and AR
recommended evidence is back-filled for them. New controls/sub-controls are added.
Every active sub-control carries EN + Saudi-native AR recommended evidence.

Idempotent: re-running deactivates then re-seeds + re-activates the framework's
controls/sub-controls. ERM is already a first-class GRC Expert family
(classifyFamily), so no backend rebuild is needed — it surfaces immediately.
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


def sub(cid, code, name_en, name_ar, guidance_en, guidance_ar, desc_en=None):
    cur.execute("SELECT id FROM evidence.sub_controls WHERE control_id=? AND sub_control_code=?", cid, code)
    r = cur.fetchone()
    if r:
        sid = int(r[0]); cur.execute("UPDATE evidence.sub_controls SET name_en=?, name_ar=?, description_en=?, evidence_guidance=?, evidence_guidance_ar=?, is_active=1 WHERE id=?",
                                      name_en, name_ar, desc_en, guidance_en, guidance_ar, sid)
    else:
        cur.execute("INSERT INTO evidence.sub_controls (control_id,sub_control_code,name_en,name_ar,description_en,evidence_guidance,evidence_guidance_ar,is_active) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,1)",
                    cid, code, name_en, name_ar, desc_en, guidance_en, guidance_ar)
        sid = int(cur.fetchone()[0])
    return sid


fid = framework(
    "ERM-1",
    "Enterprise Risk Management (ISO 31000 + COSO ERM 2017 + SAMA)",
    "إدارة المخاطر المؤسسية (الأيزو 31000 وإطار COSO ERM 2017 وإطار البنك المركزي السعودي)",
    "ISO 31000:2018 / COSO ERM 2017 / SAMA",
    "ISO / COSO / SAMA",
    "Comprehensive enterprise risk management framework integrating ISO 31000:2018 (principles, framework, process), COSO ERM 2017 (governance & culture, strategy & objective-setting, performance, review & revision, information/communication/reporting), and SAMA enterprise-risk expectations (board oversight, risk appetite, ICAAP/stress testing, three-lines-of-defence). Practitioner SME mapping; structure aligned to the published standards, control text paraphrased (not verbatim)."
)

# ── ERM-1-1: Risk Governance & Framework (existing 3 + 2 new) ────────────────
c1 = control(fid, "ERM-1-1", "Risk Governance & Framework", "حوكمة وإطار المخاطر",
             "Board oversight, risk policy, appetite and an ISO 31000-aligned risk-management framework.")
sub(c1, "ERM-1-1-1", "Risk Management Policy", "سياسة إدارة المخاطر",
    "Board-approved enterprise risk management policy (with version history) defining scope, objectives, risk taxonomy, roles, and review cycle; published and accessible; last review date evidenced.",
    "سياسة إدارة المخاطر المؤسسية معتمدة من مجلس الإدارة (مع سجل الإصدارات) تحدّد النطاق والأهداف وتصنيف المخاطر والأدوار ودورة المراجعة؛ منشورة ومتاحة؛ مع إثبات تاريخ آخر مراجعة.")
sub(c1, "ERM-1-1-2", "Risk Appetite Statement", "بيان شهية المخاطر",
    "Board-approved risk appetite statement with quantitative thresholds and tolerance bands per risk category; linkage to strategy and capital; annual review and breach-escalation procedure.",
    "بيان شهية المخاطر معتمد من مجلس الإدارة يتضمّن حدودًا كميّة ونطاقات تحمّل لكل فئة مخاطر؛ ربطه بالاستراتيجية ورأس المال؛ مراجعة سنوية وإجراء تصعيد عند تجاوز الحدود.")
sub(c1, "ERM-1-1-3", "Risk Committee Charter", "اختصاصات لجنة المخاطر",
    "Risk committee terms of reference (composition, authority, quorum, frequency); meeting minutes evidencing risk oversight at least quarterly; escalation path to the board.",
    "الاختصاصات التأسيسية للجنة المخاطر (التشكيل والصلاحيات والنصاب والوتيرة)؛ محاضر اجتماعات تُثبت الإشراف على المخاطر ربع سنويًا على الأقل؛ مسار التصعيد إلى المجلس.")
sub(c1, "ERM-1-1-4", "Board & Senior Management Oversight", "إشراف مجلس الإدارة والإدارة العليا",
    "Evidence of board/senior-management ERM oversight: annual risk strategy approval, risk-appetite sign-off, periodic risk dashboards to the board, and documented challenge of management's risk view.",
    "أدلة إشراف مجلس الإدارة/الإدارة العليا على إدارة المخاطر المؤسسية: اعتماد استراتيجية المخاطر السنوية، واعتماد شهية المخاطر، ولوحات مخاطر دورية تُرفع للمجلس، وتوثيق مساءلة رؤية الإدارة للمخاطر.")
sub(c1, "ERM-1-1-5", "Risk Management Framework & ISO 31000 Alignment", "إطار إدارة المخاطر والتوافق مع الأيزو 31000",
    "Documented risk-management framework mapped to ISO 31000 principles/framework/process; integration with strategy, governance and other management systems; periodic effectiveness review of the framework itself.",
    "إطار موثّق لإدارة المخاطر مرتبط بمبادئ وإطار وعملية الأيزو 31000؛ تكامله مع الاستراتيجية والحوكمة وأنظمة الإدارة الأخرى؛ مراجعة دورية لفاعلية الإطار نفسه.")

# ── ERM-1-2: Risk Identification & Assessment (existing 3 + 2 new) ───────────
c2 = control(fid, "ERM-1-2", "Risk Identification & Assessment", "تحديد وتقييم المخاطر",
             "Identify, analyse and evaluate risks with a consistent methodology and a maintained risk register.")
sub(c2, "ERM-1-2-1", "Risk Register", "سجل المخاطر",
    "Current enterprise risk register with each risk's owner, category, inherent and residual likelihood/impact ratings, controls, and treatment status; maintained in the GRC system; last-updated date evidenced.",
    "سجل مخاطر مؤسسي محدّث يتضمّن لكل مخاطرة مالكها وفئتها وتقييم الاحتمالية/الأثر الكامن والمتبقّي والضوابط وحالة المعالجة؛ مُدار في نظام الحوكمة والمخاطر؛ مع إثبات تاريخ آخر تحديث.")
sub(c2, "ERM-1-2-2", "Risk Assessment Methodology", "منهجية تقييم المخاطر",
    "Approved risk assessment methodology defining likelihood/impact scales, the 5×5 (or equivalent) matrix, scoring rules, and rating thresholds; consistently applied across the register.",
    "منهجية تقييم مخاطر معتمدة تحدّد مقاييس الاحتمالية/الأثر ومصفوفة 5×5 (أو ما يعادلها) وقواعد التقييم وحدود التصنيف؛ مطبَّقة باتّساق عبر السجل.")
sub(c2, "ERM-1-2-3", "Quarterly Risk Review Report", "تقرير المراجعة الربعية للمخاطر",
    "Quarterly risk review report presented to the risk committee: top risks, movements, KRI status, appetite breaches, and emerging risks; minutes evidencing review and decisions.",
    "تقرير مراجعة ربعي للمخاطر يُعرض على لجنة المخاطر: أبرز المخاطر وتحرّكاتها وحالة مؤشرات المخاطر الرئيسية وتجاوزات الشهية والمخاطر الناشئة؛ محاضر تُثبت المراجعة والقرارات.")
sub(c2, "ERM-1-2-4", "Inherent vs Residual Risk Assessment", "تقييم المخاطر الكامنة مقابل المتبقّية",
    "Documented assessment of inherent vs residual risk per material risk, showing the effect of controls; residual ratings drive the operating register and board reporting; rationale for residual ratings retained.",
    "تقييم موثّق للمخاطر الكامنة مقابل المتبقّية لكل مخاطرة جوهرية يُبيّن أثر الضوابط؛ تُستخدم التقييمات المتبقّية في السجل التشغيلي وتقارير المجلس؛ مع الاحتفاظ بمبرّرات التقييم المتبقّي.")
sub(c2, "ERM-1-2-5", "Risk Aggregation & Concentration Analysis", "تجميع المخاطر وتحليل التركّزات",
    "Risk aggregation across categories/business units and concentration analysis (single-name, sector, geography); methodology and results reported to the risk committee; linkage to appetite and capital.",
    "تجميع المخاطر عبر الفئات/وحدات الأعمال وتحليل التركّزات (الجهة الواحدة والقطاع والجغرافيا)؛ المنهجية والنتائج تُرفع للجنة المخاطر؛ ربطها بالشهية ورأس المال.")

# ── ERM-1-3: Risk Treatment & Monitoring (existing 2 + 2 new) ────────────────
c3 = control(fid, "ERM-1-3", "Risk Treatment & Monitoring", "معالجة ومراقبة المخاطر",
             "Treat risks per appetite, govern acceptances, and monitor control effectiveness and KRIs.")
sub(c3, "ERM-1-3-1", "Risk Treatment Plans", "خطط معالجة المخاطر",
    "Risk treatment plans (avoid/mitigate/transfer/accept) per material risk with owner, due date, target residual rating, and progress tracking; reviewed by the risk function.",
    "خطط معالجة المخاطر (تجنّب/تخفيف/تحويل/قبول) لكل مخاطرة جوهرية مع المالك وتاريخ الاستحقاق والتقييم المتبقّي المستهدف وتتبّع التقدّم؛ تُراجعها وظيفة المخاطر.")
sub(c3, "ERM-1-3-2", "Key Risk Indicators Dashboard", "لوحة مؤشرات المخاطر الرئيسية",
    "KRI dashboard with defined thresholds (green/amber/red), data sources, owners, and breach-escalation workflow for the latest period; trend analysis and reporting to the risk committee.",
    "لوحة مؤشرات المخاطر الرئيسية بحدود مُعرّفة (أخضر/أصفر/أحمر) ومصادر بيانات وملّاك وآلية تصعيد عند التجاوز لآخر فترة؛ تحليل الاتجاهات ورفعها للجنة المخاطر.")
sub(c3, "ERM-1-3-3", "Risk Acceptance & Exception Governance", "حوكمة قبول المخاطر والاستثناءات",
    "Risk acceptance/exception register with documented rationale, compensating controls, approval at the appropriate authority (per appetite), expiry date, and periodic re-validation.",
    "سجل قبول المخاطر/الاستثناءات مع توثيق المبرّرات والضوابط التعويضية والاعتماد من الصلاحية المناسبة (وفق الشهية) وتاريخ الانتهاء وإعادة التحقّق الدوري.")
sub(c3, "ERM-1-3-4", "Control Effectiveness Monitoring", "مراقبة فاعلية الضوابط",
    "Evidence that key controls mitigating material risks are monitored for effectiveness (testing results, control performance metrics); deficiencies feed treatment plans and residual-rating updates.",
    "أدلة على مراقبة فاعلية الضوابط الرئيسية المخفّفة للمخاطر الجوهرية (نتائج الاختبارات ومقاييس أداء الضوابط)؛ تغذّي أوجه القصور خطط المعالجة وتحديثات التقييم المتبقّي.")

# ── ERM-1-4: Risk Culture & Awareness (COSO governance & culture) ────────────
c4 = control(fid, "ERM-1-4", "Risk Culture & Awareness", "ثقافة المخاطر والتوعية",
             "Embed a sound risk culture through assessment, training, and conduct/accountability linkage.")
sub(c4, "ERM-1-4-1", "Risk Culture Assessment", "تقييم ثقافة المخاطر",
    "Periodic risk-culture assessment (survey/indicators) with results, root-cause analysis, and an action plan reported to the board/risk committee.",
    "تقييم دوري لثقافة المخاطر (استبيان/مؤشرات) مع النتائج وتحليل الأسباب الجذرية وخطة عمل تُرفع للمجلس/لجنة المخاطر.")
sub(c4, "ERM-1-4-2", "Risk Training & Awareness Programme", "برنامج التدريب والتوعية بالمخاطر",
    "Annual risk training & awareness programme with content, target audiences, completion tracking (≥ target rate), and effectiveness measurement.",
    "برنامج سنوي للتدريب والتوعية بالمخاطر يتضمّن المحتوى والفئات المستهدفة وتتبّع الإكمال (بنسبة لا تقل عن المستهدف) وقياس الفاعلية.")
sub(c4, "ERM-1-4-3", "Conduct & Accountability Linkage", "ربط السلوك والمساءلة",
    "Evidence that risk and conduct outcomes are reflected in performance management/remuneration (e.g., risk-adjusted scorecards, malus/clawback where applicable) per SAMA expectations.",
    "أدلة على انعكاس نتائج المخاطر والسلوك في إدارة الأداء/المكافآت (مثل بطاقات الأداء المعدّلة بالمخاطر وآليات الاسترداد عند الاقتضاء) وفق متطلّبات البنك المركزي السعودي.")

# ── ERM-1-5: Emerging Risk & Horizon Scanning ────────────────────────────────
c5 = control(fid, "ERM-1-5", "Emerging Risk & Horizon Scanning", "المخاطر الناشئة واستشراف الأفق",
             "Identify forward-looking risks and test resilience through scenario analysis.")
sub(c5, "ERM-1-5-1", "Emerging Risk Identification & Horizon Scanning", "تحديد المخاطر الناشئة واستشراف الأفق",
    "Documented horizon-scanning process and emerging-risk register (e.g., geopolitical, climate, technology, regulatory) with monitoring indicators and periodic reporting to the risk committee.",
    "عملية موثّقة لاستشراف الأفق وسجل للمخاطر الناشئة (مثل الجيوسياسية والمناخية والتقنية والتنظيمية) مع مؤشرات للمتابعة ورفع دوري للجنة المخاطر.")
sub(c5, "ERM-1-5-2", "Scenario Analysis & Stress Testing (Strategic/Operational)", "تحليل السيناريوهات واختبارات الضغط (الاستراتيجية/التشغيلية)",
    "Scenario analysis / stress testing of severe-but-plausible scenarios on strategy and operations; assumptions, results, management actions, and board reporting documented.",
    "تحليل سيناريوهات/اختبارات ضغط لسيناريوهات شديدة لكنها محتملة على الاستراتيجية والعمليات؛ مع توثيق الافتراضات والنتائج وإجراءات الإدارة ورفعها للمجلس.")

# ── ERM-1-6: Capital Adequacy & Stress Testing (ICAAP — SAMA) ────────────────
c6 = control(fid, "ERM-1-6", "Capital Adequacy & ICAAP (SAMA)", "كفاية رأس المال وعملية التقييم الداخلي (ICAAP)",
             "Internal capital adequacy assessment and stress testing aligned to SAMA requirements.")
sub(c6, "ERM-1-6-1", "ICAAP Document & Governance", "وثيقة وحوكمة عملية التقييم الداخلي لكفاية رأس المال",
    "Board-approved ICAAP document covering material risks, capital planning, and risk-appetite linkage; governance and challenge process evidenced; annual SAMA submission record where applicable.",
    "وثيقة ICAAP معتمدة من مجلس الإدارة تغطّي المخاطر الجوهرية وتخطيط رأس المال وربطه بشهية المخاطر؛ مع إثبات الحوكمة وعملية المساءلة؛ وسجل التقديم السنوي للبنك المركزي السعودي عند الاقتضاء.")
sub(c6, "ERM-1-6-2", "Stress Testing & Reverse Stress Testing Programme", "برنامج اختبارات الضغط واختبارات الضغط العكسي",
    "Capital/liquidity stress-testing programme incl. reverse stress testing; scenario suite, results, capital impact, and management actions; results reported to the board and SAMA as required.",
    "برنامج اختبارات ضغط لرأس المال/السيولة يشمل اختبارات الضغط العكسي؛ مجموعة السيناريوهات والنتائج وأثر رأس المال وإجراءات الإدارة؛ ترفع النتائج للمجلس وللبنك المركزي السعودي عند الطلب.")

# ── ERM-1-7: Three Lines of Defence & Assurance ──────────────────────────────
c7 = control(fid, "ERM-1-7", "Three Lines of Defence & Assurance", "خطوط الدفاع الثلاثة والتطمين",
             "Clear roles across the three lines and combined assurance over material risks.")
sub(c7, "ERM-1-7-1", "Three-Lines-of-Defence Model & Roles", "نموذج خطوط الدفاع الثلاثة والأدوار",
    "Documented three-lines-of-defence model defining responsibilities (business/management, risk & compliance, internal audit), with evidence of operating independence of the second and third lines.",
    "نموذج موثّق لخطوط الدفاع الثلاثة يحدّد المسؤوليات (الأعمال/الإدارة، المخاطر والالتزام، التدقيق الداخلي) مع أدلة على استقلالية الخطّين الثاني والثالث.")
sub(c7, "ERM-1-7-2", "Combined Assurance Mapping", "خريطة التطمين المُجمّع",
    "Combined assurance map linking material risks/controls to assurance providers (management testing, risk/compliance reviews, internal/external audit) to identify gaps and over-reliance.",
    "خريطة تطمين مُجمّع تربط المخاطر/الضوابط الجوهرية بمقدّمي التطمين (اختبارات الإدارة ومراجعات المخاطر/الالتزام والتدقيق الداخلي/الخارجي) لتحديد الفجوات والاعتماد المفرط.")

# ── ERM-1-8: Risk Reporting & Data Aggregation (COSO information & reporting) ─
c8 = control(fid, "ERM-1-8", "Risk Reporting & Data Aggregation", "تقارير المخاطر وتجميع البيانات",
             "Timely, accurate enterprise risk reporting underpinned by sound risk-data aggregation.")
sub(c8, "ERM-1-8-1", "Enterprise Risk Reporting to the Board", "تقارير المخاطر المؤسسية إلى المجلس",
    "Board/risk-committee risk reporting pack: risk profile vs appetite, top & emerging risks, KRIs, treatment status, and loss events; defined frequency, owners, and sign-off.",
    "حزمة تقارير المخاطر للمجلس/لجنة المخاطر: ملف المخاطر مقابل الشهية، وأبرز المخاطر والناشئة، ومؤشرات المخاطر، وحالة المعالجة، وأحداث الخسائر؛ بوتيرة محدّدة وملّاك واعتماد.")
sub(c8, "ERM-1-8-2", "Risk Data Aggregation & Quality (BCBS 239-aligned)", "تجميع بيانات المخاطر وجودتها (بما يتوافق مع BCBS 239)",
    "Evidence of risk-data aggregation capability and data-quality controls (accuracy, completeness, timeliness, adaptability) aligned to BCBS 239 principles; data lineage and reconciliation for key risk reports.",
    "أدلة على قدرة تجميع بيانات المخاطر وضوابط جودة البيانات (الدقة والاكتمال والتوقيت والقابلية للتكيّف) بما يتوافق مع مبادئ BCBS 239؛ وتتبّع مصدر البيانات وتسويتها لتقارير المخاطر الرئيسية.")

# ── Commit & summary ──────────────────────────────────────────────────────────
conn.commit()
cur.execute("SELECT COUNT(*) FROM evidence.controls WHERE framework_id=? AND is_active=1", fid)
nc = int(cur.fetchone()[0])
cur.execute("""SELECT COUNT(*), SUM(CASE WHEN evidence_guidance IS NOT NULL AND evidence_guidance_ar IS NOT NULL THEN 1 ELSE 0 END)
               FROM evidence.sub_controls WHERE control_id IN (SELECT id FROM evidence.controls WHERE framework_id=?) AND is_active=1""", fid)
ns, nbil = cur.fetchone()
print(f"[ERM-1] Deepened OK — fid={fid}, {nc} controls, {ns} sub-controls, {nbil}/{ns} fully bilingual (EN+AR)")
conn.close()
