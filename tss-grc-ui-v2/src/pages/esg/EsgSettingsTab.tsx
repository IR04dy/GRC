/**
 * F2-16 tranche 3 — the sustainability module settings tab, moved out of `EsgPage.tsx`.
 *
 * One of seven extracted together, each **self-contained by measurement**: zero references to any
 * page-local identifier outside its own range, exactly one call site in the page's tab switch, and
 * nothing declared inside it needed outside.
 *
 * <b>⚠ The boundary rule that got this tab here is not the one tranche 2 used, and the difference is
 * a defect tranche 2's rule would have shipped.</b> That tranche walked up from the declaration over
 * adjacent `//` lines. `ClimateTab` is documented with a `/** … *&#47;` block and `CapaTab`/`AnnexTab`
 * have multi-paragraph banners split by blank lines — so the walk-up would have left their
 * documentation behind on the page, orphaned above an unrelated declaration, compiling cleanly. The
 * rule now is the file's own convention: everything between the previous top-level declaration and
 * this one belongs to this one.
 *
 * <b>Proven, not trusted.</b> The moved range appears here verbatim with one deliberate `export`; the
 * page keeps every line it did not lose, byte for byte; no `function` or banner line of a moved tab
 * survives on the page; and the line count is conserved. `noUnusedLocals` makes `tsc` the checker for
 * the import surgery. Tests: `EsgOperationalTabs.test.tsx`.
 */
import { useEffect, useState } from 'react';
import {
  Trash2, CheckCircle2,
  Wrench,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  getSettings, updateSettings, resetSettings,
  type OrganisationSettings as SustSettings,
} from '../../lib/sustainabilitySettings';

// ─── Settings tab (slice 14-A · V65) ────────────────────────────────────────
//
// CRUD over the per-org Sustainability thresholds. Lazy-creates the
// row on first load via getSettings(); save uses partial-update
// semantics so callers only need to send what they changed.

export function SettingsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';
  const [settings, setSettings] = useState<Partial<SustSettings> | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  async function reload() {
    setLoading(true);
    try { setSettings(await getSettings()); }
    catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل الإعدادات' : 'Failed to load settings'));
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  async function onSave() {
    if (!settings) return;
    setSaving(true);
    try {
      const updated = await updateSettings(settings);
      setSettings(updated);
      toast.success(isAr ? 'تم الحفظ' : 'Saved');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Save failed');
    } finally { setSaving(false); }
  }

  async function onReset() {
    if (!window.confirm(isAr
      ? 'إعادة كل العتبات إلى القيم الافتراضية؟'
      : 'Reset all thresholds to defaults?')) return;
    setSaving(true);
    try {
      const fresh = await resetSettings();
      setSettings(fresh);
      toast.info(isAr ? 'أُعيدت العتبات إلى الافتراضي' : 'Settings reset to defaults');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Reset failed');
    } finally { setSaving(false); }
  }

  if (loading || !settings) {
    return <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>;
  }

  // Generic numeric-field renderer. Bilingual label + helper text +
  // optional min/max + unit suffix. Keeps the form file-short by
  // declaring fields data-style instead of one JSX block per field.
  function Num(label: { en: string; ar: string }, key: keyof SustSettings,
               opts: { min?: number; max?: number; step?: number; unit?: string;
                       help?: { en: string; ar: string } }) {
    const val = settings![key] as number | undefined;
    return (
      <div className="stack-xs">
        <label className="esg-field-label">
          {isAr ? label.ar : label.en}
        </label>
        <div className="esg-field-row">
          <input
            type="number"
            min={opts.min} max={opts.max} step={opts.step ?? 1}
            value={val ?? ''}
            onChange={e => setSettings(s => ({ ...s!, [key]: Number(e.target.value) }))}
            style={{
              padding: '6px 10px', fontSize: 13, width: 120,
              border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-1)', color: 'var(--ink-1)',
            }}
          />
          {opts.unit && <span className="esg-field-hint">{opts.unit}</span>}
        </div>
        {opts.help && (
          <span className="esg-field-hint">
            {isAr ? opts.help.ar : opts.help.en}
          </span>
        )}
      </div>
    );
  }

  function Text(label: { en: string; ar: string }, key: keyof SustSettings,
                opts: { help?: { en: string; ar: string }; placeholder?: string }) {
    const val = settings![key] as string | undefined;
    return (
      <div className="stack-xs">
        <label className="esg-field-label">
          {isAr ? label.ar : label.en}
        </label>
        <input
          type="text"
          value={val ?? ''}
          placeholder={opts.placeholder}
          onChange={e => setSettings(s => ({ ...s!, [key]: e.target.value }))}
          style={{
            padding: '6px 10px', fontSize: 13, fontFamily: 'var(--font-mono)',
            border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-1)', color: 'var(--ink-1)', maxWidth: 280,
          }}
        />
        {opts.help && (
          <span className="esg-field-hint">
            {isAr ? opts.help.ar : opts.help.en}
          </span>
        )}
      </div>
    );
  }

  return (
    <div className="esg-stack">
      <Card title={isAr ? 'إعدادات الاستدامة' : 'Sustainability settings'} Icon={Wrench}
        action={
          <div className="esg-actions-inline">
            <Button size="sm" variant="ghost" Icon={Trash2} onClick={onReset} disabled={saving}>
              {isAr ? 'إعادة للافتراضي' : 'Reset defaults'}
            </Button>
            <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={onSave} disabled={saving}>
              {saving ? (isAr ? 'يحفظ…' : 'Saving…') : (isAr ? 'حفظ' : 'Save')}
            </Button>
          </div>
        }>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'تُحدّد هذه العتبات سلوك القواعد 1 و 3 و 9 و 13 و 14 على مستوى المؤسسة. التغييرات تسري فوراً.'
            : 'These thresholds drive the runtime behaviour of Rules 1, 3, 9, 13 and 14 at the organisation level. Changes take effect immediately.'}
        </div>

        {/* Section: Rule 14 — ESG readiness bands */}
        <div className="esg-settings-section">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 14 — لافتة جاهزية الاستدامة' : 'Rule 14 — ESG readiness banner'}
          </h4>
          <div className="esg-settings-grid">
            {Num({ en: 'Warning threshold', ar: 'عتبة التحذير' }, 'esgReadinessWarningThreshold',
              { min: 0, max: 100, unit: '/ 100',
                help: { en: 'Banner appears below this score.', ar: 'تظهر اللافتة دون هذه الدرجة.' } })}
            {Num({ en: 'Critical threshold', ar: 'عتبة الحرج' }, 'esgReadinessCriticalThreshold',
              { min: 0, max: 100, unit: '/ 100',
                help: { en: 'Banner shows in danger tone below this.', ar: 'تتحوّل اللافتة إلى نغمة الخطر دون هذه الدرجة.' } })}
          </div>
        </div>

        {/* Section: Rule 1 — Scope 3 */}
        <div className="esg-settings-section">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 1 — تغطية النطاق 3' : 'Rule 1 — Scope 3 coverage'}
          </h4>
          <div className="esg-settings-grid">
            {Num({ en: 'Partial-coverage threshold', ar: 'عتبة التغطية الجزئية' }, 'scope3PartialCoverageThreshold',
              { min: 0, max: 100, step: 0.5, unit: '%',
                help: { en: 'Below this weighted coverage, GHG aggregate flags Scope 3 as Partial.', ar: 'دون هذه التغطية الموزونة، يصنّف تجميع الانبعاثات النطاق 3 كجزئي.' } })}
            {Num({ en: 'Carbon-offset cap', ar: 'سقف أرصدة الكربون' }, 'carbonOffsetCapPct',
              { min: 0, max: 100, step: 0.5, unit: '%',
                help: { en: 'Retired credits may not exceed this % of the disclosed gross reduction (REG-11 integrity).', ar: 'لا يجوز أن تتجاوز الأرصدة المسحوبة هذه النسبة من إجمالي الخفض المفصح عنه (نزاهة REG-11).' } })}
          </div>
        </div>

        {/* Section: Rule 3 — SGI at-risk */}
        <div className="esg-settings-section">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 3 — تصنيف "معرّض للخطر" لالتزامات SGI' : 'Rule 3 — SGI at-risk classification'}
          </h4>
          <div className="esg-settings-grid">
            {Num({ en: 'At-risk factor', ar: 'عامل الخطر' }, 'sgiAtRiskProgressFactor',
              { min: 0, max: 1, step: 0.05,
                help: { en: 'Commitment is "at risk" when actual progress < expected × factor (e.g. 0.70 = 70%).', ar: 'الالتزام "معرّض للخطر" عندما يكون التقدّم الفعلي < المتوقّع × العامل (مثال 0.70 = 70%).' } })}
          </div>
        </div>

        {/* Section: Rule 9 — Evidence expiry */}
        <div className="esg-settings-section">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 9 — انتهاء صلاحية الأدلة' : 'Rule 9 — Evidence expiry'}
          </h4>
          <div className="esg-settings-grid">
            {Num({ en: 'Warning window (days)', ar: 'نافذة التحذير (أيام)' }, 'evidenceExpiryWarningDays',
              { min: 0, max: 365, unit: isAr ? 'يوم' : 'days',
                help: { en: 'Evidence within this window of expiry is flagged as "expiring soon".', ar: 'الأدلة التي تنتهي خلال هذه النافذة تُعلَّم "تنتهي قريباً".' } })}
            {Text({ en: 'Sweep cron', ar: 'جدولة الفحص' }, 'evidenceSweepCron',
              { placeholder: '0 0 6 * * *',
                help: { en: 'Spring cron expression for the nightly expiry sweep (6 fields, second-level).', ar: 'تعبير cron الخاص بـ Spring للفحص الليلي (6 خانات، بمستوى الثواني).' } })}
          </div>
        </div>

        {/* Section: Rule 13 — CAPA escalation */}
        <div className="esg-settings-section esg-settings-section--last">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 13 — تصعيد الإجراءات التصحيحية' : 'Rule 13 — Corrective-action escalation'}
          </h4>
          <div className="esg-settings-grid">
            {Num({ en: 'Owner-warning window (days)', ar: 'نافذة تحذير المالك (أيام)' }, 'capaOverdueWarningDays',
              { min: 0, max: 90, unit: isAr ? 'يوم' : 'days',
                help: { en: 'Days past due before owner gets warned.', ar: 'الأيام بعد تاريخ الاستحقاق قبل تحذير المالك.' } })}
            {Num({ en: 'Executive-escalation window (days)', ar: 'نافذة تصعيد الإدارة (أيام)' }, 'capaExecutiveEscalationDays',
              { min: 0, max: 365, unit: isAr ? 'يوم' : 'days',
                help: { en: 'Days past due before action escalates to exec.', ar: 'الأيام بعد تاريخ الاستحقاق قبل التصعيد إلى الإدارة.' } })}
          </div>
        </div>
      </Card>
    </div>
  );
}
