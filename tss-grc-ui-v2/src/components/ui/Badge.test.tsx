import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Badge } from './Badge';

// WS14 (FUNC-001): first React-Testing-Library component test, proving the
// jsdom + RTL setup runs under `npm test`.
describe('Badge', () => {
  it('renders its children and the tone class', () => {
    render(<Badge tone="danger">Critical</Badge>);
    const el = screen.getByText('Critical');
    expect(el).toBeInTheDocument();
    expect(el).toHaveClass('badge', 'badge-danger');
  });

  it('defaults to the neutral tone', () => {
    render(<Badge>Plain</Badge>);
    expect(screen.getByText('Plain')).toHaveClass('badge-neutral');
  });

  it('applies the sm size modifier and passes through title', () => {
    render(<Badge size="sm" title="tooltip">x</Badge>);
    const el = screen.getByText('x');
    expect(el).toHaveClass('badge-sm');
    expect(el).toHaveAttribute('title', 'tooltip');
  });
});
