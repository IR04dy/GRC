import { type ButtonHTMLAttributes, type ReactNode } from 'react';
import { type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  Icon?: LucideIcon;
  children?: ReactNode;
}

export function Button({ variant = 'primary', size = 'md', Icon, children, className, type, ...rest }: ButtonProps) {
  const iconSize = size === 'sm' ? 14 : size === 'lg' ? 18 : 16;
  // Default to type="button" so a Button inside a <form> does NOT auto-submit
  // the surrounding form on click. Callers that want submit-behaviour
  // (e.g. FormModal's main action) pass type="submit" explicitly.
  // HTML's default of "submit" was the cause of "Cancel/Update/Filter buttons
  // refresh the page" reports across the platform.
  return (
    <button
      type={type ?? 'button'}
      className={cn(
        'btn',
        `btn-${variant}`,
        size === 'sm' && 'btn-sm',
        size === 'lg' && 'btn-lg',
        !children && 'btn-icon',
        className,
      )}
      {...rest}
    >
      {Icon && <Icon size={iconSize} strokeWidth={2} />}
      {children}
    </button>
  );
}
