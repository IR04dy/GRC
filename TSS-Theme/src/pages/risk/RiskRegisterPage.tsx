import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Filter, Download, Plus, Flame, ShieldCheck, ClipboardCheck, Construction } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { RiskTable, type RiskRow } from '../../components/viz/RiskTable';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { riskApi, type RiskResponse } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useI18n } from '../../lib/i18n';
import { NewRiskModal } from './NewRiskModal';
import { MOCK_RISKS } from './mockRisks';

type TabId = 'register' | 'heatmap' | 'kri' | 'appetite';

const STATUS_TONES: Record<string, BadgeTone> = {
  OPEN: 'danger',
  IN_PROGRESS: 'warning',
  MITIGATE: 'info',
  TREATED: 'success',
  CLOSED: 'neutral',
};

function statusLabel(status: string): string {
  return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}

export function RiskRegisterPage() {
  const { t, lang } = useI18n();
  const [tab, setTab] = useState<TabId>('register');
  const [risks, setRisks] = useState<RiskResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const demoMode = !isAuthenticated();

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (demoMode) {
        setRisks(MOCK_RISKS);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const page = await riskApi.list(undefined, 0, 100);
        if (!cancelled) setRisks(page.content ?? []);
      } catch {
        if (!cancelled) {
          setError(t('riskLoadError'));
          setRisks(MOCK_RISKS);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [demoMode, t]);

  const rows: RiskRow[] = useMemo(() => risks.map(r => ({
    id: r.id,
    code: r.riskCode,
    name: lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn,
    category: lang === 'ar' && r.categoryNameAr ? r.categoryNameAr : (r.categoryNameEn ?? r.categoryName ?? '—'),
    score: r.inherentScore,
    status: statusLabel(r.status),
    statusTone: STATUS_TONES[r.status] ?? 'neutral',
    owner: r.ownerFullNameEn ?? r.ownerName ?? '—',
  })), [risks, lang]);

  const heatData: HeatCell[] = useMemo(() => {
    const buckets = new Map<string, HeatCell>();
    for (const r of risks) {
      const key = `${r.inherentLikelihood}|${r.inherentImpact}`;
      const cur = buckets.get(key);
      if (cur) cur.count += 1;
      else buckets.set(key, { l: r.inherentLikelihood, i: r.inherentImpact, count: 1 });
    }
    return Array.from(buckets.values());
  }, [risks]);

  const criticalCount = risks.filter(r => r.inherentScore >= 16).length;
  const treatedCount  = risks.filter(r => r.status === 'TREATED' || r.status === 'CLOSED').length;
  const openCount     = risks.filter(r => r.status === 'OPEN').length;

  const tabs = [
    { id: 'register' as const, label: t('riskTabRegister') },
    { id: 'heatmap'  as const, label: t('riskTabHeatMap') },
    { id: 'kri'      as const, label: t('riskTabKri') },
    { id: 'appetite' as const, label: t('riskTabAppetite') },
  ];

  return (
    <Shell title={t('modRisk')} sub="ISO 31000 + COSO ERM">
      <PageHeader
        eyebrow={t('riskEyebrow')}
        title={t('riskTitle')}
        sub={t('riskSub')}
        actions={
          <>
            {demoMode && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={Filter}>{t('riskFilter')}</Button>
            <Button variant="secondary" Icon={Download}>{t('riskExport')}</Button>
            <Button variant="primary"   Icon={Plus} onClick={() => setModalOpen(true)}>{t('riskNew')}</Button>
          </>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={AlertTriangle}   label={t('kpiTotalRisks')}    value={risks.length} tone="primary" />
        <Kpi Icon={Flame}           label={t('kpiCriticalRisks')} value={criticalCount} tone="danger" />
        <Kpi Icon={ShieldCheck}     label="Treated"               value={treatedCount} tone="success" />
        <Kpi Icon={ClipboardCheck}  label="Open"                  value={openCount} tone="warning" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'register' && (
        <Card padded={false}>
          {error && <div style={{ padding: 16, color: 'var(--danger-fg)', background: 'var(--danger-bg)', borderBottom: '1px solid #ECCFCC', fontSize: 13 }}>{error}</div>}
          {loading
            ? <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>
            : rows.length === 0
              ? <EmptyState title={t('riskEmpty')} sub={t('riskEmptySub')} />
              : <RiskTable rows={rows} />}
        </Card>
      )}

      {tab === 'heatmap' && (
        <Card title={t('riskTabHeatMap')} action={<Badge tone="primary">5 × 5</Badge>}>
          <HeatMap data={heatData} />
        </Card>
      )}

      {tab === 'kri' && (
        <Card title={t('riskTabKri')}>
          <PlaceholderInside />
        </Card>
      )}

      {tab === 'appetite' && (
        <Card title={t('riskTabAppetite')}>
          <PlaceholderInside />
        </Card>
      )}

      <NewRiskModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={(r) => setRisks(prev => [r, ...prev])}
        demoMode={demoMode}
      />
    </Shell>
  );
}

function EmptyState({ title, sub }: { title: string; sub: string }) {
  return (
    <div className="placeholder">
      <AlertTriangle size={48} strokeWidth={1.5} />
      <h4>{title}</h4>
      <p>{sub}</p>
    </div>
  );
}

function PlaceholderInside() {
  const { t } = useI18n();
  return (
    <div className="placeholder">
      <Construction size={48} strokeWidth={1.5} />
      <h4>{t('comingSoon')}</h4>
      <p>{t('comingSoonSub')}</p>
    </div>
  );
}
