import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ClipboardList, Plus, ChevronLeft } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  kriApi,
  formatKriValue,
  KRI_CATEGORY_LABEL, KRI_FREQUENCY_LABEL, KRI_SOURCE_LABEL,
  KRI_STATUS_LABEL, KRI_STATUS_TONE,
  type KriDefinitionRequest, type KriDefinitionResponse,
} from '../../lib/kriEngine';

/**
 * KRI Admin — Sprint 13.
 *
 * Operator surface for the seven KRI roles (RISK_MANAGER, CISO,
 * COMPLIANCE_OFFICER, PLATFORM_ADMIN with write; auditors read-only).
 *
 * Lists every KRI with category, status, frequency, owner. The
 * inline "create" form covers the minimum-viable definition; richer
 * editing happens on the detail page.
 *
 * Saudi-native bilingual heading:
 *   "إدارة مؤشّرات المخاطر" = KRI administration
 */
export function KriAdminPage() {
  const { lang } = useI18n();
  const [defs, setDefs] = useState<KriDefinitionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [draft, setDraft] = useState<KriDefinitionRequest>({
    titleEn: '', titleAr: '',
    category: 'OPERATIONAL', measurementUnit: 'COUNT',
    valueDirection: 'LOWER_IS_BETTER',
    frequency: 'MONTHLY', sourceQueryType: 'MANUAL',
    isActive: true,
  });

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setDefs(await kriApi.listDefinitions());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load KRIs');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); }, []);

  async function create() {
    if (!draft.titleEn || !draft.titleAr) {
      setError(lang === 'ar' ? 'العنوان بالعربية والإنجليزية مطلوب' : 'EN + AR title required');
      return;
    }
    setCreating(true);
    setError(null);
    try {
      await kriApi.createDefinition(draft);
      setDraft({
        titleEn: '', titleAr: '',
        category: 'OPERATIONAL', measurementUnit: 'COUNT',
        valueDirection: 'LOWER_IS_BETTER',
        frequency: 'MONTHLY', sourceQueryType: 'MANUAL',
        isActive: true,
      });
      setShowForm(false);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create KRI');
    } finally {
      setCreating(false);
    }
  }

  async function toggle(d: KriDefinitionResponse) {
    try {
      if (d.isActive) await kriApi.deactivate(d.externalId);
      else await kriApi.activate(d.externalId);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to update KRI');
    }
  }

  const cols: Column<KriDefinitionResponse>[] = [
    {
      header: lang === 'ar' ? 'الرمز' : 'Code', width: '200px',
      cell: d => <Link to={`/risk/kri/${d.externalId}`}>
        <code style={{ fontSize: 12 }}>{d.kriCode}</code>
      </Link>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: d => <span>{lang === 'ar' ? d.titleAr : d.titleEn}</span>,
    },
    {
      header: lang === 'ar' ? 'الفئة' : 'Category', width: '160px',
      cell: d => KRI_CATEGORY_LABEL[d.category][lang === 'ar' ? 'ar' : 'en'],
    },
    {
      header: lang === 'ar' ? 'الدورية' : 'Frequency', width: '110px',
      cell: d => KRI_FREQUENCY_LABEL[d.frequency][lang === 'ar' ? 'ar' : 'en'],
    },
    {
      header: lang === 'ar' ? 'المصدر' : 'Source', width: '140px',
      cell: d => KRI_SOURCE_LABEL[d.sourceQueryType][lang === 'ar' ? 'ar' : 'en'],
    },
    {
      header: lang === 'ar' ? 'القيمة الأخيرة' : 'Latest', width: '120px', align: 'end',
      cell: d => <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: 12 }}>
        {formatKriValue(d.latestValue, d.measurementUnit)}
      </span>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '120px', align: 'center',
      cell: d => d.latestStatus
        ? <Badge tone={KRI_STATUS_TONE[d.latestStatus]} dot>
            {KRI_STATUS_LABEL[d.latestStatus][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <Badge tone="neutral">—</Badge>,
    },
    {
      header: lang === 'ar' ? 'فعّال' : 'Active', width: '100px', align: 'center',
      cell: d => (
        <button onClick={() => toggle(d)}
                style={{ fontSize: 12, padding: '4px 8px',
                         border: '1px solid var(--border)', borderRadius: 4,
                         background: d.isActive ? 'var(--bg-success-subtle, transparent)' : 'transparent',
                         cursor: 'pointer' }}>
          {d.isActive ? (lang === 'ar' ? 'مُفعّل' : 'On')
                      : (lang === 'ar' ? 'موقوف' : 'Off')}
        </button>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'إدارة مؤشّرات المخاطر' : 'KRI administration'}
      sub={lang === 'ar' ? 'سجل التعريفات' : 'Definitions register'}
    >
      <Link to="/risk/kri/dashboard"
            style={{ fontSize: 12, color: 'var(--ink-3)', display: 'inline-block', marginBottom: 4 }}>
        <ChevronLeft size={12} style={{ verticalAlign: '-1px' }} />
        {lang === 'ar' ? 'العودة إلى لوحة المؤشّرات' : 'Back to KRI dashboard'}
      </Link>
      <PageHeader
        eyebrow={lang === 'ar' ? 'إدارة المخاطر' : 'Enterprise Risk'}
        title={lang === 'ar' ? 'إدارة مؤشّرات المخاطر' : 'KRI administration'}
        sub={lang === 'ar'
          ? 'إنشاء وتعديل وتفعيل/إيقاف تعريفات مؤشّرات المخاطر. الكتابة محصورة بأدوار: مدير المخاطر، رئيس أمن المعلومات، مسؤول الامتثال، مدير المنصة.'
          : 'Create, edit and toggle KRI definitions. Write access is limited to the RISK_MANAGER / CISO / COMPLIANCE_OFFICER / PLATFORM_ADMIN roles.'}
        actions={
          <button onClick={() => setShowForm(s => !s)}
                  style={{ padding: '6px 12px', border: '1px solid var(--primary)',
                           borderRadius: 6, background: 'var(--primary)', color: 'white',
                           cursor: 'pointer', fontSize: 13 }}>
            <Plus size={14} style={{ verticalAlign: '-2px', marginInlineEnd: 6 }} />
            {lang === 'ar' ? 'مؤشّر جديد' : 'New KRI'}
          </button>
        }
      />

      {error && (
        <Card padded>
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        </Card>
      )}

      {showForm && (
        <Card padded>
          <h3 style={{ marginTop: 0 }}>
            {lang === 'ar' ? 'تعريف مؤشّر جديد' : 'New KRI definition'}
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
            <FormField label={lang === 'ar' ? 'العنوان (الإنجليزية)' : 'Title (English)'} required>
              <input value={draft.titleEn}
                     onChange={e => setDraft(d => ({ ...d, titleEn: e.target.value }))} />
            </FormField>
            <FormField label={lang === 'ar' ? 'العنوان (العربية)' : 'Title (Arabic)'} required>
              <input value={draft.titleAr}
                     onChange={e => setDraft(d => ({ ...d, titleAr: e.target.value }))} dir="rtl" />
            </FormField>
            <FormField label={lang === 'ar' ? 'الفئة' : 'Category'}>
              <select value={draft.category}
                      onChange={e => setDraft(d => ({ ...d, category: e.target.value as KriDefinitionRequest['category'] }))}>
                {Object.keys(KRI_CATEGORY_LABEL).map(k => (
                  <option key={k} value={k}>
                    {KRI_CATEGORY_LABEL[k as keyof typeof KRI_CATEGORY_LABEL][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label={lang === 'ar' ? 'الوحدة' : 'Unit'}>
              <select value={draft.measurementUnit}
                      onChange={e => setDraft(d => ({ ...d, measurementUnit: e.target.value as KriDefinitionRequest['measurementUnit'] }))}>
                {['COUNT', 'PERCENTAGE', 'RATIO', 'CURRENCY_SAR', 'DAYS', 'HOURS', 'BPS'].map(u => (
                  <option key={u} value={u}>{u}</option>
                ))}
              </select>
            </FormField>
            <FormField label={lang === 'ar' ? 'الاتجاه' : 'Direction'}>
              <select value={draft.valueDirection}
                      onChange={e => setDraft(d => ({ ...d, valueDirection: e.target.value as KriDefinitionRequest['valueDirection'] }))}>
                <option value="LOWER_IS_BETTER">
                  {lang === 'ar' ? 'الأقل أفضل' : 'lower = better'}
                </option>
                <option value="HIGHER_IS_BETTER">
                  {lang === 'ar' ? 'الأعلى أفضل' : 'higher = better'}
                </option>
              </select>
            </FormField>
            <FormField label={lang === 'ar' ? 'الدورية' : 'Frequency'}>
              <select value={draft.frequency}
                      onChange={e => setDraft(d => ({ ...d, frequency: e.target.value as KriDefinitionRequest['frequency'] }))}>
                {Object.keys(KRI_FREQUENCY_LABEL).map(f => (
                  <option key={f} value={f}>
                    {KRI_FREQUENCY_LABEL[f as keyof typeof KRI_FREQUENCY_LABEL][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label={lang === 'ar' ? 'الهدف' : 'Target'}>
              <input type="number" value={draft.targetValue ?? ''}
                     onChange={e => setDraft(d => ({ ...d, targetValue: e.target.value ? Number(e.target.value) : undefined }))} />
            </FormField>
            <FormField label={lang === 'ar' ? 'حدّ الأخضر' : 'Green threshold'}>
              <input type="number" value={draft.greenThreshold ?? ''}
                     onChange={e => setDraft(d => ({ ...d, greenThreshold: e.target.value ? Number(e.target.value) : undefined }))} />
            </FormField>
            <FormField label={lang === 'ar' ? 'حدّ الكهرماني' : 'Amber threshold'}>
              <input type="number" value={draft.amberThreshold ?? ''}
                     onChange={e => setDraft(d => ({ ...d, amberThreshold: e.target.value ? Number(e.target.value) : undefined }))} />
            </FormField>
            <FormField label={lang === 'ar' ? 'حدّ الأحمر' : 'Red anchor'}>
              <input type="number" value={draft.redThreshold ?? ''}
                     onChange={e => setDraft(d => ({ ...d, redThreshold: e.target.value ? Number(e.target.value) : undefined }))} />
            </FormField>
          </div>
          <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
            <button onClick={create} disabled={creating}
                    style={{ padding: '6px 12px', border: '1px solid var(--primary)',
                             borderRadius: 4, background: 'var(--primary)', color: 'white',
                             cursor: 'pointer', opacity: creating ? 0.5 : 1, fontSize: 13 }}>
              {lang === 'ar' ? 'إنشاء' : 'Create'}
            </button>
            <button onClick={() => setShowForm(false)}
                    style={{ padding: '6px 12px', border: '1px solid var(--border)',
                             borderRadius: 4, background: 'transparent',
                             cursor: 'pointer', fontSize: 13 }}>
              {lang === 'ar' ? 'إلغاء' : 'Cancel'}
            </button>
          </div>
        </Card>
      )}

      <Card padded>
        <h3 style={{ marginTop: 0 }}>
          {lang === 'ar' ? 'كل المؤشّرات' : 'All KRIs'}
          <span style={{ marginInlineStart: 8, fontSize: 12, color: 'var(--ink-3)' }}>
            · {defs.length}
          </span>
        </h3>
        {loading
          ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : <DataTable
              columns={cols}
              rows={defs}
              empty={<EmptyState
                Icon={ClipboardList}
                title={lang === 'ar' ? 'لا توجد مؤشّرات بعد' : 'No KRIs defined yet'}
                sub={lang === 'ar'
                  ? 'استخدم زرّ «مؤشّر جديد» أعلاه لإنشاء أول تعريف.'
                  : 'Use the "New KRI" button above to create the first definition.'} />}
            />
        }
      </Card>
    </Shell>
  );
}

interface FormFieldProps {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}
function FormField({ label, required, children }: FormFieldProps) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12, color: 'var(--ink-3)' }}>
      <span>{label}{required ? ' *' : ''}</span>
      <div style={{ ['--input-padding' as string]: '6px 8px' }} className="kri-form-field">
        {children}
      </div>
      <style>{`
        .kri-form-field input, .kri-form-field select {
          width: 100%;
          padding: 6px 8px;
          border: 1px solid var(--border);
          border-radius: 4px;
          background: var(--bg);
          color: var(--ink-1);
          font-size: 13px;
        }
      `}</style>
    </label>
  );
}
