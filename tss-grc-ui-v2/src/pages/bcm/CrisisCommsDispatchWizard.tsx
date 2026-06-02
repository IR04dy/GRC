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
  listTemplates, dispatchCascade,
  AUDIENCES, SCENARIO_CATEGORIES, CHANNELS,
  AUDIENCE_LABELS, SCENARIO_LABELS, CHANNEL_LABELS,
  cascadeStatusTone, parsePlaceholders,
  type CrisisCommsTemplate, type Audience, type ScenarioCategory,
  type ChannelType, type DispatchResult,
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
      toast.success(isAr
        ? `تم الإرسال — ${out.totalSuccess}/${out.totalRecipients}`
        : `Dispatched — ${out.totalSuccess}/${out.totalRecipients}`);
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
              </label>
            ))}
          </div>
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
          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16,
          }}>
            <Kpi label={isAr ? 'القنوات' : 'Channels'} value={String(result.cascades.length)} />
            <Kpi label={isAr ? 'المستلمون' : 'Recipients'} value={String(result.totalRecipients)} />
            <Kpi label={isAr ? 'نجح' : 'Success'} value={String(result.totalSuccess)} tone="success" />
            <Kpi label={isAr ? 'الزمن' : 'Elapsed'} value={`${result.elapsedMillis} ms`} />
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
                    <Badge tone={cascadeStatusTone(c.status)}>
                      {c.status === 'COMPLETED' && <CheckCircle2 size={12} style={{ marginInlineEnd: 4 }} />}
                      {c.status === 'FAILED' && <AlertTriangle size={12} style={{ marginInlineEnd: 4 }} />}
                      {c.status}
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

function Kpi(props: { label: string; value: string; tone?: 'success' | 'warning' | 'danger' }) {
  const color = props.tone === 'success' ? 'var(--success)'
    : props.tone === 'warning' ? 'var(--warning)'
    : props.tone === 'danger' ? 'var(--danger)'
    : 'var(--ink-1)';
  return (
    <div style={{
      padding: 12, border: '1px solid var(--border)', borderRadius: 6,
      background: 'var(--surface)',
    }}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{props.label}</div>
      <div style={{ fontSize: 24, fontWeight: 700, color }}>{props.value}</div>
    </div>
  );
}
