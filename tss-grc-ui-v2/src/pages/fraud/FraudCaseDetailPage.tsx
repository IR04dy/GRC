import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, AlertTriangle, Search, FileText, Flag,
  Building2, Wallet, XCircle, CheckCircle2, Lock, ScrollText,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Input, Textarea } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  fraudApi,
  CASE_STATUS_LABEL, CASE_STATUS_TONE,
  SEVERITY_LABEL, SEVERITY_TONE,
  DETECTION_SOURCE_LABEL, UPDATE_TYPE_LABEL,
  type CaseResponse, type CaseUpdateResponse,
  type Severity, type Outcome,
  type InvestigationUpdateRequest,
} from '../../lib/fraud';

/**
 * Fraud Case Detail — /fraud/cases/:id.
 *
 * Shows case header (ref + subject + status + severity badges),
 * 3-column meta card, loss summary, narrative, an action panel
 * whose buttons change with `status`, and a newest-first timeline.
 * All write actions reload the case + timeline so the UI stays
 * consistent with the backend state machine.
 */
export function FraudCaseDetailPage() {
  const { lang } = useI18n();
  const navigate = useNavigate();
  const { id: idParam } = useParams<{ id: string }>();
  const id = idParam ? parseInt(idParam, 10) : NaN;

  const [caseData, setCaseData] = useState<CaseResponse | null>(null);
  const [timeline, setTimeline] = useState<CaseUpdateResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Which action modal is open, if any.
  type ActionKey =
    | 'triage' | 'falsePositive'
    | 'evidence' | 'finding' | 'str' | 'lawEnf' | 'recovery'
    | 'outcome' | 'close';
  const [activeAction, setActiveAction] = useState<ActionKey | null>(null);

  async function load() {
    if (!Number.isFinite(id)) return;
    setLoading(true);
    setError(null);
    try {
      const [c, tl] = await Promise.all([
        fraudApi.getCase(id),
        fraudApi.listTimeline(id),
      ]);
      setCaseData(c);
      setTimeline(tl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل القضية' : 'Failed to load case'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [id]);

  const sarFmt = useMemo(() => new Intl.NumberFormat(lang === 'ar' ? 'ar-SA' : 'en-SA', {
    style: 'currency', currency: 'SAR', maximumFractionDigits: 0,
  }), [lang]);

  // Newest-first timeline ordering.
  const sortedTimeline = useMemo(
    () => [...timeline].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt)),
    [timeline],
  );

  if (!Number.isFinite(id)) {
    return (
      <Shell title={lang === 'ar' ? 'القضية غير موجودة' : 'Case not found'}>
        <Card padded>
          <EmptyState
            Icon={AlertTriangle}
            title={lang === 'ar' ? 'مُعرّف غير صالح' : 'Invalid identifier'}
            sub={lang === 'ar' ? 'لا يمكن قراءة مُعرّف القضية من الرابط.' : 'Could not parse case id from URL.'} />
        </Card>
      </Shell>
    );
  }

  const status = caseData?.status;

  return (
    <Shell
      title={caseData
        ? (lang === 'ar' ? caseData.subjectAr : caseData.subjectEn)
        : '…'}
      sub={lang === 'ar'
        ? 'تفاصيل قضية احتيال — التحقيق والقرار والإغلاق وفق إطار ساما 2018.'
        : 'Fraud case detail — investigation, outcome and closure per SAMA 2018 framework.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'مكافحة الاحتيال · المرحلة ١٤' : 'Fraud Risk · Sprint 14'}
        title={caseData
          ? `${caseData.externalReference} — ${lang === 'ar' ? caseData.subjectAr : caseData.subjectEn}`
          : '…'}
        sub={caseData
          ? `${caseData.typologyCodeSnapshot} · ${
              lang === 'ar' ? (caseData.typologyNameAr ?? '') : (caseData.typologyNameEn ?? '')
            }`
          : undefined}
        actions={(
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            {caseData && (
              <>
                <Badge tone={SEVERITY_TONE[caseData.severity]}>
                  {SEVERITY_LABEL[caseData.severity][lang === 'ar' ? 'ar' : 'en']}
                </Badge>
                <Badge tone={CASE_STATUS_TONE[caseData.status]}>
                  {CASE_STATUS_LABEL[caseData.status][lang === 'ar' ? 'ar' : 'en']}
                </Badge>
              </>
            )}
            <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/fraud/cases')}>
              {lang === 'ar' ? 'رجوع' : 'Back'}
            </Button>
          </div>
        )}
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {loading || !caseData ? (
        <Card padded>
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        </Card>
      ) : (
        <div className="fraud-detail-grid" style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}>
          {/* ── Main column ─────────────────────────────────────── */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, minWidth: 0 }}>
            {/* Meta */}
            <Card padded title={lang === 'ar' ? 'بيانات القضية' : 'Case info'}>
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                gap: 14,
              }}>
                <Field label={lang === 'ar' ? 'النمط' : 'Typology'}
                  value={`${caseData.typologyCodeSnapshot} — ${
                    lang === 'ar' ? (caseData.typologyNameAr ?? '') : (caseData.typologyNameEn ?? '')
                  }`} />
                <Field label={lang === 'ar' ? 'مصدر الرصد' : 'Detection Source'}
                  value={DETECTION_SOURCE_LABEL[caseData.detectionSource][lang === 'ar' ? 'ar' : 'en']} />
                {caseData.detectionReference && (
                  <Field label={lang === 'ar' ? 'مرجع الرصد' : 'Detection ref'}
                    value={caseData.detectionReference} />
                )}
                <Field label={lang === 'ar' ? 'تاريخ الرصد' : 'Detected at'}
                  value={fmtDateTime(caseData.detectedAt, lang)} />
                {caseData.reportedAt && (
                  <Field label={lang === 'ar' ? 'تاريخ البلاغ' : 'Reported at'}
                    value={fmtDateTime(caseData.reportedAt, lang)} />
                )}
                {caseData.triagedAt && (
                  <Field label={lang === 'ar' ? 'تاريخ الفرز' : 'Triaged at'}
                    value={fmtDateTime(caseData.triagedAt, lang)} />
                )}
                {caseData.investigationStartedAt && (
                  <Field label={lang === 'ar' ? 'بدء التحقيق' : 'Investigation started'}
                    value={fmtDateTime(caseData.investigationStartedAt, lang)} />
                )}
                {caseData.outcomeSetAt && (
                  <Field label={lang === 'ar' ? 'تسجيل النتيجة' : 'Outcome set'}
                    value={fmtDateTime(caseData.outcomeSetAt, lang)} />
                )}
                {caseData.closedAt && (
                  <Field label={lang === 'ar' ? 'تاريخ الإغلاق' : 'Closed at'}
                    value={fmtDateTime(caseData.closedAt, lang)} />
                )}
                {caseData.incidentDate && (
                  <Field label={lang === 'ar' ? 'تاريخ الواقعة' : 'Incident date'}
                    value={new Date(caseData.incidentDate).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')} />
                )}
              </div>
            </Card>

            {/* Loss summary */}
            <Card padded title={lang === 'ar' ? 'ملخّص الخسائر' : 'Loss summary'}>
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                gap: 14,
              }}>
                <Field label={lang === 'ar' ? 'الخسارة المقدّرة' : 'Estimated'}
                  value={caseData.estimatedLossSar != null ? sarFmt.format(caseData.estimatedLossSar) : '—'} />
                <Field label={lang === 'ar' ? 'الخسارة المؤكَّدة' : 'Confirmed'}
                  value={caseData.confirmedLossSar != null ? sarFmt.format(caseData.confirmedLossSar) : '—'} />
                <Field label={lang === 'ar' ? 'المبلغ المُستردّ' : 'Recovered'}
                  value={caseData.recoveredAmountSar != null ? sarFmt.format(caseData.recoveredAmountSar) : '—'} />
                <Field label={lang === 'ar' ? 'العملاء المتضرّرون' : 'Affected customers'}
                  value={String(caseData.affectedCustomerCount ?? 0)} />
                <Field label={lang === 'ar' ? 'بلاغ معاملة مشبوهة' : 'STR filed'}
                  valueNode={caseData.strFiled
                    ? <Badge tone="success">
                        {lang === 'ar' ? `نعم${caseData.strReference ? ` — ${caseData.strReference}` : ''}` : `Yes${caseData.strReference ? ` — ${caseData.strReference}` : ''}`}
                      </Badge>
                    : <Badge tone="neutral">{lang === 'ar' ? 'لا' : 'No'}</Badge>} />
                <Field label={lang === 'ar' ? 'تبليغ جهة إنفاذ' : 'Law enforcement'}
                  valueNode={caseData.lawEnforcementEngaged
                    ? <Badge tone="warning">
                        {lang === 'ar' ? `نعم${caseData.lawEnforcementReference ? ` — ${caseData.lawEnforcementReference}` : ''}` : `Yes${caseData.lawEnforcementReference ? ` — ${caseData.lawEnforcementReference}` : ''}`}
                      </Badge>
                    : <Badge tone="neutral">{lang === 'ar' ? 'لا' : 'No'}</Badge>} />
              </div>
            </Card>

            {/* Narrative */}
            <Card padded title={lang === 'ar' ? 'السرد التفصيلي' : 'Narrative'}>
              {(caseData.narrativeEn || caseData.narrativeAr) ? (
                <div style={{
                  whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.6, color: 'var(--ink-1)',
                  direction: lang === 'ar' ? 'rtl' : 'ltr',
                }}>
                  {lang === 'ar' ? (caseData.narrativeAr || caseData.narrativeEn) : (caseData.narrativeEn || caseData.narrativeAr)}
                </div>
              ) : (
                <InlineEmpty>{lang === 'ar' ? 'لا يوجد سرد تفصيلي.' : 'No narrative recorded.'}</InlineEmpty>
              )}
            </Card>

            {/* Closure notes */}
            {(caseData.closureNotesEn || caseData.closureNotesAr) && (
              <Card padded title={lang === 'ar' ? 'ملاحظات الإغلاق' : 'Closure notes'}>
                <div style={{
                  whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.6, color: 'var(--ink-1)',
                  direction: lang === 'ar' ? 'rtl' : 'ltr',
                }}>
                  {lang === 'ar' ? (caseData.closureNotesAr || caseData.closureNotesEn) : (caseData.closureNotesEn || caseData.closureNotesAr)}
                </div>
              </Card>
            )}

            {/* Timeline */}
            <Card padded title={lang === 'ar' ? 'سجلّ التحقيق' : 'Investigation timeline'}>
              {sortedTimeline.length === 0 ? (
                <InlineEmpty>
                  {lang === 'ar' ? 'لا توجد تحديثات بعد.' : 'No updates yet.'}
                </InlineEmpty>
              ) : (
                <ol style={{
                  listStyle: 'none', margin: 0, padding: 0,
                  display: 'flex', flexDirection: 'column', gap: 10,
                }}>
                  {sortedTimeline.map(u => (
                    <li key={u.id} style={{
                      border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                      padding: 10, background: 'var(--surface-1)',
                    }}>
                      <div style={{
                        display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
                        gap: 10, flexWrap: 'wrap', marginBottom: 4,
                      }}>
                        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                          <Badge tone="info">
                            {UPDATE_TYPE_LABEL[u.updateType][lang === 'ar' ? 'ar' : 'en']}
                          </Badge>
                          {u.statusAfter && (
                            <Badge tone={CASE_STATUS_TONE[u.statusAfter]}>
                              {CASE_STATUS_LABEL[u.statusAfter][lang === 'ar' ? 'ar' : 'en']}
                            </Badge>
                          )}
                          {u.actorName && (
                            <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
                              {lang === 'ar' ? 'بواسطة' : 'by'} {u.actorName}
                            </span>
                          )}
                        </div>
                        <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                          {fmtDateTime(u.occurredAt, lang)}
                        </span>
                      </div>
                      {(u.noteEn || u.noteAr) && (
                        <div style={{
                          fontSize: 13, color: 'var(--ink-1)', marginTop: 4,
                          whiteSpace: 'pre-wrap',
                        }}>
                          {lang === 'ar' ? (u.noteAr || u.noteEn) : (u.noteEn || u.noteAr)}
                        </div>
                      )}
                      {u.evidenceReference && (
                        <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 6 }}>
                          {lang === 'ar' ? 'مرجع الدليل: ' : 'Evidence ref: '}
                          <code style={{ fontSize: 12 }}>{u.evidenceReference}</code>
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              )}
            </Card>
          </div>

          {/* ── Action panel ─────────────────────────────────────── */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, minWidth: 0 }}>
            <Card padded title={lang === 'ar' ? 'الإجراءات' : 'Actions'}>
              {status === 'DETECTED' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <Button variant="primary" Icon={Search}
                    onClick={() => setActiveAction('triage')}>
                    {lang === 'ar' ? 'بدء التحقيق' : 'Triage → Investigation'}
                  </Button>
                  <Button variant="secondary" Icon={XCircle}
                    onClick={() => setActiveAction('falsePositive')}>
                    {lang === 'ar' ? 'إنذار كاذب' : 'Mark False Positive'}
                  </Button>
                </div>
              )}

              {status === 'INVESTIGATING' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <Button variant="secondary" Icon={FileText}
                    onClick={() => setActiveAction('evidence')}>
                    {lang === 'ar' ? 'تسجيل دليل' : 'Record evidence'}
                  </Button>
                  <Button variant="secondary" Icon={ScrollText}
                    onClick={() => setActiveAction('finding')}>
                    {lang === 'ar' ? 'تسجيل ملاحظة' : 'Record finding'}
                  </Button>
                  <Button variant="secondary" Icon={Flag}
                    onClick={() => setActiveAction('str')}>
                    {lang === 'ar' ? 'تأكيد إيداع بلاغ ساما' : 'Mark STR filed'}
                  </Button>
                  <Button variant="secondary" Icon={Building2}
                    onClick={() => setActiveAction('lawEnf')}>
                    {lang === 'ar' ? 'إبلاغ جهة إنفاذ' : 'Notify law enforcement'}
                  </Button>
                  <Button variant="secondary" Icon={Wallet}
                    onClick={() => setActiveAction('recovery')}>
                    {lang === 'ar' ? 'تسجيل استرداد' : 'Record recovery'}
                  </Button>
                  <div style={{ height: 6 }} />
                  <Button variant="primary" Icon={CheckCircle2}
                    onClick={() => setActiveAction('outcome')}>
                    {lang === 'ar' ? 'تسجيل نتيجة التحقيق' : 'Set outcome'}
                  </Button>
                </div>
              )}

              {(status === 'CONFIRMED' || status === 'CLEARED' || status === 'FALSE_POSITIVE') && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <Button variant="primary" Icon={Lock}
                    onClick={() => setActiveAction('close')}>
                    {lang === 'ar' ? 'إغلاق القضية' : 'Close case'}
                  </Button>
                </div>
              )}

              {status === 'CLOSED' && (
                <InlineEmpty>
                  {lang === 'ar' ? 'القضية مغلقة — لا توجد إجراءات إضافية.' : 'Case closed — no further actions.'}
                </InlineEmpty>
              )}
            </Card>
          </div>
        </div>
      )}

      {/* ── Action modals ─────────────────────────────────────── */}
      {caseData && (
        <>
          <TriageModal
            open={activeAction === 'triage' || activeAction === 'falsePositive'}
            falsePositive={activeAction === 'falsePositive'}
            caseId={caseData.id}
            onClose={() => setActiveAction(null)}
            onDone={() => { setActiveAction(null); load(); }} />

          <InvestigationUpdateModal
            open={activeAction === 'evidence' || activeAction === 'finding'
              || activeAction === 'str' || activeAction === 'lawEnf'
              || activeAction === 'recovery'}
            kind={activeAction === 'evidence' ? 'EVIDENCE_RECORDED'
                 : activeAction === 'finding' ? 'FINDING_RECORDED'
                 : activeAction === 'str' ? 'STR_FILED'
                 : activeAction === 'lawEnf' ? 'LAW_ENFORCEMENT_NOTIFIED'
                 : 'RECOVERY_RECORDED'}
            caseId={caseData.id}
            onClose={() => setActiveAction(null)}
            onDone={() => { setActiveAction(null); load(); }} />

          <OutcomeModal
            open={activeAction === 'outcome'}
            caseId={caseData.id}
            onClose={() => setActiveAction(null)}
            onDone={() => { setActiveAction(null); load(); }} />

          <CloseModal
            open={activeAction === 'close'}
            caseId={caseData.id}
            onClose={() => setActiveAction(null)}
            onDone={() => { setActiveAction(null); load(); }} />
        </>
      )}
    </Shell>
  );
}

// ─── Helpers ──────────────────────────────────────────────────────

function fmtDateTime(iso: string, lang: 'en' | 'ar'): string {
  const d = new Date(iso);
  return d.toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB', {
    year: 'numeric', month: 'short', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

function Field({ label, value, valueNode }: { label: string; value?: string; valueNode?: React.ReactNode }) {
  return (
    <div>
      <div style={{
        fontSize: 11, color: 'var(--ink-3)',
        textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 4,
      }}>
        {label}
      </div>
      <div style={{ fontSize: 13, color: 'var(--ink-1)' }}>{valueNode ?? value ?? '—'}</div>
    </div>
  );
}

// ─── Triage / False-positive modal ────────────────────────────────

function TriageModal({
  open, falsePositive, caseId, onClose, onDone,
}: {
  open: boolean;
  falsePositive: boolean;
  caseId: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const { lang } = useI18n();
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [notesEn, setNotesEn] = useState('');
  const [notesAr, setNotesAr] = useState('');
  const [assignee, setAssignee] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setSeverity('MEDIUM');
      setNotesEn('');
      setNotesAr('');
      setAssignee('');
      setError(null);
    }
  }, [open]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const aid = assignee.trim() ? parseInt(assignee, 10) : undefined;
      await fraudApi.triage(caseId, {
        severity,
        proceedToInvestigation: !falsePositive,
        triageNotesEn: notesEn || undefined,
        triageNotesAr: notesAr || undefined,
        assignedInvestigatorUserId: Number.isFinite(aid) ? aid : undefined,
      });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إتمام الفرز' : 'Failed to triage case'));
    } finally {
      setSubmitting(false);
    }
  }

  const severityOptions: Array<{ value: Severity; label: string }> = [
    { value: 'CRITICAL', label: SEVERITY_LABEL.CRITICAL[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'HIGH',     label: SEVERITY_LABEL.HIGH[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'MEDIUM',   label: SEVERITY_LABEL.MEDIUM[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'LOW',      label: SEVERITY_LABEL.LOW[lang === 'ar' ? 'ar' : 'en'] },
  ];

  return (
    <Modal
      open={open}
      title={falsePositive
        ? (lang === 'ar' ? 'تسجيل إنذار كاذب' : 'Mark False Positive')
        : (lang === 'ar' ? 'بدء التحقيق' : 'Triage → Investigation')}
      onClose={onClose}
      size="md"
    >
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Select label={lang === 'ar' ? 'الخطورة' : 'Severity'}
          value={severity}
          onChange={e => setSeverity(e.target.value as Severity)}
          options={severityOptions} />

        {!falsePositive && (
          <Input label={lang === 'ar' ? 'مُعرّف المحقّق (اختياري)' : 'Investigator user id (optional)'}
            type="number"
            value={assignee}
            onChange={e => setAssignee(e.target.value)} />
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <Textarea label={lang === 'ar' ? 'ملاحظات الفرز (EN)' : 'Triage notes (EN)'}
            value={notesEn} onChange={e => setNotesEn(e.target.value)} rows={3} />
          <Textarea label={lang === 'ar' ? 'ملاحظات الفرز (AR)' : 'Triage notes (AR)'}
            value={notesAr} onChange={e => setNotesAr(e.target.value)} rows={3} dir="rtl" />
        </div>

        {error && <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button type="button" variant="ghost" onClick={onClose}>
            {lang === 'ar' ? 'إلغاء' : 'Cancel'}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {falsePositive
              ? (lang === 'ar' ? 'تأكيد الإنذار الكاذب' : 'Confirm false positive')
              : (lang === 'ar' ? 'بدء التحقيق' : 'Start investigation')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ─── Investigation update modal (5 variants) ─────────────────────

type InvestigationKind = InvestigationUpdateRequest['updateType'];

function InvestigationUpdateModal({
  open, kind, caseId, onClose, onDone,
}: {
  open: boolean;
  kind: InvestigationKind;
  caseId: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const { lang } = useI18n();
  const [noteEn, setNoteEn] = useState('');
  const [noteAr, setNoteAr] = useState('');
  const [evidenceReference, setEvidenceReference] = useState('');
  const [recoveredAmount, setRecoveredAmount] = useState('');
  const [strReference, setStrReference] = useState('');
  const [lawEnforcementReference, setLawEnforcementReference] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setNoteEn('');
      setNoteAr('');
      setEvidenceReference('');
      setRecoveredAmount('');
      setStrReference('');
      setLawEnforcementReference('');
      setError(null);
    }
  }, [open]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const body: InvestigationUpdateRequest = {
        updateType: kind,
        noteEn: noteEn || undefined,
        noteAr: noteAr || undefined,
      };
      if (kind === 'EVIDENCE_RECORDED' && evidenceReference) {
        body.evidenceReference = evidenceReference;
      }
      if (kind === 'STR_FILED' && strReference) {
        body.strReference = strReference;
      }
      if (kind === 'LAW_ENFORCEMENT_NOTIFIED' && lawEnforcementReference) {
        body.lawEnforcementReference = lawEnforcementReference;
      }
      if (kind === 'RECOVERY_RECORDED') {
        const n = parseFloat(recoveredAmount);
        if (Number.isFinite(n)) body.recoveredAmountSar = n;
      }
      await fraudApi.recordUpdate(caseId, body);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر حفظ التحديث' : 'Failed to record update'));
    } finally {
      setSubmitting(false);
    }
  }

  const title = (() => {
    switch (kind) {
      case 'EVIDENCE_RECORDED':        return lang === 'ar' ? 'تسجيل دليل' : 'Record evidence';
      case 'FINDING_RECORDED':         return lang === 'ar' ? 'تسجيل ملاحظة تحقيق' : 'Record finding';
      case 'STR_FILED':                return lang === 'ar' ? 'تأكيد إيداع بلاغ ساما' : 'Mark STR filed (SAFIU)';
      case 'LAW_ENFORCEMENT_NOTIFIED': return lang === 'ar' ? 'إبلاغ جهة إنفاذ' : 'Notify law enforcement';
      case 'RECOVERY_RECORDED':        return lang === 'ar' ? 'تسجيل استرداد' : 'Record recovery';
    }
  })();

  return (
    <Modal open={open} title={title} onClose={onClose} size="md">
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {kind === 'EVIDENCE_RECORDED' && (
          <Input label={lang === 'ar' ? 'مرجع الدليل' : 'Evidence reference'}
            value={evidenceReference}
            onChange={e => setEvidenceReference(e.target.value)}
            placeholder="EVD-2025-…" />
        )}
        {kind === 'STR_FILED' && (
          <Input label={lang === 'ar' ? 'مرجع بلاغ ساما (SAFIU)' : 'STR reference (SAFIU)'}
            value={strReference}
            onChange={e => setStrReference(e.target.value)}
            placeholder="STR-…" required />
        )}
        {kind === 'LAW_ENFORCEMENT_NOTIFIED' && (
          <Input label={lang === 'ar' ? 'مرجع البلاغ لدى جهة الإنفاذ' : 'Law enforcement reference'}
            value={lawEnforcementReference}
            onChange={e => setLawEnforcementReference(e.target.value)}
            placeholder="LE-…" required />
        )}
        {kind === 'RECOVERY_RECORDED' && (
          <Input label={lang === 'ar' ? 'المبلغ المُستردّ (بالريال)' : 'Recovered amount (SAR)'}
            type="number" step="0.01"
            value={recoveredAmount}
            onChange={e => setRecoveredAmount(e.target.value)}
            required />
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <Textarea label={lang === 'ar' ? 'ملاحظة (EN)' : 'Note (EN)'}
            value={noteEn} onChange={e => setNoteEn(e.target.value)} rows={3} />
          <Textarea label={lang === 'ar' ? 'ملاحظة (AR)' : 'Note (AR)'}
            value={noteAr} onChange={e => setNoteAr(e.target.value)} rows={3} dir="rtl" />
        </div>

        {error && <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button type="button" variant="ghost" onClick={onClose}>
            {lang === 'ar' ? 'إلغاء' : 'Cancel'}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {lang === 'ar' ? 'حفظ' : 'Save'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ─── Outcome modal ───────────────────────────────────────────────

function OutcomeModal({
  open, caseId, onClose, onDone,
}: {
  open: boolean;
  caseId: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const { lang } = useI18n();
  const [outcome, setOutcome] = useState<Outcome>('CONFIRMED');
  const [confirmedLoss, setConfirmedLoss] = useState('');
  const [findingsEn, setFindingsEn] = useState('');
  const [findingsAr, setFindingsAr] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setOutcome('CONFIRMED');
      setConfirmedLoss('');
      setFindingsEn('');
      setFindingsAr('');
      setError(null);
    }
  }, [open]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const n = parseFloat(confirmedLoss);
      await fraudApi.setOutcome(caseId, {
        outcome,
        confirmedLossSar: Number.isFinite(n) ? n : undefined,
        findingsEn: findingsEn || undefined,
        findingsAr: findingsAr || undefined,
      });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تسجيل النتيجة' : 'Failed to set outcome'));
    } finally {
      setSubmitting(false);
    }
  }

  const outcomeOptions: Array<{ value: Outcome; label: string }> = [
    { value: 'CONFIRMED',      label: lang === 'ar' ? 'مؤكَّدة (احتيال فعلي)' : 'Confirmed fraud' },
    { value: 'CLEARED',        label: lang === 'ar' ? 'تمت تبرئتها (لا يوجد احتيال)' : 'Cleared (no fraud)' },
    { value: 'FALSE_POSITIVE', label: lang === 'ar' ? 'إنذار كاذب' : 'False positive' },
  ];

  return (
    <Modal
      open={open}
      title={lang === 'ar' ? 'تسجيل نتيجة التحقيق' : 'Set investigation outcome'}
      onClose={onClose}
      size="md"
    >
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Select label={lang === 'ar' ? 'النتيجة' : 'Outcome'}
          value={outcome}
          onChange={e => setOutcome(e.target.value as Outcome)}
          options={outcomeOptions} />

        {outcome === 'CONFIRMED' && (
          <Input label={lang === 'ar' ? 'الخسارة المؤكَّدة (بالريال)' : 'Confirmed loss (SAR)'}
            type="number" step="0.01"
            value={confirmedLoss}
            onChange={e => setConfirmedLoss(e.target.value)} />
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <Textarea label={lang === 'ar' ? 'الخلاصة (EN)' : 'Findings (EN)'}
            value={findingsEn} onChange={e => setFindingsEn(e.target.value)} rows={3} />
          <Textarea label={lang === 'ar' ? 'الخلاصة (AR)' : 'Findings (AR)'}
            value={findingsAr} onChange={e => setFindingsAr(e.target.value)} rows={3} dir="rtl" />
        </div>

        {error && <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button type="button" variant="ghost" onClick={onClose}>
            {lang === 'ar' ? 'إلغاء' : 'Cancel'}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {lang === 'ar' ? 'حفظ النتيجة' : 'Save outcome'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ─── Close modal ─────────────────────────────────────────────────

function CloseModal({
  open, caseId, onClose, onDone,
}: {
  open: boolean;
  caseId: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const { lang } = useI18n();
  const [closureNotesEn, setClosureNotesEn] = useState('');
  const [closureNotesAr, setClosureNotesAr] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setClosureNotesEn('');
      setClosureNotesAr('');
      setError(null);
    }
  }, [open]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await fraudApi.closeCase(caseId, {
        closureNotesEn,
        closureNotesAr,
      });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إغلاق القضية' : 'Failed to close case'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={open}
      title={lang === 'ar' ? 'إغلاق القضية' : 'Close case'}
      onClose={onClose}
      size="md"
    >
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <Textarea label={lang === 'ar' ? 'ملاحظات الإغلاق (EN)' : 'Closure notes (EN)'}
            value={closureNotesEn}
            onChange={e => setClosureNotesEn(e.target.value)}
            rows={4} required />
          <Textarea label={lang === 'ar' ? 'ملاحظات الإغلاق (AR)' : 'Closure notes (AR)'}
            value={closureNotesAr}
            onChange={e => setClosureNotesAr(e.target.value)}
            rows={4} required dir="rtl" />
        </div>

        {error && <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button type="button" variant="ghost" onClick={onClose}>
            {lang === 'ar' ? 'إلغاء' : 'Cancel'}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {lang === 'ar' ? 'إغلاق القضية' : 'Close case'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
