import type { LucideProps } from 'lucide-react';
import { ArrowRight, ArrowLeft } from 'lucide-react';
import { useI18n } from '../../lib/i18n';

/**
 * RTL-aware directional arrow. Renders ArrowRight in LTR and ArrowLeft in
 * RTL so the visual semantic (forward / next) flips correctly with the
 * document direction. Saudi enterprise buyers judge Arabic parity
 * rigorously — broken arrow direction signals an Arabic afterthought.
 */
export function DirectionalArrow(props: LucideProps) {
  const { lang } = useI18n();
  const Icon = lang === 'ar' ? ArrowLeft : ArrowRight;
  return <Icon {...props} />;
}

/**
 * Inverse of DirectionalArrow — for "back" or "previous" affordances.
 * In LTR shows ArrowLeft; in RTL shows ArrowRight.
 */
export function DirectionalBackArrow(props: LucideProps) {
  const { lang } = useI18n();
  const Icon = lang === 'ar' ? ArrowRight : ArrowLeft;
  return <Icon {...props} />;
}
