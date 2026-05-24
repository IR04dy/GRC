import { useEffect, useMemo, useState } from 'react';
import { ShieldCheck, Save, Star, Sliders } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  vulnSlaApi,
  previewEffectiveHours,
  formatHours,
  effectiveHoursTone,
  VULN_SLA_SEVERITY_LABEL,
  VULN_SLA_EXPOSURE_LABEL,
  VULN_SLA_CRITICALITY_LABEL,
  type VulnSlaPolicyResponse,
  type VulnSlaPolicyRequest,
  type VulnSlaSeverity,
  type VulnSlaExposure,
  type VulnSlaCriticality,
} from '../../lib/vulnSla';

/**
 * Vulnerability SLA Policy editor — Sprint 11.
 *
 * <p>Editable policy form on the left, live 4×4×4 preview heatmap on
 * the right. Every keystroke recomputes the {@code effective_sla_hours}
 * for every (severity × exposure × criticality) combination so the
 * operator can SEE the impact of every multiplier change before saving.
 *
 * <p>Saudi-native bilingual chrome — page title uses the same phrasing
 * as the dashboard.
 */
export function VulnSlaPolicyPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [policies, setPolicies] = useState<VulnSlaPolicyResponse[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [draft, setDraft] = useState<VulnSlaPolicyRequest | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  async function load() {
    if (demoMode) {
      setPolicies([]);
      setSelectedId(null);
      setDraft(stockDraft());
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await vulnSlaApi.listPolicies();
      setPolicies(data);
      // Default-select the tenant default; fall back to first.
      const next = data.find(p => p.isDefault) ?? data[0];
      if (next) {
        setSelectedId(next.externalId);
        setDraft(toRequest(next));
      } else {
        setSelectedId(null);
        setDraft(stockDraft());
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load policies');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const selected = useMemo(() => policies.find(p => p.externalId === selectedId) ?? null, [policies, selectedId]);

  function selectPolicy(externalId: string) {
    setSelectedId(externalId);
    const p = policies.find(pp => pp.externalId === externalId);
    if (p) setDraft(toRequest(p));
    setError(null);
    setInfo(null);
  }

  async function save() {
    if (!draft || demoMode || saving) return;
    setSaving(true);
    setError(null);
    setInfo(null);
    try {
      if (selectedId) {
        const out = await vulnSlaApi.updatePolicy(selectedId, draft);
        setInfo(lang === 'ar' ? 'تم حفظ السياسة.' : 'Policy saved.');
        // Refresh local cache.
        setPolicies(prev => prev.map(p => p.externalId === out.externalId ? out : p));
      } else {
        const out = await vulnSlaApi.createPolicy(draft);
        setPolicies(prev => [...prev, out]);
        setSelectedId(out.externalId);
        setInfo(lang === 'ar' ? 'تم إنشاء السياسة.' : 'Policy created.');
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  async function promoteDefault() {
    if (!selectedId || demoMode) return;
    try {
      const out = await vulnSlaApi.setDefault(selectedId);
      setPolicies(prev => prev.map(p =>
        p.externalId === out.externalId ? out
          : { ...p, isDefault: false }));
      setInfo(lang === 'ar' ? 'تم رفع السياسة إلى الافتراضية.' : 'Policy promoted to tenant default.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Promote failed');
    }
  }

  function newPolicy() {
    setSelectedId(null);
    setDraft(stockDraft());
    setInfo(null);
    setError(null);
  }

  // ─── Render ────────────────────────────────────────────────────

  return (
    <Shell
      title={lang === 'ar' ? 'سياسات اتفاقيات مستوى الخدمة للثغرات' : 'Vulnerability SLA Policies'}
      sub={lang === 'ar' ? 'الأمن السيبراني' : 'Cybersecurity'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الأمن السيبراني' : 'Cybersecurity'}
        title={lang === 'ar' ? 'سياسات اتفاقيات مستوى الخدمة للثغرات الأمنية' : 'Vulnerability SLA Policies'}
        sub={lang === 'ar'
          ? 'حرّر مهلة المعالجة الأساسية ومضاعفات التعرّض والأهمية. تظهر النتيجة الفعلية فوراً في خريطة المعاينة قبل الحفظ.'
          : 'Edit base remediation hours and the exposure / criticality multipliers. The 4×4×4 preview heatmap shows the effective clock for every combination — live, before saving.'}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            {demoMode && <Badge tone="warning">{lang === 'ar' ? 'وضع العرض' : 'Demo mode'}</Badge>}
            {!demoMode && (
              <>
                <button type="button" onClick={newPolicy} style={btnStyle('secondary', false)}>
                  {lang === 'ar' ? 'سياسة جديدة' : 'New policy'}
                </button>
                {selectedId && !selected?.isDefault && (
                  <button type="button" onClick={promoteDefault} style={btnStyle('secondary', false)}>
                    <Star size={14} style={{ verticalAlign: 'middle', marginInlineEnd: 4 }} />
                    {lang === 'ar' ? 'اجعلها الافتراضية' : 'Make default'}
                  </button>
                )}
                <button type="button" onClick={save} disabled={!draft || saving} style={btnStyle('primary', saving)}>
                  <Save size={14} style={{ verticalAlign: 'middle', marginInlineEnd: 4 }} />
                  {saving
                    ? (lang === 'ar' ? 'جارٍ الحفظ…' : 'Saving…')
                    : selectedId ? (lang === 'ar' ? 'حفظ التعديلات' : 'Save changes')
                                 : (lang === 'ar' ? 'إنشاء' : 'Create')}
                </button>
              </>
            )}
          </div>
        }
      />

      {error && <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>}
      {info  && <div style={{ color: 'var(--success)', fontSize: 13 }}>{info}</div>}

      {/* Policy chooser */}
      <Card padded>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <Sliders size={16} style={{ color: 'var(--primary)' }} />
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>
            {lang === 'ar' ? 'السياسات المتاحة' : 'Available policies'}
          </h3>
        </div>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : policies.length === 0 && !draft ? (
          <EmptyState
            Icon={ShieldCheck}
            title={lang === 'ar' ? 'لا توجد سياسات' : 'No policies'}
            sub={lang === 'ar' ? 'أنشئ سياسة جديدة لبدء استخدام محرّك اتفاقيات الخدمة.' : 'Create a new policy to start using the SLA engine.'}
          />
        ) : (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {policies.map(p => (
              <button
                key={p.externalId}
                type="button"
                onClick={() => selectPolicy(p.externalId)}
                style={{
                  padding: '8px 12px',
                  borderRadius: 6,
                  border: '1px solid ' + (selectedId === p.externalId ? 'var(--primary)' : 'var(--border)'),
                  background: selectedId === p.externalId ? 'var(--primary-soft, rgba(0,0,0,0.04))' : 'var(--surface)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                <code style={{ fontSize: 11 }}>{p.policyCode}</code>
                <span style={{ fontSize: 12 }}>
                  {lang === 'ar' && p.titleAr ? p.titleAr : p.titleEn}
                </span>
                {p.isDefault && <Badge tone="success" dot>{lang === 'ar' ? 'افتراضي' : 'Default'}</Badge>}
              </button>
            ))}
          </div>
        )}
      </Card>

      {draft && (
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 380px) 1fr', gap: 16, alignItems: 'flex-start' }}>
          {/* ─── Editor form ───────────────────────────────────── */}
          <Card padded>
            <h3 style={{ margin: '0 0 12px 0', fontSize: 14, fontWeight: 600 }}>
              {lang === 'ar' ? 'بنود السياسة' : 'Policy fields'}
            </h3>

            <Field label={lang === 'ar' ? 'عنوان السياسة (إنجليزي)' : 'Title (English)'}>
              <input type="text" value={draft.titleEn} onChange={e => setDraft({ ...draft, titleEn: e.target.value })}
                style={inputStyle} />
            </Field>
            <Field label={lang === 'ar' ? 'عنوان السياسة (عربي)' : 'Title (Arabic)'}>
              <input type="text" value={draft.titleAr ?? ''} onChange={e => setDraft({ ...draft, titleAr: e.target.value })}
                style={inputStyle} dir="rtl" />
            </Field>

            <SectionLabel>{lang === 'ar' ? 'الساعات الأساسية حسب الخطورة' : 'Base hours by severity'}</SectionLabel>
            <Field label={lang === 'ar' ? 'حرج (CVSS)' : 'Critical (CVSS)'}>
              <NumberInput value={draft.criticalSlaHours} onChange={v => setDraft({ ...draft, criticalSlaHours: v })} step={0.5} />
            </Field>
            <Field label={lang === 'ar' ? 'مرتفع (CVSS)' : 'High (CVSS)'}>
              <NumberInput value={draft.highSlaHours} onChange={v => setDraft({ ...draft, highSlaHours: v })} step={1} />
            </Field>
            <Field label={lang === 'ar' ? 'متوسط (CVSS)' : 'Medium (CVSS)'}>
              <NumberInput value={draft.mediumSlaHours} onChange={v => setDraft({ ...draft, mediumSlaHours: v })} step={1} />
            </Field>
            <Field label={lang === 'ar' ? 'منخفض (CVSS)' : 'Low (CVSS)'}>
              <NumberInput value={draft.lowSlaHours} onChange={v => setDraft({ ...draft, lowSlaHours: v })} step={1} />
            </Field>

            <SectionLabel>{lang === 'ar' ? 'مضاعفات التعرّض' : 'Exposure multipliers'}</SectionLabel>
            <Field label={lang === 'ar' ? 'عام' : 'Public'}>
              <NumberInput value={draft.exposurePublicMul} onChange={v => setDraft({ ...draft, exposurePublicMul: v })} step={0.05} />
            </Field>
            <Field label={lang === 'ar' ? 'شريك' : 'Partner'}>
              <NumberInput value={draft.exposurePartnerMul} onChange={v => setDraft({ ...draft, exposurePartnerMul: v })} step={0.05} />
            </Field>
            <Field label={lang === 'ar' ? 'داخلي' : 'Internal'}>
              <NumberInput value={draft.exposureInternalMul} onChange={v => setDraft({ ...draft, exposureInternalMul: v })} step={0.05} />
            </Field>
            <Field label={lang === 'ar' ? 'مقيّد' : 'Restricted'}>
              <NumberInput value={draft.exposureRestrictedMul} onChange={v => setDraft({ ...draft, exposureRestrictedMul: v })} step={0.05} />
            </Field>

            <SectionLabel>{lang === 'ar' ? 'مضاعفات أهمية الأصل' : 'Criticality multipliers'}</SectionLabel>
            <Field label={lang === 'ar' ? 'حرج' : 'Critical'}>
              <NumberInput value={draft.criticalityCriticalMul} onChange={v => setDraft({ ...draft, criticalityCriticalMul: v })} step={0.05} />
            </Field>
            <Field label={lang === 'ar' ? 'مرتفع' : 'High'}>
              <NumberInput value={draft.criticalityHighMul} onChange={v => setDraft({ ...draft, criticalityHighMul: v })} step={0.05} />
            </Field>
            <Field label={lang === 'ar' ? 'متوسط' : 'Medium'}>
              <NumberInput value={draft.criticalityMediumMul} onChange={v => setDraft({ ...draft, criticalityMediumMul: v })} step={0.05} />
            </Field>
            <Field label={lang === 'ar' ? 'منخفض' : 'Low'}>
              <NumberInput value={draft.criticalityLowMul} onChange={v => setDraft({ ...draft, criticalityLowMul: v })} step={0.05} />
            </Field>

            <SectionLabel>{lang === 'ar' ? 'حدود التصعيد' : 'Escalation thresholds'}</SectionLabel>
            <Field label={lang === 'ar' ? 'نسبة التنبيه (٪)' : 'Warning at (% of clock)'}>
              <NumberInput value={draft.escalationWarningPct} onChange={v => setDraft({ ...draft, escalationWarningPct: v })} step={0.05} />
            </Field>
            <Field label={lang === 'ar' ? 'نسبة الإخلال (٪)' : 'Breach at (% of clock)'}>
              <NumberInput value={draft.escalationCriticalPct} onChange={v => setDraft({ ...draft, escalationCriticalPct: v })} step={0.05} />
            </Field>

            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>
              {lang === 'ar'
                ? 'ملاحظة: تطبَّق دائماً حدود دنيا قدرها 25٪ من الساعات الأساسية، لمنع مهل غير قابلة للتنفيذ.'
                : 'Note: a 0.25× base-hours floor always applies — prevents the multipliers from producing sub-minute SLAs.'}
            </div>
          </Card>

          {/* ─── Live preview heatmap ────────────────────────── */}
          <Card padded>
            <h3 style={{ margin: '0 0 12px 0', fontSize: 14, fontWeight: 600 }}>
              {lang === 'ar' ? 'خريطة المعاينة — المهلة الفعلية لكل تركيبة' : 'Preview heatmap — effective SLA for every combination'}
            </h3>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>
              {lang === 'ar'
                ? 'تظهر هنا 64 خانة (4 خطورة × 4 تعرّض × 4 أهمية). الألوان تشير إلى مدى تشدّد المهلة مقارنةً بالأساس.'
                : '64 cells (4 severity × 4 exposure × 4 criticality). Colours show how tight the effective SLA is compared to base.'}
            </div>
            <HeatmapMatrix draft={draft} />
          </Card>
        </div>
      )}
    </Shell>
  );
}

// ─── Internal helpers ──────────────────────────────────────────────

const SEVS:  VulnSlaSeverity[]    = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const EXPS:  VulnSlaExposure[]    = ['PUBLIC', 'PARTNER', 'INTERNAL', 'RESTRICTED'];
const CRITS: VulnSlaCriticality[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

interface HeatmapProps { draft: VulnSlaPolicyRequest; }
function HeatmapMatrix({ draft }: HeatmapProps) {
  const { lang } = useI18n();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {SEVS.map(sev => {
        const base = numericBaseHoursForSeverity(draft, sev);
        return (
          <div key={sev}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 6 }}>
              <span style={{ fontWeight: 600, fontSize: 13 }}>
                {VULN_SLA_SEVERITY_LABEL[sev][lang === 'ar' ? 'ar' : 'en']}
              </span>
              <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                {lang === 'ar' ? `الأساس ${formatHours(base)}` : `Base ${formatHours(base)}`}
              </span>
            </div>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ borderCollapse: 'collapse', minWidth: 460 }}>
                <thead>
                  <tr>
                    <th style={headCellStyle}>{lang === 'ar' ? 'تعرّض ↓ / أهمية →' : 'Exposure ↓ / Criticality →'}</th>
                    {CRITS.map(c => (
                      <th key={c} style={headCellStyle}>
                        {VULN_SLA_CRITICALITY_LABEL[c][lang === 'ar' ? 'ar' : 'en']}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {EXPS.map(exp => (
                    <tr key={exp}>
                      <th style={rowHeadStyle}>
                        {VULN_SLA_EXPOSURE_LABEL[exp][lang === 'ar' ? 'ar' : 'en']}
                      </th>
                      {CRITS.map(crit => {
                        const hours = previewEffectiveHours(draft, sev, exp, crit);
                        const tone = effectiveHoursTone(hours, base);
                        return (
                          <td key={crit} style={{
                            ...heatCellStyle,
                            background: toneToBg(tone),
                            color: toneToFg(tone),
                          }}>
                            {formatHours(hours)}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function numericBaseHoursForSeverity(p: VulnSlaPolicyRequest, s: VulnSlaSeverity): number {
  switch (s) {
    case 'CRITICAL': return p.criticalSlaHours;
    case 'HIGH':     return p.highSlaHours;
    case 'MEDIUM':   return p.mediumSlaHours;
    case 'LOW':      return p.lowSlaHours;
  }
}

function toneToBg(tone: 'danger' | 'warning' | 'info' | 'success'): string {
  switch (tone) {
    case 'danger':  return 'rgba(220, 38, 38, 0.18)';
    case 'warning': return 'rgba(217, 119, 6, 0.18)';
    case 'info':    return 'rgba(2, 132, 199, 0.14)';
    case 'success': return 'rgba(22, 163, 74, 0.14)';
  }
}
function toneToFg(tone: 'danger' | 'warning' | 'info' | 'success'): string {
  switch (tone) {
    case 'danger':  return '#991b1b';
    case 'warning': return '#92400e';
    case 'info':    return '#075985';
    case 'success': return '#166534';
  }
}

const headCellStyle: React.CSSProperties = {
  padding: '6px 10px',
  borderBottom: '1px solid var(--border)',
  fontSize: 11,
  fontWeight: 600,
  textAlign: 'center',
  color: 'var(--text-muted)',
};
const rowHeadStyle: React.CSSProperties = {
  padding: '8px 10px',
  borderRight: '1px solid var(--border)',
  fontSize: 12,
  fontWeight: 500,
  textAlign: 'start',
  whiteSpace: 'nowrap',
};
const heatCellStyle: React.CSSProperties = {
  padding: '10px 12px',
  textAlign: 'center',
  fontSize: 13,
  fontWeight: 600,
  border: '1px solid rgba(0,0,0,0.04)',
  minWidth: 80,
};

interface FieldProps { label: string; children: React.ReactNode; }
function Field({ label, children }: FieldProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 10 }}>
      <label style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4 }}>
        {label}
      </label>
      {children}
    </div>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div style={{
      fontSize: 11,
      fontWeight: 600,
      color: 'var(--text-muted)',
      textTransform: 'uppercase',
      letterSpacing: 0.5,
      borderTop: '1px solid var(--border)',
      paddingTop: 10,
      marginTop: 6,
      marginBottom: 8,
    }}>
      {children}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '6px 8px',
  border: '1px solid var(--border)',
  borderRadius: 4,
  background: 'var(--surface)',
  color: 'var(--text)',
  fontSize: 13,
  width: '100%',
};

interface NumberInputProps { value: number; onChange: (v: number) => void; step?: number; }
function NumberInput({ value, onChange, step = 0.05 }: NumberInputProps) {
  return (
    <input
      type="number"
      value={value}
      step={step}
      min={0}
      onChange={e => {
        const n = parseFloat(e.target.value);
        onChange(Number.isFinite(n) ? n : 0);
      }}
      style={inputStyle}
    />
  );
}

function btnStyle(tone: 'primary' | 'secondary', busy: boolean): React.CSSProperties {
  return {
    padding: '8px 14px',
    border: tone === 'primary' ? 'none' : '1px solid var(--border)',
    borderRadius: 6,
    background: tone === 'primary' ? 'var(--primary)' : 'var(--surface)',
    color: tone === 'primary' ? 'var(--on-primary, #fff)' : 'var(--text)',
    fontSize: 13,
    fontWeight: 500,
    cursor: busy ? 'wait' : 'pointer',
    opacity: busy ? 0.7 : 1,
  };
}

function toRequest(p: VulnSlaPolicyResponse): VulnSlaPolicyRequest {
  return {
    policyCode: p.policyCode,
    titleEn: p.titleEn,
    titleAr: p.titleAr,
    descriptionEn: p.descriptionEn,
    descriptionAr: p.descriptionAr,
    criticalSlaHours:        toNum(p.criticalSlaHours, 1),
    highSlaHours:            toNum(p.highSlaHours, 4),
    mediumSlaHours:          toNum(p.mediumSlaHours, 24),
    lowSlaHours:             toNum(p.lowSlaHours, 72),
    exposurePublicMul:       toNum(p.exposurePublicMul, 0.5),
    exposurePartnerMul:      toNum(p.exposurePartnerMul, 0.75),
    exposureInternalMul:     toNum(p.exposureInternalMul, 1.0),
    exposureRestrictedMul:   toNum(p.exposureRestrictedMul, 1.5),
    criticalityCriticalMul:  toNum(p.criticalityCriticalMul, 0.5),
    criticalityHighMul:      toNum(p.criticalityHighMul, 0.75),
    criticalityMediumMul:    toNum(p.criticalityMediumMul, 1.0),
    criticalityLowMul:       toNum(p.criticalityLowMul, 1.5),
    escalationWarningPct:    toNum(p.escalationWarningPct, 0.75),
    escalationCriticalPct:   toNum(p.escalationCriticalPct, 1.0),
    isDefault:               p.isDefault,
  };
}

function stockDraft(): VulnSlaPolicyRequest {
  return {
    titleEn: 'New SLA policy',
    titleAr: 'سياسة جديدة لاتفاقيات مستوى الخدمة',
    criticalSlaHours: 1,
    highSlaHours:     4,
    mediumSlaHours:   24,
    lowSlaHours:      72,
    exposurePublicMul:       0.5,
    exposurePartnerMul:      0.75,
    exposureInternalMul:     1.0,
    exposureRestrictedMul:   1.5,
    criticalityCriticalMul:  0.5,
    criticalityHighMul:      0.75,
    criticalityMediumMul:    1.0,
    criticalityLowMul:       1.5,
    escalationWarningPct:    0.75,
    escalationCriticalPct:   1.0,
    isDefault: false,
  };
}

function toNum(v: number | string, fallback: number): number {
  if (typeof v === 'number') return v;
  const n = parseFloat(v);
  return Number.isFinite(n) ? n : fallback;
}
