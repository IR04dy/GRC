import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Fingerprint, RefreshCw, ShieldX, ShieldCheck, Gavel } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { socopsApi, type ForensicsArtefact, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'legal_hold' | 'compromised' | 'disposal_due';

const INTEGRITY_TONE: Record<string, 'success' | 'warning' | 'danger'> = {
  VERIFIED: 'success', UNKNOWN: 'warning', COMPROMISED: 'danger', CORRUPTED: 'danger',
};
const COMPROMISED = new Set(['COMPROMISED', 'CORRUPTED']);

const INTEGRITY_LABEL: Record<string, { en: string; ar: string }> = {
  VERIFIED:   { en: 'Verified',   ar: 'موثّقة' },
  UNKNOWN:    { en: 'Unknown',    ar: 'غير معروفة' },
  COMPROMISED:{ en: 'Compromised',ar: 'مخترقة' },
  CORRUPTED:  { en: 'Compromised',ar: 'مخترقة' },
};

export function ForensicsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<ForensicsArtefact> | null>(null);
  const [filter, setFilter] = useState<Filter>('all');
  const [errMsg, setErrMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [confirmFlag, setConfirmFlag] = useState<ForensicsArtefact | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.forensics(0, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, []);

  function applyUpdate(updated: ForensicsArtefact) {
    setPage(prev => prev
      ? { ...prev, content: prev.content.map(f => (f.id === updated.id ? updated : f)) }
      : prev);
  }

  async function runVerify(f: ForensicsArtefact, verified: boolean) {
    setBusyId(f.id);
    try {
      const updated = await socopsApi.forensicsVerify(f.id, verified);
      applyUpdate(updated);
      if (verified) toast.success(isAr ? `تم التحقق من سلامة ${f.artefactCode}` : `Integrity verified · ${f.artefactCode}`);
      else          toast.danger(isAr ? `سُجِّل كسرٌ في سلسلة الحفظ لـ ${f.artefactCode}` : `Integrity break recorded · ${f.artefactCode}`);
    } catch (e) {
      const msg = e instanceof ApiError && e.status === 403
        ? (isAr ? 'لا تملك صلاحية فحص السلامة (يتطلب دور أدلة جنائية/أمن سيبراني)' : 'You lack permission to run an integrity check')
        : ((e as Error)?.message ?? String(e));
      toast.danger(msg);
    } finally { setBusyId(null); setConfirmFlag(null); }
  }

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    const now = Date.now();
    if (filter === 'all') return all;
    if (filter === 'legal_hold')   return all.filter(f => f.legalHold);
    if (filter === 'compromised')  return all.filter(f => COMPROMISED.has(f.integrityStatus));
    if (filter === 'disposal_due') return all.filter(f => f.retentionUntil
      && new Date(f.retentionUntil).getTime() < now + 30 * 86400000);
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    const now = Date.now();
    return {
      total: all.length,
      legalHold: all.filter(f => f.legalHold).length,
      compromised: all.filter(f => COMPROMISED.has(f.integrityStatus)).length,
      disposal: all.filter(f => f.retentionUntil && new Date(f.retentionUntil).getTime() < now + 30 * 86400000).length,
    };
  }, [page]);

  const integrityLabel = (s: string) => (isAr ? INTEGRITY_LABEL[s]?.ar : INTEGRITY_LABEL[s]?.en) ?? s;

  return (
    <Shell title={isAr ? 'الأدلة الجنائية الرقمية' : 'Forensics artefacts'} sub={isAr ? 'سلسلة حفظ مع MD5/SHA-256/SHA-512' : 'Chain-of-custody with MD5/SHA-256/SHA-512'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'الأدلة الجنائية الرقمية' : 'Digital forensics artefacts'}
        sub={isAr ? 'سلسلة الحفظ + قابلية الإثبات أمام المحاكم — سلامة موثقة كل تحويل'
                  : 'Chain-of-custody + court-admissibility — integrity verified on every transfer'}
        actions={<Button variant="ghost" size="sm" Icon={RefreshCw} onClick={() => void load()}>{isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Fingerprint} label={isAr ? 'إجمالي الأدلة' : 'Total artefacts'}     value={String(counts.total)}        tone="info" onClick={() => setFilter('all')} active={filter === 'all'} />
        <Kpi Icon={Gavel}       label={isAr ? 'تحت الحجز القانوني' : 'Under legal hold'} value={String(counts.legalHold)}    tone="warning" onClick={() => setFilter('legal_hold')} active={filter === 'legal_hold'} />
        <Kpi Icon={ShieldX}     label={isAr ? 'سلامة مخترقة' : 'Integrity compromised'} value={String(counts.compromised)}  tone={counts.compromised > 0 ? 'danger' : 'success'} onClick={() => setFilter('compromised')} active={filter === 'compromised'} />
        <Kpi Icon={Gavel}       label={isAr ? 'إتلاف قادم ≤٣٠ يوم' : 'Disposal ≤30d'}  value={String(counts.disposal)}     tone={counts.disposal > 0 ? 'warning' : 'success'} onClick={() => setFilter('disposal_due')} active={filter === 'disposal_due'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'all',          label: isAr ? 'الكل'         : 'All',           count: counts.total },
          { id: 'legal_hold',   label: isAr ? 'حجز قانوني'  : 'Legal hold',    count: counts.legalHold },
          { id: 'compromised',  label: isAr ? 'سلامة مكسورة' : 'Compromised',   count: counts.compromised },
          { id: 'disposal_due', label: isAr ? 'إتلاف قادم'   : 'Disposal due',  count: counts.disposal },
        ] as { id: Filter; label: string; count: number }[]).map(td => (
          <button key={td.id} onClick={() => setFilter(td.id)} style={{ padding: '8px 12px', fontSize: 12, fontWeight: 600,
            border: `1px solid ${filter === td.id ? 'var(--primary)' : 'var(--border)'}`,
            background: filter === td.id ? 'var(--primary-50)' : 'var(--surface-1)',
            borderRadius: 'var(--r-sm)', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            {td.label}<Badge tone={filter === td.id ? 'primary' : 'neutral'}>{td.count}</Badge>
          </button>
        ))}
      </div>

      <Card>
        {state === 'loading' && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
        {state === 'error' && <div style={{ padding: 32, textAlign: 'center', fontSize: 12 }}><div style={{ color: 'var(--danger-fg)' }}>{errMsg}</div></div>}
        {state === 'ready' && rows.length === 0 && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>}
        {state === 'ready' && rows.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead><tr>
              <th>{isAr ? 'رمز الدليل' : 'Artefact'}</th>
              <th>{isAr ? 'القضية' : 'Case'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'الاستحواذ' : 'Acquired'}</th>
              <th>{isAr ? 'الحارس الحالي' : 'Custodian'}</th>
              <th>{isAr ? 'المكان' : 'Location'}</th>
              <th>{isAr ? 'البصمة (SHA-256)' : 'Fingerprint (SHA-256)'}</th>
              <th>{isAr ? 'السلامة' : 'Integrity'}</th>
              <th>{isAr ? 'حجز قانوني' : 'Legal hold'}</th>
              <th>{isAr ? 'إتلاف' : 'Disposal'}</th>
              <th style={{ textAlign: isAr ? 'left' : 'right' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
            </tr></thead>
            <tbody>{rows.map(f => {
              const title = (isAr ? f.titleAr : f.titleEn) || f.titleEn;
              const busy = busyId === f.id;
              return (
              <tr key={f.id}>
                <td className="mono" style={{ fontSize: 11 }}>
                  <Link to={`/socops/forensics/${f.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {f.artefactCode}
                  </Link>
                  {title && <div style={{ fontSize: 10, color: 'var(--ink-3)', fontFamily: 'inherit', maxWidth: 240, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{title}</div>}
                </td>
                <td className="mono" style={{ fontSize: 10 }}>{f.caseCode}</td>
                <td style={{ fontSize: 11 }}>{f.artefactType ?? '—'}</td>
                <td className="mono" style={{ fontSize: 10 }}>{f.capturedAt?.slice(0, 16).replace('T', ' ') ?? '—'}</td>
                <td style={{ fontSize: 11 }}>{f.currentCustodian ?? '—'}</td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{f.currentLocation ?? '—'}</td>
                <td className="mono" style={{ fontSize: 9, color: 'var(--ink-3)', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}>{f.sha256Hash ?? '—'}</td>
                <td>
                  <Badge tone={INTEGRITY_TONE[f.integrityStatus] ?? 'neutral'}>{integrityLabel(f.integrityStatus)}</Badge>
                  {f.integrityVerifiedAt && <div style={{ fontSize: 9, color: 'var(--ink-3)', marginTop: 2 }}>{isAr ? 'فُحصت' : 'checked'} {f.integrityVerifiedAt.slice(0, 10)}</div>}
                </td>
                <td>{f.legalHold ? <Badge tone="warning">{isAr ? 'محجوز' : 'On hold'}</Badge> : '—'}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{f.retentionUntil?.slice(0, 10) ?? '—'}</td>
                <td>
                  <div style={{ display: 'flex', gap: 6, justifyContent: isAr ? 'flex-start' : 'flex-end', flexWrap: 'wrap' }}>
                    <Button variant="secondary" size="sm" Icon={ShieldCheck} disabled={busy}
                      onClick={() => void runVerify(f, true)}>
                      {isAr ? 'فحص السلامة' : 'Verify'}
                    </Button>
                    <Button variant="ghost" size="sm" Icon={ShieldX} disabled={busy}
                      onClick={() => setConfirmFlag(f)}>
                      {isAr ? 'تسجيل اختراق' : 'Flag'}
                    </Button>
                  </div>
                </td>
              </tr>);
            })}</tbody>
          </table>
        )}
      </Card>

      <ConfirmDialog
        open={confirmFlag != null}
        destructive
        title={isAr ? 'تسجيل كسر سلسلة الحفظ' : 'Record chain-of-custody break'}
        message={isAr
          ? `سيُسجَّل الدليل ${confirmFlag?.artefactCode ?? ''} كـ «مخترق السلامة» ويُبلَّغ المستشار القانوني ومدير الأمن. يصبح الدليل غير صالح أمام المحكمة حتى إعادة الفحص. هل تريد المتابعة؟`
          : `Artefact ${confirmFlag?.artefactCode ?? ''} will be recorded as integrity-compromised and legal counsel + the CISO will be paged. It becomes court-inadmissible until re-verified. Continue?`}
        confirmLabel={isAr ? 'تسجيل الاختراق' : 'Record break'}
        busy={busyId != null}
        onCancel={() => setConfirmFlag(null)}
        onConfirm={() => { if (confirmFlag) void runVerify(confirmFlag, false); }}
      />
    </Shell>
  );
}
