/**
 * Knowledge Base module — Sprint 4 §2.9.
 *
 * Bilingual KB articles + categories. Used by:
 *   - Standalone /knowledge-base browse + search
 *   - Inline help-icon popovers next to form fields (HelpIcon component)
 *
 * Schema: {@code kb.*} (V21 migration). Articles carry both {@code titleEn}/
 * {@code titleAr} and {@code bodyEnMd}/{@code bodyArMd} (markdown). Search
 * uses SQL Server full-text in a follow-up migration.
 *
 * <p><strong>Status:</strong> SCAFFOLD only — entity + repository + service stub
 * + controller stub. No real article seed yet.</p>
 */
package com.tss.grc.module.knowledge;
