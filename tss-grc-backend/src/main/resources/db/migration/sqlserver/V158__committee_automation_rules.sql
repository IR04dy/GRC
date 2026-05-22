-- V158 — F8 automation rules consuming the new committee triggers.
--
-- AR-146..AR-155 (10 rules) — wires the V154-onwards committee triggers
-- into the existing F8 engine so resolutions and action items don't sit
-- silently in their tables.
--
-- Trigger events fired by the new services:
--   COMMITTEE_RESOLUTION_APPROVED        — every approved resolution
--   COMMITTEE_RESOLUTION_REJECTED        — rejected resolutions
--   COMMITTEE_RESOLUTION_DEFERRED        — deferred resolutions
--   COMMITTEE_RESOLUTION_WITHDRAWN       — withdrawn motions
--   COMMITTEE_ACTION_CREATED             — new action item from a meeting
--   COMMITTEE_ACTION_IN_PROGRESS         — owner started work
--   COMMITTEE_ACTION_COMPLETED           — closed with evidence
--   COMMITTEE_ACTION_OVERDUE             — sweep flipped after due date
--   COMMITTEE_ACTION_CANCELLED           — Chair-approved closure without completion
--   COMMITTEE_ACTION_DEFERRED            — due date moved with Chair approval
--
-- Targets the demo tenant (org=1). Customers replicate via the V96 seed
-- pattern (organisation_licenses entitlement → rule clone).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-146 ── Resolution with regulatorNotify=1 → page Compliance + GC
-- CMA / SAMA / SDAIA expect material decisions to be disclosed within
-- statutory windows. The Compliance team needs to be alerted the moment
-- the Board carries any motion with regulatory disclosure implications
-- so the filing clock can start.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-146')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-146',
         N'Resolution approved with regulator notify → alert Compliance + GC',
         N'قرار معتمد مع إخطار تنظيمي → تنبيه الالتزام والمستشار العام',
         N'CMA / SAMA / SDAIA require certain material Board / committee '
         + N'decisions to be disclosed within statutory windows. The '
         + N'Compliance team and General Counsel must be paged the moment '
         + N'the resolution is recorded so the disclosure clock starts visibly.',
         N'تتطلب هيئة السوق المالية وساما وSDAIA الإفصاح عن قرارات معينة '
         + N'للمجلس / اللجان خلال نوافذ قانونية. يجب تنبيه فريق الالتزام والمستشار '
         + N'العام فور تسجيل القرار حتى تبدأ ساعة الإفصاح بشكل واضح.',
         N'COMMITTEE_RESOLUTION_APPROVED',
         N'{"regulatorNotify":true}',
         N'{"type":"NOTIFY","roles":["CHIEF_COMPLIANCE_OFFICER","GENERAL_COUNSEL","DPO"],"channels":["EMAIL","SMS","INBOX"],"template":"COMMITTEE_REGULATOR_NOTIFICATION","slaHours":24}',
         1, N'CUSTOM');
END

-- AR-147 ── Board resolution APPROVED → log in board-resolutions register
-- The Board Secretary maintains the formal resolutions register. Every
-- approved Board resolution must be captured there with vote breakdown,
-- effective date, and review trigger.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-147')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-147',
         N'Board resolution APPROVED → board secretariat notification',
         N'قرار مجلس معتمد → إخطار أمانة المجلس',
         N'Every approved Board resolution must be filed in the formal '
         + N'board-resolutions register maintained by the Company Secretary. '
         + N'This rule notifies the secretariat so the register is updated '
         + N'within 48 hours of the meeting.',
         N'يجب تقديم كل قرار مجلس معتمد في سجل قرارات المجلس الرسمي الذي '
         + N'يحتفظ به أمين سر الشركة. يخطر هذا القانون الأمانة لتحديث السجل '
         + N'خلال 48 ساعة من الاجتماع.',
         N'COMMITTEE_RESOLUTION_APPROVED',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMPANY_SECRETARY"],"channels":["EMAIL","INBOX"],"template":"BOARD_RESOLUTION_FILED","slaHours":48}',
         2, N'CUSTOM');
END

-- AR-148 ── Action item OVERDUE → escalate to owner + manager
-- When the sweep flips an action to OVERDUE, the owner gets notified
-- with their line manager copied. Per the SAMA examiner expectation,
-- the action register is the primary evidence that committees follow
-- through on their decisions — overdue items must be visibly escalated.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-148')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-148',
         N'Committee action OVERDUE → escalate to owner + line manager',
         N'إجراء لجنة متأخر → تصعيد للمالك ومديره المباشر',
         N'SAMA examiners treat the action register as the primary evidence '
         + N'that committees follow through on their decisions. When an '
         + N'action flips to OVERDUE, the platform notifies the owner with '
         + N'their line manager copied; the item is also re-tabled at the '
         + N'next committee meeting under "Matters arising".',
         N'يعامل فاحصو ساما سجل الإجراءات كدليل أساسي على متابعة اللجان '
         + N'لقراراتها. عندما يتحول إجراء إلى متأخر، تخطر المنصة المالك '
         + N'مع نسخة لمديره المباشر؛ كما يُعاد إدراج البند في الاجتماع '
         + N'التالي تحت "الأمور المنبثقة".',
         N'COMMITTEE_ACTION_OVERDUE',
         N'{}',
         N'{"type":"ESCALATE","escalateTo":"LINE_MANAGER","cc":["COMMITTEE_CHAIR","COMPANY_SECRETARY"],"template":"ACTION_OVERDUE","channels":["EMAIL","SMS","INBOX"]}',
         1, N'CUSTOM');
END

-- AR-149 ── CRITICAL action item created → owner SMS within 1 hour
-- Critical actions (typically arising from regulator examinations or
-- crisis-management committees) need same-business-day acknowledgement.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-149')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-149',
         N'CRITICAL action created → owner SMS within 1 hour',
         N'إنشاء إجراء حرج → رسالة قصيرة للمالك خلال ساعة',
         N'Critical-priority committee actions (typically from regulator '
         + N'examinations or crisis-management resolutions) must be '
         + N'acknowledged same business day. The platform sends an '
         + N'SMS + email to the owner within 1 hour of creation.',
         N'يجب الإقرار بإجراءات اللجان عالية الأولوية (عادة من الفحوصات '
         + N'التنظيمية أو قرارات إدارة الأزمات) في يوم العمل نفسه. ترسل '
         + N'المنصة رسالة قصيرة وبريداً إلكترونياً للمالك خلال ساعة من الإنشاء.',
         N'COMMITTEE_ACTION_CREATED',
         N'{"priority":"CRITICAL"}',
         N'{"type":"NOTIFY","channels":["SMS","EMAIL","INBOX"],"template":"CRITICAL_ACTION_ASSIGNED","slaMinutes":60}',
         1, N'CUSTOM');
END

-- AR-150 ── Action linked to RISK module COMPLETED → update risk-register entry
-- When a committee action item that was linked to a Risk record is
-- completed, the Risk register entry needs to reflect the closure so the
-- residual exposure can be re-rated.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-150')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-150',
         N'Risk-linked action COMPLETED → update risk register entry',
         N'إجراء مرتبط بالمخاطر مكتمل → تحديث إدخال سجل المخاطر',
         N'When a committee action that was created as a treatment for a '
         + N'risk-register entry is completed, the Risk record needs to '
         + N'reflect the closure so the residual exposure can be re-rated '
         + N'and the risk acceptance reviewed. This rule fires a '
         + N'CREATE_RECORD action against the Risk module to log the '
         + N'completion in the Risk timeline.',
         N'عندما يكتمل إجراء لجنة أُنشئ كمعالجة لإدخال في سجل المخاطر، يجب '
         + N'أن يعكس سجل المخاطر الإغلاق حتى يُعاد تقييم التعرض المتبقي '
         + N'ومراجعة قبول المخاطر. يفعّل هذا القانون إجراء CREATE_RECORD '
         + N'في وحدة المخاطر لتسجيل الإكمال في الجدول الزمني للمخاطر.',
         N'COMMITTEE_ACTION_COMPLETED',
         N'{"linkedModule":"RISK"}',
         N'{"type":"CREATE_RECORD","module":"RISK","entityType":"RiskTimelineEntry","template":"COMMITTEE_ACTION_CLOSED"}',
         2, N'CUSTOM');
END

-- AR-151 ── Action linked to AUDIT FINDING COMPLETED → close CAPA stage
-- Same pattern as AR-150 but for audit findings. When a committee
-- action that was tracking remediation of an audit finding completes,
-- the finding's CAPA stage advances toward closure.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-151')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-151',
         N'Audit-linked action COMPLETED → advance CAPA stage',
         N'إجراء مرتبط بمراجعة مكتمل → تقدم مرحلة معالجة الملاحظة',
         N'When a committee action remediating an audit finding is '
         + N'completed, the finding''s CAPA stage advances toward closure. '
         + N'The Audit Committee secretariat is notified so they can '
         + N'trigger the independent-testing stage if the finding requires '
         + N'one.',
         N'عندما يكتمل إجراء لجنة لمعالجة ملاحظة مراجعة، تتقدم مرحلة معالجة '
         + N'الملاحظة نحو الإغلاق. تُخطر أمانة لجنة المراجعة حتى تستطيع '
         + N'تفعيل مرحلة الاختبار المستقل إذا كانت الملاحظة تتطلبها.',
         N'COMMITTEE_ACTION_COMPLETED',
         N'{"linkedModule":"AUDIT"}',
         N'{"type":"CREATE_RECORD","module":"AUDIT","entityType":"FindingTimelineEntry","template":"COMMITTEE_ACTION_CLOSED","notifyRoles":["HEAD_OF_INTERNAL_AUDIT","AUDIT_COMMITTEE_CHAIR"]}',
         2, N'CUSTOM');
END

-- AR-152 ── Resolution DEFERRED → re-table at next meeting
-- Deferred motions automatically re-appear on the next meeting's agenda
-- in the relevant section. The agenda auto-populator uses this rule to
-- pre-populate.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-152')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-152',
         N'Resolution DEFERRED → re-table at next meeting',
         N'قرار مؤجل → إعادة طرحه في الاجتماع التالي',
         N'A deferred motion automatically re-appears on the next meeting''s '
         + N'agenda. The secretary is notified so the deferral reason can be '
         + N'incorporated into the re-tabled motion.',
         N'يظهر الاقتراح المؤجل تلقائياً في جدول أعمال الاجتماع التالي. '
         + N'يُخطر الأمين حتى يدمج سبب التأجيل في الاقتراح المُعاد طرحه.',
         N'COMMITTEE_RESOLUTION_DEFERRED',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMMITTEE_SECRETARY"],"template":"RESOLUTION_DEFERRED","channels":["INBOX"]}',
         3, N'CUSTOM');
END

-- AR-153 ── Action CANCELLED → notify Chair with reason
-- Action cancellation (without completion) is a sensitive event because
-- it implies a committee decision wasn't followed through. The Chair
-- should review the cancellation reason explicitly.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-153')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-153',
         N'Action CANCELLED → notify Chair for review',
         N'إجراء ملغى → إخطار الرئيس للمراجعة',
         N'Cancelling an action item without completion implies a committee '
         + N'decision was not followed through. The Chair must explicitly '
         + N'review the cancellation reason — this rule routes the '
         + N'notification.',
         N'إلغاء بند إجراء دون إكماله يعني أن قرار اللجنة لم يُتابَع. يجب '
         + N'على الرئيس مراجعة سبب الإلغاء صراحة — يوجّه هذا القانون الإخطار.',
         N'COMMITTEE_ACTION_CANCELLED',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMMITTEE_CHAIR","COMPANY_SECRETARY"],"template":"ACTION_CANCELLED_REVIEW","channels":["EMAIL","INBOX"]}',
         2, N'CUSTOM');
END

-- AR-154 ── Resolution with review_required_by ≤ 30 days → reminder
-- Resolutions that sunset or require re-confirmation (e.g. risk-appetite
-- changes, BCR approvals) need a renewal cycle. The platform reminds
-- the Chair 30 days before the review date.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-154')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-154',
         N'Resolution review due in 30 days → reminder to Chair',
         N'مراجعة قرار مستحقة خلال 30 يوماً → تذكير للرئيس',
         N'Resolutions that sunset or require re-confirmation (e.g. risk-'
         + N'appetite changes, BCR approvals, capital commitments) need a '
         + N'renewal cycle. The platform reminds the Chair + Secretary 30 '
         + N'days before the review date so the re-confirmation can be '
         + N'scheduled into an upcoming meeting agenda.',
         N'القرارات التي تنتهي صلاحيتها أو تتطلب إعادة تأكيد (مثل تغييرات '
         + N'شهية المخاطر، اعتمادات القواعد الملزمة، الالتزامات الرأسمالية) '
         + N'تحتاج دورة تجديد. تذكر المنصة الرئيس والأمين قبل تاريخ المراجعة '
         + N'بـ 30 يوماً حتى يمكن جدولة إعادة التأكيد في جدول أعمال اجتماع قادم.',
         N'COMMITTEE_RESOLUTION_REVIEW_DUE_30D',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMMITTEE_CHAIR","COMMITTEE_SECRETARY"],"template":"RESOLUTION_REVIEW_REMINDER","channels":["EMAIL","INBOX"]}',
         2, N'CUSTOM');
END

-- AR-155 ── Committee charter review due in 30 days → reminder
-- CMA Corporate Governance expects committees to review their charter
-- annually. The platform reminds the Nomination Committee Chair 30 days
-- before next_charter_review_at lapses.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-155')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-155',
         N'Committee charter review due in 30 days → reminder',
         N'مراجعة ميثاق اللجنة مستحقة خلال 30 يوماً → تذكير',
         N'CMA Corporate Governance Article 26 expects every committee to '
         + N'review its charter annually. The platform reminds the Chair + '
         + N'Nomination Committee 30 days before next_charter_review_at to '
         + N'ensure the review is scheduled and completed in time for the '
         + N'annual Corporate Governance Report.',
         N'تتوقع المادة 26 من لائحة حوكمة الشركات الصادرة عن هيئة السوق '
         + N'المالية أن تراجع كل لجنة ميثاقها سنوياً. تذكر المنصة الرئيس '
         + N'ولجنة الترشيحات قبل تاريخ المراجعة بـ 30 يوماً لضمان جدولة '
         + N'المراجعة وإكمالها في الوقت المناسب للتقرير السنوي لحوكمة الشركات.',
         N'COMMITTEE_CHARTER_REVIEW_DUE_30D',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMMITTEE_CHAIR","NOMINATION_COMMITTEE_CHAIR","COMPANY_SECRETARY"],"template":"CHARTER_REVIEW_REMINDER","channels":["EMAIL","INBOX"]}',
         2, N'CUSTOM');
END
GO

PRINT N'V158 — AR-146..AR-155 (10 committee automation rules) applied.';
GO
