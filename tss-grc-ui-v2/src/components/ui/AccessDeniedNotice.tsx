import { ShieldAlert } from 'lucide-react';
import { useI18n } from '../../lib/i18n';

/**
 * JRN-13 — the disclosure for a **permission denial**, which is not a demo tenant.
 *
 * <p>`useApiList` collapsed four different outcomes into one screen: no session, an
 * empty register, the backend down, and **403 Forbidden**. All four substituted the
 * page's demo corpus and raised `isDemo`, so all four rendered
 * {@link DemoDataNotice}'s *"sample data"* wording. For the first three that is the
 * deliberate W2-1b contract. For the fourth it is a lie with a plausible register
 * attached: a narrow analyst opening BCM, CYBER, PRIVACY or TPRM saw a full, well-formed,
 * un-writable list of records that do not exist, badged as a demo.
 *
 * <p><b>Why a separate component rather than a fourth sentence in DemoDataNotice.</b>
 * That component's whole contract is "rows on screen are fabricated" — it exists to
 * describe substituted data. A denial has the opposite shape: there is no data, and the
 * message must say why the table is empty rather than what the rows are. Folding them
 * together would mean a `signedIn`-style boolean choosing between two unrelated
 * statements, which is how the four hand-rolled demo badges drifted in the first place.
 *
 * <p>Tone is `danger`, not `warning`: a warning reads as degraded service, and this is
 * the product working correctly.
 */
export function AccessDeniedNotice({ detailKey }: {
  /** Optional i18n key naming what was denied, e.g. `modCyber`. */
  detailKey?: string;
}) {
  const { t } = useI18n();
  return (
    <div className="access-denied-notice" role="status">
      <ShieldAlert size={13} aria-hidden />
      <span>
        <strong>{t('accessDenied')}</strong>
        {detailKey && <> · {t(detailKey)}</>}
        {' · '}{t('accessDeniedSub')}
      </span>
    </div>
  );
}
