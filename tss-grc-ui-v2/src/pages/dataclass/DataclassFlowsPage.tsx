import { useEffect, useState } from 'react';
import { Plus, Share2, FolderOpen } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { Card } from '../../components/ui/Card';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { ResidencyMap } from '../../components/viz/ResidencyMap';
import {
  dataclassApi,
  FLOW_STATUS_LABEL, FLOW_STATUS_TONE,
  ADEQUACY_LABEL,
  nextFlowStatuses,
  type FlowResponse, type FlowStatus,
  type DestinationAdequacy, type AssetResponse,
} from '../../lib/dataclass';

const ADEQUACIES: DestinationAdequacy[] = ['IN_KINGDOM','GCC','ADEQUATE','NON_ADEQUATE'];

/** Sprint 32 — Data Flows register (PDPL Art.7 cross-border gate). */
export function DataclassFlowsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<FlowResponse[]>([]);
  const [assets, setAssets] = useState<AssetResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  // create
  const [showNew, setShowNew] = useState(false);
  const [assetId, setAssetId] = useState<number | ''>('');
  const [ref, setRef] = useState('');
  const [src, setSrc] = useState('');
  const [dest, setDest] = useState('');
  const [country, setCountry] = useState('SA');
  const [adequacy, setAdequacy] = useState<DestinationAdequacy>('IN_KINGDOM');
  const [mode, setMode] = useState('REST_API');
  const [purposeEn, setPurposeEn] = useState('');
  const [purposeAr, setPurposeAr] = useState('');
  const [art7Ref, setArt7Ref] = useState('');

  // transition
  const [moveTarget, setMoveTarget] = useState<FlowResponse | null>(null);
  const [docsTarget, setDocsTarget] = useState<FlowResponse | null>(null);
  const [toStatus, setToStatus] = useState<FlowStatus | null>(null);
  const [reasonEn, setReasonEn] = useState('');
  const [reasonAr, setReasonAr] = useState('');
  const [art7TransitionRef, setArt7TransitionRef] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const [f, a] = await Promise.all([
        dataclassApi.listFlows(),
        dataclassApi.listAssets(),
      ]);
      setRows(f); setAssets(a);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل تدفّقات البيانات' : 'Failed to load data flows'));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (typeof assetId !== 'number' || !ref.trim()) return;
    setActing(true);
    try {
      await dataclassApi.createFlow({
        dataAssetId: assetId,
        reference: ref.trim(),
        sourceSystemLabel: src || undefined,
        destinationSystemLabel: dest || undefined,
        destinationCountry: country || undefined,
        destinationAdequacy: adequacy,
        transferMode: mode || undefined,
        purposeEn: purposeEn || undefined,
        purposeAr: purposeAr || undefined,
        pdplArt7TransferRef: art7Ref || undefined,
      });
      setShowNew(false);
      setAssetId(''); setRef(''); setSrc(''); setDest(''); setArt7Ref('');
      setPurposeEn(''); setPurposeAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء التدفّق' : 'Failed to create flow'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!moveTarget || !toStatus) return;
    setActing(true);
    try {
      await dataclassApi.transitionFlow(moveTarget.id, {
        toStatus,
        reasonEn: reasonEn || undefined,
        reasonAr: reasonAr || undefined,
        pdplArt7TransferRef: art7TransitionRef || undefined,
      });
      setMoveTarget(null); setToStatus(null);
      setReasonEn(''); setReasonAr(''); setArt7TransitionRef('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل (تحقّق من شرط مادة ٧)' : 'Failed to transition (check PDPL Art.7 gate)'));
    } finally { setActing(false); }
  };

  const cols: Column<FlowResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', width: '140px', mono: true,
      cell: f => <span style={{ fontSize: 12 }}>{f.reference}</span> },
    { header: lang === 'ar' ? 'الأصل' : 'Asset', width: '80px',
      cell: f => <span style={{ fontSize: 12 }}>#{f.dataAssetId}</span> },
    { header: lang === 'ar' ? 'المصدر → الوجهة' : 'Source → Destination',
      cell: f => (
        <div style={{ fontSize: 12 }}>
          {f.sourceSystemLabel ?? '?'} → {f.destinationSystemLabel ?? '?'}
          {f.destinationCountry && (
            <div style={{ fontSize: 11, opacity: 0.7 }}>{f.destinationCountry}</div>
          )}
        </div>
      ) },
    { header: lang === 'ar' ? 'الكفاية' : 'Adequacy', width: '160px',
      cell: f => f.destinationAdequacy
        ? <Badge tone={f.destinationAdequacy === 'IN_KINGDOM' ? 'success' :
                       f.destinationAdequacy === 'GCC' ? 'info' :
                       f.destinationAdequacy === 'ADEQUATE' ? 'warning' : 'danger'}>
            {ADEQUACY_LABEL[f.destinationAdequacy][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'مرجع مادة ٧' : 'Art.7 ref', width: '140px',
      cell: f => {
        const crossBorder = f.destinationAdequacy && f.destinationAdequacy !== 'IN_KINGDOM';
        if (!crossBorder) return <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>;
        return f.pdplArt7TransferRef
          ? <span style={{ fontSize: 11 }}>{f.pdplArt7TransferRef}</span>
          : <Badge tone="danger">{lang === 'ar' ? 'مفقود' : 'Missing'}</Badge>;
      } },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: f => <Badge tone={FLOW_STATUS_TONE[f.status]}>
        {FLOW_STATUS_LABEL[f.status][lang === 'ar' ? 'ar' : 'en']}</Badge> },
    { header: '', width: '140px',
      cell: f => {
        const next = nextFlowStatuses(f.status);
        return (
          <span style={{ display: 'inline-flex', gap: 4, justifyContent: 'flex-end' }}>
            <Button variant="ghost" size="sm" Icon={FolderOpen}
              title={lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls'}
              onClick={(e) => { e.stopPropagation(); setDocsTarget(f); }} />
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={(e) => { e.stopPropagation();
                  setMoveTarget(f); setToStatus(next[0]);
                  setReasonEn(''); setReasonAr(''); setArt7TransitionRef(''); }}>
                {lang === 'ar' ? 'تحريك' : 'Move'}
              </Button>
            )}
          </span>
        );
      } },
  ];

  return (
    <Shell>
      <PageHeader
        eyebrow={lang === 'ar' ? 'تدفّقات البيانات' : 'Data Flows'}
        title={lang === 'ar' ? 'سجل تدفّقات البيانات' : 'Data Flows Register'}
        sub={lang === 'ar'
          ? 'بوّابة نقل البيانات عبر الحدود (مادة ٧ من نظام حماية البيانات الشخصية)'
          : 'PDPL Art.7 cross-border transfer gate — non-IN_KINGDOM destinations require Art.7 ref'}
        actions={
          <Button onClick={() => setShowNew(s => !s)} variant="primary" Icon={Plus}>
            {showNew ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
                     : (lang === 'ar' ? 'تدفّق جديد' : 'New flow')}
          </Button>
        } />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {rows.length > 0 && (() => {
        const crossBorder = rows.filter(r => r.destinationAdequacy && r.destinationAdequacy !== 'IN_KINGDOM');
        const missingArt7 = crossBorder.filter(r => !r.pdplArt7TransferRef).length;
        return (
          <div style={{ marginBottom: 16 }}>
          <Card padded>
            <div style={{ display: 'flex', gap: 20, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'center' }}>
              <ResidencyMap
                ar={lang === 'ar'}
                flows={rows.map(r => ({
                  id: r.id,
                  zone: r.destinationAdequacy,
                  sourceLabel: r.sourceSystemLabel,
                  destLabel: r.destinationSystemLabel,
                  destCountry: r.destinationCountry,
                  hasArt7Ref: !!r.pdplArt7TransferRef,
                }))} />
              <div style={{ minWidth: 200, maxWidth: 280 }}>
                <div style={{ font: '700 11px var(--font-sans)', textTransform: 'uppercase', letterSpacing: '0.1em', color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'بوّابة النقل عبر الحدود — مادة ٧' : 'Cross-border transfer gate — Art. 7'}
                </div>
                <div style={{ display: 'flex', gap: 18, marginTop: 12 }}>
                  <div>
                    <div className="tnum" style={{ font: '800 28px var(--font-sans)', color: 'var(--ink-1)' }}>{crossBorder.length}</div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{lang === 'ar' ? 'تدفّقات عبر الحدود' : 'cross-border flows'}</div>
                  </div>
                  <div>
                    <div className="tnum" style={{ font: '800 28px var(--font-sans)', color: missingArt7 > 0 ? 'var(--danger)' : 'var(--success)' }}>{missingArt7}</div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{lang === 'ar' ? 'ينقصها مرجع م.٧' : 'missing Art. 7 ref'}</div>
                  </div>
                </div>
                <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 12, lineHeight: 1.6 }}>
                  {lang === 'ar'
                    ? 'كل تدفّق يغادر المملكة بدون مرجع نقل (مادة ٧) يظهر كشعاع أحمر متقطّع.'
                    : 'Every flow leaving the Kingdom without an Art. 7 transfer reference shows as a dashed red, pulsing ray.'}
                </div>
              </div>
            </div>
          </Card>
          </div>
        );
      })()}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'اقتراح تدفّق بيانات' : 'Propose a data flow'}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
              <Field label={lang === 'ar' ? 'الأصل *' : 'Data asset *'}>
                <select value={assetId}
                  onChange={e => setAssetId(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle}>
                  <option value="">—</option>
                  {assets.map(a => (
                    <option key={a.id} value={a.id}>
                      {a.reference} — {lang === 'ar' ? a.nameAr : a.nameEn}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'المرجع *' : 'Reference *'}>
                <input value={ref} onChange={e => setRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="DF-2026-009" />
              </Field>
              <Field label={lang === 'ar' ? 'النظام المصدر' : 'Source system'}>
                <input value={src} onChange={e => setSrc(e.target.value)}
                  style={inputStyle} placeholder="T24 — Customer Master" />
              </Field>
              <Field label={lang === 'ar' ? 'النظام الوجهة' : 'Destination system'}>
                <input value={dest} onChange={e => setDest(e.target.value)}
                  style={inputStyle} placeholder="Salesforce Marketing Cloud (EU-West)" />
              </Field>
              <Field label={lang === 'ar' ? 'بلد الوجهة' : 'Destination country'}>
                <input value={country} onChange={e => setCountry(e.target.value)}
                  style={inputStyle} maxLength={2} placeholder="SA" />
              </Field>
              <Field label={lang === 'ar' ? 'كفاية الوجهة' : 'Destination adequacy'}>
                <select value={adequacy}
                  onChange={e => setAdequacy(e.target.value as DestinationAdequacy)}
                  style={inputStyle}>
                  {ADEQUACIES.map(a => (
                    <option key={a} value={a}>
                      {ADEQUACY_LABEL[a][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'وضع النقل' : 'Transfer mode'}>
                <select value={mode} onChange={e => setMode(e.target.value)} style={inputStyle}>
                  <option value="REST_API">REST_API</option>
                  <option value="SFTP_BATCH">SFTP_BATCH</option>
                  <option value="ETL">ETL</option>
                  <option value="STREAMING">STREAMING</option>
                  <option value="DB_REPLICATION">DB_REPLICATION</option>
                  <option value="MANUAL_EXPORT">MANUAL_EXPORT</option>
                </select>
              </Field>
              {adequacy !== 'IN_KINGDOM' && (
                <Field label={lang === 'ar' ? 'مرجع تحويل مادة ٧ (مطلوب للحسّاسة)'
                                              : 'PDPL Art.7 transfer ref (required for sensitive)'}>
                  <input value={art7Ref} onChange={e => setArt7Ref(e.target.value)}
                    style={inputStyle} maxLength={64} placeholder="ART7-2026-014" />
                </Field>
              )}
              <Field label={lang === 'ar' ? 'الغرض (إنجليزي)' : 'Purpose (English)'}>
                <textarea value={purposeEn} onChange={e => setPurposeEn(e.target.value)}
                  rows={2} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'الغرض (عربي)' : 'Purpose (Arabic)'}>
                <textarea value={purposeAr} dir="rtl" onChange={e => setPurposeAr(e.target.value)}
                  rows={2} style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submit}
                disabled={acting || typeof assetId !== 'number' || !ref.trim()}>
                {acting ? (lang === 'ar' ? 'جارٍ الإنشاء…' : 'Creating…')
                         : (lang === 'ar' ? 'إنشاء' : 'Create')}
              </Button>
              <Button variant="secondary" onClick={() => setShowNew(false)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {moveTarget && toStatus && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? `تحريك ${moveTarget.reference}` : `Move ${moveTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={toStatus}
                onChange={e => setToStatus(e.target.value as FlowStatus)}
                style={inputStyle}>
                {nextFlowStatuses(moveTarget.status).map(s => (
                  <option key={s} value={s}>
                    {FLOW_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            {toStatus === 'APPROVED' && moveTarget.destinationAdequacy
                && moveTarget.destinationAdequacy !== 'IN_KINGDOM' && (
              <Field label={lang === 'ar' ? 'مرجع تحويل مادة ٧ *' : 'PDPL Art.7 transfer ref *'}>
                <input value={art7TransitionRef} onChange={e => setArt7TransitionRef(e.target.value)}
                  style={inputStyle} maxLength={64} placeholder="ART7-2026-014" />
              </Field>
            )}
            <div style={{ display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12, marginTop: 12 }}>
              <Field label={lang === 'ar' ? 'السبب (إنجليزي)' : 'Reason (English)'}>
                <textarea value={reasonEn} onChange={e => setReasonEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'السبب (عربي)' : 'Reason (Arabic)'}>
                <textarea value={reasonAr} dir="rtl" onChange={e => setReasonAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitTransition} disabled={acting}>
                {acting ? (lang === 'ar' ? 'جارٍ التنفيذ…' : 'Applying…')
                         : (lang === 'ar' ? 'تنفيذ' : 'Apply')}
              </Button>
              <Button variant="secondary"
                onClick={() => { setMoveTarget(null); setToStatus(null); }}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل التدفّقات' : 'All flows'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable columns={cols} rows={rows}
            empty={<EmptyState Icon={Share2}
              title={lang === 'ar' ? 'لا تدفّقات بعد' : 'No flows yet'}
              sub={lang === 'ar'
                ? 'اقترح أوّل تدفّق بيانات لبدء بوّابة مادة ٧.'
                : 'Propose the first data flow to start the PDPL Art.7 gate.'} />}
          />
        )}
      </Card>
      <EntityDocsModal
        entityType="DATACLASS_FLOW"
        entityId={docsTarget?.id ?? null}
        open={!!docsTarget}
        recordLabel={docsTarget?.reference}
        onClose={() => setDocsTarget(null)}
      />
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6, border: '1px solid var(--border)', borderRadius: 4,
  fontSize: 13, width: '100%', fontFamily: 'inherit',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{label}</span>
      {children}
    </label>
  );
}
