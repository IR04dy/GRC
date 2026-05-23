"""One-shot: fix title_ar/purpose_ar on admin.committee_form_templates only.

The bulk fix_arabic_mojibake.py skipped these rows because their LOB
columns (content_md_ar / completion_guide_ar) trip an SC_UTF8 binding
error when included in the same UPDATE. This script targets only the
title + purpose columns so the LOB columns are untouched.
"""
import pyodbc
import ftfy

CONN = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    "SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes"
)


def main():
    cn = pyodbc.connect(CONN, autocommit=True)
    cur = cn.cursor()
    cur.execute("SELECT id, title_ar, purpose_ar FROM admin.committee_form_templates")
    rows = cur.fetchall()
    fixed = 0
    for rid, ta, pa in rows:
        new_ta = ftfy.fix_text(ta) if ta else ta
        new_pa = ftfy.fix_text(pa) if pa else pa
        sets, params = [], []
        if ta and new_ta != ta:
            sets.append("title_ar = ?")
            params.append(new_ta)
        if pa and new_pa != pa:
            sets.append("purpose_ar = ?")
            params.append(new_pa)
        if not sets:
            continue
        params.append(rid)
        try:
            sql = "UPDATE admin.committee_form_templates SET " + ", ".join(sets) + " WHERE id = ?"
            cur.execute(sql, *params)
            fixed += 1
            print("  id=" + str(rid) + ": fixed " + str(len(sets)) + " column(s)")
        except pyodbc.Error as e:
            print("  id=" + str(rid) + ": FAILED - " + str(e)[:80])
    print("Total fixed: " + str(fixed))
    cn.close()


if __name__ == "__main__":
    main()
