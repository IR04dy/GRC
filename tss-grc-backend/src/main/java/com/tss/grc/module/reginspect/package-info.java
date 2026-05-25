/**
 * Regulatory Inspection Workspace — Sprint 15 PR #1.
 *
 * <p>Operational workspace for managing on-site visits by Saudi regulators
 * (SAMA, NCA, CMA, SDAIA, NCEMA). Every Saudi bank gets a SAMA on-site
 * every 12-18 months; NCA OTCC + CMA listing audits are similarly periodic.
 * Until now teams have run these out of spreadsheets and SharePoint —
 * this module brings them into the platform with SLA tracking, document-
 * request register, finding response workflow, and timeline audit trail.
 *
 * <p><b>Inspection lifecycle</b>:
 * <pre>
 *   PLANNED              — notification received, inspection scoped, dates set
 *   ENTRY_MEETING         — opened with the inspector / engagement letter received
 *   FIELDWORK             — document requests + interviews + walkthrough
 *   EXIT_MEETING          — provisional findings discussed
 *   RESPONSE_DRAFTING     — management responses being drafted
 *   CLOSED                — formal letter issued + responses filed; CAPA tracked elsewhere
 * </pre>
 *
 * <p><b>Document request lifecycle</b>:
 * <pre>
 *   OPEN → IN_PROGRESS → SUBMITTED
 *                     ↘ OVERDUE (if past dueDate)
 * </pre>
 *
 * <p><b>Saudi-native bilingual</b>:
 * SAMA correspondence flows primarily in Arabic; the workspace stores both
 * EN + AR for every operator-visible field. Operator-natural Arabic, not
 * literal translation:
 * <ul>
 *   <li><i>التفتيش الميداني</i> — on-site inspection (not literal: التفتيش في الموقع)</li>
 *   <li><i>طلب وثائق</i> — document request</li>
 *   <li><i>اجتماع الافتتاح / اجتماع الإغلاق</i> — entry / exit meeting</li>
 *   <li><i>خطاب الردّ الإداري</i> — management response letter</li>
 * </ul>
 *
 * <p>Emits F8 trigger events:
 * <ul>
 *   <li>{@code REG_INSPECTION_CREATED}</li>
 *   <li>{@code REG_INSPECTION_TRANSITIONED}</li>
 *   <li>{@code REG_DOCUMENT_REQUEST_OPENED}</li>
 *   <li>{@code REG_DOCUMENT_REQUEST_SUBMITTED}</li>
 *   <li>{@code REG_DOCUMENT_REQUEST_OVERDUE} (raised by F8 scheduler)</li>
 *   <li>{@code REG_INSPECTION_CLOSED}</li>
 * </ul>
 */
package com.tss.grc.module.reginspect;
