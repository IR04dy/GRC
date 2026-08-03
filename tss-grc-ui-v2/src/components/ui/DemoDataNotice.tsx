import { Info } from 'lucide-react';
import { useI18n } from '../../lib/i18n';

/**
 * F2-98 — one disclosure for demo substitution, so every page says the same true thing.
 *
 * <p>The platform substitutes sample rows in three situations: the visitor is not signed in, the
 * register came back **empty**, or the API **threw**. That is designed behaviour (W2-1b pinned it
 * deliberately) — but only the first was ever disclosed, because the flag guarding the disclosure
 * was `!isAuthenticated()`, which is false for the other two. A signed-in user on a fresh tenant,
 * or anyone at all while the backend is down, saw a full register of invented records presented as
 * their own.
 *
 * <p><b>Why a component rather than the fix repeated four times.</b> The four hand-rolled sites had
 * already drifted: two rendered a hard-coded English sentence in an Arabic-primary product, and both
 * of those sentences ended *"Sign in for live …"* — advice that is wrong for the user who most needs
 * the notice. Centralising means the wording is correct once and bilingual once.
 *
 * <p>`signedIn` decides which sentence applies. Pass it rather than reading the session here: the
 * caller already knows, and a component that both renders and detects is two responsibilities.
 */
export function DemoDataNotice({ signedIn, detailKey }: {
  /** True when a session exists — i.e. the substitution was NOT caused by being signed out. */
  signedIn: boolean;
  /** Optional i18n key naming what was substituted, e.g. `demoNoticeKris`. */
  detailKey?: string;
}) {
  const { t } = useI18n();
  return (
    <div className="demo-data-notice" role="status">
      <Info size={13} aria-hidden />
      <span>
        {signedIn ? t('demoDataSubstituted') : t('demoMode')}
        {detailKey && <> · {t(detailKey)}</>}
      </span>
    </div>
  );
}
