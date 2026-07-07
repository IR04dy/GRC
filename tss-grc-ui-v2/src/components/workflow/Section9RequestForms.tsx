// §9 forms (remediation plan) — the mid-weight GRC request forms the platform lacked, each a thin
// configuration of GRCRequestDrawer: a real, routed, SLA'd engine WorkflowRequest with a distinct
// request_type, anchored to its source record, captured in a context-preserving drawer.
//   KRI breach response · Patch request · SOC containment · Data-sharing request · Processor review
import { AlertTriangle, Wrench, ShieldOff, Share2, ClipboardCheck } from 'lucide-react';
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

/** §9.1 ERM — KRI breach response. Anchor to the breached KRI; routes an owner→risk-manager task. */
export function KriBreachRequestButton({ sourceRecordType = 'Kri', ...p }: Common) {
  return <GRCRequestDrawer {...p} sourceModule="RISK" sourceRecordType={sourceRecordType}
    requestType="KRI_BREACH" Icon={AlertTriangle}
    label="KRI breach response" labelAr="استجابة تجاوز مؤشر"
    headingEn="KRI breach response" headingAr="استجابة تجاوز مؤشر مخاطرة"
    defaultTitleEn={p.defaultTitleEn ?? 'KRI breach response'}
    defaultPriority="HIGH"
    fields={[
      { key: 'breachValue', labelEn: 'Breach value', labelAr: 'القيمة المتجاوِزة', kind: 'text', required: true },
      { key: 'threshold', labelEn: 'Threshold', labelAr: 'العتبة', kind: 'text' },
      { key: 'breachDate', labelEn: 'Breach date', labelAr: 'تاريخ التجاوز', kind: 'date' },
      { key: 'rootCause', labelEn: 'Root-cause note', labelAr: 'ملاحظة السبب الجذري', kind: 'textarea', required: true },
    ]} />;
}

/** §9.2 Cyber — Patch request. Anchor to the vulnerability; routes to the asset/IT owner with a
 *  severity-driven SLA and patch-proof evidence expected on closure. */
export function PatchRequestButton({ sourceRecordType = 'CyberVulnerability', ...p }: Common) {
  return <GRCRequestDrawer {...p} sourceModule="CYBER" sourceRecordType={sourceRecordType}
    requestType="PATCH_REQUEST" Icon={Wrench}
    label="Patch request" labelAr="طلب ترقيع"
    headingEn="Patch request" headingAr="طلب ترقيع"
    defaultTitleEn={p.defaultTitleEn ?? 'Patch request'}
    defaultRouting="SEQUENTIAL"
    fields={[
      { key: 'affectedAsset', labelEn: 'Affected asset(s)', labelAr: 'الأصول المتأثرة', kind: 'text', required: true },
      { key: 'cve', labelEn: 'CVE', labelAr: 'المُعرّف CVE', kind: 'text' },
      { key: 'cvss', labelEn: 'CVSS', labelAr: 'درجة CVSS', kind: 'text' },
      { key: 'targetPatchDate', labelEn: 'Target patch date', labelAr: 'تاريخ الترقيع المستهدف', kind: 'date' },
      { key: 'rollbackNote', labelEn: 'Rollback / change note', labelAr: 'ملاحظة التراجع/التغيير', kind: 'textarea' },
    ]} />;
}

/** §9.3 SOC — Containment request. Anchor to the SOC case/event; routes to IT/security with a
 *  1h-CRITICAL-style SLA and before/after evidence. */
export function ContainmentRequestButton({ ...p }: Common) {
  return <GRCRequestDrawer {...p} sourceModule="SOCOPS"
    requestType="CONTAINMENT" Icon={ShieldOff}
    label="Containment request" labelAr="طلب احتواء"
    headingEn="Containment request" headingAr="طلب احتواء"
    defaultTitleEn={p.defaultTitleEn ?? 'Containment request'}
    defaultPriority="HIGH" defaultRouting="SEQUENTIAL"
    fields={[
      { key: 'affectedSystem', labelEn: 'Affected system / account', labelAr: 'النظام/الحساب المتأثر', kind: 'text', required: true },
      { key: 'action', labelEn: 'Containment action', labelAr: 'إجراء الاحتواء', kind: 'select', required: true,
        options: [
          { value: 'ISOLATE', labelEn: 'Isolate host', labelAr: 'عزل المضيف' },
          { value: 'DISABLE', labelEn: 'Disable account', labelAr: 'تعطيل الحساب' },
          { value: 'BLOCK', labelEn: 'Block indicator', labelAr: 'حظر المؤشر' },
          { value: 'OTHER', labelEn: 'Other', labelAr: 'أخرى' },
        ] },
      { key: 'reversible', labelEn: 'Reversible?', labelAr: 'قابل للعكس؟', kind: 'select',
        options: [{ value: 'YES', labelEn: 'Yes', labelAr: 'نعم' }, { value: 'NO', labelEn: 'No', labelAr: 'لا' }] },
      { key: 'justification', labelEn: 'Justification', labelAr: 'المبرر', kind: 'textarea', required: true },
    ]} />;
}

/** §9.4a PDPL — Data-sharing request. Routes privacy→legal→executive (sequential) with a lawful
 *  basis + DPA; anchor to a data-sharing record where one exists. */
export function DataSharingRequestButton({ sourceRecordType = 'DataSharing', ...p }: Common) {
  return <GRCRequestDrawer {...p} sourceModule="PRIVACY" sourceRecordType={sourceRecordType}
    requestType="DATA_SHARING" Icon={Share2}
    label="Data-sharing request" labelAr="طلب مشاركة بيانات"
    headingEn="Data-sharing request" headingAr="طلب مشاركة بيانات"
    defaultTitleEn={p.defaultTitleEn ?? 'Data-sharing request'}
    defaultRouting="SEQUENTIAL"
    fields={[
      { key: 'recipientParty', labelEn: 'Recipient party', labelAr: 'الطرف المستلِم', kind: 'text', required: true },
      { key: 'dataCategories', labelEn: 'Data categories', labelAr: 'فئات البيانات', kind: 'textarea', required: true },
      { key: 'lawfulBasis', labelEn: 'Lawful basis', labelAr: 'الأساس النظامي', kind: 'select',
        options: [
          { value: 'PDPL_ART4', labelEn: 'PDPL Art. 4 — consent', labelAr: 'نظام حماية البيانات م.4 — الموافقة' },
          { value: 'PDPL_ART5', labelEn: 'PDPL Art. 5 — contract', labelAr: 'م.5 — تنفيذ عقد' },
          { value: 'PDPL_ART6', labelEn: 'PDPL Art. 6 — legal obligation', labelAr: 'م.6 — التزام نظامي' },
          { value: 'OTHER', labelEn: 'Other basis', labelAr: 'أساس آخر' },
        ] },
      { key: 'purpose', labelEn: 'Purpose', labelAr: 'الغرض', kind: 'textarea' },
      { key: 'retention', labelEn: 'Retention period', labelAr: 'مدة الاحتفاظ', kind: 'text' },
    ]} />;
}

/** §9.4b PDPL/TPRM — Processor (sub-processor) review. Anchor to the sub-processor; routes a DD
 *  review with a periodic re-review cadence. */
export function ProcessorReviewButton({ sourceRecordType = 'Subprocessor', ...p }: Common) {
  return <GRCRequestDrawer {...p} sourceModule="PRIVACY" sourceRecordType={sourceRecordType}
    requestType="PROCESSOR_REVIEW" Icon={ClipboardCheck}
    label="Processor review" labelAr="مراجعة معالج"
    headingEn="Processor review" headingAr="مراجعة معالِج بيانات"
    defaultTitleEn={p.defaultTitleEn ?? 'Processor review'}
    fields={[
      { key: 'processorName', labelEn: 'Processor', labelAr: 'المعالِج', kind: 'text', required: true },
      { key: 'service', labelEn: 'Service provided', labelAr: 'الخدمة المقدَّمة', kind: 'text' },
      { key: 'riskRating', labelEn: 'Risk rating', labelAr: 'تصنيف المخاطر', kind: 'select',
        options: [
          { value: 'LOW', labelEn: 'Low', labelAr: 'منخفض' },
          { value: 'MEDIUM', labelEn: 'Medium', labelAr: 'متوسط' },
          { value: 'HIGH', labelEn: 'High', labelAr: 'مرتفع' },
        ] },
      { key: 'reReviewMonths', labelEn: 'Re-review in (months)', labelAr: 'إعادة المراجعة خلال (أشهر)', kind: 'number' },
    ]} />;
}
