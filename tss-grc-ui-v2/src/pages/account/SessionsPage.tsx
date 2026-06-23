import { useCallback, useEffect, useMemo, useState } from 'react';
import { MonitorSmartphone, Laptop, Globe, Clock, LogOut, RefreshCw, ChevronLeft, ChevronRight, X } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { sessionsApi, ApiError, type AuthSessionDto } from '../../lib/api';

const PAGE_SIZE = 25;

function parseUa(ua?: string): { browser: string; os: string; isApp: boolean } {
  if (!ua) return { browser: 'Unknown', os: '', isApp: false };
  const isApp = /Electron|Claude\//.test(ua);
  const os = /Windows NT/.test(ua) ? 'Windows'
    : /Mac OS X|Macintosh/.test(ua) ? 'macOS'
    : /Android/.test(ua) ? 'Android'
    : /iPhone|iPad/.test(ua) ? 'iOS'
    : /Linux/.test(ua) ? 'Linux' : '';
  const browser = isApp ? 'Claude Desktop'
    : /Edg\//.test(ua) ? 'Edge'
    : /Chrome\//.test(ua) ? 'Chrome'
    : /Firefox\//.test(ua) ? 'Firefox'
    : /Safari\//.test(ua) ? 'Safari' : 'Browser';
  return { browser, os, isApp };
}

const fmtDate = (iso?: string) => (iso ? iso.slice(0, 16).replace('T', ' ') : '—');
const ageDays = (iso: string) => Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000));

/**
 * Active sessions — surfaces GET/DELETE /api/v1/auth/sessions (the refresh-token
 * store). A self-service security surface: see every device with an active
 * session (parsed device + IP + issued/expiry), revoke any one, or sign out of
 * every device at once. Bilingual (Arabic primary).
 */
export function SessionsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<AuthSessionDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [confirmAll, setConfirmAll] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await sessionsApi.listMine();
      list.sort((a, b) => (b.issuedAt > a.issuedAt ? 1 : -1));
      setRows(list);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل الجلسات' : 'Failed to load sessions'));
      setRows([]);
    } finally { setLoading(false); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  const stats = useMemo(() => {
    const devices = new Set(rows.map(r => { const u = parseUa(r.userAgent); return `${u.browser}|${u.os}`; }));
    const ips = new Set(rows.map(r => r.ipAddress ?? '—'));
    const oldest = rows.reduce((m, r) => Math.max(m, ageDays(r.issuedAt)), 0);
    return { total: rows.length, devices: devices.size, ips: ips.size, oldest };
  }, [rows]);

  const totalPages = Math.ceil(rows.length / PAGE_SIZE);
  const pageRows = rows.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

  async function revokeOne(s: AuthSessionDto) {
    setBusyId(s.id);
    try {
      await sessionsApi.revoke(s.id);
      toast.success(isAr ? 'تم إنهاء الجلسة' : 'Session revoked');
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الإنهاء' : 'Revoke failed'));
    } finally { setBusyId(null); }
  }

  async function revokeAll() {
    setConfirmAll(false);
    try {
      const res = await sessionsApi.revokeAll();
      toast.success(isAr ? `تم إنهاء ${res.revoked} جلسة` : `${res.revoked} sessions ended`);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل تسجيل الخروج' : 'Sign-out failed'));
    }
  }

  return (
    <Shell title={isAr ? 'الجلسات النشطة' : 'Active sessions'} sub={isAr ? 'إدارة الأجهزة المسجّلة الدخول' : 'Manage signed-in devices'}>
      <PageHeader
        eyebrow={isAr ? 'الأمان · الحساب' : 'Security · Account'}
        title={isAr ? 'الجلسات النشطة' : 'Active sessions'}
        sub={isAr
          ? 'كل جهاز لديه جلسة دخول نشطة على حسابك. راجع الأجهزة غير المألوفة وأنهِ جلستها — أو سجّل الخروج من كل الأجهزة.'
          : 'Every device with an active session on your account. Review unfamiliar devices and revoke them — or sign out everywhere.'}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={MonitorSmartphone} label={isAr ? 'جلسات نشطة' : 'Active sessions'} value={loading ? '—' : stats.total} tone="primary" to="/account/sessions" />
        <Kpi Icon={Laptop} label={isAr ? 'أجهزة مميّزة' : 'Distinct devices'} value={loading ? '—' : stats.devices} tone="info" to="/account/sessions" />
        <Kpi Icon={Globe}  label={isAr ? 'عناوين IP' : 'IP addresses'} value={loading ? '—' : stats.ips} tone="neutral" to="/account/sessions" />
        <Kpi Icon={Clock}  label={isAr ? 'أقدم جلسة (يوم)' : 'Oldest (days)'} value={loading ? '—' : stats.oldest} tone="warning" to="/account/sessions" />
      </div>

      <Card title={isAr ? 'الأجهزة المسجّلة الدخول' : 'Signed-in devices'} Icon={MonitorSmartphone}
        action={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>{isAr ? 'تحديث' : 'Refresh'}</Button>
            {confirmAll
              ? <>
                  <span className="text-2xs" style={{ color: 'var(--danger)', fontWeight: 600 }}>{isAr ? 'سيتم تسجيل خروجك من كل الأجهزة' : 'This signs you out everywhere'}</span>
                  <Button size="sm" variant="danger" Icon={LogOut} onClick={() => void revokeAll()}>{isAr ? 'تأكيد' : 'Confirm'}</Button>
                  <Button size="sm" variant="ghost" Icon={X} onClick={() => setConfirmAll(false)}>{isAr ? 'إلغاء' : 'Cancel'}</Button>
                </>
              : <Button size="sm" variant="ghost" Icon={LogOut} onClick={() => setConfirmAll(true)} disabled={loading || rows.length === 0}>{isAr ? 'تسجيل الخروج من كل الأجهزة' : 'Sign out everywhere'}</Button>}
          </div>
        }>
        {loading && rows.length === 0
          ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : rows.length === 0
            ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا توجد جلسات نشطة' : 'No active sessions'}</div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'الجهاز' : 'Device'}</th>
                  <th>{isAr ? 'عنوان IP' : 'IP address'}</th>
                  <th>{isAr ? 'بدأت' : 'Issued'}</th>
                  <th>{isAr ? 'تنتهي' : 'Expires'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'إجراء' : 'Action'}</th>
                </tr></thead>
                <tbody>
                  {pageRows.map(s => {
                    const u = parseUa(s.userAgent);
                    return (
                      <tr key={s.id}>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span style={{ fontWeight: 600, fontSize: 13 }}>{u.browser}{u.os ? ` · ${u.os}` : ''}</span>
                            {u.isApp && <Badge tone="info">{isAr ? 'تطبيق' : 'App'}</Badge>}
                          </div>
                        </td>
                        <td className="mono text-2xs">{s.ipAddress === '0:0:0:0:0:0:0:1' ? 'localhost (::1)' : (s.ipAddress ?? '—')}</td>
                        <td className="mono text-2xs">{fmtDate(s.issuedAt)}</td>
                        <td className="mono text-2xs">{fmtDate(s.expiresAt)}</td>
                        <td style={{ textAlign: 'end' }}>
                          <Button size="sm" variant="ghost" onClick={() => void revokeOne(s)} disabled={busyId === s.id}>
                            {isAr ? 'إنهاء' : 'Revoke'}
                          </Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>}

        {totalPages > 1 && (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, marginTop: 12 }}>
            <span className="text-2xs text-muted">
              {isAr ? `صفحة ${page + 1} من ${totalPages} · ${stats.total} جلسة` : `Page ${page + 1} of ${totalPages} · ${stats.total} sessions`}
            </span>
            <Button size="sm" variant="ghost" Icon={ChevronLeft} disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>{isAr ? 'السابق' : 'Prev'}</Button>
            <Button size="sm" variant="ghost" Icon={ChevronRight} disabled={page >= totalPages - 1} onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}>{isAr ? 'التالي' : 'Next'}</Button>
          </div>
        )}
      </Card>
    </Shell>
  );
}
