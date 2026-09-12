import { Sparkles } from 'lucide-react';
import { useI18n } from '../../lib/i18n';

/** Inline pill badge — for nav items / page tiles that aren't shipped yet. */
export function ComingSoonBadge() {
  const { lang } = useI18n();
  return (
    <span className="cs-badge">
      <Sparkles size={10} />
      {lang === 'ar' ? 'قريباً' : 'Soon'}
    </span>
  );
}

/** Page-level overlay for routes that show in nav but aren't implemented yet. */
export function ComingSoonPage({
  titleEn = 'Coming soon',
  titleAr = 'قريباً',
  descriptionEn = 'This workspace is on the product roadmap. Talk to your TSS account team if you need it sooner.',
  descriptionAr = 'مساحة العمل هذه ضمن خارطة طريق المنتج. تواصل مع فريق حسابك في TSS إذا كنت بحاجة إليها قبل ذلك.',
}: {
  titleEn?: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
}) {
  const { lang } = useI18n();
  return (
    <div className="cs-page-overlay">
      <Sparkles size={48} />
      <h2>{lang === 'ar' ? titleAr : titleEn}</h2>
      <p>{lang === 'ar' ? descriptionAr : descriptionEn}</p>
    </div>
  );
}
