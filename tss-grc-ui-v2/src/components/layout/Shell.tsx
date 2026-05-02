import { type ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

export function Shell({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  return (
    <div className="grc-shell">
      <Sidebar />
      <div className="grc-main">
        <Topbar title={title} sub={sub} />
        <div className="page">{children}</div>
      </div>
    </div>
  );
}
