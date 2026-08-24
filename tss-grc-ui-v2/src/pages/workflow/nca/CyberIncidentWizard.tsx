import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertOctagon, AlertTriangle, ClipboardCheck, FileText, Search,
  Shield, ShieldCheck, Sparkles, Workflow,
} from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { WizardShell, type WizardStep } from '../../../components/workflow/WizardShell';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { ai } from '../../../lib/ai';
import { cyberApi } from '../../../lib/api';

/**
 * NCA §7.5 — Cyber Incident Response wizard (NIST 800-61).
 *
 * <p>9-step lifecycle:
 *   1. Detection — signal, source, IOC
 *   2. Triage — severity (1-5), scope, NCA-reportable?
 *   3. Containment — short-term + long-term
 *   4. Eradication — root system, malware, persistence
 *   5. Recovery — restore plan, monitoring
 *   6. Notifications — NCA / SAMA / PDPL gates
 *   7. Root-cause analysis — 5-Whys + AI assist
 *   8. Lessons learned — what worked, what failed
 *   9. Close out — CAPA + retest + sign-off
 */
type Severity = 1|2|3|4|5;

interface CyberDraft {
  titleEn: string;
  detectionSource: string;
  iocs: string;
  severity: Severity;
  scopeEn: string;
  ncaReportable: boolean;
  pdplReportable: boolean;
  samaReportable: boolean;

  containmentShortEn: string;
  containmentLongEn: string;
  eradicationEn: string;
  recoveryEn: string;
  monitoringEn: string;

  notificationLog: string;
  rcaEn: string;
  lessonsEn: string;

  capaOpened: boolean;
  retestDate: string;
  signedOffByCiso: boolean;
}

export function CyberIncidentWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [aiBusy, setAiBusy] = useState(false);
  const [draft, setDraft] = useState<CyberDraft>({
    titleEn: '', detectionSource: '', iocs: '',
    severity: 3, scopeEn: '',
    ncaReportable: false, pdplReportable: false, samaReportable: false,
    containmentShortEn: '', containmentLongEn: '',
    eradicationEn: '', recoveryEn: '', monitoringEn: '',
    notificationLog: '', rcaEn: '', lessonsEn: '',
    capaOpened: false, retestDate: '', signedOffByCiso: false,
  });

  function set<K extends keyof CyberDraft>(key: K, value: CyberDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function rcaFromAi() {
    if (!draft.titleEn.trim()) {
      toast.warning(isAr ? 'أدخل عنوان الحادث أولاً' : 'Enter the incident title first');
      return;
    }
    setAiBusy(true);
    try {
      const sitReps = draft.containmentShortEn
        ? [draft.containmentShortEn, draft.containmentLongEn, draft.eradicationEn]
            .filter(s => s.trim()).slice(0, 5)
        : undefined;
      const s = await ai.incidentRootCause({
        title: draft.titleEn, severity: draft.severity,
        triggerSource: draft.detectionSource,
        sitRepHighlights: sitReps,
      });
      const out = JSON.parse(s.outputJson) as { rootCauseDraftEn?: string };
      if (out.rootCauseDraftEn) set('rcaEn', out.rootCauseDraftEn);
      toast.success(isAr ? 'تم ملء مسودة السبب الجذري' : 'Root-cause draft filled from AI');
    } catch (e) { toast.danger((e as Error).message); }
    finally { setAiBusy(false); }
  }

  async function submit() {
    setSubmitting(true);
    try {
      // CyberIncident is a 9-step lifecycle — stitch the wizard fields into
      // the lessons + workflow status that the backend tracks.
      const lessons = [
        draft.rcaEn?.trim() ? `RCA: ${draft.rcaEn}` : null,
        draft.lessonsEn?.trim() ? `Lessons: ${draft.lessonsEn}` : null,
        draft.ncaReportable ? 'NCA-reportable: yes' : null,
        draft.pdplReportable ? 'PDPL-reportable: yes' : null,
        draft.samaReportable ? 'SAMA-reportable: yes' : null,
        draft.notificationLog?.trim() ? `Notifications: ${draft.notificationLog}` : null,
      ].filter(Boolean).join(' · ');

      const created = await cyberApi.createIncident({
        titleEn: draft.titleEn,
        incidentType: draft.detectionSource || 'OTHER',
        severity: draft.severity >= 5 ? 'CRITICAL'
              : draft.severity >= 4 ? 'HIGH'
              : draft.severity >= 3 ? 'MEDIUM'
              : 'LOW',
        impact: draft.scopeEn,
        workflowStatus: draft.signedOffByCiso ? 'CLOSED' : 'LESSONS_LEARNED',
        bcmHandoff: draft.samaReportable || draft.ncaReportable,
        lessonsLearned: lessons || undefined,
      });
      toast.success(isAr
        ? `تم تسجيل الحادث (#${created.id})`
        : `Incident #${created.id} recorded`);
      navigate('/incidents');
    } catch (e) { toast.danger((e as Error).message); }
    finally { setSubmitting(false); }
  }

  const steps: WizardStep[] = [
    {
      id: 'detect',
      title: '1. Detection', titleAr: '١. الاكتشاف',
      Icon: AlertOctagon,
      validate: () => !draft.titleEn.trim() || !draft.detectionSource.trim()
        ? (isAr ? 'العنوان والمصدر مطلوبان' : 'Title and detection source are required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'عنوان الحادث' : 'Incident title'} required full>
            <input className="input" value={draft.titleEn}
                   onChange={e => set('titleEn', e.target.value)}
                   placeholder="e.g. Confirmed ransomware on finance file server"/>
          </Field>
          <Field label={isAr ? 'مصدر الاكتشاف' : 'Detection source'} required>
            <select className="input" value={draft.detectionSource}
                    onChange={e => set('detectionSource', e.target.value)}>
              <option value="">—</option>
              <option value="SIEM_ALERT">SIEM alert</option>
              <option value="EDR_ALERT">EDR alert (Defender / CrowdStrike)</option>
              <option value="USER_REPORT">User report</option>
              <option value="VULN_SCAN">Vulnerability scanner</option>
              <option value="THREAT_INTEL">Threat-intel feed</option>
              <option value="EXTERNAL_PARTY">External party / supplier</option>
              <option value="REGULATOR">Regulator notification</option>
            </select>
          </Field>
          <Field label={isAr ? 'المؤشرات (IOCs)' : 'IOCs'} full>
            <textarea className="input" rows={3} value={draft.iocs}
                      onChange={e => set('iocs', e.target.value)}
                      placeholder="IPs, hashes, domains, user accounts…"/>
          </Field>
        </div>
      ),
    },
    {
      id: 'triage',
      title: '2. Triage', titleAr: '٢. الفرز',
      Icon: AlertTriangle,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الشدة' : 'Severity (1-5)'} required>
            <select className="input" value={draft.severity}
                    onChange={e => set('severity', Number(e.target.value) as Severity)}>
              <option value={1}>1 — Operational event</option>
              <option value={2}>2 — Significant incident</option>
              <option value={3}>3 — Major disruption</option>
              <option value={4}>4 — Crisis</option>
              <option value={5}>5 — National-level emergency</option>
            </select>
          </Field>
          <Field label={isAr ? 'النطاق المتأثر' : 'Affected scope'} full>
            <textarea className="input" rows={4} value={draft.scopeEn}
                      onChange={e => set('scopeEn', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'إشعار NCA مطلوب' : 'NCA-reportable?'} >
            <Toggle checked={draft.ncaReportable} onChange={v => set('ncaReportable', v)}
                    label={isAr ? 'نعم' : 'Yes'} />
          </Field>
          <Field label={isAr ? 'إشعار PDPL مطلوب' : 'PDPL-reportable?'} >
            <Toggle checked={draft.pdplReportable} onChange={v => set('pdplReportable', v)}
                    label={isAr ? 'نعم' : 'Yes'} />
          </Field>
          <Field label={isAr ? 'إشعار SAMA مطلوب' : 'SAMA-reportable?'} >
            <Toggle checked={draft.samaReportable} onChange={v => set('samaReportable', v)}
                    label={isAr ? 'نعم' : 'Yes'} />
          </Field>
        </div>
      ),
    },
    {
      id: 'contain',
      title: '3. Containment', titleAr: '٣. الاحتواء',
      Icon: Shield,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الاحتواء قصير المدى' : 'Short-term containment'} full>
            <textarea className="input" rows={4} value={draft.containmentShortEn}
                      onChange={e => set('containmentShortEn', e.target.value)} />
          </Field>
          <Field label={isAr ? 'الاحتواء طويل المدى' : 'Long-term containment'} full>
            <textarea className="input" rows={4} value={draft.containmentLongEn}
                      onChange={e => set('containmentLongEn', e.target.value)} />
          </Field>
        </div>
      ),
    },
    {
      id: 'eradicate',
      title: '4. Eradication', titleAr: '٤. الاستئصال',
      Icon: ShieldCheck,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الاستئصال' : 'Eradication steps'} full>
            <textarea className="input" rows={6} value={draft.eradicationEn}
                      onChange={e => set('eradicationEn', e.target.value)}
                      placeholder={isAr
                        ? 'حذف البرنامج الضار، إعادة التصوير، إبطال البيانات الاعتمادية…'
                        : 'malware removal, system reimage, credential invalidation…'} />
          </Field>
        </div>
      ),
    },
    {
      id: 'recover',
      title: '5. Recovery', titleAr: '٥. التعافي',
      Icon: ClipboardCheck,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'خطة الاستعادة' : 'Recovery plan'} full>
            <textarea className="input" rows={5} value={draft.recoveryEn}
                      onChange={e => set('recoveryEn', e.target.value)} />
          </Field>
          <Field label={isAr ? 'المراقبة المعززة' : 'Heightened monitoring'} full>
            <textarea className="input" rows={4} value={draft.monitoringEn}
                      onChange={e => set('monitoringEn', e.target.value)} />
          </Field>
        </div>
      ),
    },
    {
      id: 'notify',
      title: '6. Notifications', titleAr: '٦. الإشعارات',
      Icon: AlertOctagon,
      content: () => (
        <div className="wf-form-grid">
          {(draft.ncaReportable || draft.pdplReportable || draft.samaReportable) && (
            <div className="wf-summary" style={{ gridColumn: '1 / -1' }}>
              {draft.ncaReportable  && <Badge tone="info">NCA</Badge>}{' '}
              {draft.pdplReportable && <Badge tone="warning">PDPL Art. 20 — 72h</Badge>}{' '}
              {draft.samaReportable && <Badge tone="info">SAMA</Badge>}
            </div>
          )}
          <Field label={isAr ? 'سجل الإشعارات المرسلة' : 'Notification log (one per line: regulator + reference)'} full>
            <textarea className="input" rows={6} value={draft.notificationLog}
                      onChange={e => set('notificationLog', e.target.value)}
                      placeholder={'NCA 2026-CYBER-001 filed via portal at 14:32\nSDAIA-PDPL-…'}/>
          </Field>
        </div>
      ),
    },
    {
      id: 'rca',
      title: '7. Root cause', titleAr: '٧. السبب الجذري',
      Icon: Search,
      content: () => (
        <div className="wf-form-grid">
          <div style={{ gridColumn: '1 / -1', display: 'flex', justifyContent: 'flex-end' }}>
            <Button variant="secondary" size="sm" Icon={Sparkles}
                    onClick={rcaFromAi} disabled={aiBusy}>
              {aiBusy ? '…' : (isAr ? 'مسودة بالذكاء الاصطناعي' : 'Draft with AI')}
            </Button>
          </div>
          <Field label={isAr ? 'تحليل السبب الجذري' : 'Root-cause analysis'} full>
            <textarea className="input" rows={8} value={draft.rcaEn}
                      onChange={e => set('rcaEn', e.target.value)}/>
          </Field>
        </div>
      ),
    },
    {
      id: 'lessons',
      title: '8. Lessons', titleAr: '٨. الدروس',
      Icon: FileText,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'الدروس المستفادة' : 'Lessons learned'} full>
            <textarea className="input" rows={6} value={draft.lessonsEn}
                      onChange={e => set('lessonsEn', e.target.value)}/>
          </Field>
        </div>
      ),
    },
    {
      id: 'close',
      title: '9. Close out', titleAr: '٩. الإغلاق',
      Icon: Workflow,
      validate: () => !draft.signedOffByCiso
        ? (isAr ? 'يلزم اعتماد CISO' : 'CISO sign-off required')
        : null,
      content: () => (
        <div className="wf-form-grid">
          <Field label={isAr ? 'إجراء تصحيحي مفتوح' : 'CAPA opened'} full>
            <Toggle checked={draft.capaOpened} onChange={v => set('capaOpened', v)}
                    label={isAr ? 'تم فتح CAPA' : 'CAPA created'}/>
          </Field>
          <Field label={isAr ? 'موعد إعادة الاختبار' : 'Retest date'}>
            <input className="input" type="date" value={draft.retestDate}
                   onChange={e => set('retestDate', e.target.value)}/>
          </Field>
          <Field label={isAr ? 'اعتماد CISO' : 'CISO sign-off'} required full>
            <Toggle checked={draft.signedOffByCiso} onChange={v => set('signedOffByCiso', v)}
                    label={isAr ? 'مُعتمد' : 'Signed off'}/>
          </Field>
        </div>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'الاستجابة لحادث سيبراني' : 'Cyber incident response'}
        sub={isAr ? 'معالج من ٩ خطوات وفق NIST 800-61' : '9-step lifecycle aligned with NIST 800-61'}
        eyebrow={isAr ? 'الأمن السيبراني / NCA' : 'NCA / Cybersecurity'}
      />
      <WizardShell
        steps={steps}
        active={step}
        onChange={setStep}
        rtl={isAr}
        submitting={submitting}
        onSubmit={submit}
        onCancel={() => navigate('/incidents')}
        submitLabel={isAr ? 'إغلاق الحادث' : 'Close incident'}
      />
    </Shell>
  );
}

function Field({ label, required, full, children }: {
  label: string; required?: boolean; full?: boolean; children: React.ReactNode;
}) {
  return (
    <label className={'wf-field' + (full ? ' wf-field-full' : '')}>
      <span className="wf-label">{label}{required && <span className="wf-required"> *</span>}</span>
      {children}
    </label>
  );
}

function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <label className="wf-toggle">
      <input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)}/>
      <span>{label}</span>
    </label>
  );
}
