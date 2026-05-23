import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Plus, Send } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listTemplates,
  AUDIENCES, SCENARIO_CATEGORIES, SEVERITY_BANDS, APPROVAL_STATES,
  AUDIENCE_LABELS, SCENARIO_LABELS, SEVERITY_LABELS, APPROVAL_LABELS,
  severityTone, approvalTone,
  type CrisisCommsTemplate,
  type Audience, type ScenarioCategory, type SeverityBand, type ApprovalStatus,
} from '../../lib/crisisComms';

/**
 * Slice 9-E — Crisis Communications template register.
 *
 * <p>Saudi-native landing for the BCM Manager / CISO / Comms Lead. Lists
 * every pre-approved crisis-comms template with filters for audience,
 * scenario, severity, and approval status. Click a row to edit or run
 * the approval state machine. Click "Dispatch cascade" to go straight
 * to the dispatch wizard.
 *
 * <p>AR title is "قوالب الاتصالات في حالات الأزمات" — Saudi-native phrasing
 * matching the dispatch wizard wording.
 */
export function CrisisCommsTemplatesPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [items, setItems] = useState<CrisisCommsTemplate[]>([]);
  const [loading, setLoading] = useState(false);

  const [audience, setAudience] = useState<Audience | ''>('');
  const [scenario, setScenario] = useState<ScenarioCategory | ''>('');
  const [severity, setSeverity] = useState<SeverityBand | ''>('');
  const [approval, setApproval] = useState<ApprovalStatus | ''>('');
  const [search, setSearch] = useState('');

  async function reload() {
    setLoading(true);
    try {
      const list = await listTemplates({
        audience: audience || undefined,
        scenario: scenario || undefined,
        approvalStatus: approval || undefined,
      });
      setItems(Array.isArray(list) ? list : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل قوالب الاتصالات' : 'Failed to load crisis-comms templates'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, [audience, scenario, approval]); // eslint-disable-line react-hooks/exhaustive-deps

  // Client-side severity filter + free-text search (server applies the
  // other three).
  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return items.filter(t => {
      if (severity && t.severityBand !== severity) return false;
      if (!needle) return true;
      const hay = (t.templateCode + ' ' + t.titleEn + ' ' + t.titleAr
        + ' ' + (t.regulatoryAnchor || '')).toLowerCase();
      return hay.includes(needle);
    });
  }, [items, severity, search]);

  // ─── KPIs ───────────────────────────────────────────────────────────
  const approvedCount = items.filter(t => t.approvalStatus === 'APPROVED_BY_CISO').length;
  const pendingCount  = items.filter(t => t.approvalStatus === 'IN_REVIEW'
                                       || t.approvalStatus === 'APPROVED_BY_LEGAL').length;
  const draftCount    = items.filter(t => t.approvalStatus === 'DRAFT').length;

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'استمرارية الأعمال — الاتصالات' : 'BCM — Crisis Communications'}
        title={isAr ? 'قوالب الاتصالات في حالات الأزمات' : 'Crisis Communications Templates'}
        sub={isAr
          ? 'قوالب جاهزة ومعتمدة من الإدارة القانونية وأمن المعلومات — مجلس الإدارة، الجهات التنظيمية، العملاء، الموظفون، الشركاء، الإعلام، الجمهور.'
          : 'Pre-approved templates across 7 stakeholder groups — Board, Regulator, Customers, Staff, Partners, Media, Public. Approved by Legal + CISO.'}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="primary" onClick={() => navigate('/bcm/comms/dispatch')}>
              <Send size={16} style={{ marginInlineEnd: 6 }} />
              {isAr ? 'إرسال تدفّق اتصالات' : 'Dispatch cascade'}
            </Button>
            <Button variant="secondary" onClick={() => navigate('/bcm/comms/templates/new')}>
              <Plus size={16} style={{ marginInlineEnd: 6 }} />
              {isAr ? 'قالب جديد' : 'New template'}
            </Button>
          </div>
        }
      />

      {/* KPI strip */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 16,
      }}>
        <Card>
          <div style={{ padding: 16 }}>
            <div style={{ color: 'var(--ink-3)', fontSize: 12 }}>
              {isAr ? 'معتمَد من أمن المعلومات' : 'Approved by CISO'}
            </div>
            <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--success)' }}>
              {approvedCount}
            </div>
          </div>
        </Card>
        <Card>
          <div style={{ padding: 16 }}>
            <div style={{ color: 'var(--ink-3)', fontSize: 12 }}>
              {isAr ? 'بانتظار الاعتماد' : 'Pending approval'}
            </div>
            <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--warning)' }}>
              {pendingCount}
            </div>
          </div>
        </Card>
        <Card>
          <div style={{ padding: 16 }}>
            <div style={{ color: 'var(--ink-3)', fontSize: 12 }}>
              {isAr ? 'مسودات' : 'Drafts'}
            </div>
            <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--ink-2)' }}>
              {draftCount}
            </div>
          </div>
        </Card>
      </div>

      {/* Filters */}
      <Card>
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)',
          gap: 12, padding: 16, alignItems: 'end',
        }}>
          <FilterSelect
            label={isAr ? 'الجمهور' : 'Audience'}
            value={audience}
            onChange={v => setAudience(v as Audience | '')}
            options={[['', isAr ? 'الكل' : 'All'] as [string, string]].concat(
              AUDIENCES.map(a => [a, AUDIENCE_LABELS[a][isAr ? 'ar' : 'en']]))}
          />
          <FilterSelect
            label={isAr ? 'فئة السيناريو' : 'Scenario'}
            value={scenario}
            onChange={v => setScenario(v as ScenarioCategory | '')}
            options={[['', isAr ? 'الكل' : 'All'] as [string, string]].concat(
              SCENARIO_CATEGORIES.map(s => [s, SCENARIO_LABELS[s][isAr ? 'ar' : 'en']]))}
          />
          <FilterSelect
            label={isAr ? 'الخطورة' : 'Severity'}
            value={severity}
            onChange={v => setSeverity(v as SeverityBand | '')}
            options={[['', isAr ? 'الكل' : 'All'] as [string, string]].concat(
              SEVERITY_BANDS.map(s => [s, SEVERITY_LABELS[s][isAr ? 'ar' : 'en']]))}
          />
          <FilterSelect
            label={isAr ? 'حالة الاعتماد' : 'Approval'}
            value={approval}
            onChange={v => setApproval(v as ApprovalStatus | '')}
            options={[['', isAr ? 'الكل' : 'All'] as [string, string]].concat(
              APPROVAL_STATES.map(a => [a, APPROVAL_LABELS[a][isAr ? 'ar' : 'en']]))}
          />
          <div>
            <label style={{ display: 'block', fontSize: 12, color: 'var(--ink-3)', marginBottom: 4 }}>
              {isAr ? 'بحث' : 'Search'}
            </label>
            <div style={{ position: 'relative' }}>
              <Search size={14} style={{
                position: 'absolute', insetInlineStart: 8, top: 10, color: 'var(--ink-3)' }} />
              <input
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder={isAr ? 'رمز القالب أو العنوان' : 'Template code or title'}
                style={{
                  width: '100%', paddingInline: 28, paddingBlock: 8,
                  border: '1px solid var(--border)', borderRadius: 6,
                  background: 'var(--surface)',
                }}
              />
            </div>
          </div>
        </div>
      </Card>

      {/* Table */}
      <div style={{ marginTop: 16 }}>
      <Card>
        {loading && <div style={{ padding: 16 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
        {!loading && filtered.length === 0 && (
          <InlineEmpty
            text={isAr
              ? 'لا توجد قوالب مطابقة. جرّب تخفيف الفلاتر أو أضف قالباً جديداً.'
              : 'No matching templates. Try relaxing the filters or add a new template.'}
          />
        )}
        {!loading && filtered.length > 0 && (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border)' }}>
                <th style={th}>{isAr ? 'الرمز' : 'Code'}</th>
                <th style={th}>{isAr ? 'العنوان' : 'Title'}</th>
                <th style={th}>{isAr ? 'الجمهور' : 'Audience'}</th>
                <th style={th}>{isAr ? 'السيناريو' : 'Scenario'}</th>
                <th style={th}>{isAr ? 'الخطورة' : 'Severity'}</th>
                <th style={th}>{isAr ? 'الاعتماد' : 'Approval'}</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(t => (
                <tr
                  key={t.id}
                  onClick={() => navigate(`/bcm/comms/templates/${t.externalId}`)}
                  style={{
                    borderBottom: '1px solid var(--border)',
                    cursor: 'pointer',
                  }}
                  className="row-hover"
                >
                  <td style={td}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                      {t.templateCode}
                    </span>
                  </td>
                  <td style={td}>{isAr ? t.titleAr : t.titleEn}</td>
                  <td style={td}>
                    <Badge tone="info">
                      {AUDIENCE_LABELS[t.audience][isAr ? 'ar' : 'en']}
                    </Badge>
                  </td>
                  <td style={td}>
                    <Badge tone="neutral">
                      {SCENARIO_LABELS[t.scenarioCategory][isAr ? 'ar' : 'en']}
                    </Badge>
                  </td>
                  <td style={td}>
                    <Badge tone={severityTone(t.severityBand)}>
                      {SEVERITY_LABELS[t.severityBand][isAr ? 'ar' : 'en']}
                    </Badge>
                  </td>
                  <td style={td}>
                    <Badge tone={approvalTone(t.approvalStatus)}>
                      {APPROVAL_LABELS[t.approvalStatus][isAr ? 'ar' : 'en']}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      </div>
    </Shell>
  );
}

const th: CSSProperties = {
  padding: '10px 12px', textAlign: 'start',
  fontSize: 12, fontWeight: 600, color: 'var(--ink-3)',
};
const td: CSSProperties = {
  padding: '10px 12px', fontSize: 14, color: 'var(--ink-1)',
};

function FilterSelect(props: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: Array<[string, string]>;
}) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 12, color: 'var(--ink-3)', marginBottom: 4 }}>
        {props.label}
      </label>
      <select
        value={props.value}
        onChange={e => props.onChange(e.target.value)}
        style={{
          width: '100%', padding: 8,
          border: '1px solid var(--border)', borderRadius: 6,
          background: 'var(--surface)', color: 'var(--ink-1)',
        }}
      >
        {props.options.map(([value, label]) => (
          <option key={value} value={value}>{label}</option>
        ))}
      </select>
    </div>
  );
}
