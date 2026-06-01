"""
SME catalog seed — ESG / Sustainability framework (CMA + Saudi Exchange + GRI/ISSB/TCFD).

Adds a bilingual ESG control framework to evidence.frameworks/controls/sub_controls so
ESG becomes attestable through the GRC Expert surface (GET /api/v1/evidence/expert) with
COVERED/PARTIAL/EXPIRED/GAP rollup per sub-control — the same way NCA/NDI/PDPL/BCM are.
Structure aligned to the published guidelines; control text is a practitioner paraphrase
(not verbatim). Each sub-control carries EN + Saudi-native AR recommended evidence.

Idempotent: re-running deactivates the framework's existing controls/sub-controls then
re-seeds + re-activates, so it is safe on a DB refresh.
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


# ─── Framework ────────────────────────────────────────────────────────────────
fid = framework(
    "ESG-CMA-TADAWUL-1",
    "ESG Disclosure & Sustainability (CMA + Saudi Exchange + GRI / ISSB / TCFD)",
    "الإفصاح عن الاستدامة والحوكمة البيئية والاجتماعية (هيئة السوق المالية وتداول السعودية ومعايير GRI / ISSB / TCFD)",
    "2021/2024",
    "CMA / Saudi Exchange / ISSB",
    "Integrated ESG & sustainability framework mapping the CMA Corporate Governance ESG expectations, the Saudi Exchange (Tadawul) ESG Disclosure Guidelines, GRI Standards, ISSB S1/S2, and TCFD recommendations — aligned to Saudi Vision 2030 and the Saudi Green Initiative. Covers ESG governance, materiality, environmental & climate (GHG Scope 1/2/3, energy, water, waste), TCFD climate risk, social (workforce/Saudization, H&S, human rights, community), and disclosure/assurance. Practitioner SME mapping; structure aligned to the published guidelines, control text paraphrased (not verbatim)."
)

# ─── G1: ESG Governance & Strategy ────────────────────────────────────────────
g1 = control(fid, "G1", "ESG Governance & Strategy",
             "حوكمة الاستدامة والاستراتيجية البيئية والاجتماعية",
             "Board-level oversight, ESG policy, accountability, and sustainability commitments aligned to Vision 2030 and the Saudi Green Initiative.")

sub(g1, "G1-1",
    "Board & Committee ESG Oversight",
    "إشراف مجلس الإدارة واللجان على الاستدامة",
    "Board or board-committee charter assigning ESG/sustainability oversight; meeting minutes evidencing ESG agenda items at least semi-annually; documented escalation of material ESG risks to the board; annual board review of ESG strategy and performance.",
    "ميثاق مجلس الإدارة أو لجنته المنبثقة يُسند الإشراف على الاستدامة والحوكمة البيئية والاجتماعية؛ محاضر اجتماعات تُثبت إدراج بنود الاستدامة على جدول الأعمال مرتين سنويًا على الأقل؛ توثيق تصعيد مخاطر الاستدامة الجوهرية إلى المجلس؛ مراجعة سنوية من المجلس لاستراتيجية الاستدامة وأدائها.",
    "Establish board-level accountability for ESG, with a mandated committee, regular agenda coverage, and escalation of material ESG risks.")

sub(g1, "G1-2",
    "ESG / Sustainability Policy & Strategy",
    "سياسة واستراتيجية الاستدامة",
    "Board-approved ESG/sustainability policy and multi-year strategy with objectives, scope, and review cycle; alignment statement to Saudi Vision 2030 and the Saudi Green Initiative; published policy accessible to stakeholders; version history.",
    "سياسة واستراتيجية استدامة معتمدة من مجلس الإدارة تتضمن الأهداف والنطاق ودورة المراجعة؛ بيان التوافق مع رؤية السعودية 2030 ومبادرة السعودية الخضراء؛ سياسة منشورة ومتاحة للأطراف المعنية؛ سجل الإصدارات.",
    "Adopt a board-approved ESG policy and strategy aligned to national sustainability priorities, communicated and reviewed at defined intervals.")

sub(g1, "G1-3",
    "ESG Roles, Accountability & Remuneration Linkage",
    "أدوار الاستدامة والمساءلة وربطها بالمكافآت",
    "RACI for ESG roles (board sponsor, chief sustainability officer / ESG lead, data owners per metric); documented executive accountability; evidence that ESG/sustainability targets are linked to senior-management KPIs or remuneration; review on org change.",
    "مصفوفة مسؤوليات (RACI) لأدوار الاستدامة (راعي من المجلس، رئيس الاستدامة أو مسؤول الحوكمة البيئية والاجتماعية، ملّاك البيانات لكل مؤشر)؛ توثيق مساءلة الإدارة التنفيذية؛ دليل على ربط مستهدفات الاستدامة بمؤشرات أداء الإدارة العليا أو مكافآتها؛ المراجعة عند تغيّر الهيكل التنظيمي.",
    "Define ESG roles and accountabilities and link sustainability targets to executive performance and remuneration.")

sub(g1, "G1-4",
    "Sustainability Commitments & Targets (Saudi Green Initiative alignment)",
    "التزامات ومستهدفات الاستدامة (التوافق مع مبادرة السعودية الخضراء)",
    "Published sustainability commitments with baseline year, targets, and milestones (e.g., net-zero date, emission-reduction %, Saudization, localisation); mapping to Saudi Green Initiative / Vision 2030 goals; progress dashboard reviewed by the ESG committee.",
    "التزامات استدامة منشورة تتضمن سنة الأساس والمستهدفات والمراحل (مثل تاريخ الحياد الكربوني ونسبة خفض الانبعاثات والسعودة والمحتوى المحلي)؛ ربطها بأهداف مبادرة السعودية الخضراء ورؤية 2030؛ لوحة متابعة التقدّم تُراجعها لجنة الاستدامة.",
    "Publish measurable sustainability commitments with baselines and milestones, mapped to national targets, and track progress.")

# ─── M1: Materiality & Stakeholder Engagement ────────────────────────────────
m1 = control(fid, "M1", "Materiality & Stakeholder Engagement",
             "الأهمية النسبية وإشراك الأطراف المعنية",
             "Identify, assess, and prioritise material ESG topics through structured stakeholder engagement and double-materiality analysis.")

sub(m1, "M1-1",
    "Double-Materiality Assessment",
    "تقييم الأهمية النسبية المزدوجة",
    "Documented materiality assessment methodology covering impact materiality and financial materiality; materiality matrix with prioritised topics; data sources and scoring; board/management approval; refresh at least every two years or on significant change.",
    "منهجية موثقة لتقييم الأهمية النسبية تغطّي الأهمية من حيث الأثر والأهمية المالية؛ مصفوفة أهمية نسبية تتضمن الموضوعات المرتّبة حسب الأولوية؛ مصادر البيانات وآلية التقييم؛ اعتماد المجلس أو الإدارة؛ تحديث كل سنتين على الأقل أو عند حدوث تغيّر جوهري.",
    "Conduct a double-materiality assessment to identify and prioritise the ESG topics most significant to the business and its stakeholders.")

sub(m1, "M1-2",
    "Stakeholder Mapping & Engagement",
    "تحديد الأطراف المعنية وإشراكهم",
    "Stakeholder register (investors, regulators, employees, communities, suppliers, customers); engagement plan with channels and frequency; records of engagement activities and feedback; evidence that stakeholder input fed the materiality assessment.",
    "سجل الأطراف المعنية (المستثمرون والجهات التنظيمية والموظفون والمجتمعات والموردون والعملاء)؛ خطة إشراك تتضمن القنوات والوتيرة؛ سجلات أنشطة الإشراك والتغذية الراجعة؛ دليل على إدماج مدخلات الأطراف المعنية في تقييم الأهمية النسبية.",
    "Map stakeholders and engage them systematically, feeding their input into the materiality assessment and strategy.")

sub(m1, "M1-3",
    "Material Topics Register & Review",
    "سجل الموضوعات الجوهرية ومراجعته",
    "Material topics register linking each topic to a topic owner, relevant GRI/ISSB disclosure, target, and KPI; review cycle and last-review date; change log when topics are added/retired following reassessment.",
    "سجل الموضوعات الجوهرية يربط كل موضوع بمالك له وبالإفصاح المرتبط من معايير GRI/ISSB ومستهدف ومؤشر أداء؛ دورة المراجعة وتاريخ آخر مراجعة؛ سجل التغييرات عند إضافة موضوعات أو إلغائها بعد إعادة التقييم.",
    "Maintain a register of material topics mapped to disclosures, owners, and KPIs, and review it on a defined cycle.")

# ─── E1: Environmental — GHG, Energy, Water & Waste ──────────────────────────
e1 = control(fid, "E1", "Environmental — GHG, Energy, Water & Waste",
             "البيئة — انبعاثات الغازات والطاقة والمياه والنفايات",
             "Measure, manage, and reduce greenhouse-gas emissions, energy, water, and waste, with credible reduction targets.")

sub(e1, "E1-1",
    "GHG Inventory — Scope 1 & Scope 2",
    "جرد انبعاثات غازات الدفيئة — النطاق الأول والثاني",
    "GHG inventory for Scope 1 (direct) and Scope 2 (purchased energy) prepared to the GHG Protocol; documented organisational/operational boundaries, emission factors, and activity data; calculation workbook with data lineage; year-on-year comparison.",
    "جرد انبعاثات غازات الدفيئة للنطاق الأول (المباشر) والنطاق الثاني (الطاقة المشتراة) وفق بروتوكول غازات الدفيئة؛ توثيق الحدود التنظيمية والتشغيلية ومعاملات الانبعاث وبيانات النشاط؛ ملف الحسابات مع تتبّع مصدر البيانات؛ المقارنة السنوية.",
    "Compile a GHG inventory for Scope 1 and 2 emissions to the GHG Protocol with documented boundaries, factors, and activity data.")

sub(e1, "E1-2",
    "Scope 3 / Value-Chain & Financed Emissions",
    "النطاق الثالث وانبعاثات سلسلة القيمة والانبعاثات المموَّلة",
    "Scope 3 screening across relevant categories with materiality rationale; methodology for value-chain emissions; for financial institutions, financed-emissions estimate per PCAF with portfolio coverage and data-quality scores; improvement plan for data gaps.",
    "فحص انبعاثات النطاق الثالث عبر الفئات ذات الصلة مع مبررات الأهمية النسبية؛ منهجية احتساب انبعاثات سلسلة القيمة؛ للمؤسسات المالية، تقدير الانبعاثات المموَّلة وفق معيار PCAF مع تغطية المحفظة ودرجات جودة البيانات؛ خطة تحسين لسد فجوات البيانات.",
    "Screen and, where material, quantify Scope 3 and (for financial firms) financed emissions, with a plan to improve data quality.")

sub(e1, "E1-3",
    "Energy Management & Efficiency",
    "إدارة الطاقة وكفاءتها",
    "Energy consumption register (electricity, fuel, renewable share) with intensity metrics; energy-efficiency / renewable initiatives with savings tracking; meter or utility-bill evidence; alignment to Saudi Energy Efficiency Program where applicable.",
    "سجل استهلاك الطاقة (الكهرباء والوقود وحصة الطاقة المتجددة) مع مؤشرات الكثافة؛ مبادرات كفاءة الطاقة أو الطاقة المتجددة مع تتبّع الوفورات؛ أدلة من العدادات أو فواتير المرافق؛ التوافق مع البرنامج السعودي لكفاءة الطاقة عند الاقتضاء.",
    "Track and reduce energy use and increase renewable share, with metered evidence and intensity metrics.")

sub(e1, "E1-4",
    "Water, Waste & Resource Management",
    "إدارة المياه والنفايات والموارد",
    "Water withdrawal/consumption and waste-generation/diversion data by stream; recycling and circular-economy initiatives; hazardous-waste handling and disposal records; compliance with National Center for Environmental Compliance requirements.",
    "بيانات سحب/استهلاك المياه وتوليد/تحويل النفايات حسب النوع؛ مبادرات إعادة التدوير والاقتصاد الدائري؛ سجلات مناولة النفايات الخطرة والتخلص منها؛ الامتثال لمتطلبات المركز الوطني للرقابة على الالتزام البيئي.",
    "Measure and manage water, waste, and resource use, with diversion initiatives and regulatory compliance evidence.")

sub(e1, "E1-5",
    "Emission-Reduction & Net-Zero Targets",
    "مستهدفات خفض الانبعاثات والحياد الكربوني",
    "Documented emission-reduction targets (near-term and net-zero) with baseline year and methodology (e.g., SBTi-aligned); decarbonisation roadmap with initiatives and capex; progress tracking against target trajectory; board endorsement.",
    "مستهدفات موثقة لخفض الانبعاثات (قريبة المدى والحياد الكربوني) مع سنة الأساس والمنهجية (مثل التوافق مع مبادرة الأهداف المبنية على العلم SBTi)؛ خارطة طريق لخفض الكربون تتضمن المبادرات والإنفاق الرأسمالي؛ متابعة التقدّم مقابل المسار المستهدف؛ اعتماد المجلس.",
    "Set and govern credible emission-reduction and net-zero targets with a costed decarbonisation roadmap and progress tracking.")

# ─── C1: Climate Risk — TCFD / ISSB S2 ───────────────────────────────────────
c1 = control(fid, "C1", "Climate Risk & Opportunity (TCFD / ISSB S2)",
             "مخاطر وفرص المناخ (TCFD / المعيار الثاني للمجلس الدولي لمعايير الاستدامة)",
             "Govern, assess, and disclose climate-related risks and opportunities across governance, strategy, risk management, and metrics & targets.")

sub(c1, "C1-1",
    "Climate Governance & Strategy",
    "حوكمة المناخ والاستراتيجية",
    "Documented board and management oversight of climate matters; description of climate-related risks/opportunities and their impact on strategy and financial planning over short/medium/long term; integration into business strategy; TCFD/ISSB-S2 governance disclosure.",
    "توثيق إشراف المجلس والإدارة على الشؤون المناخية؛ وصف المخاطر والفرص المتعلقة بالمناخ وأثرها على الاستراتيجية والتخطيط المالي للآجال القصيرة والمتوسطة والطويلة؛ دمجها في استراتيجية الأعمال؛ إفصاح الحوكمة وفق TCFD/المعيار الثاني للمجلس الدولي لمعايير الاستدامة.",
    "Govern climate as a board/management matter and describe its impact on strategy and financial planning per TCFD/ISSB S2.")

sub(c1, "C1-2",
    "Climate Risk & Opportunity Identification",
    "تحديد مخاطر وفرص المناخ",
    "Climate risk register covering physical (acute/chronic) and transition (policy, technology, market, reputation) risks and opportunities; assessment of likelihood, impact, and time horizon; linkage to the enterprise risk register and ERM methodology.",
    "سجل مخاطر المناخ يغطّي المخاطر المادية (الحادة والمزمنة) ومخاطر التحوّل (السياسات والتقنية والسوق والسمعة) والفرص؛ تقييم الاحتمالية والأثر والأفق الزمني؛ ربطه بسجل مخاطر المؤسسة ومنهجية إدارة المخاطر المؤسسية.",
    "Identify physical and transition climate risks and opportunities and integrate them into enterprise risk management.")

sub(c1, "C1-3",
    "Climate Scenario Analysis",
    "تحليل السيناريوهات المناخية",
    "Scenario analysis using at least one ≤2°C/1.5°C and one higher-warming scenario; documented assumptions, parameters, and data sources; assessment of strategy resilience; results reported to the board; methodology refreshed periodically.",
    "تحليل سيناريوهات يستخدم سيناريو واحدًا على الأقل بحدّ ≤2°م/1.5°م وآخر بارتفاع حراري أعلى؛ توثيق الافتراضات والمعايير ومصادر البيانات؛ تقييم مرونة الاستراتيجية؛ رفع النتائج إلى المجلس؛ تحديث المنهجية دوريًا.",
    "Perform climate scenario analysis (incl. a ≤2°C scenario) to test strategic resilience and disclose assumptions and results.")

sub(c1, "C1-4",
    "Climate Metrics & Targets",
    "مقاييس وأهداف المناخ",
    "Climate metrics disclosed (Scope 1/2/3 emissions, intensity, internal carbon price if used, % assets/revenue exposed to climate risk); targets with baselines and trajectories; methodology notes; year-on-year performance against targets.",
    "مقاييس مناخية مُفصح عنها (انبعاثات النطاق الأول/الثاني/الثالث، الكثافة، سعر الكربون الداخلي إن وُجد، نسبة الأصول/الإيرادات المعرّضة لمخاطر المناخ)؛ أهداف مع سنوات الأساس والمسارات؛ ملاحظات منهجية؛ الأداء السنوي مقابل الأهداف.",
    "Disclose climate metrics and targets with methodology, and report performance against the target trajectory.")

# ─── S1: Social — Workforce, Health & Community ──────────────────────────────
s1 = control(fid, "S1", "Social — Workforce, Health, Human Rights & Community",
             "الجانب الاجتماعي — القوى العاملة والصحة وحقوق الإنسان والمجتمع",
             "Manage workforce, Saudization, health & safety, human rights, and community impact responsibly across operations and supply chain.")

sub(s1, "S1-1",
    "Workforce, Saudization & Diversity",
    "القوى العاملة والسعودة والتنوّع",
    "Workforce metrics (headcount, turnover, gender split, nationalisation/Saudization rate vs. Nitaqat band); diversity & inclusion initiatives; training and development hours per employee; evidence sourced from HR/GOSI/Qiwa systems.",
    "مؤشرات القوى العاملة (عدد الموظفين ومعدل الدوران ونسبة الجنسين ونسبة التوطين/السعودة مقابل نطاق نطاقات)؛ مبادرات التنوّع والشمول؛ ساعات التدريب والتطوير لكل موظف؛ أدلة من أنظمة الموارد البشرية والتأمينات الاجتماعية ومنصة قوى.",
    "Track and disclose workforce, Saudization (Nitaqat), and diversity metrics with development investment, evidenced from HR systems.")

sub(s1, "S1-2",
    "Health, Safety & Wellbeing",
    "الصحة والسلامة والعافية",
    "OHS management system (e.g., ISO 45001) scope and policy; incident metrics (LTIFR, TRIR, fatalities) with trend; safety training and drill records; wellbeing programmes; compliance with Saudi OSH Council requirements.",
    "نطاق وسياسة نظام إدارة الصحة والسلامة المهنية (مثل الأيزو 45001)؛ مؤشرات الحوادث (معدل تكرار الإصابات المفقِدة للوقت والمعدل الكلي للإصابات والوفيات) مع الاتجاه؛ سجلات التدريب والتمارين على السلامة؛ برامج العافية؛ الامتثال لمتطلبات المجلس السعودي للسلامة والصحة المهنية.",
    "Operate an OHS management system, track safety performance, and run wellbeing programmes meeting Saudi OSH requirements.")

sub(s1, "S1-3",
    "Human Rights & Labour Practices (incl. Supply Chain)",
    "حقوق الإنسان وممارسات العمل (بما في ذلك سلسلة التوريد)",
    "Human-rights / labour policy aligned to Saudi Labor Law and ILO core principles; supplier code of conduct with ESG clauses; due-diligence and audit records for high-risk suppliers; grievance mechanism with case log; remediation evidence.",
    "سياسة حقوق الإنسان/العمل متوافقة مع نظام العمل السعودي والمبادئ الأساسية لمنظمة العمل الدولية؛ مدوّنة سلوك للموردين تتضمن بنود الاستدامة؛ سجلات العناية الواجبة والتدقيق للموردين مرتفعي المخاطر؛ آلية للتظلّم مع سجل للحالات؛ أدلة المعالجة.",
    "Uphold human rights and fair labour practices across operations and supply chain, with due diligence and a grievance mechanism.")

sub(s1, "S1-4",
    "Community Investment & Social Impact",
    "الاستثمار المجتمعي والأثر الاجتماعي",
    "Community investment / CSR programme with spend, beneficiaries, and outcomes; local-content and SME-enablement contribution; alignment to Vision 2030 social objectives; impact measurement methodology and reporting.",
    "برنامج الاستثمار المجتمعي/المسؤولية الاجتماعية مع الإنفاق والمستفيدين والمخرجات؛ مساهمة المحتوى المحلي وتمكين المنشآت الصغيرة والمتوسطة؛ التوافق مع الأهداف الاجتماعية لرؤية 2030؛ منهجية قياس الأثر وإعداد التقارير.",
    "Invest in communities and local content with measured social outcomes aligned to national social objectives.")

# ─── D1: Disclosure, Reporting & Assurance ───────────────────────────────────
d1 = control(fid, "D1", "ESG Disclosure, Reporting & Assurance",
             "الإفصاح عن الاستدامة وإعداد التقارير والتحقق",
             "Produce credible, comparable ESG disclosures with sound data governance, external assurance, and regulatory submission.")

sub(d1, "D1-1",
    "Annual Sustainability / ESG Report",
    "تقرير الاستدامة السنوي",
    "Published annual sustainability/ESG report prepared to a recognised standard (GRI / ISSB / Saudi Exchange ESG Guidelines); index mapping disclosures to standard references; board approval; comparable prior-year data.",
    "تقرير استدامة سنوي منشور مُعدّ وفق معيار معترف به (GRI / المجلس الدولي لمعايير الاستدامة / إرشادات تداول السعودية للإفصاح عن الاستدامة)؛ فهرس يربط الإفصاحات بمراجع المعيار؛ اعتماد المجلس؛ بيانات مقارنة للسنة السابقة.",
    "Publish a board-approved annual ESG report to a recognised standard, with a disclosure index and comparable data.")

sub(d1, "D1-2",
    "ESG Data Governance & Controls",
    "حوكمة بيانات الاستدامة وضوابطها",
    "ESG data governance: defined data owners, source systems, calculation methodologies, and controls over collection/aggregation; audit trail and version control for reported figures; restatement policy; controls tested before publication.",
    "حوكمة بيانات الاستدامة: ملّاك بيانات محدّدون وأنظمة مصدرية ومنهجيات حساب وضوابط على الجمع والتجميع؛ مسار تدقيق وضبط إصدارات للأرقام المُفصح عنها؛ سياسة إعادة العرض؛ اختبار الضوابط قبل النشر.",
    "Govern ESG data with defined owners, methodologies, audit trail, and controls tested prior to disclosure.")

sub(d1, "D1-3",
    "External Assurance of ESG Data",
    "التحقق الخارجي من بيانات الاستدامة",
    "Independent assurance engagement over selected ESG/GHG metrics (limited or reasonable) per ISAE 3000 / ISAE 3410; assurance statement and scope; management response to findings; plan to expand assurance coverage over time.",
    "ارتباط تحقق مستقل على مقاييس مختارة للاستدامة/الانبعاثات (محدود أو معقول) وفق المعيار الدولي للتكليفات بالتأكيد ISAE 3000 / ISAE 3410؛ بيان التحقق ونطاقه؛ استجابة الإدارة للملاحظات؛ خطة لتوسيع نطاق التحقق تدريجيًا.",
    "Obtain independent external assurance over key ESG/GHG metrics and act on assurance findings.")

sub(d1, "D1-4",
    "Regulatory ESG Disclosure Submission (CMA / Saudi Exchange)",
    "تقديم الإفصاح التنظيمي عن الاستدامة (هيئة السوق المالية / تداول السعودية)",
    "Evidence of ESG disclosures submitted/filed per CMA and Saudi Exchange (Tadawul) requirements and timelines; mapping to the Tadawul ESG metric set; record of any comply-or-explain positions; submission confirmations retained.",
    "دليل على تقديم/إيداع إفصاحات الاستدامة وفق متطلبات وأطر زمنية هيئة السوق المالية وتداول السعودية؛ الربط بمجموعة مؤشرات الاستدامة الخاصة بتداول؛ سجل لأي حالات «الالتزام أو التوضيح»؛ الاحتفاظ بتأكيدات التقديم.",
    "File ESG disclosures with the CMA and Saudi Exchange on time, mapped to the Tadawul metric set, retaining submission evidence.")

# ─── Commit & Summary ─────────────────────────────────────────────────────────
conn.commit()

cur.execute("SELECT COUNT(*) FROM evidence.controls WHERE framework_id=? AND is_active=1", fid)
nc = int(cur.fetchone()[0])
cur.execute("""SELECT COUNT(*), SUM(CASE WHEN evidence_guidance IS NOT NULL AND evidence_guidance_ar IS NOT NULL THEN 1 ELSE 0 END)
               FROM evidence.sub_controls WHERE control_id IN (SELECT id FROM evidence.controls WHERE framework_id=?) AND is_active=1""", fid)
ns, nbil = cur.fetchone()
print(f"[ESG-CMA-TADAWUL-1] Seeded OK — fid={fid}, {nc} controls, {ns} sub-controls, {nbil}/{ns} fully bilingual (EN+AR)")
conn.close()
