import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  CheckCheck, FileText, Fingerprint, ListChecks, Lock, PenLine, Users,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  auditCommitteeDashboardApi,
  parseResolutions, shortSignature,
  MEETING_TYPE_LABEL, ROLE_LABEL,
  type AttestationRole, type SnapshotResponse,
} from '../../lib/auditCommitteeDashboard';

/**
 * Audit Committee Snapshot detail page — Sprint 11.
 *
 * Shows the frozen KPIs JSON, the running list of signed
 * attestations, the captured resolutions, and (if not yet
 * finalized) the executive-summary capture form.
 *
 * Saudi-native bilingual:
 *   ar: لقطة الاجتماع   en: Meeting snapshot
 */
export function AuditCommitteeSnapshotPage() {
  const { lang } = useI18n();
  const t = (en: string, ar: string) => (lang === 'ar' ? ar : en);
  const { id } = useParams<{ id: string }>();
  const demoMode = !isAuthenticated();

  const [snap, setSnap] = useState<SnapshotResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<'kpi' | 'attest' | 'resolutions'>('kpi');

  // Attestation form
  const [attestRole, setAttestRole] = useState<AttestationRole>('MEMBER');
  const [attestEn, setAttestEn] = useState('');
  const [attestAr, setAttestAr] = useState('');
  const [attestBusy, setAttestBusy] = useState(false);
  const [attestError, setAttestError] = useState<string | null>(null);

  // Resolution form
  const [resEn, setResEn] = useState('');
  const [resAr, setResAr] = useState('');
  const [resBusy, setResBusy] = useState(false);
  const [resError, setResError] = useState<string | null>(null);

  // Finalize form
  const [showFinalize, setShowFinalize] = useState(false);
  const [summaryEn, setSummaryEn] = useState('');
  const [summaryAr, setSummaryAr] = useState('');
  const [finalizeBusy, setFinalizeBusy] = useState(false);
  const [finalizeError, setFinalizeError] = useState<string | null>(null);

  async function load() {
    if (!id || demoMode) {
      setSnap(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setSnap(await auditCommitteeDashboardApi.getSnapshot(id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message
        : t('Failed to load snapshot', 'تعذّر تحميل اللقطة'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [id]);

  async function attest() {
    if (!id || !attestEn.trim()) return;
    setAttestBusy(true);
    setAttestError(null);
    try {
      await auditCommitteeDashboardApi.attest(id, {
        attestedRole: attestRole,
        attestationTextEn: attestEn,
        attestationTextAr: attestAr || undefined,
      });
      setAttestEn(''); setAttestAr('');
      await load();
    } catch (err) {
      setAttestError(err instanceof ApiError ? err.message
        : t('Failed to attest', 'تعذّر الإقرار'));
    } finally {
      setAttestBusy(false);
    }
  }

  async function recordResolution() {
    if (!id || !resEn.trim()) return;
    setResBusy(true);
    setResError(null);
    try {
      await auditCommitteeDashboardApi.recordResolution(id, {
        textEn: resEn,
        textAr: resAr || undefined,
      });
      setResEn(''); setResAr('');
      await load();
    } catch (err) {
      setResError(err instanceof ApiError ? err.message
        : t('Failed to record', 'تعذّر التسجيل'));
    } finally {
      setResBusy(false);
    }
  }

  async function finalize() {
    if (!id || !summaryEn.trim()) return;
    setFinalizeBusy(true);
    setFinalizeError(null);
    try {
      await auditCommitteeDashboardApi.finalize(id, {
        executiveSummaryEn: summaryEn,
        executiveSummaryAr: summaryAr || undefined,
      });
      setShowFinalize(false);
      await load();
    } catch (err) {
      setFinalizeError(err instanceof ApiError ? err.message
        : t('Failed to finalize', 'تعذّر اعتماد الاجتماع'));
    } finally {
      setFinalizeBusy(false);
    }
  }

  const resolutions = parseResolutions(snap?.committeeResolutions);
  const kpis = (() => {
    try {
      return snap?.kpisJson ? JSON.parse(snap.kpisJson) : null;
    } catch {
      return null;
    }
  })();

  return (
    <Shell title={t('Meeting snapshot', 'لقطة الاجتماع')}
           sub={snap?.snapshotCode}>
      <PageHeader
        eyebrow={
          <span>
            <Link to="/audit-committee/dashboard">
              {t('Audit Committee Dashboard', 'لوحة لجنة المراجعة')}
            </Link>
            {' › '}
            {t('Snapshot', 'لقطة')}
          </span> as unknown as string
        }
        title={snap?.snapshotCode ?? t('Loading…', 'جارٍ التحميل…')}
        sub={snap ?
          `${snap.meetingDate} · ${MEETING_TYPE_LABEL[snap.meetingType][lang === 'ar' ? 'ar' : 'en']}`
          : undefined}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            {snap?.isFinalized ? (
              <Badge tone="success" dot>{t('Finalized', 'مُعتمَدة')}</Badge>
            ) : (
              <button onClick={() => setShowFinalize(true)}
                      style={{ padding: '6px 12px', border: '1px solid var(--primary)',
                               borderRadius: 6, background: 'var(--primary)',
                               color: 'white', fontSize: 13, cursor: 'pointer' }}>
                <Lock size={14} style={{ verticalAlign: '-2px', marginInlineEnd: 6 }} />
                {t('Finalize meeting', 'اعتماد الاجتماع')}
              </button>
            )}
            {demoMode && <Badge tone="warning">{t('Demo mode', 'وضع العرض')}</Badge>}
          </div>
        }
      />

      {error && (
        <Card padded>
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        </Card>
      )}

      {loading && !snap && (
        <Card padded>
          <InlineEmpty>{t('Loading snapshot…', 'جارٍ تحميل اللقطة…')}</InlineEmpty>
        </Card>
      )}

      {snap && (
        <>
          {/* Tabs */}
          <div style={{ display: 'flex', gap: 6, marginBottom: 12, marginTop: 12 }}>
            <TabBtn active={tab === 'kpi'} onClick={() => setTab('kpi')}
                    Icon={FileText} label={t('KPIs (frozen)', 'المؤشّرات (مُجمَّدة)')} />
            <TabBtn active={tab === 'attest'} onClick={() => setTab('attest')}
                    Icon={PenLine} label={t('Attestations', 'إقرارات الأعضاء')}
                    count={snap.attestations.length} />
            <TabBtn active={tab === 'resolutions'} onClick={() => setTab('resolutions')}
                    Icon={ListChecks} label={t('Resolutions', 'القرارات')}
                    count={resolutions.length} />
          </div>

          {tab === 'kpi' && (
            <Card padded>
              {snap.executiveSummaryEn && (
                <div style={{ marginBottom: 16, padding: 12,
                              background: 'var(--info-bg)',
                              border: '1px solid var(--info)',
                              borderRadius: 6 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--info-fg)',
                                marginBottom: 4 }}>
                    {t('Executive Summary', 'التلخيص التنفيذي')}
                  </div>
                  <p style={{ margin: 0, whiteSpace: 'pre-wrap', fontSize: 13 }}>
                    {lang === 'ar' && snap.executiveSummaryAr
                      ? snap.executiveSummaryAr
                      : snap.executiveSummaryEn}
                  </p>
                </div>
              )}
              <h3 style={{ marginTop: 0, fontSize: 14 }}>
                {t('Frozen KPIs at meeting time', 'المؤشّرات المُجمَّدة وقت الاجتماع')}
              </h3>
              {kpis ? (
                <KpiSnapshotView kpis={kpis} t={t} />
              ) : (
                <pre style={{ background: 'var(--surface-2, var(--surface))', padding: 12,
                              fontSize: 11, overflow: 'auto', maxHeight: 400 }}>
                  {snap.kpisJson}
                </pre>
              )}
            </Card>
          )}

          {tab === 'attest' && (
            <>
              <Card padded title={t('Signed attestations', 'إقرارات الأعضاء الموقَّعة')}>
                {snap.attestations.length === 0 ? (
                  <InlineEmpty>
                    {t('No attestations yet.', 'لا توجد إقرارات بعد.')}
                  </InlineEmpty>
                ) : (
                  <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                    {snap.attestations.map(a => (
                      <li key={a.id} style={{ padding: '12px 0',
                                              borderBottom: '1px solid var(--border)' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8,
                                      flexWrap: 'wrap' }}>
                          <Badge tone="info" dot>
                            {ROLE_LABEL[a.attestedRole][lang === 'ar' ? 'ar' : 'en']}
                          </Badge>
                          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                            user#{a.attestedByUserId}
                          </span>
                          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                            {new Date(a.attestedAt).toLocaleString(
                                lang === 'ar' ? 'ar-SA' : 'en-GB')}
                          </span>
                          <code style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                            <Fingerprint size={11}
                                         style={{ verticalAlign: '-1px',
                                                  marginInlineEnd: 4 }} />
                            {shortSignature(a.signatureHash)}
                          </code>
                        </div>
                        {(lang === 'ar' && a.attestationTextAr
                          ? a.attestationTextAr
                          : a.attestationTextEn) && (
                          <p style={{ margin: '6px 0 0 0', fontSize: 13,
                                       whiteSpace: 'pre-wrap' }}>
                            {lang === 'ar' && a.attestationTextAr
                              ? a.attestationTextAr
                              : a.attestationTextEn}
                          </p>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
              {!snap.isFinalized && (
                <Card padded title={t('Add my attestation', 'إقرار من العضو')}>
                  <div style={{ display: 'grid', gap: 8 }}>
                    <label style={{ display: 'grid', gap: 4, fontSize: 13 }}>
                      <span>{t('Role', 'الدور')}</span>
                      <select value={attestRole}
                              onChange={e => setAttestRole(e.target.value as AttestationRole)}
                              style={inputStyle}>
                        {(['CHAIR', 'MEMBER', 'AUDIT_DIRECTOR',
                            'EXTERNAL_AUDITOR', 'COMPANY_SECRETARY'] as AttestationRole[])
                          .map(r => (
                            <option key={r} value={r}>
                              {ROLE_LABEL[r][lang === 'ar' ? 'ar' : 'en']}
                            </option>
                          ))}
                      </select>
                    </label>
                    <label style={{ display: 'grid', gap: 4, fontSize: 13 }}>
                      <span>{t('Attestation text (English)', 'نصّ الإقرار (إنجليزي)')}</span>
                      <textarea rows={2} value={attestEn}
                                onChange={e => setAttestEn(e.target.value)}
                                style={inputStyle} />
                    </label>
                    <label style={{ display: 'grid', gap: 4, fontSize: 13 }}>
                      <span>{t('Attestation text (Arabic)', 'نصّ الإقرار (عربي)')}</span>
                      <textarea rows={2} value={attestAr}
                                onChange={e => setAttestAr(e.target.value)}
                                style={inputStyle} />
                    </label>
                    {attestError && (
                      <div style={{ color: 'var(--danger)', fontSize: 13 }}>{attestError}</div>
                    )}
                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <button onClick={attest} disabled={attestBusy || !attestEn.trim()}
                              style={btnPrimaryStyle}>
                        <CheckCheck size={14}
                                    style={{ verticalAlign: '-2px', marginInlineEnd: 6 }} />
                        {attestBusy ? t('Submitting…', 'جارٍ الإرسال…')
                                    : t('Submit attestation', 'تسجيل الإقرار')}
                      </button>
                    </div>
                  </div>
                </Card>
              )}
            </>
          )}

          {tab === 'resolutions' && (
            <>
              <Card padded title={t('Captured resolutions', 'القرارات المُسجَّلة')}>
                {resolutions.length === 0 ? (
                  <InlineEmpty>
                    {t('No resolutions yet.', 'لا توجد قرارات بعد.')}
                  </InlineEmpty>
                ) : (
                  <ol style={{ paddingInlineStart: 20, margin: 0 }}>
                    {resolutions.map(r => (
                      <li key={r.id} style={{ marginBottom: 12 }}>
                        <p style={{ margin: 0, fontSize: 13 }}>
                          {lang === 'ar' && r.textAr ? r.textAr : r.textEn}
                        </p>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                          user#{r.recordedByUserId} ·{' '}
                          {new Date(r.recordedAt).toLocaleString(
                              lang === 'ar' ? 'ar-SA' : 'en-GB')}
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
              </Card>
              {!snap.isFinalized && (
                <Card padded title={t('Record resolution', 'تسجيل قرار')}>
                  <div style={{ display: 'grid', gap: 8 }}>
                    <label style={{ display: 'grid', gap: 4, fontSize: 13 }}>
                      <span>{t('Resolution text (English)', 'نصّ القرار (إنجليزي)')}</span>
                      <textarea rows={2} value={resEn}
                                onChange={e => setResEn(e.target.value)}
                                style={inputStyle} />
                    </label>
                    <label style={{ display: 'grid', gap: 4, fontSize: 13 }}>
                      <span>{t('Resolution text (Arabic)', 'نصّ القرار (عربي)')}</span>
                      <textarea rows={2} value={resAr}
                                onChange={e => setResAr(e.target.value)}
                                style={inputStyle} />
                    </label>
                    {resError && (
                      <div style={{ color: 'var(--danger)', fontSize: 13 }}>{resError}</div>
                    )}
                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <button onClick={recordResolution}
                              disabled={resBusy || !resEn.trim()}
                              style={btnPrimaryStyle}>
                        {resBusy ? t('Recording…', 'جارٍ التسجيل…')
                                 : t('Record resolution', 'تسجيل قرار')}
                      </button>
                    </div>
                  </div>
                </Card>
              )}
            </>
          )}
        </>
      )}

      {/* Finalize modal */}
      {showFinalize && snap && (
        <div onClick={() => setShowFinalize(false)} role="presentation"
             style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      zIndex: 1000 }}>
          <div onClick={e => e.stopPropagation()} role="dialog" aria-modal="true"
               style={{ background: 'var(--surface)', padding: 20, borderRadius: 8,
                        minWidth: 360, maxWidth: 600,
                        boxShadow: '0 8px 32px rgba(0,0,0,0.2)' }}>
            <h3 style={{ marginTop: 0 }}>{t('Finalize meeting', 'اعتماد الاجتماع')}</h3>
            <div style={{ display: 'grid', gap: 12 }}>
              <label style={{ display: 'grid', gap: 4, fontSize: 13 }}>
                <span>{t('Executive summary (English)',
                         'التلخيص التنفيذي (إنجليزي)')}</span>
                <textarea rows={4} value={summaryEn}
                          onChange={e => setSummaryEn(e.target.value)}
                          style={inputStyle} />
              </label>
              <label style={{ display: 'grid', gap: 4, fontSize: 13 }}>
                <span>{t('Executive summary (Arabic)',
                         'التلخيص التنفيذي (عربي)')}</span>
                <textarea rows={4} value={summaryAr}
                          onChange={e => setSummaryAr(e.target.value)}
                          style={inputStyle} />
              </label>
              {finalizeError && (
                <div style={{ color: 'var(--danger)', fontSize: 13 }}>{finalizeError}</div>
              )}
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                {t('Once finalized the snapshot is locked from further changes.',
                   'بعد الاعتماد تُقفَل اللقطة عن أي تعديل لاحق.')}
              </div>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button onClick={() => setShowFinalize(false)} disabled={finalizeBusy}
                        style={btnSecondaryStyle}>
                  {t('Cancel', 'إلغاء')}
                </button>
                <button onClick={finalize} disabled={finalizeBusy || !summaryEn.trim()}
                        style={btnPrimaryStyle}>
                  {finalizeBusy ? t('Finalizing…', 'جارٍ الاعتماد…')
                                : t('Finalize', 'اعتماد')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </Shell>
  );
}

// ─── Helpers ────────────────────────────────────────────────────

function TabBtn({ active, onClick, Icon, label, count }: {
  active: boolean;
  onClick: () => void;
  Icon: typeof Users;
  label: string;
  count?: number;
}) {
  return (
    <button onClick={onClick}
            style={{ padding: '6px 12px',
                     border: '1px solid ' + (active ? 'var(--primary)' : 'var(--border)'),
                     borderRadius: 6,
                     background: active ? 'var(--primary)' : 'var(--surface)',
                     color: active ? 'white' : 'var(--text)',
                     fontSize: 13, cursor: 'pointer',
                     display: 'flex', alignItems: 'center', gap: 6 }}>
      <Icon size={14} />
      {label}
      {count != null && count > 0 && (
        <span style={{ background: active ? 'rgba(255,255,255,0.3)' : 'var(--surface-2)',
                       padding: '0 6px', borderRadius: 10, fontSize: 11 }}>{count}</span>
      )}
    </button>
  );
}

/** Pretty-render the frozen KPIs JSON. */
function KpiSnapshotView({ kpis, t }: { kpis: any; t: (en: string, ar: string) => string }) {
  if (!kpis) return null;
  const sections: Array<[string, string, string]> = [
    ['risk',        t('Risk posture', 'وضع المخاطر'),               'totalRisks'],
    ['controls',    t('Control posture', 'وضع الضوابط'),             'totalControls'],
    ['findings',    t('Findings', 'الملاحظات'),                       'totalOpen'],
    ['capa',        t('CAPA', 'الإجراءات التصحيحية'),                  'totalOpen'],
    ['ai',          t('AI models', 'نماذج الذكاء الاصطناعي'),          'totalModels'],
    ['climate',     t('Climate', 'المناخ'),                            'totalScenarios'],
    ['obligations', t('Obligations', 'الالتزامات'),                    'totalActive'],
    ['incidents',   t('Incidents', 'الحوادث'),                         'lastQuarterTotal'],
    ['vendorBcp',   t('Vendor BCP', 'استمرارية المورِّدين'),            'latestCampaignInvited'],
    ['threeLines',  t('Three Lines', 'خطوط الدفاع'),                   'line1Count'],
  ];
  return (
    <div style={{ display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 10 }}>
      {sections.map(([key, title, primaryField]) => {
        const sect = kpis[key];
        if (!sect) return null;
        return (
          <div key={key} style={{ border: '1px solid var(--border)', borderRadius: 6,
                                   padding: 12 }}>
            <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6 }}>{title}</div>
            <pre style={{ margin: 0, fontSize: 11, overflow: 'auto', maxHeight: 200,
                          whiteSpace: 'pre-wrap' }}>
              {JSON.stringify(sect, null, 2)}
            </pre>
            {sect[primaryField] != null && (
              <div style={{ marginTop: 6, fontSize: 18, fontWeight: 600,
                            color: 'var(--primary)' }}>
                {sect[primaryField]}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '6px 10px',
  border: '1px solid var(--border)',
  borderRadius: 4,
  fontSize: 14,
  background: 'var(--surface)',
  color: 'var(--text)',
  fontFamily: 'inherit',
};

const btnPrimaryStyle: React.CSSProperties = {
  padding: '6px 14px',
  border: '1px solid var(--primary)',
  borderRadius: 4,
  background: 'var(--primary)',
  color: 'white',
  fontSize: 13,
  cursor: 'pointer',
};

const btnSecondaryStyle: React.CSSProperties = {
  padding: '6px 14px',
  border: '1px solid var(--border)',
  borderRadius: 4,
  background: 'var(--surface)',
  color: 'var(--text)',
  fontSize: 13,
  cursor: 'pointer',
};
