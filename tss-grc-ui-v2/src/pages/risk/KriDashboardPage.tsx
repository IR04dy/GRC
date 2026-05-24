import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity, AlertOctagon, AlertTriangle, BarChart3, ShieldCheck, ClipboardList,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  kriApi,
  formatKriValue,
  KRI_CATEGORY_LABEL, KRI_STATUS_LABEL, KRI_STATUS_TONE,
  type KriCategory, type KriDashboardSnapshot,
} from '../../lib/kriEngine';

/**
 * KRI Dashboard — Sprint 13 Key Risk Indicator engine.
 *
 * Saudi-native bilingual heading:
 *   ar: مؤشّرات المخاطر الرئيسية  (not literal "كاي آر آي")
 *   en: Key Risk Indicators (KRIs)
 *
 * Wired to GET /api/v1/risk/kri/dashboard. Top KPIs (total / active /
 * green / amber / red / open-breaches) sit above a category-grouped
 * heatmap. Each cell links to /risk/kri/:externalId.
 */
export function KriDashboardPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [snap, setSnap] = useState<KriDashboardSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    if (demoMode) { setSnap(null); return; }
    setLoading(true);
    setError(null);
    try {
      setSnap(await kriApi.dashboard());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load KRI dashboard');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  // Group cells by category for the heatmap. Maintain a stable
  // category order so a section that always sits at the top
  // (CYBER / OPERATIONAL / COMPLIANCE) stays put visit-to-visit.
  const CATEGORY_ORDER: KriCategory[] = [
    'CYBER', 'OPERATIONAL', 'COMPLIANCE', 'AML', 'PRIVACY', 'BCM',
    'CREDIT', 'MARKET', 'LIQUIDITY', 'STRATEGIC', 'ESG', 'REPUTATIONAL',
  ];
  const groups = useMemo(() => {
    const cells = snap?.cells ?? [];
    return CATEGORY_ORDER
      .map(cat => ({ cat, items: cells.filter(c => c.category === cat) }))
      .filter(g => g.items.length > 0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [snap]);

  return (
    <Shell
      title={lang === 'ar' ? 'مؤشّرات المخاطر الرئيسية' : 'Key Risk Indicators (KRIs)'}
      sub={lang === 'ar'
        ? 'SAMA إطار الامتثال §4.2 · NCA ECC-2 2-12 · SAMA BCF §3.5'
        : 'SAMA Compliance Framework §4.2 · NCA ECC-2 2-12 · SAMA BCF §3.5'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'إدارة المخاطر' : 'Enterprise Risk'}
        title={lang === 'ar' ? 'مؤشّرات المخاطر الرئيسية' : 'Key Risk Indicators (KRIs)'}
        sub={lang === 'ar'
          ? 'سجل منظَّم بدلاً من جداول إكسل ربعية. كل مؤشّر يحمل عتبات GREEN/AMBER/RED، مالكاً مُسمّى، وارتباطاً تنظيمياً صريحاً. الخروقات تُفتح وتُغلق تلقائياً، وتُفتح حالة معالجة (CAPA) عند استمرار الحالة الحمراء.'
          : 'A structured register replacing the quarterly Excel pivot. Each KRI carries GREEN/AMBER/RED thresholds, a named owner and a regulatory anchor. Breaches open and close automatically; sustained RED escalates into a CAPA Issue.'}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            <Link to="/risk/kri/admin"
                  style={{ padding: '6px 12px', border: '1px solid var(--border)', borderRadius: 6,
                           textDecoration: 'none', color: 'var(--text)', fontSize: 13 }}>
              <ClipboardList size={14} style={{ verticalAlign: '-2px', marginInlineEnd: 6 }} />
              {lang === 'ar' ? 'إدارة المؤشّرات' : 'Manage KRIs'}
            </Link>
            {demoMode && <Badge tone="warning">{lang === 'ar' ? 'وضع العرض' : 'Demo mode'}</Badge>}
          </div>
        }
      />

      <div className="kpi-grid-sm">
        <Kpi Icon={ClipboardList}
             label={lang === 'ar' ? 'إجمالي المؤشّرات' : 'Total KRIs'}
             value={snap?.totalKris ?? 0} tone="primary" />
        <Kpi Icon={ShieldCheck}
             label={lang === 'ar' ? 'النشطة' : 'Active'}
             value={snap?.activeKris ?? 0} tone="info" />
        <Kpi Icon={Activity}
             label={lang === 'ar' ? 'ضمن الهدف' : 'Within target'}
             value={snap?.greenCount ?? 0} tone="success" />
        <Kpi Icon={AlertTriangle}
             label={lang === 'ar' ? 'تحت المراقبة' : 'Watch'}
             value={snap?.amberCount ?? 0}
             tone={(snap?.amberCount ?? 0) > 0 ? 'warning' : 'success'} />
        <Kpi Icon={AlertOctagon}
             label={lang === 'ar' ? 'خرق المؤشر' : 'Breach'}
             value={snap?.redCount ?? 0}
             tone={(snap?.redCount ?? 0) > 0 ? 'danger' : 'success'} />
        <Kpi Icon={BarChart3}
             label={lang === 'ar' ? 'خروقات مفتوحة' : 'Open breaches'}
             value={(snap?.openBreachAmber ?? 0) + (snap?.openBreachRed ?? 0)}
             tone={(snap?.openBreachRed ?? 0) > 0 ? 'danger'
                 : (snap?.openBreachAmber ?? 0) > 0 ? 'warning' : 'success'} />
      </div>

      {error && (
        <Card padded>
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        </Card>
      )}

      <Card padded>
        <h3 style={{ marginTop: 0 }}>
          {lang === 'ar' ? 'خريطة المؤشّرات حسب الفئة' : 'KRI heatmap by category'}
        </h3>
        {loading && <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>}
        {!loading && groups.length === 0 && (
          <InlineEmpty>
            {lang === 'ar'
              ? 'لا توجد مؤشّرات معرّفة بعد. ابدأ من شاشة الإدارة.'
              : 'No KRIs defined yet. Start from the admin screen.'}
          </InlineEmpty>
        )}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {groups.map(g => (
            <div key={g.cat}>
              <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8, color: 'var(--text-muted)' }}>
                {KRI_CATEGORY_LABEL[g.cat][lang === 'ar' ? 'ar' : 'en']}
                <span style={{ marginInlineStart: 8 }}>· {g.items.length}</span>
              </div>
              <div style={{ display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
                            gap: 8 }}>
                {g.items.map(cell => (
                  <Link key={cell.externalId} to={`/risk/kri/${cell.externalId}`}
                        style={{ textDecoration: 'none', color: 'var(--text)',
                                 border: '1px solid var(--border)', borderRadius: 6,
                                 padding: 10, display: 'flex', flexDirection: 'column', gap: 4,
                                 background: cell.status === 'RED' ? 'rgba(239,68,68,0.06)'
                                          : cell.status === 'AMBER' ? 'rgba(245,158,11,0.06)'
                                          : cell.status === 'GREEN' ? 'rgba(34,197,94,0.04)'
                                          : 'transparent' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <code style={{ fontSize: 11, color: 'var(--text-muted)' }}>{cell.kriCode}</code>
                      <Badge tone={KRI_STATUS_TONE[cell.status]} dot>
                        {KRI_STATUS_LABEL[cell.status][lang === 'ar' ? 'ar' : 'en']}
                      </Badge>
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 500 }}>
                      {lang === 'ar' ? cell.titleAr : cell.titleEn}
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between',
                                  fontSize: 12, color: 'var(--text-muted)' }}>
                      <span style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {formatKriValue(cell.latestValue, cell.measurementUnit)}
                      </span>
                      <span>
                        {cell.latestMeasuredAt
                          ? new Date(cell.latestMeasuredAt).toLocaleDateString(
                              lang === 'ar' ? 'ar-SA' : 'en-GB')
                          : (lang === 'ar' ? 'لا قياسات' : 'no data')}
                      </span>
                    </div>
                    {cell.hasOpenBreach && (
                      <div style={{ fontSize: 11, color: 'var(--danger)' }}>
                        {lang === 'ar' ? '⚠ خرق مفتوح' : '⚠ Open breach'}
                      </div>
                    )}
                  </Link>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Card>

      <Card padded>
        <h3 style={{ marginTop: 0 }}>
          {lang === 'ar' ? 'الخروقات المفتوحة' : 'Open breaches'}
        </h3>
        {(snap?.openBreaches ?? []).length === 0
          ? <InlineEmpty>
              {lang === 'ar' ? 'لا خروقات مفتوحة حالياً.' : 'No open breaches right now.'}
            </InlineEmpty>
          : <ul style={{ listStyle: 'none', padding: 0, margin: 0,
                         display: 'flex', flexDirection: 'column', gap: 4 }}>
              {(snap?.openBreaches ?? []).map(b => (
                <li key={b.externalId}>
                  <Link to={`/risk/kri/${b.kriId ? '' : ''}${b.kriCode ?? ''}`.replace(/^$/, '/risk/kri')}
                        onClick={(e) => e.preventDefault()}
                        style={{ display: 'flex', justifyContent: 'space-between',
                                 padding: '6px 8px', border: '1px solid var(--border)',
                                 borderRadius: 4, textDecoration: 'none', color: 'var(--text)' }}>
                    <span>
                      <Badge tone={b.breachLevel === 'RED' ? 'danger' : 'warning'} dot>
                        {b.breachLevel}
                      </Badge>
                      <code style={{ fontSize: 12, color: 'var(--text-muted)', marginInlineStart: 8 }}>
                        {b.kriCode}
                      </code>
                      <span style={{ marginInlineStart: 8 }}>
                        {lang === 'ar' ? b.titleAr : b.titleEn}
                      </span>
                    </span>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      {lang === 'ar' ? 'منذ' : 'opened'} {new Date(b.openedAt).toLocaleDateString(
                        lang === 'ar' ? 'ar-SA' : 'en-GB')}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
        }
      </Card>
    </Shell>
  );
}
