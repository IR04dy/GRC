import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, Send, ChevronRight, CheckCircle2, AlertTriangle } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listTemplates, dispatchCascade, listCascades, listChannelReadiness,
  AUDIENCES, SCENARIO_CATEGORIES, CHANNELS,
  AUDIENCE_LABELS, SCENARIO_LABELS, CHANNEL_LABELS,
  cascadeStatusTone, parsePlaceholders, isSimulatedChannel,
  type CrisisCommsTemplate, type Audience, type ScenarioCategory,
  type ChannelType, type DispatchResult, type ChannelReadiness,
  type CascadeResponse,
} from '../../lib/crisisComms';

/**
 * Slice 9-E — Crisis Communications dispatch wizard.
 *
 * <p>Four-step wizard so the Crisis Manager can blast a coordinated
 * cascade across Email / SMS / WhatsApp / Teams in under two minutes:
 *
 * <ol>
 *   <li>Pick crisis event + template (filtered by audience + scenario).</li>
 *   <li>Pick channels (Email / SMS / WhatsApp / Teams).</li>
 *   <li>Fill placeholders + paste recipient list.</li>
 *   <li>Preview + send. The result panel shows per-channel cascade rows
 *       with success / failure / latency.</li>
 * </ol>
 */
export function CrisisCommsDispatchWizard() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [step, setStep] = useState(1);
  const [templates, setTemplates] = useState<CrisisCommsTemplate[]>([]);
  const [loading, setLoading] = useState(false);

  // Step-1 state
  const [crisisEventId, setCrisisEventId] = useState<number | ''>('');
  const [audienceFilter, setAudienceFilter] = useState<Audience | ''>('');
  const [scenarioFilter, setScenarioFilter] = useState<ScenarioCategory | ''>('');
  const [templateId, setTemplateId] = useState<number | ''>('');

  // Step-2 state
  const [channels, setChannels] = useState<ChannelType[]>(['EMAIL']);

  // Step-3 state
  const [placeholders, setPlaceholders] = useState<Record<string, string>>({});
  const [recipientsRaw, setRecipientsRaw] = useState('');
  const [dispatchedByName, setDispatchedByName] = useState('');

  // Result
  const [result, setResult] = useState<DispatchResult | null>(null);
  const [sending, setSending] = useState(false);

  // F2-147 — per-channel readiness, and the cascades already sent for this
  // event. Both exist so the operator is not learning what this tool actually
  // does from a provider string after they have pulled the trigger.
  const [readiness, setReadiness] = useState<ChannelReadiness[]>([]);
  const [history, setHistory] = useState<CascadeResponse[]>([]);

  // Load only APPROVED_BY_CISO templates for dispatch.
  useEffect(() => {
    setLoading(true);
    listTemplates({ approvalStatus: 'APPROVED_BY_CISO' })
      .then(list => setTemplates(Array.isArray(list) ? list : []))
      .catch(e => toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل القوالب' : 'Failed to load templates')))
      .finally(() => setLoading(false));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Channel readiness. A failure here is not fatal to dispatch, but it must not
  // fail *quietly* into an empty list — an empty list renders no warnings, which
  // reads exactly like "every channel is live".
  useEffect(() => {
    listChannelReadiness()
      .then(list => setReadiness(Array.isArray(list) ? list : []))
      .catch(() => toast.danger(isAr
        ? 'تعذّر التحقق من جاهزية القنوات — قد تكون بعض القنوات محاكاة دون تنبيه'
        : 'Could not check channel readiness — simulated channels may go unlabelled'));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Cascades already dispatched for this crisis event.
  useEffect(() => {
    if (crisisEventId === '') { setHistory([]); return; }
    listCascades(Number(crisisEventId))
      .then(list => setHistory(Array.isArray(list) ? list : []))
      .catch(() => setHistory([]));
  }, [crisisEventId]);

  const readinessOf = useMemo(() => {
    const map = new Map<string, ChannelReadiness>();
    for (const r of readiness) map.set(r.channelType, r);
    return map;
  }, [readiness]);

  /** Channels the operator has selected that will not reach a gateway. */
  const selectedSimulated = useMemo(
    () => channels.filter(c => {
      const r = readinessOf.get(c);
      return r ? isSimulatedChannel(r) : false;
    }),
    [channels, readinessOf]);

  /** Did the dispatch that just ran involve any channel with no gateway? */
  const anySimulated = (result?.simulatedChannels?.length ?? 0) > 0;

  const filteredTemplates = useMemo(() => templates.filter(t => {
    if (audienceFilter && t.audience !== audienceFilter) return false;
    if (scenarioFilter && t.scenarioCategory !== scenarioFilter) return false;
    return true;
  }), [templates, audienceFilter, scenarioFilter]);

  const selectedTemplate = templates.find(t => t.id === templateId);
  const placeholdersList = useMemo(
    () => parsePlaceholders(selectedTemplate?.placeholdersJson),
    [selectedTemplate],
  );

  // Reset placeholder values when template changes.
  useEffect(() => {
    const next: Record<string, string> = {};
    for (const p of placeholdersList) next[p.key] = placeholders[p.key] ?? '';
    setPlaceholders(next);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templateId]);

  function toggleChannel(c: ChannelType) {
    setChannels(prev => prev.includes(c) ? prev.filter(x => x !== c) : [...prev, c]);
  }

  const recipients = useMemo(() =>
    recipientsRaw.split(/[\s,;]+/).map(s => s.trim()).filter(Boolean),
    [recipientsRaw]);

  async function send() {
    if (!templateId || !crisisEventId) {
      toast.danger(isAr ? 'يجب اختيار حدث وقالب' : 'Pick a crisis event and template');
      return;
    }
    if (channels.length === 0) {
      toast.danger(isAr ? 'اختر قناة واحدة على الأقل' : 'Pick at least one channel');
      return;
    }
    setSending(true);
    try {
      const out = await dispatchCascade({
        crisisEventId: Number(crisisEventId),
        templateId: Number(templateId),
        channels,
        audienceFilter: audienceFilter || undefined,
        recipients,
        placeholders,
        dispatchedByName: dispatchedByName || undefined,
      });
      setResult(out);
      setStep(5);
      // F2-147 — the toast is the one thing every operator reads, so it is the
      // one place the qualification cannot be optional. A cascade with any
      // simulated channel is not reported as a send, and not with a green tick.
      const sim = out.simulatedChannels ?? [];
      if (sim.length > 0) {
        toast.warning(isAr
          ? `تم التسجيل دون إرسال فعلي — ${sim.join('، ')} محاكاة (${out.totalSuccess}/${out.totalRecipients} مُسجَّل)`
          : `Recorded, not sent — ${sim.join(', ')} simulated (${out.totalSuccess}/${out.totalRecipients} logged)`);
      } else {
        toast.success(isAr
          ? `تم الإرسال — ${out.totalSuccess}/${out.totalRecipients}`
          : `Dispatched — ${out.totalSuccess}/${out.totalRecipients}`);
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل الإرسال' : 'Dispatch failed'));
    } finally {
      setSending(false);
    }
  }

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'استمرارية الأعمال — الاتصالات' : 'BCM — Crisis Communications'}
        title={isAr ? 'إرسال تدفّق اتصالات' : 'Dispatch cascade'}
        sub={isAr
          ? 'تدفّق اتصالات متعدد القنوات خلال أقل من دقيقتين.'
          : 'Multi-channel cascade in under two minutes.'}
        actions={
          <Button variant="ghost" onClick={() => navigate('/bcm/comms/templates')}>
            <ChevronLeft size={16} style={{ marginInlineEnd: 6 }} />
            {isAr ? 'رجوع' : 'Back'}
          </Button>
        }
      />

      {/* Stepper */}
      <div style={{
        display: 'flex', gap: 8, marginBottom: 16, alignItems: 'center',
      }}>
        {[1, 2, 3, 4].map(n => (
          <div key={n} style={{
            flex: 1, padding: '8px 12px',
            border: '1px solid var(--border)',
            borderRadius: 6,
            background: step >= n ? 'var(--primary-soft)' : 'var(--surface)',
            color: step >= n ? 'var(--primary)' : 'var(--ink-3)',
            fontWeight: step === n ? 600 : 400,
            fontSize: 13,
          }}>
            {n}. {stepLabel(n, isAr)}
          </div>
        ))}
      </div>

      {loading && <div style={{ padding: 24 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}

      {!loading && step === 1 && (
        <Card title={isAr ? 'الخطوة 1 — اختيار الحدث والقالب' : 'Step 1 — pick crisis event + template'}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Field label={isAr ? 'رقم حدث الأزمة' : 'Crisis event ID'}>
              <input
                type="number"
                value={crisisEventId}
                onChange={e => setCrisisEventId(e.target.value === '' ? '' : Number(e.target.value))}
                placeholder="e.g. 777"
                style={inputStyle}
              />
            </Field>
            <Field label={isAr ? 'فلتر الجمهور' : 'Audience filter'}>
              <select
                value={audienceFilter}
                onChange={e => setAudienceFilter(e.target.value as Audience | '')}
                style={inputStyle}
              >
                <option value="">{isAr ? 'الكل' : 'All'}</option>
                {AUDIENCES.map(a => (
                  <option key={a} value={a}>{AUDIENCE_LABELS[a][isAr ? 'ar' : 'en']}</option>
                ))}
              </select>
            </Field>
            <Field label={isAr ? 'فلتر السيناريو' : 'Scenario filter'}>
              <select
                value={scenarioFilter}
                onChange={e => setScenarioFilter(e.target.value as ScenarioCategory | '')}
                style={inputStyle}
              >
                <option value="">{isAr ? 'الكل' : 'All'}</option>
                {SCENARIO_CATEGORIES.map(s => (
                  <option key={s} value={s}>{SCENARIO_LABELS[s][isAr ? 'ar' : 'en']}</option>
                ))}
              </select>
            </Field>
          </div>
          <div style={{ marginTop: 16 }}>
            <label style={{
              display: 'block', fontSize: 12, color: 'var(--ink-3)', marginBottom: 4,
            }}>
              {isAr ? 'القالب' : 'Template'} ({filteredTemplates.length}
              {isAr ? ' متاح' : ' available'})
            </label>
            <select
              value={templateId}
              onChange={e => setTemplateId(e.target.value === '' ? '' : Number(e.target.value))}
              style={inputStyle}
            >
              <option value="">{isAr ? '— اختر —' : '— select —'}</option>
              {filteredTemplates.map(t => (
                <option key={t.id} value={t.id}>
                  {t.templateCode} — {isAr ? t.titleAr : t.titleEn}
                </option>
              ))}
            </select>
          </div>
          <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
            <Button variant="primary" disabled={!templateId || !crisisEventId}
              onClick={() => setStep(2)}>
              {isAr ? 'التالي' : 'Next'}
              <ChevronRight size={16} style={{ marginInlineStart: 6 }} />
            </Button>
          </div>

          {/* F2-147 — the cascades already dispatched against this event. The server
              has served this list since Slice 9-E and nothing had ever read it
              (`listCascades` sat in apiClientReachability's KNOWN_UNREACHED), so the
              only view of a cascade was the transient result step, discarded on "Send
              another". It belongs here: what has already gone out for this event is
              what an operator needs before deciding to send again. */}
          {history.length > 0 && (
            <div style={{ marginTop: 20, borderTop: '1px solid var(--border)', paddingTop: 14 }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', marginBottom: 8 }}>
                {isAr
                  ? `تدفّقات سابقة لهذا الحدث (${history.length})`
                  : `Cascades already dispatched for this event (${history.length})`}
              </div>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--border)' }}>
                    <th style={th}>{isAr ? 'التاريخ' : 'When'}</th>
                    <th style={th}>{isAr ? 'القناة' : 'Channel'}</th>
                    <th style={th}>{isAr ? 'الحالة' : 'Status'}</th>
                    <th style={th}>{isAr ? 'نجح/المستلمون' : 'Success/Recipients'}</th>
                    <th style={th}>{isAr ? 'المُرسِل' : 'Dispatched by'}</th>
                  </tr>
                </thead>
                <tbody>
                  {history.slice(0, 10).map(c => (
                    <tr key={c.id} style={{ borderBottom: '1px solid var(--border)' }}>
                      <td style={td}>{(c.dispatchedAt ?? '').replace('T', ' ').slice(0, 16)}</td>
                      <td style={td}>{CHANNEL_LABELS[c.channel]?.[isAr ? 'ar' : 'en'] ?? c.channel}</td>
                      <td style={td}>
                        <Badge tone={c.simulated ? 'warning' : cascadeStatusTone(c.status)} size="sm">
                          {c.simulated
                            ? `${c.status} · ${isAr ? 'محاكاة' : 'SIMULATED'}`
                            : c.status}
                        </Badge>
                      </td>
                      <td style={td}>{c.successCount}/{c.recipientCount}</td>
                      <td style={td}>{c.dispatchedByName ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {history.length > 10 && (
                <div style={{ marginTop: 6, fontSize: 11, color: 'var(--ink-3)' }}>
                  {isAr
                    ? `تُعرض أحدث 10 من ${history.length}`
                    : `Showing the 10 most recent of ${history.length}`}
                </div>
              )}
            </div>
          )}
        </Card>
      )}

      {!loading && step === 2 && (
        <Card title={isAr ? 'الخطوة 2 — اختيار القنوات' : 'Step 2 — pick channels'}>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {CHANNELS.map(c => (
              <label key={c} style={{
                display: 'flex', alignItems: 'center', gap: 8,
                padding: '12px 16px',
                border: channels.includes(c)
                  ? '2px solid var(--primary)' : '1px solid var(--border)',
                borderRadius: 8,
                background: channels.includes(c) ? 'var(--primary-soft)' : 'var(--surface)',
                cursor: 'pointer',
                minWidth: 140,
              }}>
                <input
                  type="checkbox"
                  checked={channels.includes(c)}
                  onChange={() => toggleChannel(c)}
                />
                <span style={{ fontWeight: 600 }}>
                  {CHANNEL_LABELS[c][isAr ? 'ar' : 'en']}
                </span>
                {/* F2-147 — before the trigger, not after. Absent readiness renders
                    nothing rather than an unearned reassurance. */}
                {(() => {
                  const r = readinessOf.get(c);
                  if (!r) return null;
                  return isSimulatedChannel(r)
                    ? <Badge tone="warning" size="sm"
                        title={isAr
                          ? 'لا توجد بوابة مُهيّأة — سيُسجَّل الإرسال دون إرساله'
                          : 'No gateway configured — the dispatch is recorded, not sent'}>
                        {isAr ? 'محاكاة' : 'SIMULATED'}
                      </Badge>
                    : <Badge tone="success" size="sm">{isAr ? 'مباشر' : 'LIVE'}</Badge>;
                })()}
              </label>
            ))}
          </div>
          {selectedSimulated.length > 0 && (
            <div style={{
              marginTop: 12, padding: 10, borderRadius: 6, fontSize: 13,
              background: 'var(--warning-soft, var(--surface-2))',
              border: '1px solid var(--warning)', color: 'var(--ink-2)',
            }}>
              <AlertTriangle size={14} color="var(--warning)"
                style={{ marginInlineEnd: 6, verticalAlign: 'text-bottom' }} />
              {isAr
                ? `${selectedSimulated.join('، ')}: لا توجد بوابة مُهيّأة. سيُسجَّل التدفّق كاملًا للتدقيق `
                  + 'ولن تصل أي رسالة إلى المستلمين.'
                : `${selectedSimulated.join(', ')}: no gateway is configured. The cascade will be `
                  + 'recorded in full for audit and no message will reach anyone.'}
            </div>
          )}
          <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between' }}>
            <Button variant="ghost" onClick={() => setStep(1)}>
              <ChevronLeft size={16} style={{ marginInlineEnd: 6 }} />
              {isAr ? 'السابق' : 'Back'}
            </Button>
            <Button variant="primary" disabled={channels.length === 0}
              onClick={() => setStep(3)}>
              {isAr ? 'التالي' : 'Next'}
              <ChevronRight size={16} style={{ marginInlineStart: 6 }} />
            </Button>
          </div>
        </Card>
      )}

      {!loading && step === 3 && selectedTemplate && (
        <Card title={isAr ? 'الخطوة 3 — البيانات والمستلمون' : 'Step 3 — placeholders & recipients'}>
          {placeholdersList.length > 0 && (
            <div style={{
              display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16,
            }}>
              {placeholdersList.map(p => (
                <Field key={p.key} label={
                  isAr
                    ? `${p.label_ar ?? p.key}${p.required ? ' *' : ''}`
                    : `${p.label_en ?? p.key}${p.required ? ' *' : ''}`
                }>
                  {p.type === 'textarea' ? (
                    <textarea
                      value={placeholders[p.key] ?? ''}
                      onChange={e => setPlaceholders({ ...placeholders, [p.key]: e.target.value })}
                      rows={3} style={inputStyle}
                    />
                  ) : (
                    <input
                      type={p.type === 'datetime' ? 'datetime-local'
                          : p.type === 'number'   ? 'number' : 'text'}
                      value={placeholders[p.key] ?? ''}
                      onChange={e => setPlaceholders({ ...placeholders, [p.key]: e.target.value })}
                      style={inputStyle}
                    />
                  )}
                </Field>
              ))}
            </div>
          )}
          <Field label={isAr ? 'المستلمون (افصل بفاصلة أو سطر جديد)' : 'Recipients (comma- or line-separated)'}>
            <textarea
              value={recipientsRaw}
              onChange={e => setRecipientsRaw(e.target.value)}
              rows={5} style={inputStyle}
              placeholder={isAr
                ? 'مثال: customer1@example.sa, +966500000000'
                : 'e.g. customer1@example.sa, +966500000000'}
            />
            <div style={{ marginTop: 4, fontSize: 12, color: 'var(--ink-3)' }}>
              {recipients.length} {isAr ? 'مستلم' : 'recipients'}
            </div>
          </Field>
          <Field label={isAr ? 'المُرسِل' : 'Dispatched by (name)'}>
            <input
              value={dispatchedByName}
              onChange={e => setDispatchedByName(e.target.value)}
              placeholder={isAr
                ? 'مثال: فيصل السبيعي — قائد الأزمة'
                : 'e.g. Faisal Al-Subaie — Crisis Manager'}
              style={inputStyle}
            />
          </Field>
          <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between' }}>
            <Button variant="ghost" onClick={() => setStep(2)}>
              <ChevronLeft size={16} style={{ marginInlineEnd: 6 }} />
              {isAr ? 'السابق' : 'Back'}
            </Button>
            <Button variant="primary" onClick={() => setStep(4)}>
              {isAr ? 'معاينة' : 'Preview'}
              <ChevronRight size={16} style={{ marginInlineStart: 6 }} />
            </Button>
          </div>
        </Card>
      )}

      {!loading && step === 4 && selectedTemplate && (
        <Card title={isAr ? 'الخطوة 4 — معاينة وإرسال' : 'Step 4 — preview & send'}>
          <div style={{ marginBottom: 12 }}>
            <Badge tone="info">
              {AUDIENCE_LABELS[selectedTemplate.audience][isAr ? 'ar' : 'en']}
            </Badge>{' '}
            <Badge tone="neutral">
              {SCENARIO_LABELS[selectedTemplate.scenarioCategory][isAr ? 'ar' : 'en']}
            </Badge>
          </div>
          <div style={{
            padding: 16, background: 'var(--surface-2)', borderRadius: 6,
            fontFamily: 'var(--font-mono)', fontSize: 13, whiteSpace: 'pre-wrap',
            border: '1px solid var(--border)',
          }}>
            <div style={{ fontWeight: 700, marginBottom: 8 }}>
              {isAr ? 'الموضوع: ' : 'Subject: '}
              {renderPreview(isAr ? selectedTemplate.subjectAr : selectedTemplate.subjectEn,
                placeholders)}
            </div>
            <div>
              {renderPreview(isAr ? selectedTemplate.bodyAr : selectedTemplate.bodyEn,
                placeholders)}
            </div>
          </div>
          <div style={{ marginTop: 12, fontSize: 13, color: 'var(--ink-2)' }}>
            <div>{isAr ? 'القنوات: ' : 'Channels: '}{channels.join(', ')}</div>
            <div>{isAr ? 'المستلمون: ' : 'Recipients: '}{recipients.length}</div>
          </div>
          <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between' }}>
            <Button variant="ghost" onClick={() => setStep(3)}>
              <ChevronLeft size={16} style={{ marginInlineEnd: 6 }} />
              {isAr ? 'السابق' : 'Back'}
            </Button>
            <Button variant="primary" onClick={send} disabled={sending}>
              <Send size={16} style={{ marginInlineEnd: 6 }} />
              {sending
                ? (isAr ? 'جارٍ الإرسال…' : 'Sending…')
                : (isAr ? 'أرسل الآن' : 'Send now')}
            </Button>
          </div>
        </Card>
      )}

      {step === 5 && result && (
        <Card title={isAr ? 'نتيجة الإرسال' : 'Dispatch result'}>
          {/* F2-147 — the honest headline. Placed above the counters, because the
              counters are what an operator reads as "people were told". */}
          {(result.simulatedChannels?.length ?? 0) > 0 && (
            <div style={{
              display: 'flex', gap: 10, alignItems: 'flex-start',
              padding: 12, marginBottom: 16, borderRadius: 6,
              background: 'var(--warning-soft, var(--surface-2))',
              border: '1px solid var(--warning)',
            }}>
              <AlertTriangle size={18} color="var(--warning)" style={{ flexShrink: 0, marginTop: 1 }} />
              <div style={{ fontSize: 13, color: 'var(--ink-1)' }}>
                <strong>
                  {isAr
                    ? 'لم يتم إرسال هذه الرسائل فعليًا'
                    : 'These messages were not actually sent'}
                </strong>
                <div style={{ marginTop: 4, color: 'var(--ink-2)' }}>
                  {isAr
                    ? `القنوات ${result.simulatedChannels!.join('، ')} مُحاكاة — لا توجد بوابة مُهيّأة لها، `
                      + 'فالأعداد أدناه تسجّل ما كان سيُرسل. سجل التدفّق مكتمل للتدقيق، '
                      + 'ولكنه ليس دليلًا على إبلاغ المستلمين.'
                    : `${result.simulatedChannels!.join(', ')} ${result.simulatedChannels!.length === 1 ? 'has' : 'have'} `
                      + 'no configured gateway, so the counters below record what would have been '
                      + 'sent. The cascade is a complete audit record — it is not evidence that '
                      + 'anyone was notified.'}
                </div>
              </div>
            </div>
          )}
          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16,
          }}>
            {/* F2-142: these counted THIS cascade but linked to the fabricated global outbox.
                 The drill-through is removed rather than repointed at the inbox — a cascade's
                 channel and recipient counts do not live there, and a link that goes somewhere
                 wrong is worse than no link (F2-38's KRI ruling). ⚠ The Channels KPI kept its
                 `to` until F2-147 noticed the comment covered only its two neighbours; the route
                 is an F2-142 redirect stub that lands on the notification inbox. */}
            <Kpi label={isAr ? 'القنوات' : 'Channels'} value={String(result.cascades.length)} />
            <Kpi label={isAr ? 'المستلمون' : 'Recipients'} value={String(result.totalRecipients)} />
            <Kpi
              label={anySimulated
                ? (isAr ? 'مُسجَّل (محاكاة)' : 'Logged (simulated)')
                : (isAr ? 'نجح' : 'Success')}
              value={String(result.totalSuccess)}
              tone={anySimulated ? 'warning' : 'success'} />
            <Kpi label={isAr ? 'الزمن' : 'Elapsed'} value={`${result.elapsedMillis} ms`} to="/bcm/comms/dispatch" />
          </div>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border)' }}>
                <th style={th}>{isAr ? 'القناة' : 'Channel'}</th>
                <th style={th}>{isAr ? 'الحالة' : 'Status'}</th>
                <th style={th}>{isAr ? 'نجح' : 'Success'}</th>
                <th style={th}>{isAr ? 'فشل' : 'Failure'}</th>
                <th style={th}>{isAr ? 'تفاصيل' : 'Details'}</th>
              </tr>
            </thead>
            <tbody>
              {result.cascades.map(c => (
                <tr key={c.id} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={td}>{CHANNEL_LABELS[c.channel][isAr ? 'ar' : 'en']}</td>
                  <td style={td}>
                    {/* F2-147 — a simulated channel never renders COMPLETED with a green
                        tick. The status is a real fact about the dispatch attempt, so it
                        is kept; the tone and the tick are what implied delivery. */}
                    <Badge tone={c.simulated ? 'warning' : cascadeStatusTone(c.status)}>
                      {c.status === 'COMPLETED' && !c.simulated
                        && <CheckCircle2 size={12} style={{ marginInlineEnd: 4 }} />}
                      {(c.status === 'FAILED' || c.simulated)
                        && <AlertTriangle size={12} style={{ marginInlineEnd: 4 }} />}
                      {c.simulated
                        ? `${c.status} · ${isAr ? 'محاكاة' : 'SIMULATED'}`
                        : c.status}
                    </Badge>
                  </td>
                  <td style={td}>{c.successCount}</td>
                  <td style={td}>{c.failureCount}</td>
                  <td style={{ ...td, fontSize: 11, color: 'var(--ink-3)' }}>
                    {c.responsePayload}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
            <Button variant="primary" onClick={() => {
              setResult(null); setStep(1);
              setCrisisEventId(''); setTemplateId('');
            }}>
              {isAr ? 'إرسال جديد' : 'Send another'}
            </Button>
            <Button variant="ghost" onClick={() => navigate('/bcm/comms/templates')}>
              {isAr ? 'العودة للقوالب' : 'Back to templates'}
            </Button>
          </div>
        </Card>
      )}
    </Shell>
  );
}

const inputStyle: CSSProperties = {
  width: '100%', padding: 8,
  border: '1px solid var(--border)', borderRadius: 6,
  background: 'var(--surface)', color: 'var(--ink-1)',
};

const th: CSSProperties = {
  padding: '8px 10px', textAlign: 'start',
  fontSize: 12, fontWeight: 600, color: 'var(--ink-3)',
};
const td: CSSProperties = { padding: '8px 10px', fontSize: 13, color: 'var(--ink-1)' };

function stepLabel(n: number, isAr: boolean): string {
  const en = ['Crisis + template', 'Channels', 'Placeholders', 'Preview'];
  const ar = ['الحدث والقالب', 'القنوات', 'البيانات', 'المعاينة'];
  return (isAr ? ar : en)[n - 1];
}

function renderPreview(body: string, placeholders: Record<string, string>): string {
  let out = body;
  for (const k of Object.keys(placeholders)) {
    if (placeholders[k]) out = out.split(`{{${k}}}`).join(placeholders[k]);
  }
  return out;
}

function Field(props: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <label style={{
        display: 'block', fontSize: 12, color: 'var(--ink-3)', marginBottom: 4,
      }}>
        {props.label}
      </label>
      {props.children}
    </div>
  );
}

function Kpi(props: { label: string; value: string; tone?: 'success' | 'warning' | 'danger'; to?: string }) {
  const color = props.tone === 'success' ? 'var(--success)'
    : props.tone === 'warning' ? 'var(--warning)'
    : props.tone === 'danger' ? 'var(--danger)'
    : 'var(--ink-1)';
  const inner = (
    <>
      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{props.label}</div>
      <div style={{ fontSize: 24, fontWeight: 700, color }}>{props.value}</div>
    </>
  );
  return props.to ? (
    <a href={props.to} style={{
      display: 'block', padding: 12, border: '1px solid var(--border)', borderRadius: 6,
      background: 'var(--surface)', textDecoration: 'none', cursor: 'pointer',
      transition: 'border-color 0.15s',
    }}
    onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--primary)')}
    onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}>
      {inner}
    </a>
  ) : (
    <div style={{
      padding: 12, border: '1px solid var(--border)', borderRadius: 6,
      background: 'var(--surface)',
    }}>
      {inner}
    </div>
  );
}
