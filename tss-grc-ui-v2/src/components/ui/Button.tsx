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

export function Button({ variant = 'primary', size = 'md', Icon, children, className, ...rest }: ButtonProps) {
  const iconSize = size === 'sm' ? 14 : size === 'lg' ? 17 : 16;
  return (
    <button
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
