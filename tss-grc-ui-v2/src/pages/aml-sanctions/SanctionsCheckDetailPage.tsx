import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AlertTriangle, ArrowUpRight, CheckCircle2, Clock, RefreshCw, XCircle } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  sanctionsApi, resultLabel, subjectTypeLabel, hitStatusLabel,
  matchTypeLabel,
  type CheckResult, type HitStatus, type ScreeningCheck, type ScreeningHit,
} from '../../lib/sanctions';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const RESULT_TONE: Record<CheckResult, BadgeTone> = {
  NO_HIT: 'success',
  POTENTIAL_HIT: 'warning',
  CONFIRMED_HIT: 'danger',
};

const HIT_STATUS_TONE: Record<HitStatus, BadgeTone> = {
  PENDING_REVIEW: 'warning',
  TRUE_POSITIVE: 'danger',
  FALSE_POSITIVE: 'success',
  ESCALATED: 'accent',
  BLOCK_RECOMMENDED: 'danger',
  RELEASE_RECOMMENDED: 'success',
  CLOSED: 'neutral',
};

/**
 * Sprint 13 — Screening check detail page.
 *
 * <p>Shows the subject's full profile, lists screened against, and every
 * resulting hit. Each hit has an inline review action (FALSE_POSITIVE /
 * TRUE_POSITIVE) plus an "escalate" button for high-confidence cases.
 */
export function SanctionsCheckDetailPage() {
  const { id } = useParams<{ id: string }>();
  const checkId = Number(id);
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [state, setState] = useState<LoadState>('idle');
  const [check, setCheck] = useState<ScreeningCheck | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErr(null);
    try {
      setCheck(await sanctionsApi.getCheck(checkId));
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { if (!Number.isNaN(checkId)) void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [checkId]);

  async function quickReview(hit: ScreeningHit, newStatus: HitStatus,
                             dispositionEn: string, dispositionAr: string) {
    try {
      await sanctionsApi.reviewHit(hit.id, {
        newStatus, dispositionEn, dispositionAr,
      });
      await load();
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
    }
  }

  async function quickEscalate(hit: ScreeningHit) {
    const reasonEn = 'Composite match with name + DOB + ID. Senior review required.';
    const reasonAr = 'تطابق مركّب — اسم + تاريخ ميلاد + هوية. يتطلّب مراجعة من جهة أعلى.';
    try {
      await sanctionsApi.escalateHit(hit.id, {
        reasonEn, reasonAr,
      });
      await load();
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
    }
  }

  if (Number.isNaN(checkId)) {
    return (
      <Shell title="Check">
        <Card>
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>
            {isAr ? 'مُعرّف الفحص غير صالح' : 'Invalid check id'}{' '}
            <Link to="/aml/sanctions/checks">{isAr ? 'العودة' : 'Back'}</Link>
          </div>
        </Card>
      </Shell>
    );
  }

  return (
    <Shell
      title={isAr ? 'تفاصيل الفحص' : 'Screening check detail'}
      sub={check?.checkCode}
    >
      <PageHeader
        eyebrow={isAr ? 'الفحص ضد قوائم العقوبات' : 'Sanctions screening'}
        title={check ? check.checkCode : (isAr ? 'تحميل…' : 'Loading…')}
        sub={isAr
          ? 'تفاصيل الفاحوص والتطابقات والتصرّفات'
          : 'Subject details, hits, and dispositions'}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={() => void load()}>
              <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Link to="/aml/sanctions/checks">
              <Button variant="ghost" size="sm">{isAr ? 'العودة للسجلّ' : 'Back to log'}</Button>
            </Link>
          </>
        }
      />

      {state === 'loading' && (
        <Card><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div></Card>
      )}
      {state === 'error' && (
        <Card><div style={{ padding: 32, textAlign: 'center', fontSize: 12, color: 'var(--danger-fg)' }}>{err}</div></Card>
      )}

      {state === 'ready' && check && (
        <>
          {/* Subject card */}
          <Card>
            <div style={{ padding: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                <div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    {subjectTypeLabel(check.subjectType, isAr)}
                    {check.subjectReference && ` · ${check.subjectReference}`}
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 700, marginTop: 4 }}>
                    {isAr && check.subjectNameAr ? check.subjectNameAr : check.subjectNameEn}
                  </div>
                  {check.subjectNameAr && check.subjectNameEn !== check.subjectNameAr && (
                    <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>
                      {isAr ? check.subjectNameEn : check.subjectNameAr}
                    </div>
                  )}
                </div>
                <Badge tone={RESULT_TONE[check.result]}>{resultLabel(check.result, isAr)}</Badge>
              </div>

              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                gap: 12,
                fontSize: 11,
              }}>
                <KvRow k={isAr ? 'تاريخ الميلاد' : 'Date of birth'} v={check.subjectDob ?? '—'} />
                <KvRow k={isAr ? 'البلد' : 'Country'} v={check.subjectCountry ?? '—'} />
                <KvRow k={isAr ? 'وثيقة الهوية' : 'ID document'} v={check.subjectIdDocument ?? '—'} />
                <KvRow k={isAr ? 'مصدر الفحص' : 'Check source'} v={check.checkSource} />
                <KvRow k={isAr ? 'وقت الفحص' : 'Checked at'} v={check.checkAt.slice(0, 19).replace('T', ' ')} />
                <KvRow k={isAr ? 'القوائم المفحوصة' : 'Lists screened'} v={check.listsScreenedCodes ?? '—'} />
              </div>
            </div>
          </Card>

          {/* Hits */}
          <div style={{ marginTop: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
              {isAr ? `التطابقات (${check.hits.length})` : `Hits (${check.hits.length})`}
            </div>

            {check.hits.length === 0 && (
              <Card>
                <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
                  <CheckCircle2 size={20} style={{ marginBottom: 8, color: 'var(--success-fg)' }} />
                  <div>{isAr ? 'لم يتمّ العثور على أي تطابق' : 'No hits found'}</div>
                </div>
              </Card>
            )}

            {check.hits.map(h => (
              <div key={h.id} style={{ marginBottom: 12 }}>
              <Card>
                <div style={{ padding: 14 }}>
                  <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    marginBottom: 10,
                  }}>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                        <Badge tone={HIT_STATUS_TONE[h.status]}>{hitStatusLabel(h.status, isAr)}</Badge>
                        {h.slaBreach && (
                          <Badge tone="danger">
                            <Clock size={10} style={{ verticalAlign: 'middle' }} />{' '}
                            {isAr ? 'تجاوز المهلة' : 'SLA breach'}
                          </Badge>
                        )}
                      </div>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>
                        {isAr && h.matchedNameAr ? h.matchedNameAr : h.matchedNameEn}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                        {h.listCode} · {matchTypeLabel(h.matchType, isAr)}
                      </div>
                    </div>
                    <div style={{ textAlign: 'end' }}>
                      <div style={{ fontSize: 24, fontWeight: 700 }}>
                        {Number(h.matchScore).toFixed(0)}
                      </div>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                        {isAr ? 'درجة المطابقة' : 'Match score'}
                      </div>
                    </div>
                  </div>

                  {(h.dispositionEn || h.dispositionAr) && (
                    <div style={{
                      padding: 10,
                      background: 'var(--surface-2)',
                      borderRadius: 'var(--r-sm)',
                      fontSize: 11,
                      marginBottom: 10,
                    }}>
                      <div style={{ fontWeight: 600, marginBottom: 4, color: 'var(--ink-3)' }}>
                        {isAr ? 'تصرّف' : 'Disposition'}
                      </div>
                      {isAr && h.dispositionAr ? h.dispositionAr : h.dispositionEn}
                    </div>
                  )}

                  {h.status === 'PENDING_REVIEW' && (
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => void quickReview(h, 'FALSE_POSITIVE',
                          'Common Arab name, no DOB or ID match. Distinct individual.',
                          'اسم عربي شائع، لا يوجد تطابق في تاريخ الميلاد أو الهوية. شخص مختلف.')}>
                        <XCircle size={12} /> {isAr ? 'إيجابي خاطئ' : 'False positive'}
                      </Button>
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => void quickReview(h, 'TRUE_POSITIVE',
                          'Confirmed match against the designated entity. Block and file SAFIU notification.',
                          'تطابق مؤكّد مع جهة مُعيَّنة. الحجب وإبلاغ إدارة التحريات المالية.')}>
                        <AlertTriangle size={12} /> {isAr ? 'إيجابي حقيقي' : 'True positive'}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => void quickEscalate(h)}>
                        <ArrowUpRight size={12} /> {isAr ? 'تصعيد' : 'Escalate'}
                      </Button>
                    </div>
                  )}
                </div>
              </Card>
              </div>
            ))}
          </div>
        </>
      )}
    </Shell>
  );
}

function KvRow({ k, v }: { k: string; v: string }) {
  return (
    <div>
      <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{k}</div>
      <div style={{ fontSize: 12, fontWeight: 500 }}>{v}</div>
    </div>
  );
}
