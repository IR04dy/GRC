/**
 * Digital Signature module — Saudi GRC prompt §28.
 *
 * Universal digital-signature transaction facility. Any module that
 * needs cryptographically-bound human sign-off (board pack approval,
 * risk acceptance, BCP sign-off, supplier attestation, NCA compliance
 * attestation, regulatory evidence package, etc.) creates a row in
 * {@code signature.digital_signature_transactions} (V30 migration).
 *
 * Primary state machine: REQUESTED → SIGNED | REJECTED | EXPIRED.
 * SIGNED records carry the signed_document_hash + certificate_reference
 * so the audit trail can prove what was attested to.
 *
 * REST: {@code /api/signature/transactions}.
 */
package com.tss.grc.module.signature;
