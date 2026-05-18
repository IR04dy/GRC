import { useEffect, useState } from 'react';
import { Settings, RotateCcw, Save } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { getBcmSettings, updateBcmSettings, resetBcmSettings, type BcmSettings } from '../../lib/bcmSettings';

/**
 * BCM Settings page — audit Tier-1 #6.
 *
 * <p>Exposes the 13 spec §R settings in one form. Scalar settings (thresholds,
 * frequencies, grace periods, matrix dimensions) appear as numeric inputs;
 * JSON blobs (approval matrix, notification rules) as textareas — power-user
 * UX for now, a structured editor is a future polish item.
 */
export function BcmSettingsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [settings, setSettings] = useState<BcmSettings | null>(null);
  const [draft, setDraft] = useState<Partial<BcmSettings>>({});
  const [saving, setSaving] = useState(false);

  async function load() {
    try {
      const s = await getBcmSettings();
      setSettings(s);
      setDraft({});  // clear unsaved changes after a reload
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التحميل' : 'Load failed'));
    }
  }
  useEffect(() => { void load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function field<K extends keyof BcmSettings>(key: K, value: BcmSettings[K]) {
    setDraft({ ...draft, [key]: value });
  }
  function valueOf<K extends keyof BcmSettings>(key: K): BcmSettings[K] | undefined {
    if (draft[key] !== undefined) return draft[key] as BcmSettings[K];
    return settings?.[key];
  }

  async function save() {
    setSaving(true);
    try {
      const saved = await updateBcmSettings(draft);
      setSettings(saved);
      setDraft({});
      toast.success(isAr ? 'تم الحفظ' : 'Saved');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الحفظ' : 'Save failed'));
    } finally {
      setSaving(false);
    }
  }

  async function reset() {
    if (!window.confirm(isAr
      ? 'إعادة جميع الإعدادات إلى القيم الافتراضية؟'
      : 'Reset all settings to defaults?')) return;
    setSaving(true);
    try {
      const saved = await resetBcmSettings();
      setSettings(saved);
      setDraft({});
      toast.success(isAr ? 'تمت إعادة التهيئة' : 'Reset to defaults');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    } finally {
      setSaving(false);
    }
  }

  if (!settings) return <Shell><div className="text-muted">Loading…</div></Shell>;

  const dirty = Object.keys(draft).length > 0;

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'إعدادات استمرارية الأعمال' : 'BCM settings'}
        sub={isAr
          ? 'العتبات وأطر الموافقة وقواعد الإشعارات لجميع وحدات BCM.'
          : 'Thresholds, approval matrix, and notification rules for every BCM workflow.'}
        eyebrow={isAr ? 'إعدادات' : 'Configuration'}
        actions={<>
          <Button variant="ghost" onClick={() => void reset()} disabled={saving}>
            <RotateCcw size={14} /> {isAr ? 'الإعدادات الافتراضية' : 'Reset to defaults'}
          </Button>
          <Button variant="primary" onClick={() => void save()} disabled={saving || !dirty}>
            <Save size={14} /> {isAr ? 'حفظ' : 'Save'}
          </Button>
        </>}
      />

      <Card title={isAr ? 'عتبات RTO / RPO (ساعات)' : 'RTO / RPO thresholds (hours)'}>
        <Grid>
          <Num label={isAr ? 'تحذير RTO' : 'RTO warning'}  value={valueOf('rtoWarningHours')}  onChange={v => field('rtoWarningHours', v)} />
          <Num label={isAr ? 'حرج RTO'   : 'RTO critical'} value={valueOf('rtoCriticalHours')} onChange={v => field('rtoCriticalHours', v)} />
          <Num label={isAr ? 'تحذير RPO' : 'RPO warning'}  value={valueOf('rpoWarningHours')}  onChange={v => field('rpoWarningHours', v)} />
          <Num label={isAr ? 'حرج RPO'   : 'RPO critical'} value={valueOf('rpoCriticalHours')} onChange={v => field('rpoCriticalHours', v)} />
        </Grid>
        <Hint isAr={isAr}
          en="Warning thresholds must be ≤ critical thresholds. BIAs whose RTO exceeds the warning bound show amber on the dashboard; critical shows red."
          ar="عتبات التحذير يجب أن تكون ≤ العتبات الحرجة. تحليلات الأثر التي يتجاوز RTO فيها العتبة التحذيرية تُعرض باللون الكهرماني على لوحة المعلومات؛ والحرجة بالأحمر."
        />
      </Card>

      <Spacer />

      <Card title={isAr ? 'تكرار التمارين (أشهر)' : 'Exercise frequency (months)'}>
        <Grid>
          <Num label={isAr ? 'الافتراضي' : 'Default'}  value={valueOf('exerciseFrequencyMonthsDefault')}
               onChange={v => field('exerciseFrequencyMonthsDefault', v)} />
          <Num label={isAr ? 'حرج'       : 'Critical'} value={valueOf('exerciseFrequencyMonthsCritical')}
               onChange={v => field('exerciseFrequencyMonthsCritical', v)} />
        </Grid>
        <Hint isAr={isAr}
          en="BCPs for critical-tier services use the critical frequency; everything else uses the default."
          ar="خطط BCP للخدمات الحرجة تستخدم التردد الحرج؛ والباقي يستخدم الافتراضي."
        />
      </Card>

      <Spacer />

      <Card title={isAr ? 'الأدلة والإجراءات التصحيحية' : 'Evidence + CAPA bookkeeping'}>
        <Grid>
          <Num label={isAr ? 'تحذير انتهاء الدليل (أيام)' : 'Evidence expiry warning (days)'}
               value={valueOf('evidenceExpiryWarningDays')} onChange={v => field('evidenceExpiryWarningDays', v)} />
          <Num label={isAr ? 'فترة سماح إجراء متأخر (أيام)' : 'CAPA overdue grace period (days)'}
               value={valueOf('capaOverdueGraceDays')} onChange={v => field('capaOverdueGraceDays', v)} />
        </Grid>
      </Card>

      <Spacer />

      <Card title={isAr ? 'مصفوفة المخاطر' : 'Risk matrix'}>
        <Grid>
          <Num label={isAr ? 'الحد الأقصى للاحتمالية' : 'Max likelihood'}
               value={valueOf('riskMatrixLikelihoodMax')} onChange={v => field('riskMatrixLikelihoodMax', v)} min={3} max={10} />
          <Num label={isAr ? 'الحد الأقصى للأثر' : 'Max impact'}
               value={valueOf('riskMatrixImpactMax')} onChange={v => field('riskMatrixImpactMax', v)} min={3} max={10} />
        </Grid>
        <Hint isAr={isAr}
          en="Risk matrix dimensions must be between 3 and 10. Default 5x5 covers most sectors."
          ar="أبعاد مصفوفة المخاطر بين 3 و10. الافتراضي 5×5 يغطي معظم القطاعات."
        />
      </Card>

      <Spacer />

      <Card title={isAr ? 'مصفوفة الموافقات (JSON)' : 'Approval matrix (JSON)'}>
        <textarea
          value={valueOf('approvalMatrixJson') ?? ''}
          onChange={e => field('approvalMatrixJson', e.target.value)}
          placeholder={'{"BIA":["BCM_MANAGER","EXECUTIVE_SPONSOR"],"BCP":["BCM_MANAGER"]}'}
          style={{ width: '100%', minHeight: 100, fontFamily: 'monospace', fontSize: 12,
                   padding: 8, border: '1px solid var(--border)', borderRadius: 4 }} />
        <Hint isAr={isAr}
          en="Map of workflow → required approver roles. Free-form JSON — different sectors have wildly different sign-off chains."
          ar="ربط بين كل سير عمل والأدوار المطلوبة للموافقة. JSON حر — لكل قطاع سلسلة موافقات مختلفة." />
      </Card>

      <Spacer />

      <Card title={isAr ? 'قواعد الإشعارات (JSON)' : 'Notification rules (JSON)'}>
        <textarea
          value={valueOf('notificationRulesJson') ?? ''}
          onChange={e => field('notificationRulesJson', e.target.value)}
          placeholder={'{"BCM_CALL_TREE_FAILED":[{"role":"BCM_MANAGER","channel":"EMAIL"}]}'}
          style={{ width: '100%', minHeight: 100, fontFamily: 'monospace', fontSize: 12,
                   padding: 8, border: '1px solid var(--border)', borderRadius: 4 }} />
      </Card>

      <Spacer />

      <Card title={isAr ? 'إصدارات الأطر التنظيمية' : 'Regulatory framework versions'}>
        <Grid>
          <Text label="ISO 22301"           value={valueOf('iso22301Version')}        onChange={v => field('iso22301Version', v)} />
          <Text label="SAMA OPRES"          value={valueOf('samaOpresVersion')}       onChange={v => field('samaOpresVersion', v)} />
          <Text label="DGA"                 value={valueOf('dgaFrameworkVersion')}    onChange={v => field('dgaFrameworkVersion', v)} />
          <Text label="NCEMA"               value={valueOf('ncemaFrameworkVersion')}  onChange={v => field('ncemaFrameworkVersion', v)} />
        </Grid>
        <div style={{ marginTop: 12 }}>
          <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
            <span className="text-muted">{isAr ? 'حالة استيراد الإطار التنظيمي' : 'Regulatory import status'}</span>
            <select value={valueOf('regulatoryImportStatus') ?? 'PLACEHOLDER'}
                    onChange={e => field('regulatoryImportStatus', e.target.value as BcmSettings['regulatoryImportStatus'])}
                    style={{ padding: '6px 10px', border: '1px solid var(--border)', borderRadius: 4 }}>
              <option value="PLACEHOLDER">{isAr ? 'افتراضي / مؤقت' : 'Placeholder'}</option>
              <option value="PARTIAL">{isAr ? 'جزئي' : 'Partial'}</option>
              <option value="COMPLETE">{isAr ? 'مكتمل' : 'Complete'}</option>
            </select>
          </label>
        </div>
        {settings.regulatoryImportStatus === 'PLACEHOLDER' && (
          <div style={{ marginTop: 8 }}>
            <Badge tone="warning">
              {isAr
                ? 'الأطر مؤقتة — تتطلب استيراد المكتبة الرسمية للضوابط'
                : 'Placeholder — requires official regulatory control library import'}
            </Badge>
          </div>
        )}
      </Card>
    </Shell>
  );
}

// ─── Helpers ───────────────────────────────────────────────────

function Grid({ children }: { children: React.ReactNode }) {
  return <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 12 }}>{children}</div>;
}

function Spacer() { return <div style={{ height: 12 }} />; }

function Hint({ en, ar, isAr }: { en: string; ar: string; isAr: boolean }) {
  return <div className="text-muted" style={{ fontSize: 11, marginTop: 8 }}>{isAr ? ar : en}</div>;
}

function Num({ label, value, onChange, min = 0, max = 9999 }: {
  label: string; value: number | undefined; onChange: (v: number) => void; min?: number; max?: number;
}) {
  return (
    <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
      <span className="text-muted">{label}</span>
      <input type="number" value={value ?? ''} min={min} max={max}
             onChange={e => onChange(Number(e.target.value))}
             style={{ padding: '6px 10px', border: '1px solid var(--border)', borderRadius: 4 }} />
    </label>
  );
}

function Text({ label, value, onChange }: {
  label: string; value: string | undefined; onChange: (v: string) => void;
}) {
  return (
    <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
      <span className="text-muted">{label}</span>
      <input value={value ?? ''} onChange={e => onChange(e.target.value)}
             placeholder="—"
             style={{ padding: '6px 10px', border: '1px solid var(--border)', borderRadius: 4 }} />
    </label>
  );
}
