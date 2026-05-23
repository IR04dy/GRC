import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Shield, ShieldAlert, ShieldCheck, Landmark, RefreshCw,
  AlertOctagon, ClipboardCheck, FileText, Crown, type LucideIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { getAccessToken, getUser } from '../../lib/auth';
import {
  threeLinesApi,
  decodeLodBands,
  type BandCounts,
  type LodBand,
  type ThreeLinesArtefact,
  type ThreeLinesOverview,
  type SlaStatus,
} from '../../lib/threeLines';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const BAND_ICON: Record<LodBand, LucideIcon> = {
  LINE_1: Shield,
  LINE_2: ShieldAlert,
  LINE_3: ShieldCheck,
  BOARD:  Crown,
};

const SLA_TONE: Record<SlaStatus, BadgeTone> = {
  ON_TRACK: 'success',
  DUE_SOON: 'warning',
  OVERDUE:  'danger',
};

/**
 * Three Lines of Defense (3LoD) — Sprint 11 strategic GRC view.
 *
 * Four-column dashboard mapping every artefact (risk / finding / CAPA /
 * policy / DSR) to the IIA 2020 band accountable for it, with Saudi-
 * native phrasing per CLAUDE.md memory feedback_saudi_native_arabic.md.
 *
 * Anchors the page in the SAMA Cybersecurity Framework 1.1.1 and the
 * IIA Three Lines Model. The Board column drills down into CRITICAL
 * audit findings that have been escalated to the Audit Committee
 * (existing /committees module).
 *
 * "My band" highlighter — reads the lod_band claim added to the JWT by
 * the Sprint-11 backend (additive, never breaks existing tokens).
 */
export function ThreeLinesOfDefensePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [state, setState] = useState<LoadState>('idle');
  const [overview, setOverview] = useState<ThreeLinesOverview | null>(null);
  const [errMsg, setErrMsg] = useState<string | null>(null);
  const [activeBand, setActiveBand] = useState<LodBand | null>(null);
  const [drill, setDrill] = useState<ThreeLinesArtefact[] | null>(null);
  const [drillState, setDrillState] = useState<LoadState>('idle');

  // "My band" — decoded from the JWT claim added in Sprint 11.
  const myBands = useMemo<LodBand[]>(
    () => decodeLodBands(getAccessToken()),
    [],
  );
  const user = getUser();

  async function loadOverview() {
    setState('loading'); setErrMsg(null);
    try {
      const data = await threeLinesApi.overview();
      setOverview(data);
      setState('ready');
    } catch (e) {
      setErrMsg((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  async function openDrill(band: LodBand) {
    setActiveBand(band);
    setDrillState('loading');
    try {
      const data = await threeLinesApi.drillDown(band);
      setDrill(data);
      setDrillState('ready');
    } catch (e) {
      setErrMsg((e as Error)?.message ?? String(e));
      setDrillState('error');
    }
  }

  useEffect(() => { void loadOverview(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  // ── Page header ──
  return (
    <Shell title={isAr ? 'خطوط الدفاع الثلاثة' : 'Three Lines of Defense'}
           sub={isAr ? 'مواءَمة مع إطار ساما للأمن السيبراني ١-١-١ ونموذج IIA لخطوط الدفاع الثلاثة'
                    : 'Aligned with SAMA Cybersecurity Framework 1.1.1 and IIA Three Lines Model'}>
      <PageHeader
        eyebrow={isAr ? 'الحوكمة' : 'Governance'}
        title={isAr ? 'خطوط الدفاع الثلاثة' : 'Three Lines of Defense'}
        sub={isAr
          ? 'توزيع المسؤوليات بين أصحاب المخاطر التشغيلية ووظائف الإشراف والمراجعة الداخلية — مرجع لجنة المراجعة ومجلس الإدارة'
          : 'Allocation of risk and compliance responsibilities across operations, oversight functions, and Internal Audit — Audit Committee reference'}
        actions={
          <Button variant="ghost" size="sm" onClick={() => void loadOverview()}>
            <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
          </Button>
        }
      />

      {/* "My band" hint — only shown when the JWT claim provides one. */}
      {myBands.length > 0 && user && (
        <div className="card" style={{ marginBottom: 16, padding: 12, background: 'var(--primary-50)', border: '1px solid var(--primary-200)' }}>
          <div style={{ fontSize: 12 }}>
            <strong>{isAr ? 'نطاقك:' : 'Your band:'}</strong>{' '}
            <span style={{ color: 'var(--ink-2)' }}>{user.username}</span>{' — '}
            {myBands.map(b => (
              <Badge key={b} tone="primary" className="ms-1">
                {bandLabel(b, isAr)}
              </Badge>
            ))}
          </div>
        </div>
      )}

      {/* Overview state */}
      {state === 'loading' && (
        <Card><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div></Card>
      )}
      {state === 'error' && (
        <Card><div style={{ padding: 32, textAlign: 'center', fontSize: 12, color: 'var(--danger-fg)' }}>
          {errMsg ?? (isAr ? 'تعذّر تحميل البيانات' : 'Could not load data')}
        </div></Card>
      )}
      {state === 'ready' && overview && (
        <>
          {/* Four-column band layout */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: 12,
            marginBottom: 16,
          }}>
            {overview.bands.map(b => (
              <BandCard
                key={b.lodBand}
                band={b}
                isAr={isAr}
                highlighted={myBands.includes(b.lodBand)}
                onOpen={() => void openDrill(b.lodBand)}
                isActive={activeBand === b.lodBand}
              />
            ))}
          </div>

          {/* Totals strip */}
          <div className="card" style={{ marginBottom: 16, padding: 12 }}>
            <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', fontSize: 12 }}>
              <TotalChip label={isAr ? 'مخاطر مفتوحة' : 'Open risks'} value={overview.totalRisksOpen} tone="warning" />
              <TotalChip label={isAr ? 'ملاحظات مفتوحة' : 'Open findings'} value={overview.totalFindingsOpen} tone="warning" />
              <TotalChip label={isAr ? 'إجراءات تصحيحية' : 'Open CAPA'} value={overview.totalCapaOpen} tone="info" />
              <TotalChip label={isAr ? 'سياسات بانتظار الاعتماد' : 'Policies pending approval'} value={overview.totalPoliciesPendingApproval} tone="info" />
              <TotalChip label={isAr ? 'متجاوز الموعد' : 'Overdue'} value={overview.totalOverdue}
                         tone={overview.totalOverdue > 0 ? 'danger' : 'success'} />
            </div>
          </div>

          {/* Drill-down panel */}
          {activeBand && (
            <Card>
              <div style={{ padding: 12, borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ fontSize: 13, fontWeight: 600 }}>
                  {isAr ? 'تفاصيل ' : 'Details — '}
                  {bandLabel(activeBand, isAr)}
                </div>
                <Button variant="ghost" size="sm" onClick={() => { setActiveBand(null); setDrill(null); }}>
                  {isAr ? 'إغلاق' : 'Close'}
                </Button>
              </div>
              {drillState === 'loading' && (
                <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
              )}
              {drillState === 'ready' && drill && drill.length === 0 && (
                <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
                  {isAr ? 'لا توجد بنود مفتوحة في هذا النطاق' : 'No open items in this band'}
                </div>
              )}
              {drillState === 'ready' && drill && drill.length > 0 && (
                <table className="tbl" style={{ width: '100%' }}>
                  <thead>
                    <tr>
                      <th>{isAr ? 'النوع' : 'Type'}</th>
                      <th>{isAr ? 'الرمز' : 'ID'}</th>
                      <th>{isAr ? 'العنوان' : 'Title'}</th>
                      <th>{isAr ? 'الشدّة' : 'Severity'}</th>
                      <th>{isAr ? 'الحالة' : 'Status'}</th>
                      <th>{isAr ? 'المسؤول' : 'Owner'}</th>
                      <th>{isAr ? 'الاستحقاق' : 'Due'}</th>
                      <th>SLA</th>
                      <th>{isAr ? 'تصعيد للمجلس' : 'Escalated'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {drill.map(r => (
                      <tr key={`${r.artefactType}-${r.id}`}>
                        <td><Badge tone="neutral">{r.artefactType}</Badge></td>
                        <td className="mono" style={{ fontSize: 10 }}>{r.externalId ?? `#${r.id}`}</td>
                        <td style={{ fontSize: 11 }}>
                          {isAr ? (r.titleAr ?? r.titleEn ?? '—') : (r.titleEn ?? r.titleAr ?? '—')}
                        </td>
                        <td>{r.severity ? <Badge tone={severityTone(r.severity)}>{r.severity}</Badge> : '—'}</td>
                        <td style={{ fontSize: 10 }}>{r.status ?? '—'}</td>
                        <td style={{ fontSize: 10 }}>{r.ownerLabel ?? '—'}</td>
                        <td className="mono" style={{ fontSize: 10 }}>{r.dueDate ?? '—'}</td>
                        <td><Badge tone={SLA_TONE[r.slaStatus]}>{slaLabel(r.slaStatus, isAr)}</Badge></td>
                        <td>
                          {r.escalatedToBoard
                            ? <Link to="/committees" title={isAr ? 'مرجع لجنة المراجعة' : 'Audit Committee reference'}>
                                <Badge tone="danger">{isAr ? 'مُصعَّد' : 'Escalated'}</Badge>
                              </Link>
                            : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </Card>
          )}

          {/* Saudi regulatory footer — NCA ECC-2 1-1 anchor. */}
          <div style={{ marginTop: 16, padding: 12, fontSize: 11, color: 'var(--ink-3)', textAlign: 'center' }}>
            {isAr
              ? 'مرجع تنظيمي: ساما — إطار الأمن السيبراني ١-١-١ (الحوكمة) · الهيئة الوطنية للأمن السيبراني — الضوابط الأساسية ٢ المجال ١-١ · IIA — نموذج خطوط الدفاع الثلاثة (٢٠٢٠)'
              : 'Regulatory anchors: SAMA CSF 1.1.1 (Governance) · NCA ECC-2 1-1 (Cybersecurity Governance) · IIA Three Lines Model (2020)'}
          </div>
        </>
      )}
    </Shell>
  );
}

// ─── Helpers (local) ─────────────────────────────────────────────────

function bandLabel(b: LodBand, isAr: boolean): string {
  switch (b) {
    case 'LINE_1': return isAr ? 'الخط الأول — أصحاب المخاطر التشغيلية' : 'Line 1 — Operations & risk owners';
    case 'LINE_2': return isAr ? 'الخط الثاني — وظائف الإشراف'          : 'Line 2 — Risk, Compliance, DPO, CISO';
    case 'LINE_3': return isAr ? 'الخط الثالث — المراجعة الداخلية'         : 'Line 3 — Internal Audit';
    case 'BOARD':  return isAr ? 'لجنة المراجعة ومجلس الإدارة'             : 'Audit Committee & Board';
  }
}

function slaLabel(s: SlaStatus, isAr: boolean): string {
  if (s === 'ON_TRACK') return isAr ? 'في الموعد'  : 'On track';
  if (s === 'DUE_SOON') return isAr ? 'يقترب'      : 'Due soon';
  return isAr ? 'متجاوز' : 'Overdue';
}

function severityTone(s: string | null): BadgeTone {
  switch ((s ?? '').toUpperCase()) {
    case 'CRITICAL': return 'danger';
    case 'HIGH':     return 'warning';
    case 'MEDIUM':   return 'info';
    case 'LOW':      return 'success';
    default:         return 'neutral';
  }
}

// ─── Sub-components ──────────────────────────────────────────────────

function BandCard({ band, isAr, highlighted, isActive, onOpen }:
  { band: BandCounts; isAr: boolean; highlighted: boolean; isActive: boolean; onOpen: () => void }) {
  const Icon = BAND_ICON[band.lodBand];
  const title = isAr ? band.titleAr : band.titleEn;
  const subtitle = isAr ? band.subtitleAr : band.subtitleEn;
  return (
    <button
      type="button"
      onClick={onOpen}
      style={{
        textAlign: 'start',
        padding: 14,
        borderRadius: 'var(--r-md)',
        border: `2px solid ${highlighted ? 'var(--primary)' : isActive ? 'var(--accent)' : 'var(--border)'}`,
        background: highlighted ? 'var(--primary-50)' : 'var(--surface-1)',
        cursor: 'pointer',
        display: 'flex', flexDirection: 'column', gap: 8,
      }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Icon size={20} />
        <div>
          <div style={{ fontWeight: 700, fontSize: 13 }}>{title}</div>
          <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{subtitle}</div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, fontSize: 11 }}>
        <CountRow Icon={AlertOctagon}    label={isAr ? 'مخاطر' : 'Risks'}     value={band.risksOpen} />
        <CountRow Icon={FileText}        label={isAr ? 'ملاحظات' : 'Findings'} value={band.findingsOpen} />
        <CountRow Icon={ClipboardCheck}  label="CAPA"                          value={band.capaOpen} />
        <CountRow Icon={Landmark}        label={isAr ? 'سياسات' : 'Policies'}  value={band.policiesPendingApproval} />
        {band.dsrCasesOpen > 0 && (
          <CountRow Icon={Shield} label={isAr ? 'طلبات أصحاب البيانات' : 'DSR'} value={band.dsrCasesOpen} />
        )}
        {band.overdueItems > 0 && (
          <CountRow Icon={AlertOctagon} label={isAr ? 'متجاوز' : 'Overdue'} value={band.overdueItems} tone="danger" />
        )}
        {band.approvalsPending > 0 && (
          <CountRow Icon={ShieldCheck} label={isAr ? 'بانتظار' : 'Pending'} value={band.approvalsPending} tone="warning" />
        )}
      </div>

      {band.roleNames.length > 0 && (
        <div style={{ fontSize: 9, color: 'var(--ink-3)', marginTop: 2 }}>
          {isAr ? 'الأدوار: ' : 'Roles: '}{band.roleNames.slice(0, 4).join(', ')}
          {band.roleNames.length > 4 && ` +${band.roleNames.length - 4}`}
        </div>
      )}
    </button>
  );
}

function CountRow({ Icon, label, value, tone = 'neutral' }:
  { Icon: LucideIcon; label: string; value: number; tone?: BadgeTone }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
      <Icon size={12} />
      <span style={{ color: 'var(--ink-3)' }}>{label}:</span>
      <Badge tone={value > 0 ? tone : 'neutral'}>{value}</Badge>
    </div>
  );
}

function TotalChip({ label, value, tone }: { label: string; value: number; tone: BadgeTone }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span style={{ color: 'var(--ink-3)' }}>{label}:</span>
      <Badge tone={tone}>{value}</Badge>
    </div>
  );
}
