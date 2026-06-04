import { useEffect, useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Database, RefreshCw, Upload, ArrowLeft, Users, ShieldAlert, AlertTriangle, Globe,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  sanctionsApi, sourceTypeLabel, entityTypeLabel,
  type SanctionsList, type SanctionsListEntry, type SanctionsEntryCreate, type EntityType,
} from '../../lib/sanctions';

const VALID_TYPES: EntityType[] = ['INDIVIDUAL', 'ENTITY', 'VESSEL', 'AIRCRAFT'];

/**
 * Sanctions list detail — watchlist entries + bulk import.
 *
 * Surfaces the orphaned GET /lists/{id}/entries and POST /lists/{id}/entries/
 * bulk-import. AML officers review the entries on a screening list and import
 * a batch of designations (one per line) into custom or refreshed lists.
 */
export function SanctionsListDetailPage() {
  const { id } = useParams<{ id: string }>();
  const listId = Number(id);
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [list, setList] = useState<SanctionsList | null>(null);
  const [entries, setEntries] = useState<SanctionsListEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const [l, e] = await Promise.all([sanctionsApi.getList(listId), sanctionsApi.listEntries(listId)]);
      setList(l);
      setEntries([...e].sort((a, b) => a.primaryNameEn.localeCompare(b.primaryNameEn)));
      setFailed(false);
    } catch (err) {
      setFailed(true);
      toast.danger(err instanceof ApiError ? err.message : (isAr ? 'تعذّر تحميل القائمة' : 'Failed to load list'));
    } finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, [listId]); // eslint-disable-line react-hooks/exhaustive-deps

  const counts = useMemo(() => ({
    total: entries.length,
    individuals: entries.filter(e => e.entityType === 'INDIVIDUAL').length,
    strong: entries.filter(e => e.severity === 'STRONG_MATCH_REQUIRED').length,
    countries: new Set(entries.map(e => e.country).filter(Boolean)).size,
  }), [entries]);

  async function submitImport(v: { rows: string }): Promise<void> {
    const lines = v.rows.split('\n').map(s => s.trim()).filter(Boolean);
    if (lines.length === 0) { toast.danger(isAr ? 'لا صفوف للاستيراد' : 'No rows to import'); return; }
    const stamp = Date.now();
    const parsed: SanctionsEntryCreate[] = lines.map((line, i) => {
      const [name, type, country, reason] = line.split('|').map(s => s.trim());
      const entityType = (VALID_TYPES.includes((type || '').toUpperCase() as EntityType)
        ? (type as string).toUpperCase() : 'INDIVIDUAL') as EntityType;
      return {
        entryExternalId: `IMP-${list?.listCode ?? listId}-${stamp}-${i + 1}`,
        entityType,
        primaryNameEn: name,
        country: country || undefined,
        listingReasonEn: reason || undefined,
        severity: 'REVIEW_REQUIRED' as const,
      };
    }).filter(e => e.primaryNameEn);
    try {
      const res = await sanctionsApi.bulkImport(listId, parsed);
      toast.success(isAr
        ? `استيراد: ${res.created} جديد · ${res.skippedExisting} موجود مسبقاً (من ${res.totalSubmitted})`
        : `Imported ${res.created} new · ${res.skippedExisting} existing (of ${res.totalSubmitted})`);
      setImportOpen(false);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الاستيراد' : 'Import failed'));
    }
  }

  const importFields: FieldDef[] = [
    { kind: 'textarea', key: 'rows', span: 12, rows: 8,
      label: isAr
        ? 'صف لكل سطر — الاسم | النوع | الدولة | السبب  (مثال: شركة الواجهة | ENTITY | IR | تحايل على العقوبات)'
        : 'One row per line — Name | Type | Country | Reason  (e.g. Front Trading Co | ENTITY | IR | Sanctions evasion)' },
  ];

  return (
    <Shell title={list ? (isAr ? list.titleAr : list.titleEn) : (isAr ? 'قائمة عقوبات' : 'Sanctions list')}
      sub={isAr ? 'إدخالات قائمة الفحص — مراجعة واستيراد' : 'Screening-list entries — review & import'}>
      <PageHeader
        eyebrow={isAr ? 'مكافحة غسل الأموال · قوائم العقوبات' : 'AML · Sanctions lists'}
        title={list ? (isAr ? list.titleAr : list.titleEn) : '—'}
        sub={list ? `${list.listCode} · ${sourceTypeLabel(list.sourceType, isAr)}` : ''}
        actions={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            <Link to="/aml/sanctions/lists" style={{ textDecoration: 'none' }}>
              <Button variant="ghost" size="sm" Icon={ArrowLeft}>{isAr ? 'القوائم' : 'Lists'}</Button>
            </Link>
            <Button variant="ghost" size="sm" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>
              {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Button variant="primary" size="sm" Icon={Upload} onClick={() => setImportOpen(true)}>
              {isAr ? 'استيراد دفعة' : 'Bulk import'}
            </Button>
          </div>
        }
      />

      {list && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
          <Badge tone="accent">{sourceTypeLabel(list.sourceType, isAr)}</Badge>
          <Badge tone={list.isActive ? 'success' : 'neutral'} dot>{list.isActive ? (isAr ? 'نشطة' : 'Active') : (isAr ? 'غير نشطة' : 'Inactive')}</Badge>
          <Badge tone="neutral">{isAr ? 'إجمالي المصدر' : 'Source size'}: {list.entryCount.toLocaleString()}</Badge>
          {list.lastRefreshedAt && <Badge tone="info">{isAr ? 'آخر تحديث' : 'Refreshed'}: {list.lastRefreshedAt.slice(0, 10)}</Badge>}
        </div>
      )}

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Database}    label={isAr ? 'الإدخالات المُحمّلة' : 'Loaded entries'} value={counts.total} tone="primary" />
        <Kpi Icon={Users}       label={isAr ? 'أفراد' : 'Individuals'} value={counts.individuals} tone="info" />
        <Kpi Icon={ShieldAlert} label={isAr ? 'تطابق قوي مطلوب' : 'Strong-match req.'} value={counts.strong} tone={counts.strong > 0 ? 'danger' : 'neutral'} />
        <Kpi Icon={Globe}       label={isAr ? 'دول' : 'Countries'} value={counts.countries} tone="neutral" />
      </div>

      <Card title={isAr ? 'الإدخالات' : 'Entries'} Icon={Database}>
        {loading && entries.length === 0
          ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : failed && entries.length === 0
            ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'القائمة غير متاحة حالياً' : 'List unavailable right now'}</div>
            : entries.length === 0
              ? <div style={{ padding: 28, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
                  <AlertTriangle size={18} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 6, color: 'var(--warning-fg)' }} />
                  {isAr ? 'لا توجد إدخالات محمّلة — استورد دفعة للبدء.' : 'No entries loaded — bulk-import a batch to begin.'}
                </div>
              : <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'المعرّف' : 'Ref'}</th>
                    <th>{isAr ? 'النوع' : 'Type'}</th>
                    <th>{isAr ? 'الاسم' : 'Name'}</th>
                    <th>{isAr ? 'الدولة' : 'Country'}</th>
                    <th>{isAr ? 'تاريخ الميلاد' : 'DOB'}</th>
                    <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                    <th>{isAr ? 'سبب الإدراج' : 'Listing reason'}</th>
                  </tr></thead>
                  <tbody>
                    {entries.map(e => (
                      <tr key={e.id}>
                        <td className="mono text-2xs">{e.entryExternalId}</td>
                        <td><Badge tone="neutral">{entityTypeLabel(e.entityType, isAr)}</Badge></td>
                        <td>
                          <div style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--ink-1)' }}>{e.primaryNameEn}</div>
                          {e.primaryNameAr && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{e.primaryNameAr}</div>}
                        </td>
                        <td className="text-2xs">{e.country ?? '—'}</td>
                        <td className="mono text-2xs">{e.dateOfBirth ?? '—'}</td>
                        <td>
                          <Badge tone={e.severity === 'STRONG_MATCH_REQUIRED' ? 'danger' : 'warning'}>
                            {e.severity === 'STRONG_MATCH_REQUIRED' ? (isAr ? 'تطابق قوي' : 'Strong') : (isAr ? 'مراجعة' : 'Review')}
                          </Badge>
                        </td>
                        <td style={{ fontSize: 11, color: 'var(--ink-3)', maxWidth: 260 }}>
                          {(isAr ? e.listingReasonAr : e.listingReasonEn) || e.listingReasonEn || '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>}
      </Card>

      <FormModal<{ rows: string }>
        open={importOpen}
        title={isAr ? 'استيراد دفعة إدخالات' : 'Bulk-import entries'}
        fields={importFields}
        initial={{ rows: '' }}
        onClose={() => setImportOpen(false)}
        onSubmit={submitImport}
        submitLabel={isAr ? 'استيراد' : 'Import'}
      />
    </Shell>
  );
}
