"""
Dev-DB fix: allow NEEDS_REVISION in evidence.evidence_items.CK_ev_items_status.

The evidence-lifecycle-hardening change extended the Java state machine
(EvidenceItemService.ITEM_STATUSES / ITEM_TRANSITIONS + EvidenceReviewService)
to add NEEDS_REVISION as a distinct evidence-item state, but the DB CHECK
constraint CK_ev_items_status was never migrated to allow it. Result:
POST /api/v1/evidence/reviews/{id}/complete?result=NEEDS_REVISION -> HTTP 500
(CHECK constraint violation), and NEEDS_REVISION is the ONLY path an officer
has to return evidence for revision.

This redefines the constraint to the full Java enum plus the two legacy
terminal values already present (ARCHIVED, SUPERSEDED) for forward-compat.
Idempotent: skips if NEEDS_REVISION is already allowed. Flyway V213 carries
the same change to staging/prod.
"""
import sys
import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;"
        "DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;")

ALLOWED = "'DRAFT','PENDING_REVIEW','APPROVED','REJECTED','NEEDS_REVISION','EXPIRED','ARCHIVED','SUPERSEDED'"


def main():
    cn = pyodbc.connect(CONN, autocommit=True)
    cur = cn.cursor()

    current = cur.execute(
        "SELECT cc.definition FROM sys.check_constraints cc "
        "WHERE cc.parent_object_id=OBJECT_ID('evidence.evidence_items') "
        "AND cc.name='CK_ev_items_status'").fetchval()

    if current and "NEEDS_REVISION" in current:
        print("= CK_ev_items_status already allows NEEDS_REVISION (no-op)")
        cn.close()
        return

    print("- current: " + str(current))
    if current is not None:
        cur.execute("ALTER TABLE evidence.evidence_items DROP CONSTRAINT CK_ev_items_status")
        print("  dropped old constraint")
    cur.execute(
        "ALTER TABLE evidence.evidence_items ADD CONSTRAINT CK_ev_items_status "
        "CHECK (status IN (" + ALLOWED + "))")
    print("+ re-added CK_ev_items_status incl. NEEDS_REVISION")

    new = cur.execute(
        "SELECT definition FROM sys.check_constraints "
        "WHERE parent_object_id=OBJECT_ID('evidence.evidence_items') "
        "AND name='CK_ev_items_status'").fetchval()
    print(("OK" if "NEEDS_REVISION" in new else "FAIL") + " — new: " + new)
    cn.close()


if __name__ == "__main__":
    main()
