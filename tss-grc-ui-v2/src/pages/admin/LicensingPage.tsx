import { useEffect, useMemo, useState } from 'react';
import { KeyRound, ShieldCheck, ShieldX, BadgeDollarSign } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listCatalog, listSellableCatalog, listLicenses, upsertLicense, disableLicense,
  type OrganisationLicense, type LicenseTier,
} from '../../lib/licensing';
import { useEntitlement } from '../../lib/entitlement';

/**
 * Module Licensing admin page — F9 follow-up.
 *
 * <p>Lets a platform admin see every module the platform knows about
 * (from {@code /licensing/catalog}) alongside the current tenant's
 * licence rows (from {@code /licensing/modules}), then enable, disable
 * or change the tier of any row.
 *
 * <p>The page is tenant-scoped: it sends no {@code organisationId}, so every
 * write lands on the organisation the admin is currently switched into.
 *
 * <p>⚠ This paragraph used to say the page was safe "because the backend
 * enforces {@code organisationId} from the JWT and ignores any value sent from
 * the client". It did do that, and that was <b>F2-45</b>: a body naming
 * organisation 2 produced a row for organisation 1 and answered 200, so the
 * behaviour the comment presented as a safety property was in fact a silent
 * cross-tenant write. Since 2026-08-01 the body is honoured and the target
 * organisation must exist. This page stays tenant-scoped by <i>omitting</i> the
 * field, which is now a choice rather than something the server imposes.
 */
const TIERS: { value: LicenseTier; label: string }[] = [
  { value: 'FREE',       label: 'FREE' },
  { value: 'PRO',        label: 'PRO' },
  { value: 'ENTERPRISE', label: 'ENTERPRISE' },
];

export function LicensingPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [catalog, setCatalog] = useState<string[]>([]);
  const [sellable, setSellable] = useState<Set<string>>(new Set());
  const [licenses, setLicenses] = useState<OrganisationLicense[]>([]);
  const [loading, setLoading] = useState(false);
  const [pendingCode, setPendingCode] = useState<string | null>(null);

  // F2-04b — granting a SKU to your OWN tenant must move the sidebar, or the
  // admin enables ESG, sees nothing appear, and reloads to check.
  const { refresh: refreshEntitlement } = useEntitlement();

  async function reload() {
    setLoading(true);
    // Per-call .catch() so one failing endpoint does not blank the whole page.
    // Real dev DBs are often missing one or the other (catalog seed lag, no
    // licence rows yet) — we still want the other table to render.
    const [cat, sell, lic] = await Promise.all([
      listCatalog().catch(() => [] as string[]),
      listSellableCatalog().catch(() => [] as string[]),
      listLicenses().catch(() => [] as OrganisationLicense[]),
    ]);
    setCatalog(cat.slice().sort());
    setSellable(new Set(sell));
    setLicenses(lic);
    setLoading(false);
    refreshEntitlement();
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Join catalog × license rows so unlicensed modules also show up. ──
  const rows = useMemo(() => {
    const byCode = new Map(licenses.map(l => [l.moduleCode, l]));
    return catalog.map(code => ({ code, license: byCode.get(code) ?? null }));
  }, [catalog, licenses]);

  const enabledCount = licenses.filter(l => l.enabled).length;
  const disabledCount = licenses.length - enabledCount;
  const uncatalogued = rows.filter(r => r.license == null).length;

  async function enable(code: string, tier: LicenseTier = 'PRO') {
    setPendingCode(code);
    try {
      await upsertLicense({ moduleCode: code, tier, enabled: true });
      toast.success(isAr ? `تم تفعيل ${code}` : `${code} enabled`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل التحديث' : 'Update failed'));
    } finally {
      setPendingCode(null);
    }
  }

  async function disable(code: string) {
    setPendingCode(code);
    try {
      await disableLicense(code);
      toast.success(isAr ? `تم تعطيل ${code}` : `${code} disabled`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل التحديث' : 'Update failed'));
    } finally {
      setPendingCode(null);
    }
  }

  async function changeTier(code: string, tier: LicenseTier) {
    setPendingCode(code);
    try {
      await upsertLicense({ moduleCode: code, tier });
      toast.success(isAr ? `الباقة → ${tier}` : `Tier → ${tier}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل التحديث' : 'Update failed'));
    } finally {
      setPendingCode(null);
    }
  }

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تراخيص الوحدات' : 'Module licensing'}
        sub={isAr
          ? 'فعّل أو عطّل الوحدات لهذه المؤسسة. التغيير يسري فوراً على جميع المستخدمين.'
          : 'Enable or disable modules for this tenant. Changes apply to every user immediately.'}
        eyebrow={isAr ? 'إعدادات النظام' : 'Platform admin'}
      />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(170px,1fr))', gap: 12, marginBottom: 16 }}>
        <Kpi Icon={KeyRound}    label={isAr ? 'وحدات معروفة' : 'Known modules'}   value={catalog.length}    tone="info"     to="/admin" />
        <Kpi Icon={ShieldCheck} label={isAr ? 'مفعّلة'         : 'Enabled'}        value={enabledCount}      tone="success"  to="/admin" />
        <Kpi Icon={ShieldX}     label={isAr ? 'معطّلة'         : 'Disabled'}       value={disabledCount}     tone="warning"  to="/admin" />
        <Kpi Icon={BadgeDollarSign} label={isAr ? 'بدون ترخيص' : 'Not yet purchased'} value={uncatalogued}  tone="info"     to="/admin" />
      </div>

      <Card title={isAr ? 'الوحدات' : 'Modules'}>
        {loading && (
          <div className="text-muted" style={{ fontSize: 11, marginBottom: 6 }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        )}
        {rows.length === 0 ? (
          <InlineEmpty text={isAr ? 'لا توجد وحدات' : 'No modules in catalog'} />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ width: '100%' }}>
              <thead>
                <tr>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'الوحدة' : 'Module'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'الحالة' : 'Status'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'الباقة' : 'Tier'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'صالح حتى' : 'Valid until'}</th>
                  <th style={{ textAlign: 'end',   width: 220 }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(({ code, license }) => (
                  <tr key={code}>
                    <td className="mono"><strong>{code}</strong></td>
                    <td>
                      {license == null
                        ? <Badge tone="neutral">{isAr ? 'غير مشتراة' : 'Not purchased'}</Badge>
                        : license.enabled
                          ? <Badge tone="success">{isAr ? 'مفعّلة' : 'Enabled'}</Badge>
                          : <Badge tone="warning">{isAr ? 'معطّلة' : 'Disabled'}</Badge>}
                    </td>
                    <td>
                      {license == null ? <span className="text-muted">—</span> : (
                        <select
                          value={license.tier}
                          disabled={pendingCode === code}
                          onChange={e => void changeTier(code, e.target.value as LicenseTier)}
                          style={{ padding: '4px 8px' }}
                        >
                          {TIERS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                        </select>
                      )}
                    </td>
                    <td className="mono">{license?.validTo ?? <span className="text-muted">—</span>}</td>
                    <td style={{ textAlign: 'end' }}>
                      {/* A retired code with no existing row cannot be created:
                          `upsert` refuses it (ADR-018 §5 retires 12 of the 27
                          codes in /catalog into other SKUs or Base). Offering
                          Enable here was a button whose only possible outcome
                          was an error toast — 12 of 27 rows. An existing row
                          keeps its Disable action so it can still be wound down. */}
                      {license?.enabled
                        ? <Button variant="ghost" disabled={pendingCode === code} onClick={() => void disable(code)}>
                            {isAr ? 'تعطيل' : 'Disable'}
                          </Button>
                        : (license == null && sellable.size > 0 && !sellable.has(code))
                          ? <Badge tone="neutral">{isAr ? 'مُتقاعدة' : 'Retired'}</Badge>
                          : <Button variant="primary" disabled={pendingCode === code} onClick={() => void enable(code)}>
                              {isAr ? 'تفعيل' : 'Enable'}
                            </Button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div style={{ marginTop: 12 }} />
      <Card title={isAr ? 'ملاحظات' : 'Notes'}>
        <ul style={{ margin: 0, paddingInlineStart: 18, lineHeight: 1.9, fontSize: 13 }}>
          <li>{isAr
            ? 'تعطيل وحدة يحجب جميع نقاط النهاية التابعة لها على مستوى الخادم (LicenseGuard).'
            : 'Disabling a module blocks every server-side endpoint it owns (LicenseGuard aspect).'}</li>
          <li>{isAr
            ? 'الباقات: FREE = للقراءة فقط، PRO = استخدام كامل، ENTERPRISE = تشمل المنصة الكاملة + الدمج المخصص.'
            : 'Tiers: FREE = read-only, PRO = full use, ENTERPRISE = full platform + bespoke integrations.'}</li>
          <li>{isAr
            ? 'التعديلات تُسجَّل في سجل التدقيق (auditlog) كنوع CUSTOM ومصدر "licensing".'
            : 'All edits are tracked in the audit log as CUSTOM events with source="licensing".'}</li>
        </ul>
      </Card>
    </Shell>
  );
}
