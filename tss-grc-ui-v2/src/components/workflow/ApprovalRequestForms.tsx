// F (remediation plan §17 / item #6) — route the three flagship approvals through the engine.
// Policy publish/attest, NDI submission-package, and data-sharing agreements previously moved state
// with a *local status flip* (a hard-coded 'admin' approver, no routing, no SLA, module-scoped audit
// only). These thin wrappers over GRCRequestDrawer raise a real, routed, SLA'd, uniformly-audited
// engine WorkflowRequest — a SEQUENTIAL approval chain the approver(s) act on — anchored to the
// source record so it surfaces in that record's ModuleEngineRequestsPanel and the unified timeline.
// The module's own status flip records the *outcome*; the engine request is the *approval workflow*.
import { Megaphone, ShieldCheck, Share2 } from 'lucide-react';
import { GRCRequestDrawer } from './GRCRequestDrawer';
import type { WorkflowRequestDto } from '../../lib/workflowRequests';

interface Common {
  sourceRecordType?: string;
  sourceRecordId?: number;
  defaultTitleEn?: string;
  size?: 'sm' | 'md';
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  onCreated?: (r: WorkflowRequestDto) => void;
}

/** F.1 Compliance — Policy publish/attest approval. Anchor to the policy; routes a sequential
 *  legal→executive sign-off chain instead of the demo-mode local status flip. */
export function PolicyPublishApprovalButton({ sourceRecordType = 'Policy', ...p }: Common) {
  return <GRCRequestDrawer {...p} sourceModule="COMPLIANCE" sourceRecordType={sourceRecordType}
    requestType="POLICY_PUBLISH" Icon={Megaphone}
    label="Route publish approval" labelAr="توجيه اعتماد النشر"
    headingEn="Policy publish approval" headingAr="اعتماد نشر السياسة"
    defaultTitleEn={p.defaultTitleEn ?? 'Policy publish approval'}
    defaultRouting="SEQUENTIAL"
    fields={[
      { key: 'version', labelEn: 'Version to publish', labelAr: 'الإصدار المراد نشره', kind: 'text', required: true },
      { key: 'effectiveFrom', labelEn: 'Effective from', labelAr: 'ساري من', kind: 'date' },
      { key: 'publicationScope', labelEn: 'Publication scope / audience', labelAr: 'نطاق النشر / الجمهور', kind: 'textarea', fullWidth: true },
    ]} />;
}

/** F.2 NDI — SDAIA submission-package approval. Anchor to the package; routes a sequential
 *  data-office→executive approval before the package can be submitted to SDAIA. */
export function NdiPackageApprovalButton({ sourceRecordType = 'SubmissionPackage', ...p }: Common) {
  return <GRCRequestDrawer {...p} sourceModule="NDI" sourceRecordType={sourceRecordType}
    requestType="NDI_PACKAGE_APPROVAL" Icon={ShieldCheck}
    label="Route package approval" labelAr="توجيه اعتماد الحزمة"
    headingEn="NDI submission-package approval" headingAr="اعتماد حزمة رفع نضيء"
    defaultTitleEn={p.defaultTitleEn ?? 'NDI submission-package approval'}
    defaultRouting="SEQUENTIAL"
    fields={[
      { key: 'packageCode', labelEn: 'Package code', labelAr: 'كود الحزمة', kind: 'text' },
      { key: 'ndiScore', labelEn: 'Overall NDI %', labelAr: 'نسبة نضيء الإجمالية', kind: 'text' },
      { key: 'blockersCleared', labelEn: 'Finalize blockers cleared?', labelAr: 'هل عولجت حواجز التجهيز؟', kind: 'select',
        options: [
          { value: 'YES', labelEn: 'Yes — preflight clean', labelAr: 'نعم — الفحص المسبق سليم' },
          { value: 'NO', labelEn: 'No — approving with exceptions', labelAr: 'لا — اعتماد مع استثناءات' },
        ] },
      { key: 'attestationNote', labelEn: 'Approver attestation note', labelAr: 'ملاحظة إقرار المعتمِد', kind: 'textarea', fullWidth: true },
    ]} />;
}

/** F.3 PDPL/Privacy — Data-sharing agreement approval. Anchor to the DSA under review; routes a
 *  sequential privacy→legal→executive approval before the agreement is activated. */
export function DataSharingApprovalButton({ sourceRecordType = 'DataSharingAgreement', ...p }: Common) {
  return <GRCRequestDrawer {...p} sourceModule="PRIVACY" sourceRecordType={sourceRecordType}
    requestType="DATA_SHARING_APPROVAL" Icon={Share2}
    label="Route approval" labelAr="توجيه الاعتماد"
    headingEn="Data-sharing agreement approval" headingAr="اعتماد اتفاقية مشاركة بيانات"
    defaultTitleEn={p.defaultTitleEn ?? 'Data-sharing agreement approval'}
    defaultRouting="SEQUENTIAL"
    fields={[
      { key: 'recipientParty', labelEn: 'Recipient party', labelAr: 'الطرف المستلِم', kind: 'text', required: true },
      { key: 'crossBorder', labelEn: 'Cross-border mechanism', labelAr: 'آلية النقل عبر الحدود', kind: 'select',
        options: [
          { value: 'NONE', labelEn: 'None (in-country)', labelAr: 'لا يوجد (داخل المملكة)' },
          { value: 'ADEQUACY', labelEn: 'Adequacy', labelAr: 'كفاية الحماية' },
          { value: 'SCC', labelEn: 'Standard contractual clauses', labelAr: 'بنود تعاقدية معيارية' },
          { value: 'BCR', labelEn: 'Binding corporate rules', labelAr: 'قواعد مؤسسية مُلزِمة' },
          { value: 'EXPLICIT_CONSENT', labelEn: 'Explicit consent', labelAr: 'موافقة صريحة' },
        ] },
      { key: 'dpaInPlace', labelEn: 'DPA / clauses in place?', labelAr: 'هل اتفاقية المعالجة/البنود قائمة؟', kind: 'select',
        options: [
          { value: 'YES', labelEn: 'Yes', labelAr: 'نعم' },
          { value: 'NO', labelEn: 'No', labelAr: 'لا' },
        ] },
    ]} />;
}
