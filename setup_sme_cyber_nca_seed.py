"""Mirror the deep NCA SME catalog (evidence.*) into the cyber.* tables that the
/framework-controls page reads, so NCA controls + sub-controls are reflected there.

Source : evidence.frameworks/controls/sub_controls (seeded by setup_sme_nca_*)
Target : cyber.cyber_frameworks/cyber_controls/cyber_sub_controls
Scope  : organisation_id = 1 (the seeded demo tenant the admin user belongs to)

Idempotent: deactivates the existing NCA cyber controls+sub-controls (by framework
name) on each run, then re-inserts from the canonical active evidence catalog.
Mapping: evidence control (subdomain) -> cyber control ; evidence sub_control
(main control) -> cyber sub_control ; evidence_guidance -> cyber description_en
(the recommended evidence shown on the page).
"""
from __future__ import annotations
import re
import sys
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')
conn = pyodbc.connect(
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;")
conn.autocommit = False
cur = conn.cursor()

ORG = 1

# evidence framework code -> cyber framework metadata (name MUST match the page tabs)
FRAMEWORKS = {
    'ECC-2:2024': dict(
        name='NCA ECC', name_ar='الضوابط الأساسية للأمن السيبراني',
        code='ECC-2:2024', version='2024', regulator='NCA',
        domains={'1': '1. Cybersecurity Governance', '2': '2. Cybersecurity Defence',
                 '3': '3. Cybersecurity Resilience', '4': '4. Third-Party & Cloud Cybersecurity',
                 '5': '5. ICS Cybersecurity'}),
    'CCC-1:2019': dict(
        name='NCA CCC', name_ar='ضوابط الأمن السيبراني للأنظمة الحساسة',
        code='CCC-1:2019', version='2019', regulator='NCA',
        domains={'1': '1. Cybersecurity Governance', '2': '2. Cybersecurity Defence',
                 '3': '3. Cybersecurity Resilience', '4': '4. Third-Party & Cloud Cybersecurity'}),
    'CSCC-1:2020': dict(
        name='NCA CSCC', name_ar='ضوابط الأمن السيبراني للحوسبة السحابية',
        code='CSCC-1:2020', version='2020', regulator='NCA', domains={},
        fallback_domain='Cloud Cybersecurity'),
    'OTCC-1:2022': dict(
        name='NCA OTCC', name_ar='ضوابط الأمن السيبراني للتقنيات التشغيلية',
        code='OTCC-1:2022', version='2022', regulator='NCA', domains={},
        fallback_domain='OT Cybersecurity'),
}


def derive_domain(meta, control_code):
    m = re.match(r'^(\d+)-', control_code or '')
    if m and m.group(1) in meta['domains']:
        return meta['domains'][m.group(1)]
    return meta.get('fallback_domain', meta['name'])


def upsert_cyber_framework(meta):
    cur.execute("SELECT id FROM cyber.cyber_frameworks WHERE organisation_id=? AND name=? AND is_deleted=0",
                ORG, meta['name'])
    r = cur.fetchone()
    if r:
        fid = int(r[0])
        cur.execute("UPDATE cyber.cyber_frameworks SET name_ar=?, framework_code=?, version=?, regulator=? WHERE id=?",
                    meta['name_ar'], meta['code'], meta['version'], meta['regulator'], fid)
        return fid
    cur.execute(
        "INSERT INTO cyber.cyber_frameworks "
        "(organisation_id, external_id, name, name_ar, framework_code, version, regulator, is_deleted, created_at) "
        "OUTPUT INSERTED.id VALUES (?, CONVERT(varchar(36),NEWID()), ?, ?, ?, ?, ?, 0, SYSUTCDATETIME())",
        ORG, meta['name'], meta['name_ar'], meta['code'], meta['version'], meta['regulator'])
    return int(cur.fetchone()[0])


total_c = total_s = 0
for ev_code, meta in FRAMEWORKS.items():
    cur.execute("SELECT id FROM evidence.frameworks WHERE code=?", ev_code)
    row = cur.fetchone()
    if not row:
        print(f"! evidence framework missing: {ev_code} — skipped")
        continue
    ev_fid = int(row[0])
    cy_fid = upsert_cyber_framework(meta)

    # Clean re-seed: deactivate any prior cyber rows for this NCA framework (by id or stale name match)
    cur.execute("UPDATE cyber.cyber_sub_controls SET is_deleted=1 WHERE control_id IN "
                "(SELECT id FROM cyber.cyber_controls WHERE organisation_id=? AND (framework_id=? OR framework_name=?))",
                ORG, cy_fid, meta['name'])
    cur.execute("UPDATE cyber.cyber_controls SET is_deleted=1 WHERE organisation_id=? AND (framework_id=? OR framework_name=?)",
                ORG, cy_fid, meta['name'])

    cur.execute("SELECT id, control_code, name_en, name_ar, description_en FROM evidence.controls "
                "WHERE framework_id=? AND is_active=1 ORDER BY id", ev_fid)
    ev_controls = cur.fetchall()

    seen_ctrl = set()
    domains = set()
    nctrl = nsub = 0
    for ecid, ccode, cnen, cnar, cdesc in ev_controls:
        if ccode in seen_ctrl:
            continue
        seen_ctrl.add(ccode)
        domain = derive_domain(meta, ccode)
        domains.add(domain)
        cur.execute(
            "INSERT INTO cyber.cyber_controls "
            "(organisation_id, external_id, framework_id, framework_name, domain, control_number, "
            " title_en, title_ar, procedure_en, control_type, implementation_status, status, is_deleted, created_at) "
            "OUTPUT INSERTED.id VALUES (?, CONVERT(varchar(36),NEWID()), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, SYSUTCDATETIME())",
            ORG, cy_fid, meta['name'], domain, ccode, cnen, cnar, cdesc, 'CONTROL', 'NOT_IMPLEMENTED', 'ACTIVE')
        new_cid = int(cur.fetchone()[0])
        nctrl += 1

        cur.execute("SELECT sub_control_code, name_en, name_ar, evidence_guidance, evidence_guidance_ar "
                    "FROM evidence.sub_controls WHERE control_id=? AND is_active=1 ORDER BY id", ecid)
        seen_sub = set()
        for scode, snen, snar, guid, guid_ar in cur.fetchall():
            if scode in seen_sub:
                continue
            seen_sub.add(scode)
            cur.execute(
                "INSERT INTO cyber.cyber_sub_controls "
                "(organisation_id, external_id, control_id, sub_control_number, title_en, title_ar, "
                " description_en, description_ar, implementation_status, evidence_required, is_deleted, created_at) "
                "VALUES (?, CONVERT(varchar(36),NEWID()), ?, ?, ?, ?, ?, ?, ?, 1, 0, SYSUTCDATETIME())",
                ORG, new_cid, scode, snen, snar, guid, guid_ar, 'NOT_IMPLEMENTED')
            nsub += 1

    cur.execute("UPDATE cyber.cyber_frameworks SET control_count=?, domain_count=? WHERE id=?",
                nctrl, len(domains), cy_fid)
    print(f"  {meta['name']:10} : {nctrl} controls, {nsub} sub-controls")
    total_c += nctrl
    total_s += nsub

conn.commit()
print(f"Cyber NCA mirror complete (org {ORG}): {total_c} controls, {total_s} sub-controls across {len(FRAMEWORKS)} frameworks.")
