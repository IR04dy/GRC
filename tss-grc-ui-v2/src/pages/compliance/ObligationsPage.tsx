import { useEffect, useMemo, useState } from 'react';
import {
  ClipboardList, AlertTriangle, Clock3, CheckCircle2, Plus, Filter, Briefcase,
  FileCheck2,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { getUser } from '../../lib/auth';
import {
  listObligations,
  getObligationKpis,
  createObligation,
  type Obligation,
  type ObligationKpis,
  type Regulator,
  type ObligationType,
  type Frequency,
  type Severity,
  type LifecycleStage,
  REGULATORS,
  TYPES,
  FREQUENCIES,
  SEVERITIES,
  LIFECYCLE_STAGES,
  REGULATOR_LABELS,
  TYPE_LABELS,
  FREQUENCY_LABELS,
  SEVERITY_LABELS,
  LIFECYCLE_LABELS,
  lifecycleTone,
  severityTone,
  daysUntilDue,
} from '../../lib/obligations';

/**
 * Sprint 11 — Regulatory Obligation Register landing page.
 *
 * <p>Saudi-native bilingual: "سجل الالتزامات الرقابية" (Regulatory
 * Obligation Register). KPI tiles cover the four numbers the
 * audit-committee actually asks for — active, overdue, due-this-month,
 * attested-this-cycle — and the table below is filterable by
 * regulator + lifecycle.
 */
export function ObligationsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [items, setItems] = useState<Obligation[]>([]);
  const [kpis, setKpis] = useState<ObligationKpis | null>(null);
  const [loading, setLoading] = useState(false);
  const [regulatorFilter, setRegulatorFilter] = useState<Regulator | ''>('');
  const [stageFilter, setStageFilter] = useState<LifecycleStage | ''>('');
  const [showCreate, setShowCreate] = useState(false);

  async function reload() {
    setLoading(true);
    try {
      const [list, k] = await Promise.all([listObligations(), getObligationKpis()]);
      setItems(Array.isArray(list) ? list : []);
      setKpis(k);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل الالتزامات' : 'Failed to load obligations'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    return items.filter(o =>
      (regulatorFilter === '' || o.regulator === regulatorFilter)
      && (stageFilter === '' || o.lifecycleStage === stageFilter));
  }, [items, regulatorFilter, stageFilter]);

  const fmtDate = (d?: string) =>
    d ? new Date(d).toLocaleDateString(isAr ? 'ar-SA' : 'en-GB') : '—';

  const createFields: FieldDef[] = useMemo(() => [
    { kind: 'text', key: 'obligationCode', span: 4, label: isAr ? 'الرمز' : 'Code', placeholder: 'OBL-...' },
    { kind: 'select', key: 'regulator', required: true, span: 4, label: isAr ? 'الجهة *' : 'Regulator *',
      options: REGULATORS.map(r => ({ value: r, label: tlabel(REGULATOR_LABELS, r, isAr) })) },
    { kind: 'select', key: 'obligationType', required: true, span: 4, label: isAr ? 'النوع *' : 'Type *',
      options: TYPES.map(x => ({ value: x, label: tlabel(TYPE_LABELS, x, isAr) })) },
    { kind: 'text', key: 'titleEn', required: true, span: 6, label: isAr ? 'العنوان (إنجليزي) *' : 'Title (EN) *' },
    { kind: 'text', key: 'titleAr', required: true, span: 6, label: isAr ? 'العنوان (عربي) *' : 'Title (AR) *' },
    { kind: 'select', key: 'frequency', required: true, span: 4, label: isAr ? 'التكرار *' : 'Frequency *',
      options: FREQUENCIES.map(x => ({ value: x, label: tlabel(FREQUENCY_LABELS, x, isAr) })) },
    { kind: 'select', key: 'severityIfMissed', span: 4, label: isAr ? 'الخطورة عند الإخلال' : 'Severity if missed',
      options: SEVERITIES.map(x => ({ value: x, label: tlabel(SEVERITY_LABELS, x, isAr) })) },
    { kind: 'number', key: 'leadTimeDays', span: 4, min: 0, label: isAr ? 'مهلة التذكير (أيام)' : 'Lead time (days)' },
    { kind: 'date', key: 'nextDueDate', required: true, span: 6, label: isAr ? 'تاريخ الاستحقاق *' : 'Next due date *' },
    { kind: 'date', key: 'firstDueDate', span: 6, label: isAr ? 'أول استحقاق' : 'First due date' },
    { kind: 'text', key: 'regulationReference', span: 6, label: isAr ? 'المرجع النظامي' : 'Regulation reference' },
    { kind: 'text', key: 'businessUnitEn', span: 6, label: isAr ? 'الوحدة التنظيمية' : 'Business unit' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2, label: isAr ? 'الوصف' : 'Description' },
  ], [isAr]);

  async function onCreate(v: Record<string, unknown>) {
    const titleEn = String(v.titleEn ?? '').trim();
    const titleAr = String(v.titleAr ?? '').trim();
    const nextDueDate = String(v.nextDueDate ?? '').trim();
    if (!titleEn)     { toast.danger(isAr ? 'العنوان (إنجليزي) مطلوب' : 'Title (EN) is required'); return; }
    if (!titleAr)     { toast.danger(isAr ? 'العنوان (عربي) مطلوب' : 'Title (AR) is required'); return; }
    if (!nextDueDate) { toast.danger(isAr ? 'تاريخ الاستحقاق مطلوب' : 'Next due date is required'); return; }
    const opt = (s: unknown) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    const num = (s: unknown) => { const x = String(s ?? '').trim(); return x ? Number(x) : undefined; };
    try {
      await createObligation({
        obligationCode: opt(v.obligationCode),
        titleEn, titleAr,
        descriptionEn: opt(v.descriptionEn),
        regulator: (v.regulator as Regulator) || 'SAMA',
        regulationReference: opt(v.regulationReference),
        obligationType: (v.obligationType as ObligationType) || 'REPORTING',
        frequency: (v.frequency as Frequency) || 'MONTHLY',
        severityIfMissed: ((opt(v.severityIfMissed) as Severity) ?? 'MEDIUM'),
        nextDueDate,
        firstDueDate: opt(v.firstDueDate),
        leadTimeDays: num(v.leadTimeDays) ?? 0,
        // Registrar owns it by default; reassignable via edit later.
        ownerUserId: getUser()?.userId ?? 1,
        businessUnitEn: opt(v.businessUnitEn),
      });
      toast.success(isAr ? 'تم إنشاء الالتزام' : 'Obligation created');
      setShowCreate(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إنشاء الالتزام' : 'Create failed'));
    }
  }

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'الالتزام والامتثال' : 'Compliance'}
        title={isAr ? 'سجل الالتزامات الرقابية' : 'Regulatory Obligation Register'}
        sub={isAr
          ? 'كل التزام رقابي متكرر للبنك في مكان واحد: المسؤول، التكرار، تاريخ الاستحقاق، والإقرار الدوري — وفق إطار الالتزام لمؤسسة النقد §3.2 وضوابط الهيئة الوطنية للأمن السيبراني ECC-2 1-1.'
          : 'Every recurring regulatory obligation for the bank, in one place: owner, frequency, due date, periodic attestation — per SAMA Compliance Framework §3.2 and NCA ECC-2 1-1.'}
        actions={
          <div className="cluster" style={{ gap: 8 }}>
            <Link to="/compliance/obligation-campaigns" className="btn btn-secondary">
              <Briefcase size={14} style={{ marginInlineEnd: 4 }} />
              {isAr ? 'حملات الإقرار' : 'Attestation campaigns'}
            </Link>
            <Button Icon={Plus} onClick={() => setShowCreate(true)}>
              {isAr ? 'التزام جديد' : 'New obligation'}
            </Button>
          </div>
        }
      />

      <div className="kpi-grid-sm" style={{ marginBottom: 'var(--sp-6)' }}>
        <Kpi
          Icon={ClipboardList}
          label={isAr ? 'الالتزامات النشطة' : 'Active obligations'}
          value={String(kpis?.activeCount ?? '—')}
          tone="info"
          onClick={() => setStageFilter('ACTIVE')}
          active={stageFilter === 'ACTIVE'}
        />
        <Kpi
          Icon={AlertTriangle}
          label={isAr ? 'متأخرات' : 'Overdue'}
          value={String(kpis?.overdueCount ?? '—')}
          tone={(kpis?.overdueCount ?? 0) > 0 ? 'danger' : 'neutral'}
          to="/compliance"
        />
        <Kpi
          Icon={Clock3}
          label={isAr ? 'مستحقة هذا الشهر' : 'Due this month'}
          value={String(kpis?.dueThisMonthCount ?? '—')}
          tone={(kpis?.dueThisMonthCount ?? 0) > 0 ? 'warning' : 'neutral'}
          to="/compliance"
        />
        <Kpi
          Icon={CheckCircle2}
          label={isAr ? 'مُقَرَّة هذه الدورة' : 'Attested this cycle'}
          value={String(kpis?.attestedThisCycleCount ?? '—')}
          tone="success"
          to="/compliance/obligation-campaigns"
        />
      </div>

      <Card title={isAr ? 'سجل الالتزامات' : 'Obligation register'} Icon={FileCheck2}>
        {/* Filter row */}
        <div className="cluster" style={{ gap: 8, marginBottom: 'var(--sp-6)' }}>
          <Filter size={14} style={{ color: 'var(--ink-3)' }} />
          <select
            value={regulatorFilter}
            onChange={e => setRegulatorFilter(e.target.value as Regulator | '')}
            className="input" style={{ width: 'auto', maxWidth: 220 }}
          >
            <option value="">{isAr ? 'كل الجهات' : 'All regulators'}</option>
            {REGULATORS.map(r => (
              <option key={r} value={r}>
                {tlabel(REGULATOR_LABELS, r, isAr)}
              </option>
            ))}
          </select>
          <select
            value={stageFilter}
            onChange={e => setStageFilter(e.target.value as LifecycleStage | '')}
            className="input" style={{ width: 'auto', maxWidth: 220 }}
          >
            <option value="">{isAr ? 'كل الحالات' : 'All stages'}</option>
            {LIFECYCLE_STAGES.map(s => (
              <option key={s} value={s}>
                {tlabel(LIFECYCLE_LABELS, s, isAr)}
              </option>
            ))}
          </select>
          <span style={{ color: 'var(--ink-3)', fontSize: 13 }}>
            {filtered.length === items.length
              ? `${items.length} ${isAr ? 'التزام' : 'obligations'}`
              : `${filtered.length} / ${items.length}`}
          </span>
        </div>

        {loading && items.length === 0 ? (
          <InlineEmpty text={isAr ? 'جارٍ التحميل…' : 'Loading…'} />
        ) : filtered.length === 0 ? (
          <InlineEmpty text={isAr ? 'لا توجد التزامات مطابقة.' : 'No matching obligations.'} />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="grc-table">
              <thead>
                <tr>
                  <th>{isAr ? 'الرمز' : 'Code'}</th>
                  <th>{isAr ? 'العنوان' : 'Title'}</th>
                  <th>{isAr ? 'الجهة' : 'Regulator'}</th>
                  <th>{isAr ? 'النوع' : 'Type'}</th>
                  <th>{isAr ? 'التكرار' : 'Frequency'}</th>
                  <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                  <th>{isAr ? 'تاريخ الاستحقاق' : 'Next due'}</th>
                  <th>{isAr ? 'الحالة' : 'Stage'}</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(o => {
                  const d = daysUntilDue(o.nextDueDate);
                  const dueColor = d < 0 ? 'var(--danger)'
                    : d <= 7 ? 'var(--warning)'
                    : d <= 30 ? 'var(--info)'
                    : 'var(--ink-2)';
                  return (
                    <tr key={o.id}>
                      <td><code style={{ fontSize: 12 }}>{o.obligationCode}</code></td>
                      <td>
                        <Link to={`/compliance/obligations/${o.id}`} className="link">
                          {isAr ? o.titleAr : o.titleEn}
                        </Link>
                        {o.businessUnitEn && (
                          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                            {isAr ? (o.businessUnitAr ?? o.businessUnitEn) : o.businessUnitEn}
                          </div>
                        )}
                      </td>
                      <td style={{ fontSize: 13 }}>
                        {tlabel(REGULATOR_LABELS, o.regulator, isAr)}
                      </td>
                      <td style={{ fontSize: 13 }}>
                        {tlabel(TYPE_LABELS, o.obligationType, isAr)}
                      </td>
                      <td style={{ fontSize: 13 }}>
                        {tlabel(FREQUENCY_LABELS, o.frequency, isAr)}
                      </td>
                      <td>
                        <Badge tone={severityTone(o.severityIfMissed)}>
                          {tlabel(SEVERITY_LABELS, o.severityIfMissed, isAr)}
                        </Badge>
                      </td>
                      <td style={{ fontSize: 13, color: dueColor, fontVariantNumeric: 'tabular-nums' }}>
                        {fmtDate(o.nextDueDate)}
                        <div style={{ fontSize: 11, color: dueColor }}>
                          {d < 0
                            ? (isAr ? `متأخر ${-d} يوماً` : `${-d}d overdue`)
                            : d === 0
                              ? (isAr ? 'اليوم' : 'today')
                              : (isAr ? `بعد ${d} يوماً` : `in ${d}d`)}
                        </div>
                      </td>
                      <td>
                        <Badge tone={lifecycleTone(o.lifecycleStage)}>
                          {tlabel(LIFECYCLE_LABELS, o.lifecycleStage, isAr)}
                        </Badge>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <FormModal
        open={showCreate}
        title={isAr ? 'التزام رقابي جديد' : 'New regulatory obligation'}
        fields={createFields}
        initial={{ regulator: 'SAMA', obligationType: 'REPORTING', frequency: 'MONTHLY', severityIfMissed: 'MEDIUM', leadTimeDays: '14' }}
        size="lg"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setShowCreate(false)}
        onSubmit={onCreate}
      />
    </Shell>
  );
}
