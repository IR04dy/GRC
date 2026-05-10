import { useEffect, useRef, useState } from 'react';
import { Palette, Upload, RefreshCw, Globe, Check, ShieldCheck, Eye } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  loadWhiteLabel, saveWhiteLabel, resetWhiteLabel, subscribeWhiteLabel,
  PALETTE_PRESETS, applyToDom,
  type WhiteLabelConfig,
} from '../../lib/whiteLabel';

/**
 * WhiteLabelPage — Track A (sales-readiness).
 *
 * Lets a tenant override the brand without forking the build:
 *   • Logo (uploaded as data URL, displayed at 80×80)
 *   • Organisation display name (EN + AR)
 *   • Tagline (EN + AR)
 *   • Primary + accent colours (free hex or Saudi-themed presets)
 *   • Custom domain (recorded only — DNS routing is infra-side)
 *
 * Live preview at the top — the Shell + Topbar pick up the colour
 * changes immediately via the CSS variables we set on <html>.
 */
export function WhiteLabelPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [config, setConfig] = useState<WhiteLabelConfig>(() => loadWhiteLabel());

  // Reactive across tabs.
  useEffect(() => subscribeWhiteLabel(() => setConfig(loadWhiteLabel())), []);

  // Apply on mount in case the user landed here without the App-level applyToDom.
  useEffect(() => { applyToDom(config); }, [config]);

  // Working draft — only persists when user clicks "Save".
  const [draft, setDraft] = useState<WhiteLabelConfig>(config);
  useEffect(() => setDraft(config), [config]);

  function patch(patch: Partial<WhiteLabelConfig>) {
    setDraft(d => ({ ...d, ...patch }));
  }

  function save() {
    const saved = saveWhiteLabel(draft);
    setConfig(saved);
    toast.success(t('wlSavedToast'));
  }

  function reset() {
    if (!confirm(t('wlResetConfirm'))) return;
    resetWhiteLabel();
    const fresh = loadWhiteLabel();
    setConfig(fresh);
    setDraft(fresh);
    toast.info(t('wlResetToast'));
  }

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  function onPickLogo(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 200_000) {
      toast.danger(t('wlLogoTooLarge'));
      return;
    }
    const reader = new FileReader();
    reader.onload = () => patch({ logoDataUrl: reader.result as string });
    reader.readAsDataURL(file);
  }

  function applyPreset(presetCode: string) {
    const preset = PALETTE_PRESETS.find(p => p.code === presetCode);
    if (!preset) return;
    patch({ primaryHex: preset.primaryHex, accentHex: preset.accentHex });
  }

  return (
    <Shell title={t('wlTitle')} sub={t('wlSub')}>
      <PageHeader
        eyebrow={t('wlEyebrow')}
        title={t('wlTitle')}
        sub={t('wlPageSub')}
        actions={
          <>
            <Button variant="secondary" Icon={RefreshCw} onClick={reset}>{t('wlReset')}</Button>
            <Button variant="primary" Icon={Check} onClick={save}>{t('wlSave')}</Button>
          </>
        }
      />

      {/* Live preview */}
      <Card title={t('wlPreviewTitle')} Icon={Eye}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('wlPreviewSub')}
        </div>
        <div style={{
          padding: 24, borderRadius: 'var(--r-md)',
          background: 'linear-gradient(135deg, var(--primary-50) 0%, var(--surface-2) 100%)',
          border: '1px solid var(--border)',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 14 }}>
            <div style={{
              width: 64, height: 64, borderRadius: 12,
              background: draft.primaryHex,
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              color: 'white', fontWeight: 800, fontSize: 24, overflow: 'hidden',
            }}>
              {draft.logoDataUrl
                ? <img src={draft.logoDataUrl} alt="logo" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
                : <ShieldCheck size={36} />}
            </div>
            <div>
              <div style={{ fontSize: 20, fontWeight: 800, color: 'var(--ink-1)' }}>
                {lang === 'ar' ? draft.organisationNameAr : draft.organisationNameEn}
              </div>
              <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>
                {lang === 'ar' ? draft.taglineAr : draft.taglineEn}
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <Badge tone="primary">primary</Badge>
            <Badge tone="accent">accent</Badge>
            <Badge tone="success">success</Badge>
            <Badge tone="warning">warning</Badge>
            <Badge tone="danger">danger</Badge>
            <Badge tone="info">info</Badge>
            <button style={{
              padding: '6px 14px',
              background: draft.primaryHex,
              color: 'white',
              border: 'none', borderRadius: 'var(--r-sm)',
              fontSize: 12, fontWeight: 600, cursor: 'pointer',
            }}>
              {t('wlSampleButton')}
            </button>
            <button style={{
              padding: '6px 14px',
              background: 'transparent',
              color: draft.accentHex,
              border: `1px solid ${draft.accentHex}`,
              borderRadius: 'var(--r-sm)',
              fontSize: 12, fontWeight: 600, cursor: 'pointer',
            }}>
              {t('wlSampleAccentBtn')}
            </button>
          </div>
          {draft.customDomain && (
            <div style={{ marginTop: 14, fontSize: 11, color: 'var(--ink-3)' }}>
              <Globe size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
              <span className="mono">{draft.customDomain}</span>
            </div>
          )}
        </div>
      </Card>

      {/* Identity card */}
      <div style={{ marginTop: 16, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <Card title={t('wlIdentityTitle')} Icon={ShieldCheck}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('wlIdentitySub')}
          </div>
          <Field label={t('wlNameEn')}>
            <input value={draft.organisationNameEn} onChange={e => patch({ organisationNameEn: e.target.value })}
              style={inputStyle} />
          </Field>
          <Field label={t('wlNameAr')}>
            <input value={draft.organisationNameAr} onChange={e => patch({ organisationNameAr: e.target.value })}
              style={inputStyle} dir="rtl" />
          </Field>
          <Field label={t('wlTaglineEn')}>
            <input value={draft.taglineEn ?? ''} onChange={e => patch({ taglineEn: e.target.value })}
              style={inputStyle} />
          </Field>
          <Field label={t('wlTaglineAr')}>
            <input value={draft.taglineAr ?? ''} onChange={e => patch({ taglineAr: e.target.value })}
              style={inputStyle} dir="rtl" />
          </Field>
          <Field label={t('wlCustomDomain')}>
            <input value={draft.customDomain ?? ''} onChange={e => patch({ customDomain: e.target.value })}
              placeholder="grc.tss.sa" className="mono" style={{ ...inputStyle, fontFamily: 'var(--font-mono)' }} />
          </Field>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5, marginTop: 4 }}>
            {t('wlCustomDomainHint')}
          </div>
        </Card>

        {/* Logo card */}
        <Card title={t('wlLogoTitle')} Icon={Upload}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('wlLogoSub')}
          </div>
          <div style={{
            padding: 16, border: '2px dashed var(--border)',
            borderRadius: 'var(--r-md)', textAlign: 'center', marginBottom: 12,
            background: 'var(--surface-2)',
          }}>
            <div style={{
              width: 96, height: 96, margin: '0 auto 10px',
              background: draft.primaryHex,
              borderRadius: 12, display: 'inline-flex',
              alignItems: 'center', justifyContent: 'center',
              color: 'white', overflow: 'hidden',
            }}>
              {draft.logoDataUrl
                ? <img src={draft.logoDataUrl} alt="logo" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
                : <ShieldCheck size={48} />}
            </div>
            <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
              <Button variant="secondary" Icon={Upload} onClick={() => fileInputRef.current?.click()}>{t('wlPickLogo')}</Button>
              {draft.logoDataUrl && (
                <Button variant="ghost" onClick={() => patch({ logoDataUrl: undefined })}>{t('wlClearLogo')}</Button>
              )}
            </div>
            <input ref={fileInputRef} type="file" accept="image/*"
              onChange={onPickLogo} style={{ display: 'none' }} />
          </div>
          <div style={{ fontSize: 10, color: 'var(--ink-3)', lineHeight: 1.5 }}>
            {t('wlLogoHint')}
          </div>
        </Card>
      </div>

      {/* Palette card */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('wlPaletteTitle')} Icon={Palette}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('wlPaletteSub')}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 8, marginBottom: 16 }}>
            {PALETTE_PRESETS.map(preset => {
              const isSelected = draft.primaryHex === preset.primaryHex && draft.accentHex === preset.accentHex;
              return (
                <button key={preset.code} onClick={() => applyPreset(preset.code)}
                  style={{
                    padding: 12, borderRadius: 'var(--r-md)',
                    border: `2px solid ${isSelected ? preset.primaryHex : 'var(--border)'}`,
                    background: 'var(--surface-1)',
                    cursor: 'pointer', textAlign: 'start',
                    display: 'flex', flexDirection: 'column', gap: 6,
                  }}>
                  <div style={{ fontSize: 12, fontWeight: 600 }}>{lang === 'ar' ? preset.nameAr : preset.nameEn}</div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <span style={{ width: 28, height: 28, background: preset.primaryHex, borderRadius: 6 }} />
                    <span style={{ width: 28, height: 28, background: preset.accentHex, borderRadius: 6 }} />
                  </div>
                </button>
              );
            })}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Field label={t('wlPrimaryColor')}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <input type="color" value={draft.primaryHex}
                  onChange={e => patch({ primaryHex: e.target.value })}
                  style={{ width: 56, height: 36, border: 'none', borderRadius: 'var(--r-sm)', cursor: 'pointer' }} />
                <input value={draft.primaryHex} onChange={e => patch({ primaryHex: e.target.value })}
                  className="mono" style={inputStyle} />
              </div>
            </Field>
            <Field label={t('wlAccentColor')}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <input type="color" value={draft.accentHex}
                  onChange={e => patch({ accentHex: e.target.value })}
                  style={{ width: 56, height: 36, border: 'none', borderRadius: 'var(--r-sm)', cursor: 'pointer' }} />
                <input value={draft.accentHex} onChange={e => patch({ accentHex: e.target.value })}
                  className="mono" style={inputStyle} />
              </div>
            </Field>
          </div>
        </Card>
      </div>
    </Shell>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 10 }}>
      <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</span>
      {children}
    </label>
  );
}

const inputStyle: React.CSSProperties = {
  flex: 1,
  padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
};
