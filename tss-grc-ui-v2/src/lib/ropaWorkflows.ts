// ROPA → PDPL workflows facade client.
//
// One round-trip fetches counts + lists of the three workflows
// (privacy notices / cross-border transfers / disposal certificates)
// attached to a given ROPA code. Used by the ROPA detail panel to
// render the "PDPL workflows" card with counter chips + action
// buttons that deep-link into /data-lifecycle with the right tab +
// pre-filled ROPA code.

import { api } from './api';
import type { CrossBorderTransfer, DisposalCertificate, PrivacyNotice } from './pdplExtensions';

export interface RopaCounts {
  notices: number;
  crossBorder: number;
  disposal: number;
}

export interface RopaBundle {
  ropaCode: string;
  counts: RopaCounts;
  notices: PrivacyNotice[];
  crossBorder: CrossBorderTransfer[];
  disposal: DisposalCertificate[];
}

export async function ropaWorkflowsCounts(ropaCode: string): Promise<RopaCounts> {
  try {
    return await api.get<RopaCounts>(
      `/api/privacy/ropa-workflows/${encodeURIComponent(ropaCode)}/counts`);
  } catch { return { notices: 0, crossBorder: 0, disposal: 0 }; }
}

export async function ropaWorkflowsBundle(ropaCode: string): Promise<RopaBundle | null> {
  try {
    return await api.get<RopaBundle>(
      `/api/privacy/ropa-workflows/${encodeURIComponent(ropaCode)}`);
  } catch { return null; }
}

/**
 * Deep-link helper — build the URL that opens /data-lifecycle on the
 * right tab with the ROPA code pre-filled for filtering + creation.
 */
export function buildDeepLink(opts: {
  tab: 'notice' | 'cbt' | 'disposal';
  ropaCode?: string;
  newRecord?: boolean;
}): string {
  const params = new URLSearchParams();
  params.set('tab', opts.tab);
  if (opts.ropaCode) params.set('ropaCode', opts.ropaCode);
  if (opts.newRecord) params.set('new', '1');
  return `/data-lifecycle?${params.toString()}`;
}
