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

# ─── Framework ────────────────────────────────────────────────────────────────
fid = framework(
    "BCM-ISO22301-SAMA",
    "Business Continuity Management (ISO 22301 + SAMA BCM + NCEMA)",
    "إدارة استمرارية الأعمال (الأيزو 22301 وإطار البنك المركزي السعودي والمركز الوطني لإدارة الطوارئ)",
    "2019/2022",
    "ISO / SAMA / NCEMA",
    "Integrated BCM framework combining ISO 22301:2019 management-system requirements, SAMA Business Continuity Framework obligations for Saudi financial institutions, and NCEMA national emergency coordination mandates. Covers context setting, leadership commitment, BIA, risk assessment, strategy selection, plan development, exercising, performance evaluation, and continual improvement — practitioner SME mapping; structure aligned to the published standard/framework, control text paraphrased (not verbatim)."
)

# ─── Clause 4: Context of the Organisation ────────────────────────────────────
c4 = control(fid, "4", "Context of the Organisation",
             "سياق المنظمة",
             "Establish internal/external context, understand stakeholder needs, and define the BCM scope.")

sub(c4, "4-1",
    "Internal & External Issues Inventory",
    "جرد العوامل الداخلية والخارجية المؤثرة في الاستمرارية",
    "PESTLE / SWOT analysis document listing internal capabilities and external threats relevant to BCM; annual review record signed by BCM owner",
    "Identify and document factors that affect the organisation's ability to achieve BCM outcomes.")

sub(c4, "4-2",
    "Interested-Party Needs & Expectations",
    "تحديد توقعات الأطراف المعنية واحتياجاتها",
    "Stakeholder register with identified BCM obligations (regulatory, contractual, reputational); documented review cycle; evidence of regulatory requirements captured (SAMA, NCA, NCEMA)",
    "Determine relevant interested parties and their BCM-related requirements and legal/regulatory obligations.")

sub(c4, "4-3",
    "BCM Scope Definition & Boundaries",
    "تحديد نطاق إدارة استمرارية الأعمال وحدوده",
    "Approved BCM scope statement referencing organisational boundaries, locations, products/services, and exclusions; board or executive sign-off minute",
    "Define and document the scope of the BCMS, including which sites, services, and processes are included or excluded.")

sub(c4, "4-4",
    "BCMS & Its Processes",
    "نظام إدارة استمرارية الأعمال وعملياته",
    "Process map or RACI chart for the BCMS lifecycle; documented interaction between BCM processes and the broader management system; version-controlled BCMS manual or programme charter",
    "Establish, implement, maintain, and continually improve the BCMS in line with defined scope.")

sub(c4, "4-5",
    "Determination of Critical Activities & Dependencies",
    "تحديد الأنشطة الحرجة والتبعيات التشغيلية",
    "Critical-activity register with upstream/downstream dependencies (people, technology, third parties, facilities); linkage to BIA output; reviewed and approved by department heads",
    "Identify the organisation's critical activities and the internal/external resources and dependencies they rely upon.")

# ─── Clause 5: Leadership & BCM Policy ───────────────────────────────────────
c5 = control(fid, "5", "Leadership & BCM Policy",
             "القيادة وسياسة استمرارية الأعمال",
             "Demonstrate top-management commitment, establish BCM policy, and assign roles, responsibilities, and authorities.")

sub(c5, "5-1",
    "Top Management Commitment & Accountability",
    "التزام الإدارة العليا ومساءلتها في استمرارية الأعمال",
    "Board resolution or executive committee minute mandating BCM programme; annual BCM performance report presented to board; evidence of budget allocation for BCM activities",
    "Top management must demonstrate leadership and commitment to the BCMS through active sponsorship, resource allocation, and accountability.")

sub(c5, "5-2",
    "BCM Policy — Content & Approval",
    "سياسة استمرارية الأعمال — المحتوى والاعتماد",
    "Approved BCM policy document (with version history) covering objectives, scope, regulatory alignment, review cycle, and consequence of non-compliance; board/CEO sign-off minute; published policy accessible to all staff",
    "Establish a BCM policy that sets direction, reflects regulatory obligations, is communicated, and is reviewed at defined intervals.")

sub(c5, "3",
    "BCM Roles, Responsibilities & Authorities",
    "أدوار استمرارية الأعمال ومسؤولياتها وصلاحياتها",
    "BCMS RACI matrix covering BCM owner, crisis management team (CMT), business continuity coordinator per BU, IT DR lead, and communications officer; documented in policy or separate governance charter; updated upon org changes",
    "Assign and communicate BCM roles, responsibilities, and authorities to ensure the BCMS operates effectively.")

sub(c5, "5-4",
    "BCM Objectives Alignment with Strategic Direction",
    "توافق أهداف استمرارية الأعمال مع التوجه الاستراتيجي",
    "BCM objectives documented in the annual BCM plan and traceable to the organisation's strategic objectives; KPIs defined per objective; progress reviewed at management review",
    "Ensure BCM objectives are consistent with the organisation's strategy, measurable, communicated, and monitored.")

# ─── Clause 6: Planning ───────────────────────────────────────────────────────
c6 = control(fid, "6", "Planning (Objectives, Risks & Opportunities)",
             "التخطيط (الأهداف والمخاطر والفرص)",
             "Address risks and opportunities, establish BCM objectives, and plan how to achieve them.")

sub(c6, "6-1",
    "Actions to Address Risks & Opportunities",
    "إجراءات معالجة المخاطر والفرص في استمرارية الأعمال",
    "BCM risk register identifying risks to achieving BCMS outcomes; documented risk treatment plan; linkage to enterprise risk register; reviewed at least annually and after significant incidents",
    "Determine BCM-related risks and opportunities, and integrate actions into BCMS processes.")

sub(c6, "6-2",
    "BCM Objectives & Planning to Achieve Them",
    "أهداف استمرارية الأعمال وخطط تحقيقها",
    "Documented BCM objectives with owner, target date, success measure, and resource requirements; embedded in annual BCM programme plan; progress tracked in management-review dashboard",
    "Establish measurable BCM objectives at relevant functions and levels, and plan the means to achieve them.")

sub(c6, "6-3",
    "Change Management within the BCMS",
    "إدارة التغيير داخل نظام إدارة استمرارية الأعمال",
    "Change-management log for BCMS updates (policy, scope, plans); impact-assessment template applied before changes take effect; communication of changes to relevant stakeholders on record",
    "Plan and manage changes to the BCMS in a controlled manner, assessing impacts on BC capability before implementation.")

# ─── Clause 7: Support ────────────────────────────────────────────────────────
c7 = control(fid, "7", "Support (Resources, Competence, Awareness, Communication, Documented Information)",
             "الدعم (الموارد والكفاءة والتوعية والتواصل والمعلومات الموثقة)",
             "Provide and manage the resources, competencies, awareness, communication, and documented information needed for an effective BCMS.")

sub(c7, "7-1",
    "BCM Resource Adequacy",
    "كفاية موارد استمرارية الأعمال",
    "Approved BCM budget line in annual financial plan; resource inventory (staff, alternate sites, tooling, vendors); gap analysis reviewed by BCM owner and signed off by executive sponsor",
    "Ensure adequate human, financial, and infrastructure resources are allocated to establish and sustain the BCMS.")

sub(c7, "7-2",
    "BCM Competence & Training Records",
    "كفاءة العاملين في استمرارية الأعمال وسجلات التدريب",
    "Competency framework for BCM roles (BCM coordinator, CMT members, IT DR staff); training completion records (classroom, tabletop, e-learning); skills gap assessment with closure plan; annual refresher schedule",
    "Determine necessary BCM competencies, ensure staff are trained, and retain evidence of competence.")

sub(c7, "7-3",
    "BCM Awareness Programme",
    "برنامج التوعية باستمرارية الأعمال",
    "Annual BCM awareness calendar (campaigns, intranet articles, tabletop teasers); completion tracking per department; post-awareness survey results; awareness content approved by BCM owner",
    "Ensure all relevant personnel are aware of the BCM policy, their role in a disruptive incident, and the consequences of non-conformity.")

sub(c7, "7-4",
    "BCM Communication Plan (Internal & External)",
    "خطة التواصل في استمرارية الأعمال (الداخلية والخارجية)",
    "Documented communication plan covering: what to communicate, when, to whom (staff, customers, regulators, media), via which channel, and who is authorised spokesperson; tested in at least one exercise per year; escalation thresholds defined",
    "Plan and implement internal and external communication that supports effective BCM response and regulatory notification.")

sub(c7, "7-5",
    "Documented Information — Control & Retention",
    "ضبط المعلومات الموثقة والاحتفاظ بها",
    "Document control procedure covering naming convention, version control, approval workflow, distribution list, retention periods, and secure off-site/cloud backup of BCM documents; document register reviewed quarterly",
    "Establish and maintain documented information required by ISO 22301, ensure availability, protect integrity, and control access.")

# ─── Clause 8-2: Business Impact Analysis ────────────────────────────────────
c82 = control(fid, "8-2", "Business Impact Analysis (RTO / RPO / MTPD / MBCO per Critical Service)",
              "تحليل تأثير الأعمال (وقت الاسترداد المستهدف ونقطة الاسترداد والحد الأقصى لفترة التوقف والحد الأدنى من الأعمال للخدمة الحرجة)",
              "Systematically assess the impact of disruptions on critical activities and establish recovery time and continuity objectives.")

sub(c82, "8-2-1",
    "BIA Methodology & Governance",
    "منهجية تحليل تأثير الأعمال وحوكمته",
    "Approved BIA methodology document defining impact criteria (financial, operational, reputational, regulatory), time horizons, scoring scale, and review frequency; signed off by BCM owner and executive sponsor",
    "Define and approve a consistent BIA methodology covering impact dimensions, measurement criteria, and governance.")

sub(c82, "8-2-2",
    "BIA Worksheets per Critical Activity / Service",
    "أوراق عمل تحليل التأثير لكل نشاط حرج وخدمة حرجة",
    "Completed BIA worksheets for each critical service/process: owner, description, dependencies, impact ratings per time horizon, agreed RTO/RPO/MTPD/MBCO; signed off by process owner and BCM coordinator; version-dated",
    "Conduct BIA for each critical activity to quantify disruption impacts over time and establish prioritised recovery objectives.")

sub(c82, "8-2-3",
    "RTO / RPO / MTPD / MBCO Definition & Sign-off",
    "تحديد أهداف وقت الاسترداد ونقطة الاسترداد والمدة القصوى للانقطاع والحد الأدنى لمستوى الأعمال واعتمادها",
    "Consolidated RTO/RPO/MTPD/MBCO register per critical service with executive sign-off; alignment evidence to SAMA impact-tolerance statement; annual review record with change log",
    "Formally define and document RTO, RPO, MTPD, and MBCO for each critical activity, and obtain executive approval.")

sub(c82, "8-2-4",
    "Critical Resource Requirements Identification",
    "تحديد متطلبات الموارد الحرجة اللازمة للاسترداد",
    "Resource dependency matrix per critical activity covering staff (minimum numbers and skills), IT systems, data, facilities, third-party services, and utilities; gaps between minimum requirements and alternate-site capacity documented",
    "Identify the minimum resources (people, technology, facilities, supply chain) required to meet recovery objectives.")

sub(c82, "8-2-5",
    "BIA Review Cycle & Trigger-based Update",
    "دورة مراجعة تحليل التأثير والتحديث المبني على المحفزات",
    "Documented BIA review policy (annual minimum + triggered by material change — new product, M&A, technology refresh, incident); review completion records; change log entries linked to triggering events",
    "Review and update BIA outputs at least annually and whenever material changes occur to the operating environment or product/service profile.")

# ─── Clause 8-3: Risk Assessment ─────────────────────────────────────────────
c83 = control(fid, "8-3", "Risk Assessment",
              "تقييم مخاطر استمرارية الأعمال",
              "Identify, analyse, and evaluate threats that could cause disruption to critical activities.")

sub(c83, "8-3-1",
    "BCM Threat Identification & Scenario Library",
    "تحديد تهديدات استمرارية الأعمال ومكتبة السيناريوهات",
    "Documented threat/scenario library (natural disasters, cyber incidents, power outages, pandemics, civil unrest, supplier failure, key-person dependency) tailored to Saudi operating context; reviewed by risk management and BCM teams",
    "Systematically identify threats and scenarios that could disrupt critical activities, drawing on the organisation's context and NCEMA national-risk profile.")

sub(c83, "8-3-2",
    "BCM Risk Register — Likelihood, Impact & Rating",
    "سجل مخاطر استمرارية الأعمال — الاحتمالية والتأثير والتقييم",
    "BCM risk register with threat scenarios rated on likelihood × impact matrix; risk owners assigned; residual-risk rating after existing controls; linked to BIA critical-activity priorities; maintained in GRC system or controlled spreadsheet",
    "Analyse and evaluate identified BCM risks using a defined methodology, producing a prioritised risk register.")

sub(c83, "8-3-3",
    "Risk Treatment Decisions & Control Mapping",
    "قرارات معالجة مخاطر الاستمرارية وربطها بالضوابط",
    "Risk treatment register showing accept/mitigate/transfer/avoid decisions per risk; controls mapped to each risk; residual-risk acceptances signed by risk owner and BCM owner; escalation to risk committee for high-rated residuals",
    "Select and document risk treatment options for BCM risks, assign owners, and track treatment implementation.")

sub(c83, "8-3-4",
    "Third-Party & Supply-Chain BCM Risk",
    "مخاطر استمرارية الأعمال للأطراف الثالثة وسلسلة التوريد",
    "Third-party BCM risk assessment (critical suppliers/vendors); contractual BCM requirements (RTO SLAs, DR testing obligations, notification requirements); evidence of supplier BCM attestations or audit results; contingency sourcing plan for critical single-source suppliers",
    "Assess and manage BCM risks arising from third-party dependencies and supply-chain vulnerabilities.")

# ─── Clause 8-4: Business Continuity Strategies & Solutions ─────────────────
c84 = control(fid, "8-4", "Business Continuity Strategies & Solutions",
              "استراتيجيات وحلول استمرارية الأعمال",
              "Select and implement continuity strategies that meet recovery objectives for critical activities.")

sub(c84, "8-4-1",
    "Strategy Options Analysis & Selection",
    "تحليل خيارات الاستراتيجية واختيارها",
    "Recovery strategy options paper per critical activity covering: do nothing, manual workaround, alternate site (warm/hot/cold), work-from-home, mutual-aid agreement, cloud failover; cost-benefit analysis; selected strategy approved by executive sponsor",
    "Evaluate multiple continuity strategy options for each critical activity and formally select strategies that meet RTO/RPO/MTPD/MBCO.")

sub(c84, "8-4-2",
    "Resource Provision for Recovery Strategies",
    "توفير الموارد اللازمة لتنفيذ استراتيجيات الاسترداد",
    "Alternate-site configuration records (seats, connectivity, licences, equipment); DR infrastructure build/test records; WFH toolkit inventory; SLA agreements with alternate-site or cloud providers; budget allocation for strategy implementation",
    "Ensure resources required to implement chosen strategies are procured, configured, tested, and maintained.")

sub(c84, "8-4-3",
    "IT Disaster Recovery Strategy & Architecture",
    "استراتيجية التعافي من كوارث تقنية المعلومات وبنيتها",
    "IT DR strategy document covering RPO/RTO per system tier; DR architecture diagram (primary–DR site replication topology); data backup and restore policy with test records; cloud-based failover design where applicable; aligned to SAMA technology risk requirements",
    "Define and implement an IT DR strategy that meets recovery objectives for all technology assets supporting critical activities.")

sub(c84, "8-4-4",
    "People Continuity & Key-Person Risk Mitigation",
    "استمرارية الكفاءات البشرية وتخفيف مخاطر الاعتماد على الأفراد الرئيسيين",
    "Key-person risk register; documented succession/cover plans for critical roles; cross-training records; contact tree (alternate contacts per role); HR emergency-response roster; evidence of succession plan tests in tabletop exercises",
    "Address risks from loss of key personnel through succession planning, cross-training, and workforce redundancy measures.")

sub(c84, "8-4-5",
    "Telecommunications & Communication Redundancy",
    "التكرار في الاتصالات والتواصل",
    "Network resilience design document (dual ISP, MPLS + internet failover, satellite backup); tested failover scenario records; out-of-band communication tools inventory (satellite phones, emergency messaging platform); SAMA notification channel readiness confirmation",
    "Ensure redundant telecommunications and communication capabilities are in place to support response and recovery operations.")

# ─── Clause 8-5: BC Plans & Incident Response ────────────────────────────────
c85 = control(fid, "8-5", "BC Plans & Incident Response Structure",
              "خطط استمرارية الأعمال وهيكل الاستجابة للحوادث",
              "Develop, document, and maintain BC plans and an incident response structure that activates and coordinates recovery.")

sub(c85, "8-5-1",
    "Business Continuity Plan (BCP) — Structure & Content",
    "خطة استمرارية الأعمال — الهيكل والمحتوى",
    "Approved master BCP document covering: purpose, scope, activation criteria, CMT structure, escalation chain, communication plan, recovery procedures per critical activity; version-controlled; off-site and offline copy maintained; last review date evidenced",
    "Develop and maintain a BCP that clearly defines activation thresholds, roles, communication procedures, and recovery actions for each critical activity.")

sub(c85, "8-5-2",
    "Crisis Management Plan & Command Structure",
    "خطة إدارة الأزمات وهيكل القيادة",
    "Crisis Management Plan defining CMT composition (Gold/Silver/Bronze or equivalent), decision-making authorities, crisis room setup, spokesperson designation, escalation matrix, and stand-down procedures; CMT contact list (current); rehearsed in CMT tabletop at least annually",
    "Establish a crisis management plan and command structure for declaring and managing a disruptive incident from initial response through recovery.")

sub(c85, "8-5-3",
    "IT Disaster Recovery Runbooks",
    "أدلة تشغيل التعافي من كوارث تقنية المعلومات",
    "Step-by-step DR runbooks per critical system/application covering: failover trigger, pre-conditions, numbered recovery steps, validation tests, roll-back procedure, and responsible team; runbooks version-controlled and accessible in DR site or cloud vault; last tested date recorded",
    "Develop detailed IT DR runbooks for each critical system that provide step-by-step recovery instructions, validation checks, and rollback procedures.")

sub(c85, "8-5-4",
    "Incident Notification & Escalation Procedures",
    "إجراءات إخطار الحوادث والتصعيد",
    "Documented incident notification matrix defining who to notify (internal tiers, regulators, customers, media) based on incident severity/type/duration; escalation timeline (e.g., SAMA notification within X hours); template notification messages approved; tested in exercises",
    "Define and document escalation and notification procedures for disruptive incidents, including regulatory and customer notification thresholds and timelines.")

sub(c85, "8-5-5",
    "Recovery Site & Remote-Working Activation Procedures",
    "إجراءات تفعيل موقع الاسترداد والعمل عن بُعد",
    "Recovery site activation checklist (step-by-step with owners and timing); remote-working activation guide (VPN capacity, device policy, application access confirmation); travel/logistics plan for key staff relocation; site readiness assessment records",
    "Document clear procedures for activating recovery sites or remote-working arrangements, including readiness checks and logistics.")

sub(c85, "8-5-6",
    "Post-Incident Recovery & Return-to-Normal Procedures",
    "إجراءات الاسترداد بعد الحادث والعودة إلى الوضع الطبيعي",
    "Stand-down and return-to-normal procedures covering: recovery verification criteria, data-integrity checks, system sign-off, primary-site reinstatement, lessons-learned trigger; documented in BCP appendix; reviewed after each activation",
    "Establish and document procedures for transitioning from recovery mode back to normal operations, including data reconciliation and sign-off.")

# ─── Clause 8-6: Exercising & Testing ────────────────────────────────────────
c86 = control(fid, "8-6", "Exercising & Testing",
              "اختبار الخطط وتمارين استمرارية الأعمال",
              "Validate BCM capability through a programme of exercises and technical tests that cover all critical activities and plans.")

sub(c86, "8-6-1",
    "Exercise Programme — Schedule & Scope",
    "برنامج التمارين — الجدول الزمني والنطاق",
    "Annual BCM exercise programme document covering: exercise types (awareness walk-through, tabletop, simulation, full failover), schedule, objectives, scope per exercise, responsible owners; approved by BCM governance body; communicated to relevant departments",
    "Develop and approve an annual BCM exercise programme that covers all critical activities, recovery strategies, and CMT capabilities.")

sub(c86, "8-6-2",
    "Tabletop & Simulation Exercise Records",
    "سجلات التمارين المكتبية ومحاكاة الحوادث",
    "Tabletop/simulation exercise records including: scenario description, participants, observations/findings, actions raised; after-action report (AAR) signed by exercise lead; improvement items logged in BCM action tracker; evidence of closure",
    "Conduct and document tabletop and simulation exercises, producing after-action reports with improvement actions tracked to closure.")

sub(c86, "8-6-3",
    "Technical DR Failover Test Records",
    "سجلات اختبارات الفشل التقني في التعافي من الكوارث",
    "DR failover test plans and results per critical system: test objectives, RPO/RTO achieved vs. target, pass/fail per test step, defects found, remediation completed, sign-off by IT DR lead and business owner; tested at minimum annually (SAMA requirement: semi-annually for critical systems)",
    "Execute and document technical DR failover tests for all critical systems, measuring actual RTO/RPO against targets and remediating gaps.")

sub(c86, "8-6-4",
    "Exercise Findings & Improvement Tracking",
    "نتائج التمارين وتتبع التحسينات",
    "BCM improvement log consolidating findings from all exercises and tests; each item with owner, priority, due date, and status; reviewed monthly by BCM coordinator; presented to management review; trend analysis for recurring themes",
    "Capture, prioritise, and track to closure all improvement actions arising from exercises and tests.")

sub(c86, "8-6-5",
    "SAMA-Mandated Cyber-Resilience & Scenario Testing",
    "الاختبارات الإلزامية بموجب إطار البنك المركزي للمرونة الإلكترونية والسيناريوهات",
    "Cyber-resilience test plan and results (threat-led penetration testing, ransomware recovery simulation, SWIFT environment DR); SAMA scenario test evidence (e.g., simultaneous site loss + cyber incident dual-scenario); results shared with CISO and BCM owner; regulatory submission where required",
    "Meet SAMA's requirement to conduct scenario-based and cyber-resilience tests covering the intersection of cyber incidents and business continuity.")

# ─── Clause 9: Performance Evaluation & Management Review ────────────────────
c9 = control(fid, "9", "Performance Evaluation & Management Review",
             "تقييم الأداء ومراجعة الإدارة",
             "Monitor, measure, analyse, and evaluate BCM performance, and conduct formal management reviews.")

sub(c9, "9-1",
    "BCM Monitoring & Measurement",
    "مراقبة وقياس أداء استمرارية الأعمال",
    "BCM KPI dashboard covering: % BCPs tested per schedule, RTO/RPO achievement in tests, open improvement-action closure rate, BIA currency (age), staff awareness completion rate, exercise frequency compliance; reviewed quarterly by BCM owner",
    "Define BCM performance indicators, collect data, and report results to monitor BCMS effectiveness.")

sub(c9, "9-2",
    "Internal BCM Audit",
    "التدقيق الداخلي لنظام إدارة استمرارية الأعمال",
    "Annual internal BCM audit plan; audit reports with findings, nonconformity ratings, and corrective-action requirements; evidence of auditor independence (not auditing own work); management response to findings; closure evidence for major NCs",
    "Conduct periodic internal audits of the BCMS to provide objective assurance on conformity and effectiveness.")

sub(c9, "9-3",
    "Management Review — Inputs, Outputs & Minutes",
    "مراجعة الإدارة — المدخلات والمخرجات والمحاضر",
    "Management review meeting minutes (at minimum annual) covering mandatory inputs: audit results, exercise findings, BCM performance KPIs, changes to context/risks, regulatory feedback, corrective actions status; documented decisions and action items with owners and dates",
    "Hold formal management reviews of the BCMS at planned intervals, covering all required inputs, and record decisions and improvement actions.")

sub(c9, "9-4",
    "Regulatory & Certification Compliance Tracking",
    "تتبع الامتثال للمتطلبات التنظيمية وشهادات الاعتماد",
    "Compliance register mapping BCMS controls to ISO 22301:2019, SAMA BCM Framework, and NCEMA requirements; compliance status (compliant/partial/non-compliant) per requirement; evidence links; annual refresh; gap closure plan for partials and non-conformities",
    "Track and evidence compliance with all applicable BCM regulatory and certification requirements, and manage identified gaps.")

# ─── Clause 10: Improvement / Corrective Action ──────────────────────────────
c10 = control(fid, "10", "Improvement / Corrective Action",
              "التحسين والإجراءات التصحيحية",
              "Manage nonconformities, implement corrective actions, and drive continual improvement of the BCMS.")

sub(c10, "10-1",
    "Nonconformity & Corrective Action Process",
    "عملية معالجة عدم المطابقة والإجراءات التصحيحية",
    "Nonconformity log with root-cause analysis, corrective action plan, owner, target date, and verification of effectiveness; linkage to audit, exercise, and incident findings; management review of overdue items; trend analysis for systemic issues",
    "Establish a process to identify, record, analyse root causes of, and correct BCM nonconformities, and verify effectiveness of corrective actions.")

sub(c10, "10-2",
    "Continual Improvement — Opportunities & Innovations",
    "التحسين المستمر — الفرص والابتكارات",
    "Annual BCMS improvement roadmap capturing lessons from exercises, incidents, regulatory changes, and benchmarking; innovation items (e.g., automation of DR failover, AI-based incident detection); approved by BCM owner; progress tracked quarterly",
    "Proactively identify and pursue opportunities for improving BCMS effectiveness beyond corrective action, incorporating lessons learned and emerging best practices.")

sub(c10, "10-3",
    "Lessons Learned from Actual Incidents",
    "الدروس المستفادة من الحوادث الفعلية",
    "Post-incident review (PIR) report for each BCM activation (including near-misses): timeline reconstruction, root cause, plan effectiveness assessment, gaps identified, improvement actions raised; PIR completed within 30 days of stand-down; findings fed into BIA and risk assessment updates",
    "Conduct structured post-incident reviews after every BCM activation or near-miss, and translate findings into documented improvements to plans, strategies, and the BCMS.")

# ─── Clause S1: SAMA-Specific Requirements ───────────────────────────────────
cs1 = control(fid, "S1", "SAMA-Specific BCM Obligations",
              "متطلبات البنك المركزي السعودي الخاصة باستمرارية الأعمال",
              "Obligations specific to the SAMA Business Continuity Framework for Saudi-licensed financial institutions, including board oversight, impact tolerance, critical-operations identification, and regulatory reporting.")

sub(cs1, "S1-1",
    "Board & Senior Management BCM Oversight",
    "إشراف مجلس الإدارة والإدارة العليا على استمرارية الأعمال",
    "Board-approved BCM policy and annual BCM report; board or risk-committee agenda item on BCM at least annually; documented board-level escalation path for BCM activations exceeding defined thresholds; board attestation to SAMA where required",
    "Demonstrate that the board and senior management actively oversee BCM, approve strategy, review performance, and are informed of material BCM events per SAMA BCM Framework.")

sub(cs1, "S1-2",
    "Impact Tolerance Statement for Critical Operations",
    "بيان تحمل التأثير للعمليات الحرجة",
    "Formal impact tolerance statement per critical operation specifying the maximum tolerable level of disruption (time and degradation); methodology used; board approval evidence; alignment with BIA-derived MTPD/MBCO; annual review and SAMA submission record",
    "Define, document, and obtain board approval for impact tolerances for each critical operation as required by SAMA, and evidence annual review.")

sub(cs1, "S1-3",
    "Critical Operations Identification & Prioritisation",
    "تحديد العمليات الحرجة وترتيب أولوياتها",
    "SAMA-aligned critical-operations register listing services that, if disrupted, would cause material harm to customers, financial stability, or regulatory compliance; prioritisation rationale; linkage to BIA; reviewed annually and after material business changes",
    "Identify and maintain a register of critical operations per SAMA definitions, prioritised by potential harm to the financial system.")

sub(cs1, "S1-4",
    "Scenario Testing — SAMA-Mandated Scenarios",
    "اختبارات السيناريوهات المطلوبة من البنك المركزي السعودي",
    "Annual scenario test schedule including SAMA-specified severe-but-plausible scenarios (e.g., data-centre complete loss, cyber attack, simultaneous pandemic + cyber, payment-system failure); test reports with RTO/RPO achieved; SAMA submission of results where required; senior-management sign-off",
    "Execute SAMA-mandated BCM scenario tests at least annually, covering severe-but-plausible scenarios, and report results to SAMA as required.")

sub(cs1, "S1-5",
    "Regulator Notification — Incidents & Material Changes",
    "إخطار الجهات التنظيمية عند الحوادث والتغييرات الجوهرية",
    "Documented SAMA notification procedure covering: trigger criteria (incident type, duration, customer impact threshold), notification timeline (e.g., initial notice within 2 hours, follow-up within 24 hours), responsible officer, template notification messages; notification log for all incidents; post-incident regulatory report records",
    "Establish and follow documented procedures for notifying SAMA of BCM activations, material disruptions, and significant changes to BCM arrangements within prescribed timeframes.")

sub(cs1, "S1-6",
    "Cyber Resilience Integration with BCM",
    "دمج المرونة الإلكترونية مع استمرارية الأعمال",
    "Documented integration between BCM and cyber-incident response (shared escalation criteria, joint CMT activation triggers, shared communication plan); ransomware recovery playbook tested at least annually; evidence of NCA and SAMA cybersecurity framework alignment within BCM plans",
    "Ensure BCM and cybersecurity incident response are integrated, with shared escalation, joint exercises, and cyber-specific recovery runbooks meeting SAMA and NCA requirements.")

sub(cs1, "S1-7",
    "SAMA Self-Assessment & Independent Review Submission",
    "التقييم الذاتي لمتطلبات البنك المركزي والمراجعة المستقلة",
    "Completed SAMA BCM Framework self-assessment with evidence references per requirement; independent (internal audit or external assurance) review of BCM capability at SAMA-specified frequency; submission to SAMA on schedule; management response to review findings",
    "Complete SAMA BCM Framework self-assessments, commission independent reviews as required, and submit results to SAMA within prescribed timelines.")

# ─── Clause N1: NCEMA Alignment ──────────────────────────────────────────────
cn1 = control(fid, "N1", "NCEMA Alignment — National Crisis-Management Coordination & Emergency Response",
              "توافق مع المركز الوطني لإدارة الطوارئ — التنسيق الوطني لإدارة الأزمات والاستجابة للطوارئ",
              "Requirements for aligning organisational BCM with NCEMA's national crisis-management and emergency-response framework.")

sub(cn1, "N1-1",
    "NCEMA Interface & Coordination Roles",
    "واجهة التنسيق مع المركز الوطني لإدارة الطوارئ وأدوار التنسيق",
    "Documented NCEMA liaison role within CMT; contact directory for NCEMA and relevant national emergency-management authorities; organisation's place in sector/national response hierarchy documented; briefing notes/registration evidence with NCEMA if required by sector",
    "Define organisational interface with NCEMA, assign liaison responsibilities, and document how the organisation fits within the national crisis-management structure.")

sub(cn1, "N1-2",
    "Alignment with National Emergency Response Plans",
    "التوافق مع خطط الاستجابة الوطنية للطوارئ",
    "Gap assessment mapping organisational BCP to relevant national emergency plans (sector contingency plans, NCEMA national crisis plan); alignment actions documented and tracked; BCM activation procedures reference national coordination protocols",
    "Ensure organisational BC plans are consistent with and support national emergency response plans and sector contingency frameworks.")

sub(cn1, "N1-3",
    "Participation in National & Sector BCM Exercises",
    "المشاركة في التمارين الوطنية وتمارين الاستمرارية على مستوى القطاع",
    "Participation records in NCEMA or sector-level BCM exercises (invitation, attendance confirmation, exercise report, lessons-learned input); internal debrief incorporating national-exercise findings; improvements actioned and evidenced",
    "Participate in NCEMA-coordinated or sector-level BCM exercises and incorporate national exercise findings into organisational BCM improvement programmes.")

sub(cn1, "N1-4",
    "Incident Reporting to NCEMA & National Authorities",
    "الإبلاغ عن الحوادث للمركز الوطني لإدارة الطوارئ والجهات الوطنية",
    "Notification procedure for reporting to NCEMA and relevant national authorities (criteria, timeline, channel, authorised officer, template); record of all national-authority notifications; post-incident national reporting submissions; national-authority feedback log",
    "Establish procedures for timely reporting of significant disruptive incidents to NCEMA and other national authorities in line with national emergency-management requirements.")

sub(cn1, "N1-5",
    "National Risk Register Alignment",
    "التوافق مع سجل المخاطر الوطني",
    "Review record of NCEMA national risk register or Saudi national risk landscape; evidence that nationally identified risks (e.g., pandemic, earthquake, extreme weather, regional conflict) are reflected in organisational BCM scenario library and BIA assumptions; annual alignment review",
    "Ensure the organisational BCM scenario library and BIA reflect risks identified in NCEMA's national risk register and Saudi national risk assessments.")

# ─── Commit & Summary ─────────────────────────────────────────────────────────
conn.commit()

n_controls = 13   # 4, 5, 6, 7, 8-2, 8-3, 8-4, 8-5, 8-6, 9, 10, S1, N1
n_subs = (5 + 4 + 3 + 5 +   # clauses 4, 5, 6, 7
          5 + 4 + 5 + 6 + 5 +  # 8-2, 8-3, 8-4, 8-5, 8-6
          4 + 3 +              # 9, 10
          7 + 5)               # S1, N1

print(f"[BCM-ISO22301-SAMA] Seeded OK — framework fid={fid}, {n_controls} controls, {n_subs} sub-controls")
conn.close()
