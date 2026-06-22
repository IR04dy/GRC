import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Users, AlertTriangle, ShieldAlert, Banknote, Plus,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  cptyApi,
  STATUS_LABEL, STATUS_TONE, SECTOR_LABEL, BAND_LABEL, BAND_TONE,
  type DashboardResponse, type CounterpartyResponse, type LimitResponse,
} from '../../lib/cpty';

export function CptyDashboardPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const r = await cptyApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar'
              ? 'تعذّر تحميل لوحة الأطراف المقابلة'
              : 'Failed to load counterparty dashboard'));
        }
      } finally { if (!cancelled) setLoading(false); }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cptyCols: Column<CounterpartyResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference',
      width: '110px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'الاسم' : 'Name',
      cell: c => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? c.legalNameAr : c.legalNameEn}
          {c.sovereignFlag && (
            <Badge tone="info" className="m-l-6">
              {lang === 'ar' ? 'سيادي' : 'sovereign'}
            </Badge>
          )}
          {c.greFlag && !c.sovereignFlag && (
            <Badge tone="info" className="m-l-6">
              {lang === 'ar' ? 'جهة حكومية' : 'GRE'}
            </Badge>
          )}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'القطاع' : 'Sector', width: '160px',
      cell: c => (
        <Badge tone="neutral">
          {SECTOR_LABEL[c.sector][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '120px',
      cell: c => (
        <Badge tone={STATUS_TONE[c.status]}>
          {STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'التصنيف الداخلي' : 'Internal grade', width: '120px',
      cell: c => <span style={{ fontSize: 12 }}>{c.currentInternalGrade ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'التصنيف الخارجي' : 'External', width: '140px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {c.currentExternalRating ? `${c.currentExternalRating} (${c.currentExternalAgency ?? ''})` : '—'}
        </span>
      ),
    },
  ];

  const limitCols: Column<LimitResponse>[] = [
    {
      header: lang === 'ar' ? 'الاسم' : 'Name',
      cell: l => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? l.displayNameAr : l.displayNameEn}
          <Badge tone="info" className="m-l-6">
            {l.scope === 'GROUP'
              ? (lang === 'ar' ? 'مجموعة' : 'group')
              : (lang === 'ar' ? 'طرف' : 'cpty')}
          </Badge>
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'المستخدم' : 'Utilised', width: '160px',
      cell: l => (
        <span style={{ fontSize: 12 }}>
          {(l.utilisedSarM ?? 0).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')}{' '}
          /{' '}
          {(l.limitSarM ?? 0).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')}{' '}
          {lang === 'ar' ? 'م.ر' : 'SAR M'}
        </span>
      ),
    },
    {
      header: '%', width: '90px',
      cell: l => (
        <strong style={{ fontSize: 13 }}>
          {Number(l.utilisationPct ?? 0).toFixed(1)}%
        </strong>
      ),
    },
    {
      header: lang === 'ar' ? 'النطاق' : 'Band', width: '100px',
      cell: l => (
        <Badge tone={BAND_TONE[l.band]}>
          {BAND_LABEL[l.band][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'الأطراف المقابلة' : 'Counterparty Registry'}
      sub={lang === 'ar'
        ? 'سجل الأطراف المقابلة، التصنيف الائتماني، وحدود التعرّض الكبير.'
        : 'Counterparty register, credit ratings, large-exposure limits.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الائتمان · المرحلة ١٨'
                                : 'Credit Risk · Sprint 18'}
        title={lang === 'ar' ? 'لوحة الأطراف المقابلة'
                              : 'Counterparty Registry Dashboard'}
        sub={lang === 'ar'
          ? 'تصنيفات، قائمة المراقبة، التعثّر، وحدود التعرّض'
          : 'Ratings, watchlist, defaults, and large-exposure limits'}
        actions={
          <Button onClick={() => nav('/cpty/counterparties')}
                  variant="primary" Icon={Plus}>
            {lang === 'ar' ? 'الأطراف' : 'Counterparties'}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12, marginBottom: 16,
      }}>
        <Kpi Icon={Users} tone="info"
          label={lang === 'ar' ? 'إجمالي الأطراف' : 'Total counterparties'}
          value={data?.totalCounterparties ?? 0}
          to="/cpty/counterparties" />
        <Kpi Icon={AlertTriangle} tone="warning"
          label={lang === 'ar' ? 'قائمة المراقبة' : 'Watchlist'}
          value={data?.watchlistCount ?? 0}
          to="/cpty/counterparties" />
        <Kpi Icon={ShieldAlert} tone="danger"
          label={lang === 'ar' ? 'متعثّرة / تجاوز' : 'Default / Breach'}
          value={(data?.defaultCount ?? 0) + (data?.limitsBreach ?? 0)}
          to="/cpty/counterparties" />
        <Kpi Icon={Banknote} tone="info"
          label={lang === 'ar' ? 'إجمالي التعرّض' : 'Total exposure'}
          value={(data?.totalExposureSarM ?? 0).toLocaleString(
            lang === 'ar' ? 'ar-SA' : 'en-US') + ' ' +
            (lang === 'ar' ? 'م.ر' : 'SAR M')}
          to="/cpty/counterparties" />
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: 12, marginBottom: 16,
      }}>
        <Card padded
          title={lang === 'ar' ? 'التوزيع حسب القطاع' : 'By sector'}>
          {loading ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.entries(data?.bySector ?? {}).map(([k, v]) => (
                <Badge key={k} tone="info">
                  {SECTOR_LABEL[k as Sector]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}: {v}
                </Badge>
              ))}
              {Object.keys(data?.bySector ?? {}).length === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'لا أطراف بعد.' : 'No counterparties yet.'}
                </span>
              )}
            </div>
          )}
        </Card>

        <Card padded
          title={lang === 'ar' ? 'التوزيع حسب التصنيف الداخلي'
                                : 'By internal grade'}>
          {loading ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.entries(data?.byInternalGrade ?? {}).map(([k, v]) => (
                <Badge key={k} tone="neutral">
                  <strong>{k}</strong>: {v}
                </Badge>
              ))}
              {Object.keys(data?.byInternalGrade ?? {}).length === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'لم تُسند تصنيفات بعد.' : 'No ratings assigned yet.'}
                </span>
              )}
            </div>
          )}
        </Card>

        <Card padded
          title={lang === 'ar' ? 'الحدود حسب النطاق' : 'Limits by band'}>
          {loading ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {(data?.limitsBreach ?? 0) > 0 && (
                <Badge tone="danger">
                  {BAND_LABEL.BREACH[lang === 'ar' ? 'ar' : 'en']}: {data?.limitsBreach}
                </Badge>
              )}
              {(data?.limitsRed ?? 0) > 0 && (
                <Badge tone="danger">
                  {BAND_LABEL.RED[lang === 'ar' ? 'ar' : 'en']}: {data?.limitsRed}
                </Badge>
              )}
              {(data?.limitsAmber ?? 0) > 0 && (
                <Badge tone="warning">
                  {BAND_LABEL.AMBER[lang === 'ar' ? 'ar' : 'en']}: {data?.limitsAmber}
                </Badge>
              )}
              {(data?.limitsBreach ?? 0) + (data?.limitsRed ?? 0) + (data?.limitsAmber ?? 0) === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'كل الحدود ضمن النطاق الأخضر.' : 'All limits within the green band.'}
                </span>
              )}
            </div>
          )}
        </Card>
      </div>

      <Card padded
        title={lang === 'ar' ? 'أحدث الأطراف' : 'Recent counterparties'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cptyCols}
            rows={data?.recentCounterparties ?? []}
            rowHref={c => `/cpty/counterparties/${c.id}`}
            empty={<EmptyState Icon={Users}
              title={lang === 'ar' ? 'لا أطراف' : 'No counterparties yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض الأطراف.' : 'Log in to view counterparties.')
                : (lang === 'ar'
                  ? 'سجّل أوّل طرف مقابل من صفحة الأطراف.'
                  : 'Register the first counterparty from the counterparties page.')} />}
          />
        )}
      </Card>

      <div style={{ marginTop: 16 }}>
        <Card padded
          title={lang === 'ar' ? 'أعلى الحدود استخداماً'
                                : 'Top utilised limits'}>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <DataTable
              columns={limitCols}
              rows={data?.topUtilisedLimits ?? []}
              empty={<EmptyState Icon={Banknote}
                title={lang === 'ar' ? 'لا توجد حدود' : 'No limits yet'}
                sub={lang === 'ar'
                  ? 'سَجّل حدود التعرّض من تفاصيل الطرف.'
                  : 'Approve limits from a counterparty detail page.'} />}
            />
          )}
        </Card>
      </div>
    </Shell>
  );
}

type Sector = keyof typeof SECTOR_LABEL;
