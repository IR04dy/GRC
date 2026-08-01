import { Link, useLocation } from 'react-router-dom';
import { Lock } from 'lucide-react';
import { Shell } from '../components/layout/Shell';
import { useI18n } from '../lib/i18n';
import { skuForPath } from '../lib/entitlement';

/**
 * F2-04b — what a deep link into an unlicensed module renders.
 *
 * <p>Hiding the nav entry is not enough: bookmarks, shared links, browser
 * history and the command palette all reach a route directly. Before this, such
 * a route mounted its real page, which fired its API calls and collected a
 * screenful of 403 toasts — and until F2-84 those were <b>500s</b>, so "you have
 * not licensed this module" reached the customer as an outage with a correlation
 * id.
 *
 * <p><b>Not a redirect.</b> Bouncing to /dashboard would discard the URL the
 * user asked for and read as a broken link. This keeps the address bar intact
 * and says what happened, which is also the only version that produces a useful
 * support conversation ("we don't have BCM" rather than "the link is broken").
 *
 * <p>It names the SKU for the same reason {@code handleModuleDisabled} puts
 * {@code moduleCode} in the ProblemDetail body: the caller should not have to
 * guess which module to go and buy. That is the F2-80 shape — a 403 whose
 * message the handler threw away — avoided here deliberately.
 */
export function ModuleNotLicensedPage() {
  const { t } = useI18n();
  const { pathname } = useLocation();
  const sku = skuForPath(pathname);

  return (
    <Shell title={t('licGateTitle')}>
      <div
        className="card"
        style={{ maxWidth: 640, margin: '48px auto', textAlign: 'center', padding: 32 }}
        data-testid="module-not-licensed"
      >
        <Lock size={40} style={{ opacity: 0.5, marginBottom: 16 }} aria-hidden="true" />
        <h2 style={{ marginBottom: 12 }}>{t('licGateTitle')}</h2>
        <p style={{ opacity: 0.8, marginBottom: 16 }}>{t('licGateBody')}</p>
        {sku && (
          <p style={{ marginBottom: 20, fontSize: 14 }}>
            <span style={{ opacity: 0.7 }}>{t('licGateSku')}: </span>
            <code style={{ padding: '2px 8px', borderRadius: 4, background: 'var(--bg)' }}>{sku}</code>
          </p>
        )}
        <p style={{ opacity: 0.7, fontSize: 14, marginBottom: 24 }}>{t('licGateHint')}</p>
        <Link to="/dashboard" className="btn btn-primary">{t('licGateBack')}</Link>
      </div>
    </Shell>
  );
}
