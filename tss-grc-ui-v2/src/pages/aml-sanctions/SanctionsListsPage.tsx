import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Database, RefreshCw, ShieldCheck } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  sanctionsApi, sourceTypeLabel,
  type SanctionsList, type SourceType,
} from '../../lib/sanctions';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const SOURCE_TONE: Record<SourceType, BadgeTone> = {
  UN: 'info',
  OFAC: 'warning',
  EU: 'info',
  SAMA: 'accent',
  KSA_NATIONAL: 'accent',
  FATF: 'neutral',
  OTHER: 'neutral',
};

/**
 * Sprint 13 — Sanctions list catalog page.
 *
 * <p>Lists every active list visible to the tenant (platform-global +
 * org-scoped). A "refresh" button stamps {@code lastRefreshedAt} and
 * fires {@code SANCTIONS_LIST_REFRESHED} — operators use this to force
 * a fresh pull from the upstream source.
 */
export function SanctionsListsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [state, setState] = useState<LoadState>('idle');
  const [lists, setLists] = useState<SanctionsList[]>([]);
  const [refreshing, setRefreshing] = useState<number | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErr(null);
    try {
      setLists(await sanctionsApi.listLists());
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  async function refresh(id: number) {
    setRefreshing(id);
    try {
      const updated = await sanctionsApi.refreshList(id);
      setLists(prev => prev.map(l => l.id === id ? updated : l));
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
    } finally {
      setRefreshing(null);
    }
  }

  useEffect(() => { void load(); }, []);

  return (
    <Shell
      title={isAr ? 'قوائم العقوبات' : 'Sanctions lists'}
      sub={isAr
        ? 'قوائم نشطة لمحرّك الفحص ضد قوائم العقوبات'
        : 'Active sanctions lists driving the screening engine'}
    >
      <PageHeader
        eyebrow={isAr ? 'الفحص ضد قوائم العقوبات' : 'Sanctions screening'}
        title={isAr ? 'قوائم العقوبات' : 'Sanctions lists'}
        sub={isAr
          ? 'كل قائمة تُعرَض هنا تتغذّى من مصدرها الأصلي ويُحفَظ آخر تحديث لها هنا. القوائم المنصّيّة (OFAC, UN, EU…) مشتركة بين جميع المستأجرين، والقوائم الداخلية تخصّ كل بنك'
          : 'Every list shown here ingests from its upstream source; the last refresh is timestamped. Platform-global lists (OFAC, UN, EU…) are shared across all tenants, internal lists are per-bank'}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={() => void load()}>
              <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Link to="/aml/sanctions/dashboard">
              <Button variant="ghost" size="sm">
                <ShieldCheck size={14} /> {isAr ? 'لوحة المعلومات' : 'Dashboard'}
              </Button>
            </Link>
          </>
        }
      />

      {state === 'loading' && (
        <Card>
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        </Card>
      )}

      {state === 'error' && (
        <Card>
          <div style={{ padding: 32, textAlign: 'center', fontSize: 12, color: 'var(--danger-fg)' }}>
            {err}
          </div>
        </Card>
      )}

      {state === 'ready' && lists.length > 0 && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))',
          gap: 12,
        }}>
          {lists.map(l => (
            <Card key={l.id}>
              <div style={{ padding: 16 }}>
                <div style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'flex-start',
                  gap: 8,
                  marginBottom: 12,
                }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontFamily: 'monospace', fontSize: 11, color: 'var(--ink-3)' }}>
                      {l.listCode}
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 600, marginTop: 4 }}>
                      {isAr ? l.titleAr : l.titleEn}
                    </div>
                  </div>
                  <Badge tone={SOURCE_TONE[l.sourceType]}>
                    {sourceTypeLabel(l.sourceType, isAr)}
                  </Badge>
                </div>

                <div style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 8,
                  fontSize: 11,
                  color: 'var(--ink-3)',
                  marginBottom: 12,
                }}>
                  <div>
                    <Database size={11} style={{ verticalAlign: 'middle' }} />{' '}
                    {isAr ? 'عدد المُدرَجين:' : 'Entries:'} <strong>{l.entryCount.toLocaleString(isAr ? 'ar-SA' : 'en-US')}</strong>
                  </div>
                  <div>
                    {isAr ? 'دورة التحديث:' : 'Refresh:'} <strong>{l.refreshFrequency}</strong>
                  </div>
                  <div>
                    {isAr ? 'النطاق:' : 'Scope:'}{' '}
                    <strong>
                      {l.organisationId == null
                        ? (isAr ? 'منصّة (مشترك)' : 'Platform (shared)')
                        : (isAr ? 'داخلي' : 'Tenant')}
                    </strong>
                  </div>
                  <div>
                    {isAr ? 'آخر تحديث:' : 'Last refresh:'}{' '}
                    <strong>{l.lastRefreshedAt
                      ? l.lastRefreshedAt.slice(0, 10)
                      : (isAr ? 'لم يُحدَّث' : 'never')}</strong>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 8 }}>
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => void refresh(l.id)}
                    disabled={refreshing === l.id || !l.isActive}
                  >
                    <RefreshCw size={12} />
                    {refreshing === l.id
                      ? (isAr ? 'جارٍ التحديث…' : 'Refreshing…')
                      : (isAr ? 'تحديث الآن' : 'Refresh now')}
                  </Button>
                  {!l.isActive && (
                    <Badge tone="neutral">{isAr ? 'مُعطَّلة' : 'Inactive'}</Badge>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </Shell>
  );
}
