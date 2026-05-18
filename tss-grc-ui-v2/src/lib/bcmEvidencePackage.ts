// BCM Audit Evidence Package client — audit Tier-2.
//
// Reuses the existing api.ts downloadFile helper which handles auth + 401
// refresh + Content-Disposition filename extraction in one place. No new
// fetch plumbing needed.

import { downloadFile } from './api';

/**
 * Trigger the browser to download the audit evidence package ZIP for the
 * given cycle. Filename comes from the server's Content-Disposition header
 * (regulator-ready: bcm-evidence-{year}-cycle-{id}-{timestamp}.zip).
 */
export async function downloadEvidencePackage(cycleId: number): Promise<void> {
  await downloadFile(
    `/api/v1/bcm/cycles/${cycleId}/evidence-package`,
    `bcm-evidence-cycle-${cycleId}.zip`);
}
