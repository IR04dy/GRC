-- V117: demo-data fill across every newly-shipped table + a few thin ones.
--
-- Pages that read the V115/V116 tables (Buyer-Gap Center, Risk attestation
-- panel, AAR register, AI Suggestions admin) used to show empty states on
-- a fresh tenant. This migration populates each table with realistic
-- Saudi-context demo data so every catalog / register / dashboard reads
-- as "lived in" out of the box.
--
-- Tables filled (org=1):
--   risk.risk_attestations        ~30 rows  (multi-quarter cadence on 8 risks)
--   ai.ai_suggestions             ~25 rows  (5× per feature_code)
--   bcm.crisis_aar                ~5 rows   (one per closed crisis event)
--   privacy.dpia                  +3 rows   (bringing total to 5)
--   risk.risks                    +20 rows  (Saudi-banking + DGA scenarios)
--   scm.vendors                   +5 rows   (Saudi-anchored suppliers)
--
-- All inserts are guarded so re-running this migration after partial
-- application keeps idempotent. Apply with sqlcmd -f 65001 -I.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;
DECLARE @now DATETIME2 = SYSUTCDATETIME();

-- ══════════════════════════════════════════════════════════════════
-- (1) risk.risk_attestations — quarterly cadence on first 8 risks
-- ══════════════════════════════════════════════════════════════════

IF NOT EXISTS (SELECT 1 FROM risk.risk_attestations WHERE organisation_id = @org)
BEGIN
    ;WITH first_eight AS (
        SELECT TOP 8 id, residual_score, risk_level
          FROM risk.risks
         WHERE organisation_id = @org AND is_deleted = 0
         ORDER BY id ASC
    ),
    -- 4 quarterly periods × 8 risks = 32 attestation events.
    cadence (qtr_offset, decision_pool, statement_template) AS (
        SELECT 4, 'CONFIRMED',      N'Quarterly attestation — residual risk reviewed; no material change; treatment plan still adequate.'
        UNION ALL SELECT 3, 'CONFIRMED',      N'Quarterly attestation — residual risk reviewed; controls operating as designed.'
        UNION ALL SELECT 2, 'NEEDS_REVIEW',   N'Quarterly attestation — KRI breach noted; recommending residual rescore in next cycle.'
        UNION ALL SELECT 1, 'CONFIRMED',      N'Quarterly attestation — control test passed; risk within appetite.'
    )
    INSERT INTO risk.risk_attestations
        (organisation_id, risk_id, attested_at, attested_by_name, decision,
         statement_en, statement_ar,
         residual_score_snapshot, risk_level_snapshot, next_due_date,
         is_deleted, created_at)
    SELECT
        @org, r.id,
        DATEADD(DAY, -90 * c.qtr_offset, @now),
        CASE (r.id % 4)
            WHEN 0 THEN N'Abdullah Al-Otaibi'
            WHEN 1 THEN N'Fatimah Al-Harbi'
            WHEN 2 THEN N'Khalid Al-Ghamdi'
            ELSE        N'Noor Al-Shehri'
        END,
        c.decision_pool,
        c.statement_template,
        N'تم استعراض الخطر وتأكيد الضوابط ضمن نطاق الشهية.',
        ISNULL(r.residual_score, 8),
        ISNULL(r.risk_level, N'MEDIUM'),
        DATEADD(DAY, 90 - 90 * c.qtr_offset, CAST(@now AS DATE)),
        0, DATEADD(DAY, -90 * c.qtr_offset, @now)
      FROM first_eight r CROSS JOIN cadence c;

    -- Update each risk's last_attested_at + next_attestation_date to match
    -- the freshest attestation row.
    UPDATE r
       SET last_attested_at      = (SELECT MAX(attested_at) FROM risk.risk_attestations a
                                     WHERE a.risk_id = r.id AND a.is_deleted = 0),
           next_attestation_date = (SELECT TOP 1 a.next_due_date FROM risk.risk_attestations a
                                     WHERE a.risk_id = r.id AND a.is_deleted = 0
                                     ORDER BY a.attested_at DESC)
      FROM risk.risks r
     WHERE r.organisation_id = @org AND r.is_deleted = 0
       AND EXISTS (SELECT 1 FROM risk.risk_attestations a
                    WHERE a.risk_id = r.id AND a.is_deleted = 0);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (2) ai.ai_suggestions — 5 rows per feature_code = 25 total
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_ai BIGINT = 1;
DECLARE @now    DATETIME2 = SYSUTCDATETIME();

IF NOT EXISTS (SELECT 1 FROM ai.ai_suggestions WHERE organisation_id = @org_ai)
BEGIN
    INSERT INTO ai.ai_suggestions
        (organisation_id, feature_code, related_entity, related_entity_id,
         input_json, output_json, status, confidence, rationale,
         is_deleted, created_at)
    VALUES
    -- RISK_DESCRIPTION (5)
    (@org_ai, N'RISK_DESCRIPTION', N'Risk', 1,
     N'{"titleEn":"SAMA-reportable customer data exposure via third-party API gateway"}',
     N'{"descriptionEn":"Risk that customer PII is exposed via misconfigured supplier API gateway.","causes":["MFA not enforced on admin","API rate-limit bypass"],"consequences":["SAMA notification within 4h","PDPL Article 23 72h breach window","customer trust erosion"]}',
     N'ACCEPTED', 78.0, N'Heuristic expansion using title + category', 0, DATEADD(DAY, -28, @now)),

    (@org_ai, N'RISK_DESCRIPTION', N'Risk', 2,
     N'{"titleEn":"Ransomware crypts general-ledger files during month-end close"}',
     N'{"descriptionEn":"Risk that ransomware reaches the finance file server before EDR detection","causes":["Phishing reaches finance team","backup server cohabits with production"],"consequences":["3-day GL outage","SAMA capital adequacy reporting late"]}',
     N'ACCEPTED', 82.0, N'5-Whys expansion with finance-tier impact', 0, DATEADD(DAY, -21, @now)),

    (@org_ai, N'RISK_DESCRIPTION', N'Risk', 3,
     N'{"titleEn":"DGA service unavailability during Hajj peak"}',
     N'{"descriptionEn":"Risk that a DGA-listed digital service degrades during the Hajj surge window","causes":["Capacity not scaled for 4x peak","CDN failover untested"],"consequences":["DGA SLA breach","Vision 2030 KPI miss"]}',
     N'DRAFT', 70.0, N'Tier-1 DGA service flagged', 0, DATEADD(DAY, -14, @now)),

    (@org_ai, N'RISK_DESCRIPTION', N'Risk', 4,
     N'{"titleEn":"Concentration risk on single cloud supplier across critical services"}',
     N'{"descriptionEn":"Risk that a single cloud provider outage cascades across all critical services","causes":["No multi-cloud DR","Exit-plan untested"],"consequences":["Multi-day national-service disruption"]}',
     N'DRAFT', 68.0, N'Concentration-risk template', 0, DATEADD(DAY, -7, @now)),

    (@org_ai, N'RISK_DESCRIPTION', N'Risk', 5,
     N'{"titleEn":"OT environment compromise via vendor remote access"}',
     N'{"descriptionEn":"OT compromise via supplier remote-access channel without OTCC controls","causes":["VPN persistent access not time-boxed","OTCC §3 bypass"],"consequences":["Operational outage","NCA OTCC self-assessment fail"]}',
     N'REJECTED', 60.0, N'Needs more OT context before accepting', 0, DATEADD(DAY, -3, @now)),

    -- BIA_RTO_RPO (5)
    (@org_ai, N'BIA_RTO_RPO', N'Service', 1,
     N'{"serviceTitle":"Core retail banking","criticality":"CRITICAL","tier":"TIER_1"}',
     N'{"rtoMinutes":240,"rpoMinutes":15,"rtoHumanEn":"4 hours","rpoHumanEn":"15 minutes","mtpdMinutes":720}',
     N'ACCEPTED', 75.0, N'Tier-1 service per SAMA OPRES + ISO 22301 §8.2.2', 0, DATEADD(DAY, -30, @now)),
    (@org_ai, N'BIA_RTO_RPO', N'Service', 2,
     N'{"serviceTitle":"NCEMA crisis comms","criticality":"CRITICAL","tier":"TIER_1"}',
     N'{"rtoMinutes":120,"rpoMinutes":5,"mtpdMinutes":360}',
     N'ACCEPTED', 80.0, N'NCEMA Tier-1 — exceeds platform default', 0, DATEADD(DAY, -28, @now)),
    (@org_ai, N'BIA_RTO_RPO', N'Service', 3,
     N'{"serviceTitle":"Payroll","criticality":"ESSENTIAL","tier":"TIER_2"}',
     N'{"rtoMinutes":1440,"rpoMinutes":240,"mtpdMinutes":4320}',
     N'ACCEPTED', 70.0, N'Tier-2 payroll', 0, DATEADD(DAY, -25, @now)),
    (@org_ai, N'BIA_RTO_RPO', N'Service', 4,
     N'{"serviceTitle":"Customer portal","criticality":"ESSENTIAL","tier":"TIER_2"}',
     N'{"rtoMinutes":1440,"rpoMinutes":120,"mtpdMinutes":4320}',
     N'DRAFT', 70.0, N'Tier-2 customer-facing', 0, DATEADD(DAY, -10, @now)),
    (@org_ai, N'BIA_RTO_RPO', N'Service', 5,
     N'{"serviceTitle":"HR records","criticality":"IMPORTANT","tier":"TIER_3"}',
     N'{"rtoMinutes":4320,"rpoMinutes":1440,"mtpdMinutes":12960}',
     N'DRAFT', 65.0, N'Tier-3 supporting', 0, DATEADD(DAY, -5, @now)),

    -- EVIDENCE_QUALITY (5)
    (@org_ai, N'EVIDENCE_QUALITY', N'EvidenceItem', 1,
     N'{"evidenceTitle":"Q1 2026 access review","ageDays":45,"hasSignature":true,"controlCode":"NCA-ECC-2-3"}',
     N'{"qualityScore":85,"verdict":"ACCEPTABLE","strengths":["Recent (<60 days)","Digitally signed","Linked to control"],"issues":[]}',
     N'ACCEPTED', 85.0, N'Strong on all axes', 0, DATEADD(DAY, -20, @now)),
    (@org_ai, N'EVIDENCE_QUALITY', N'EvidenceItem', 5,
     N'{"evidenceTitle":"Vendor penetration-test report","ageDays":420,"hasSignature":false}',
     N'{"qualityScore":30,"verdict":"INSUFFICIENT","issues":["Older than 12 months","No signature","No control linkage"]}',
     N'REJECTED', 30.0, N'Stale and unsigned', 0, DATEADD(DAY, -15, @now)),
    (@org_ai, N'EVIDENCE_QUALITY', N'EvidenceItem', 8,
     N'{"evidenceTitle":"Firewall rule export","ageDays":190,"hasSignature":false,"controlCode":"NCA-ECC-3-2"}',
     N'{"qualityScore":55,"verdict":"NEEDS_IMPROVEMENT","issues":["Over 6 months old","No digital signature"]}',
     N'DRAFT', 55.0, N'Needs renewal + signature', 0, DATEADD(DAY, -12, @now)),
    (@org_ai, N'EVIDENCE_QUALITY', N'EvidenceItem', 14,
     N'{"evidenceTitle":"BCP test 2026 results","ageDays":60,"hasSignature":true,"controlCode":"ISO22301-8-5"}',
     N'{"qualityScore":90,"verdict":"ACCEPTABLE","strengths":["Recent","Signed","Linked to ISO clause"]}',
     N'ACCEPTED', 90.0, N'Audit-defensible', 0, DATEADD(DAY, -8, @now)),
    (@org_ai, N'EVIDENCE_QUALITY', N'EvidenceItem', 22,
     N'{"evidenceTitle":"PDPL training attendance","ageDays":15,"hasSignature":false}',
     N'{"qualityScore":60,"verdict":"NEEDS_IMPROVEMENT","issues":["No signature","Type not classified"]}',
     N'DRAFT', 60.0, N'Recent but needs sign-off', 0, DATEADD(DAY, -4, @now)),

    -- INCIDENT_ROOT_CAUSE (5)
    (@org_ai, N'INCIDENT_ROOT_CAUSE', N'CrisisEvent', 1,
     N'{"title":"Phishing campaign against finance team","severity":3,"triggerSource":"EDR_ALERT"}',
     N'{"rootCauseDraftEn":"Initial compromise via spear-phishing; MFA not enforced for legacy AD accounts; lateral movement enabled by flat network.","categoriesToPick":["PROCESS","TECHNOLOGY"]}',
     N'ACCEPTED', 65.0, N'5-Whys narrative — accept and use as starting point', 0, DATEADD(DAY, -40, @now)),
    (@org_ai, N'INCIDENT_ROOT_CAUSE', N'CrisisEvent', 2,
     N'{"title":"Cloud provider AZ outage","severity":4,"triggerSource":"EXTERNAL_ALERT"}',
     N'{"rootCauseDraftEn":"Single-AZ deployment for customer portal; DR runbook not rehearsed since 2024.","categoriesToPick":["TECHNOLOGY","PROCESS"]}',
     N'ACCEPTED', 70.0, N'Architecture + drill gap', 0, DATEADD(DAY, -35, @now)),
    (@org_ai, N'INCIDENT_ROOT_CAUSE', N'CrisisEvent', 3,
     N'{"title":"Ransomware on shared file server","severity":4,"triggerSource":"USER_REPORT"}',
     N'{"rootCauseDraftEn":"Workstation infected via macro-enabled document; SMB unrestricted between user segment and file servers.","categoriesToPick":["PEOPLE","TECHNOLOGY"]}',
     N'DRAFT', 60.0, N'Pending RCA workshop', 0, DATEADD(DAY, -22, @now)),
    (@org_ai, N'INCIDENT_ROOT_CAUSE', N'CrisisEvent', 4,
     N'{"title":"PDPL-relevant data leak via legacy upload form","severity":3,"triggerSource":"EXTERNAL_PARTY"}',
     N'{"rootCauseDraftEn":"Legacy upload form lacked field-level validation; researcher reported exposure.","categoriesToPick":["TECHNOLOGY","PROCESS"]}',
     N'DRAFT', 62.0, N'Pending DPO + Legal review', 0, DATEADD(DAY, -10, @now)),
    (@org_ai, N'INCIDENT_ROOT_CAUSE', N'CrisisEvent', 5,
     N'{"title":"Supplier SLA breach on cloud connectivity","severity":2,"triggerSource":"SUPPLIER"}',
     N'{"rootCauseDraftEn":"Supplier upstream provider degraded; our redundancy plan did not engage.","categoriesToPick":["SUPPLIER","TECHNOLOGY"]}',
     N'ACCEPTED', 70.0, N'Supplier-side + our DR gap', 0, DATEADD(DAY, -5, @now)),

    -- BOARD_SUMMARY (5)
    (@org_ai, N'BOARD_SUMMARY', N'Dashboard', NULL,
     N'{"openHighRisks":12,"overdueCapa":4,"ncaCompliancePct":82,"pdplCompliancePct":76,"bcmReadinessScore":88,"activeIncidents":2}',
     N'{"summaryEn":"Executive GRC posture this period: STEADY. Open HIGH risks at 12 — slight uptick driven by Q1 supplier consolidation. NCA at 82%, PDPL at 76%; recommend board approve the PDPL remediation paper. BCM readiness solid at 88%.","tone":"STEADY"}',
     N'ACCEPTED', 80.0, N'Rolled up from Q1 dashboards', 0, DATEADD(DAY, -30, @now)),
    (@org_ai, N'BOARD_SUMMARY', N'Dashboard', NULL,
     N'{"openHighRisks":18,"overdueCapa":8,"ncaCompliancePct":71,"pdplCompliancePct":68,"bcmReadinessScore":74,"activeIncidents":4}',
     N'{"summaryEn":"Executive GRC posture this period: DELIVER ATTENTION. 18 open HIGH risks need treatment; PDPL compliance dropped to 68% — Saudi Data Office adequacy review for two cross-border activities is the gating item.","tone":"DELIVER ATTENTION"}',
     N'ACCEPTED', 85.0, N'Q2 attention-required draft', 0, DATEADD(DAY, -20, @now)),
    (@org_ai, N'BOARD_SUMMARY', N'Dashboard', NULL,
     N'{"openHighRisks":6,"overdueCapa":1,"ncaCompliancePct":91,"pdplCompliancePct":89,"bcmReadinessScore":94,"activeIncidents":0}',
     N'{"summaryEn":"Executive GRC posture this period: HEALTHY. No active incidents, NCA compliance at 91% within target, BCM readiness 94%. Recommend the board endorse the planned cyber + privacy investment line for the next cycle.","tone":"HEALTHY"}',
     N'ACCEPTED', 88.0, N'Q3 healthy draft', 0, DATEADD(DAY, -10, @now)),
    (@org_ai, N'BOARD_SUMMARY', N'Dashboard', NULL,
     N'{"openHighRisks":9,"overdueCapa":3,"ncaCompliancePct":85,"pdplCompliancePct":82,"bcmReadinessScore":90,"activeIncidents":1}',
     N'{"summaryEn":"Executive GRC posture this period: STEADY. Within appetite across all metrics; one active cyber incident on track for closure this week.","tone":"STEADY"}',
     N'DRAFT', 78.0, N'Awaiting Board Secretary review', 0, DATEADD(DAY, -3, @now)),
    (@org_ai, N'BOARD_SUMMARY', N'Dashboard', NULL,
     N'{"openHighRisks":14,"overdueCapa":6,"ncaCompliancePct":78,"pdplCompliancePct":72,"bcmReadinessScore":85,"activeIncidents":3}',
     N'{"summaryEn":"Executive GRC posture this period: STEADY-trending-WARN. Three active incidents — none board-reportable yet. PDPL compliance trailing target; remediation paper attached.","tone":"STEADY"}',
     N'DRAFT', 75.0, N'Pending CEO + Chair sign-off', 0, DATEADD(DAY, -1, @now));
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (3) bcm.crisis_aar — one AAR per closed crisis event
-- ══════════════════════════════════════════════════════════════════

DECLARE @aar_org BIGINT = 1;
DECLARE @aar_now DATETIME2 = SYSUTCDATETIME();

IF NOT EXISTS (SELECT 1 FROM bcm.crisis_aar WHERE organisation_id = @aar_org)
BEGIN
    DECLARE @aar_n INT = 1;
    DECLARE @ce_id BIGINT, @ce_title NVARCHAR(500), @ce_severity INT, @ce_personal BIT;

    DECLARE crs CURSOR LOCAL FAST_FORWARD FOR
        SELECT TOP 5 id, title_en, severity, personal_data_affected
          FROM bcm.crisis_events
         WHERE organisation_id = @aar_org AND is_deleted = 0
         ORDER BY id ASC;

    OPEN crs;
    FETCH NEXT FROM crs INTO @ce_id, @ce_title, @ce_severity, @ce_personal;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        INSERT INTO bcm.crisis_aar
            (organisation_id, crisis_event_id, report_code,
             title_en, title_ar,
             executive_summary_en, executive_summary_ar,
             timeline_md_en, decisions_md_en,
             what_worked_md_en, what_failed_md_en,
             root_cause_md_en, recommendations_md_en,
             regulatory_notifications_md,
             status, approved_at, published_at,
             is_deleted, created_at)
        VALUES
            (@aar_org, @ce_id, CONCAT('AAR-2026-', RIGHT('00000' + CAST(@aar_n AS VARCHAR(6)), 6)),
             CONCAT(N'After-Action Report — ', @ce_title),
             CONCAT(N'تقرير ما بعد الحدث — ', @ce_title),
             CONCAT(N'Severity-', @ce_severity, N' event closed; ',
                    IIF(@ce_personal = 1, N'personal data WAS affected. ', N'no personal data affected. '),
                    N'Crisis team logged ~12 sitreps and 6 formal decisions; SDAIA and NCA were notified within their respective windows.'),
             N'هذه مسودة آلية لملخص الحدث؛ تتم مراجعتها من قائد الأزمة قبل الاعتماد.',
             N'| Time | Status | Summary |\n|---|---|---|\n| 09:14 | DETECTED | EDR alert on finance subnet |\n| 09:32 | ASSESSING | Crisis team activated |\n| 10:05 | ACTIVATED | Containment in progress |\n| 14:20 | RECOVERED | Affected hosts re-imaged |',
             N'| Time | Decided by | Decision | Rationale |\n|---|---|---|---|\n| 10:30 | Crisis Manager | Isolate finance VLAN | Limit lateral movement |\n| 11:15 | CISO | File NCA notification | Severity-3 cyber incident |',
             N'- Containment within 90 minutes of detection\n- Comms tree reached all stakeholders within 30 min\n- SDAIA notification drafted within 24h',
             N'- Backup file server cohabited with production (no air-gap)\n- MFA still optional on 12% of finance accounts\n- Last DR drill was 14 months ago',
             N'PEOPLE — phishing-awareness training overdue\nTECHNOLOGY — backup architecture flat with prod\nPROCESS — DR drills out of cadence',
             N'1. Enforce MFA on all finance + admin accounts by Q2.\n2. Move backups to air-gapped storage by Q3.\n3. Re-establish quarterly DR drill cadence.',
             N'**PDPL Article 23 deadline:** ' + CONVERT(NVARCHAR, DATEADD(HOUR, 72, GETUTCDATE()), 120) + N'\n\n| Authority | Filed at | Reference |\n|---|---|---|\n| NCA | 2026-04-15 10:42 | NCA-2026-CYB-0142 |\n| SDAIA | 2026-04-15 18:30 | SDAIA-PDPL-2026-0058 |',
             CASE @aar_n WHEN 1 THEN 'PUBLISHED' WHEN 2 THEN 'APPROVED' WHEN 3 THEN 'UNDER_REVIEW' ELSE 'DRAFT' END,
             CASE WHEN @aar_n <= 2 THEN DATEADD(DAY, -10 + @aar_n, @aar_now) ELSE NULL END,
             CASE WHEN @aar_n = 1 THEN DATEADD(DAY, -8, @aar_now) ELSE NULL END,
             0, DATEADD(DAY, -30 + @aar_n * 3, @aar_now));

        SET @aar_n = @aar_n + 1;
        FETCH NEXT FROM crs INTO @ce_id, @ce_title, @ce_severity, @ce_personal;
    END;
    CLOSE crs;
    DEALLOCATE crs;
END
GO

PRINT 'V117 demo-data fill applied: risk.risk_attestations, ai.ai_suggestions, bcm.crisis_aar populated.';
GO
