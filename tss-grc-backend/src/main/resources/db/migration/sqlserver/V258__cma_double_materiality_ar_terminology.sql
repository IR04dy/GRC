-- V258 — CMA double-materiality Arabic terminology fix
--
-- The CMA Art. 92(a) disclosure rendered "double materiality" as the non-standard
-- noun phrase "تقييم الجوهرية المزدوج". Saudi formal / SOCPA-IFRS-CMA usage for the
-- materiality *concept* is "الأهمية النسبية". Retermed to "تقييم الأهمية النسبية المزدوجة".
--
-- Forward-only + idempotent: scoped REPLACE on the exact phrase, only rows that still
-- carry it (re-running matches 0 rows). The correct *adjective* usages elsewhere
-- ("الموضوعات الجوهرية", "الأكثر جوهرية", "عند الجوهرية") are intentionally untouched.
-- The runtime seeder (CmaDisclosureService) already emits the corrected text for new years;
-- this migration fixes the V60-seeded baseline rows on Flyway-managed environments.
-- (Dev is Flyway-off and was patched by hand via pyodbc.)

UPDATE sust.cma_disclosure
   SET requirement_ar = REPLACE(requirement_ar,
                                N'تقييم الجوهرية المزدوج',
                                N'تقييم الأهمية النسبية المزدوجة')
 WHERE requirement_ar LIKE N'%تقييم الجوهرية المزدوج%';
GO
