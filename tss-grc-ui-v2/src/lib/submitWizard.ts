import { workflowSubmissionApi, type WorkflowSubmissionDto } from './api';

/**
 * Thin helper used by every wizard whose backend is the generic
 * {@code workflow.workflow_submissions} ledger (V125).
 *
 * <p>Each wizard's submit handler calls {@link submitWizard} with the
 * catalog id, module bucket, bilingual title, optional summary, optional
 * regulatory anchor, and the full wizard draft (which is JSON-encoded
 * here). The server returns the freshly-created row id so the wizard can
 * show {@code #{id}} in the success toast.
 *
 * <p>Why generic? Twelve of the §7 catalog wizards (Risk Treatment, Risk
 * Acceptance, Incident RCA, Management Review, Digital Signature, Control
 * Test, Cloud CCC, NCA ECC, Consent, Cross-Border, Supplier Exit Plan,
 * Board Pack) don't yet have dedicated tables. One generic ledger keeps
 * the wizards production-real without blocking on 12 separate migrations.
 */
export interface SubmitWizardInput<TDraft> {
  /** workflowCatalog.ts id — e.g. 'erm-risk-treatment'. */
  workflowId: string;
  /** ERM | BCM | SCM | NCA | PDPL | COMPLIANCE | EVIDENCE | INCIDENT | GOVERNANCE */
  workflowModule: string;
  titleEn: string;
  titleAr?: string;
  summaryEn?: string;
  summaryAr?: string;
  /** e.g. 'PDPL Art. 23', 'ISO 22301 §9.3' */
  regulatoryAnchor?: string;
  /** DRAFT | SUBMITTED | UNDER_REVIEW | APPROVED | REJECTED | CLOSED */
  status?: string;
  draft: TDraft;
}

export async function submitWizard<TDraft>(
  input: SubmitWizardInput<TDraft>,
): Promise<WorkflowSubmissionDto> {
  return workflowSubmissionApi.create({
    workflowId:        input.workflowId,
    workflowModule:    input.workflowModule,
    titleEn:           input.titleEn,
    titleAr:           input.titleAr,
    summaryEn:         input.summaryEn,
    summaryAr:         input.summaryAr,
    regulatoryAnchor:  input.regulatoryAnchor,
    status:            input.status ?? 'SUBMITTED',
    payloadJson:       JSON.stringify(input.draft),
  });
}
