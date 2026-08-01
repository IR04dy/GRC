"""F2-68(d) + F2-66 — the clean-tenant probe.

WHY THIS EXISTS
Production is a 2026-07-11 restore of a dev dump (orphan-plan §0.6): 428 of 458 shared tables
carry identical row counts and there are zero dev-only tables. So prod's data *is* the demo data,
and no amount of per-row inspection can tell "this capability works" from "the seed put rows
there". A tenant created from nothing, driven only through the API, is the only evidence that
separates the two — which is why §0.7 scan 5 calls it "the only trustworthy provenance evidence
there is".

WHAT IT DOES
  setup     creates a clean organisation, three users with distinct grants, and the 15 ratified
            SKU licences. Everything is tagged [REVIEW-TEST].
  probe     A. the workflow engine end to end (F2-66) — create / send / approve / audit-log,
               plus three negative gates
            B. every wave-1 module read, run TWICE: once as admin in the seeded org and once as
               the clean tenant's user. The comparison is the point — an endpoint that answers
               the same way for both proves nothing about isolation, and an endpoint that 404s
               for both is a wrong URL, not a finding. (F2-04b's probe wasted a leg on exactly
               that: /api/v1/sustainability/dashboard 404'd identically before and after a
               licence was revoked.)
            C. the rows the engine wrote carry the clean org, and the seeded org cannot see them
  teardown  deletes everything it created, with CHARINDEX rather than LIKE (house rule 5 —
            '[REVIEW-TEST]' in a LIKE pattern is a character class, not a literal)

Run:  py clean_tenant_probe.py [--keep]      (default: setup, probe, teardown)
      py clean_tenant_probe.py --teardown-only
Env:  GRC_BASE (default http://localhost:8084)
"""
from __future__ import annotations

import datetime
import json
import os
import ssl
import sys
import urllib.error
import urllib.request

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

DUE = (datetime.date.today() + datetime.timedelta(days=14)).isoformat()

BASE = os.environ.get('GRC_BASE', 'http://localhost:8084')
CONN = ('DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
        'UID=sa;PWD=tss.123;TrustServerCertificate=yes')
TAG = '[REVIEW-TEST]'
PW = 'Admin@12345'          # cloned bcrypt hash from `admin`; never typed into a form
SSL_CTX = ssl.create_default_context()
SSL_CTX.check_hostname = False
SSL_CTX.verify_mode = ssl.CERT_NONE

# The 15 ratified SKUs (ADR-018). A clean tenant gets exactly these, so that what the probe
# measures is the capability and not the entitlement gate F2-04a just shipped.
SKUS = ['RISK', 'COMPLIANCE', 'AUDIT_LC', 'CYBER', 'SOCOPS', 'BCM', 'PDPL', 'TPRM',
        'ESG', 'ETHICS', 'SAUDI_FS', 'DATA_AI_GOV', 'BOARD', 'CHANGE', 'ACCESS_GOV']

# username -> (role, what it is for)
USERS = {
    'rt_initiator': ('RISK_MANAGER', 'raises the request; holds WORKFLOW:APPROVE too, so a '
                                     'refusal to self-approve is a four-eyes result and not a '
                                     'missing permission'),
    'rt_approver': ('COMPLIANCE_OFFICER', 'the designated second pair of eyes'),
    'rt_writer': ('CONTROL_OWNER', 'WORKFLOW:WRITE but NOT APPROVE — the permission negative'),
    # Probe B needs a clean-tenant identity that can READ broadly, and no single role can: measured,
    # NO role in iam.roles holds CYBER:READ, IAM:READ and a SOC-Ops read together. IAM_LEAD is the
    # widest single role (CYBER:READ + IAM:READ), so probe B tries this persona first and falls back
    # to rt_initiator — "clean" has to mean the tenant, not one persona's grant sheet.
    'rt_reader': ('IAM_LEAD', 'the widest single read role — CYBER:READ + IAM:READ'),
}

# ── wave-1 reads. Every one is probed as admin(seeded) and as the clean tenant. ────────────────
READS = [
    ('BASE', '/api/v1/dashboard/stats'),
    ('BASE', '/api/v1/dashboard/heatmap'),
    ('BASE', '/api/v1/dashboard/my-tasks'),
    ('BASE', '/api/v1/dashboard/recent-risks'),
    ('BASE', '/api/v1/dashboard/recent-incidents'),
    ('BASE', '/api/v1/dashboard/risk-by-category'),
    ('BASE', '/api/v1/dashboard/compliance-by-framework'),
    ('BASE', '/api/v1/workflows/requests'),
    ('BASE', '/api/v1/workflows/requests/inbox'),
    ('BASE', '/api/v1/workflow/definitions'),
    ('BASE', '/api/v1/workflow/chains/templates'),
    ('RISK', '/api/v1/risk/categories'),
    ('RISK', '/api/v1/risk/appetite'),
    ('RISK', '/api/v1/risk/kri/definitions'),
    ('RISK', '/api/v1/risk/methodology'),
    ('COMPLIANCE', '/api/v1/compliance/frameworks'),
    ('COMPLIANCE', '/api/v1/compliance/controls'),
    ('COMPLIANCE', '/api/v1/compliance/findings'),
    ('COMPLIANCE', '/api/v1/compliance/assessments'),
    ('BCM', '/api/v1/bcm/bia-assessments'),
    ('BCM', '/api/v1/bcm/aars'),
    ('CYBER', '/api/v1/cyber/assets'),
    ('CYBER', '/api/v1/asm/assets'),
    ('EVIDENCE', '/api/v1/evidence/items'),
    ('EVIDENCE', '/api/v1/evidence/frameworks'),
    ('ETHICS', '/api/v1/ethics/cases'),
    ('ETHICS', '/api/v1/ethics/aml-alerts'),
    ('AUDIT_LC', '/api/audit-lc/findings'),
    ('AUDIT_LC', '/api/v1/ia/universe'),
    ('SOCOPS', '/api/v1/socops/dashboard'),
    ('SOCOPS', '/api/v1/socops/awareness'),
    ('TPRM', '/api/v1/tprm/assessments'),
    ('TPRM', '/api/v1/tprm/outsourcing'),
    ('ESG', '/api/sustainability/climate-risks'),
    ('ESG', '/api/sustainability/corrective-actions'),
    ('ACCESS_GOV', '/api/v1/iam/governance/campaigns'),
    ('ACCESS_GOV', '/api/v1/iam/governance/break-glass'),
]


# ── plumbing ──────────────────────────────────────────────────────────────────────────────────
def db():
    return pyodbc.connect(CONN, autocommit=True)


def call(method: str, path: str, token: str | None = None, body=None):
    """-> (status, parsed-or-text). Never raises for an HTTP status; a 4xx IS a result."""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(req, timeout=60, context=SSL_CTX) as r:
            raw = r.read().decode('utf-8', 'replace')
            try:
                return r.status, json.loads(raw)
            except Exception:
                return r.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode('utf-8', 'replace')
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw
    except Exception as e:                                     # connection refused, timeout…
        return 0, f'{type(e).__name__}: {e}'


def login(username: str, password: str = PW):
    st, body = call('POST', '/api/auth/login', body={'username': username, 'password': password})
    if st != 200 or not isinstance(body, dict):
        return None, (st, body)
    return body.get('accessToken') or body.get('token'), (st, None)


def rowcount(cur, sql, *args):
    return cur.execute(sql, *args).fetchval()


# ── setup ─────────────────────────────────────────────────────────────────────────────────────
def setup():
    cn = db()
    c = cn.cursor()
    print('── setup ' + '─' * 68)

    existing = c.execute('SELECT id FROM platform.organisations WHERE short_code = ?',
                         'RTCLEAN').fetchval()
    if existing:
        print(f'   reusing clean org {existing} (a previous run did not tear down)')
        org = existing
    else:
        c.execute("""INSERT INTO platform.organisations
                     (name_en, name_ar, short_code, industry_sector, country_code,
                      subscription_tier, is_active, is_deleted, created_at, updated_at)
                     VALUES (?, ?, 'RTCLEAN', 'Testing', 'SA', 'ENTERPRISE', 1, 0,
                             SYSUTCDATETIME(), SYSUTCDATETIME())""",
                  f'{TAG} Clean Tenant', f'{TAG} منشأة نظيفة')
        org = c.execute('SELECT id FROM platform.organisations WHERE short_code = ?',
                        'RTCLEAN').fetchval()
        print(f'   created organisation {org} — bilingual name (house rule 7)')

    for sku in SKUS:
        if not rowcount(c, 'SELECT COUNT(*) FROM platform.organisation_licenses '
                           'WHERE organisation_id = ? AND module_code = ?', org, sku):
            c.execute("""INSERT INTO platform.organisation_licenses
                         (organisation_id, module_code, tier, enabled, valid_from,
                          created_at, updated_at)
                         VALUES (?, ?, 'ENTERPRISE', 1, CAST(SYSUTCDATETIME() AS date),
                                 SYSUTCDATETIME(), SYSUTCDATETIME())""", org, sku)
    print(f'   licensed {len(SKUS)} ratified SKUs')

    pwhash = c.execute("SELECT password_hash FROM iam.users WHERE username = 'admin'").fetchval()
    ids = {}
    for un, (role, _) in USERS.items():
        uid = c.execute('SELECT id FROM iam.users WHERE username = ?', un).fetchval()
        if not uid:
            c.execute("""INSERT INTO iam.users
                         (organisation_id, username, email, password_hash, full_name_en,
                          full_name_ar, is_active, is_locked, failed_login_attempts,
                          must_change_password, mfa_enabled, preferred_locale, is_deleted,
                          created_at, updated_at)
                         VALUES (?, ?, ?, ?, ?, ?, 1, 0, 0, 0, 0, 'en', 0,
                                 SYSUTCDATETIME(), SYSUTCDATETIME())""",
                      org, un, f'{un}@review.test', pwhash,
                      f'{TAG} {un}', f'{TAG} {un}')
            uid = c.execute('SELECT id FROM iam.users WHERE username = ?', un).fetchval()
        ids[un] = uid
        rid = c.execute('SELECT TOP 1 id FROM iam.roles WHERE role_name = ? AND is_deleted = 0 '
                        'ORDER BY CASE WHEN organisation_id IS NULL THEN 0 ELSE 1 END', role).fetchval()
        if not rid:
            sys.exit(f'   FATAL: role {role} does not exist — F2-52 territory, repoint the probe')
        if not rowcount(c, 'SELECT COUNT(*) FROM iam.user_roles WHERE user_id = ? AND role_id = ?',
                        uid, rid):
            c.execute("""INSERT INTO iam.user_roles
                         (user_id, role_id, assigned_at, assigned_by, valid_from, status,
                          source, is_deleted)
                         VALUES (?, ?, SYSUTCDATETIME(), 1, SYSUTCDATETIME(), 'ACTIVE',
                                 'REVIEW-TEST', 0)""", uid, rid)
        print(f'   user {un} (id {uid}) -> {role}')

    # The clean tenant must start with nothing of its own.
    for schema_table in ('workflow.requests', 'risk.risks', 'compliance.findings',
                         'evidence.evidence_items'):
        n = rowcount(c, f'SELECT COUNT(*) FROM {schema_table} WHERE organisation_id = ?', org)
        assert n == 0, f'   clean tenant is not clean: {schema_table} has {n} rows'
    print('   verified: the new tenant owns zero rows in requests/risks/findings/evidence')
    return org, ids


# ── probe A: the workflow engine, end to end on a tenant that has never used it ───────────────
def probe_engine(org, ids):
    print()
    print('── A. workflow engine end to end (F2-66) ' + '─' * 36)
    results = []

    def rec(name, ok, detail):
        results.append((name, ok, detail))
        print(f'   {"PASS" if ok else "FAIL"}  {name}: {detail}')

    tok_i, err = login('rt_initiator')
    if not tok_i:
        rec('initiator can log in', False, f'login failed {err}')
        return results
    rec('initiator can log in', True, 'clean-tenant user authenticates')
    tok_a, _ = login('rt_approver')
    tok_w, _ = login('rt_writer')
    rec('approver + writer can log in', bool(tok_a and tok_w), 'three personas ready')

    # The engine's own inbox on a tenant that has never used it.
    st, body = call('GET', '/api/v1/workflows/requests', tok_i)
    empty = st == 200 and isinstance(body, dict) and body.get('totalElements') == 0
    rec('a never-used engine lists zero requests', empty, f'{st} totalElements='
        f'{body.get("totalElements") if isinstance(body, dict) else body!r}')

    payload = {
        'sourceModule': 'RISK', 'sourceRecordType': 'Risk', 'sourceRecordId': 1,
        'requestType': 'APPROVAL',
        'titleEn': f'{TAG} clean-tenant engine probe',
        'titleAr': f'{TAG} اختبار محرك سير العمل',
        'description': f'{TAG} F2-66 — first request this tenant has ever had',
        'routingMode': 'SEQUENTIAL', 'completionRule': 'ALL',
        'requiredAction': 'Approve the clean-tenant probe',
        'priority': 'MEDIUM', 'slaHours': 72, 'dueDate': DUE, 'responseRequired': True,
        'approverId': ids['rt_approver'],
        'recipients': [{'recipientType': 'USER', 'recipientId': ids['rt_approver'],
                        'recipientLabel': 'rt_approver', 'actorRole': 'APPROVER', 'stepNo': 1}],
    }
    st, created = call('POST', '/api/v1/workflows/requests', tok_i, payload)
    ok = st in (200, 201) and isinstance(created, dict) and created.get('id')
    rec('create', ok, f'{st} {created if not ok else "id=" + str(created["id"]) + " status=" + str(created.get("status"))}')
    if not ok:
        return results
    rid = created['id']

    st, sent = call('POST', f'/api/v1/workflows/requests/{rid}/send', tok_i)
    rec('send', st == 200, f'{st} status={sent.get("status") if isinstance(sent, dict) else sent!r}')

    # Negative 1 — permission. WRITE without APPROVE must not reach the approval endpoint.
    st, body = call('POST', f'/api/v1/workflows/requests/{rid}/approve', tok_w, {'note': TAG})
    rec('a WRITE-only role cannot approve (F2-13b guard)', st == 403,
        f'{st} — CONTROL_OWNER holds WORKFLOW:WRITE, not APPROVE')

    # Negative 2 — four eyes. The initiator HOLDS WORKFLOW:APPROVE, so a refusal here is the
    # actor gate, not a missing permission.
    st, body = call('POST', f'/api/v1/workflows/requests/{rid}/approve', tok_i, {'note': TAG})
    msg = body.get('detail') if isinstance(body, dict) else str(body)
    rec('the initiator cannot approve a request that names someone else', st in (400, 403, 409),
        f'{st} {str(msg)[:80]}')

    # SENT -> SUBMITTED. `submit` is initiator-only (requireInitiatorOrAdmin): the engine does not
    # let an approver skip a step, so SENT -> APPROVED is a 409 by design. The first run of this
    # probe hit that 409 and it was the probe that was wrong, not the engine.
    st, sub = call('POST', f'/api/v1/workflows/requests/{rid}/submit', tok_i, {'note': TAG})
    rec('the initiator submits for approval', st == 200 and isinstance(sub, dict)
        and sub.get('status') == 'SUBMITTED',
        f'{st} status={sub.get("status") if isinstance(sub, dict) else sub!r}')

    # The designated approver.
    st, appr = call('POST', f'/api/v1/workflows/requests/{rid}/approve', tok_a, {'note': TAG})
    ok = st == 200 and isinstance(appr, dict) and appr.get('status') in ('APPROVED', 'CLOSED', 'COMPLETED')
    rec('the designated approver approves', ok,
        f'{st} status={appr.get("status") if isinstance(appr, dict) else appr!r}')

    st, closed = call('POST', f'/api/v1/workflows/requests/{rid}/close', tok_i, {'note': TAG})
    rec('the initiator closes it', st == 200 and isinstance(closed, dict)
        and closed.get('status') == 'CLOSED',
        f'{st} status={closed.get("status") if isinstance(closed, dict) else closed!r}')

    st, log = call('GET', f'/api/v1/workflows/requests/{rid}/audit-log', tok_i)
    n = len(log) if isinstance(log, list) else -1
    rec('the lifecycle left an audit trail', st == 200 and n >= 4, f'{st} {n} events: '
        + ', '.join(e.get('eventType', '?') for e in (log if isinstance(log, list) else [])[:8]))

    # Negative 3 — self-approval by naming yourself. Nothing in create() forbids
    # approverId == initiatedBy, so this asks whether four eyes is structural or conventional.
    solo = dict(payload, titleEn=f'{TAG} self-named approver',
                approverId=ids['rt_initiator'],
                recipients=[{'recipientType': 'USER', 'recipientId': ids['rt_initiator'],
                             'recipientLabel': 'rt_initiator', 'actorRole': 'APPROVER',
                             'stepNo': 1}])
    st, s1 = call('POST', '/api/v1/workflows/requests', tok_i, solo)
    if st in (200, 201) and isinstance(s1, dict):
        sid = s1['id']
        call('POST', f'/api/v1/workflows/requests/{sid}/send', tok_i)
        call('POST', f'/api/v1/workflows/requests/{sid}/submit', tok_i, {'note': TAG})
        st2, s2 = call('POST', f'/api/v1/workflows/requests/{sid}/approve', tok_i, {'note': TAG})
        blocked = st2 in (400, 403, 409)
        rec('naming yourself as approver is refused', blocked,
            f'{st2} status={s2.get("status") if isinstance(s2, dict) else ""} '
            f'{str(s2.get("detail") if isinstance(s2, dict) else s2)[:60]}'
            + ('' if blocked else '   <-- FOUR-EYES HOLE: the initiator approved their own request'))
    else:
        rec('naming yourself as approver is refused', True,
            f'create refused it outright: {st} {str(s1)[:80]}')

    # Negative 4 — the OTHER approval gate. create() accepts an APPROVAL request with no
    # approverId as long as a recipient carries actorRole=APPROVER. But approve() calls
    # requireActor(r.getApproverId() ...), which falls back to the INITIATOR when approverId is
    # null. So on that shape, does the designated APPROVER recipient get to approve at all?
    rcp = dict(payload, titleEn=f'{TAG} approver-recipient only', approverId=None,
               recipients=[{'recipientType': 'USER', 'recipientId': ids['rt_approver'],
                            'recipientLabel': 'rt_approver', 'actorRole': 'APPROVER',
                            'stepNo': 1}])
    st, r1 = call('POST', '/api/v1/workflows/requests', tok_i, rcp)
    if st in (200, 201) and isinstance(r1, dict):
        pid = r1['id']
        call('POST', f'/api/v1/workflows/requests/{pid}/send', tok_i)
        call('POST', f'/api/v1/workflows/requests/{pid}/submit', tok_i, {'note': TAG})
        st_a, b_a = call('POST', f'/api/v1/workflows/requests/{pid}/approve', tok_a, {'note': TAG})
        rec('the designated APPROVER recipient can approve', st_a == 200,
            f'{st_a} {str(b_a.get("detail") if isinstance(b_a, dict) else b_a)[:70]}')
        if st_a != 200:
            st_i, b_i = call('POST', f'/api/v1/workflows/requests/{pid}/approve', tok_i, {'note': TAG})
            rec('...and the initiator cannot approve it instead', st_i != 200,
                f'{st_i} status={b_i.get("status") if isinstance(b_i, dict) else ""}'
                + ('' if st_i != 200 else '   <-- the APPROVER recipient is decorative: only the '
                                          'initiator can approve this shape'))
    else:
        rec('an APPROVER-recipient request can be created', False, f'{st} {str(r1)[:90]}')

    return results


# ── probe B: every wave-1 read, seeded org vs clean tenant ────────────────────────────────────
def _size(body):
    """How much business content a response carries.

    ⚠ The first version of this counted a dict's non-empty fields, which made
    `/api/v1/dashboard/stats` look like DATA-ON-CLEAN when every business figure it returned for the
    clean tenant was **0** — the classifier reported a leak where the answer was a correct row of
    zeros. A KPI dict of all zeros is empty; that distinction is the whole point of W2-1b.
    """
    if isinstance(body, list):
        return len(body)
    if isinstance(body, dict):
        if 'totalElements' in body:
            return body['totalElements']
        if isinstance(body.get('content'), list):
            return len(body['content'])
        n = 0
        for v in body.values():
            if isinstance(v, (int, float)) and not isinstance(v, bool):
                n += 1 if v else 0
            elif isinstance(v, (list, dict, str)) and len(v):
                n += 1
            elif v is True:
                n += 1
        return n
    return -1


def probe_reads(org):
    print()
    print('── B. wave-1 reads: seeded org vs clean tenant (F2-68(d)) ' + '─' * 19)
    tok_admin, err = login('admin')
    clean = [(u, login(u)[0]) for u in ('rt_reader', 'rt_initiator')]
    clean = [(u, t) for u, t in clean if t]
    if not tok_admin or not clean:
        print(f'   FATAL: could not log in ({err})')
        return []

    rows, buckets = [], {}
    for sku, path in READS:
        sa, ba = call('GET', path, tok_admin)
        # "Clean" must mean the tenant, not one persona: take the first identity the guard admits.
        sc, bc, who = None, None, None
        for u, t in clean:
            sc, bc, who = *call('GET', path, t), u
            if sc != 403:
                break
        na, nc = _size(ba), _size(bc)
        if sa == 404 and sc == 404:
            verdict = 'BAD-URL'          # proves nothing either way
        elif sa >= 500 or sc >= 500:
            verdict = 'SERVER-ERROR'
        elif sc == 403:
            verdict = 'FORBIDDEN-CLEAN'  # grant, not data — no isolation evidence either way
        elif sa == 200 and sc == 200 and na > 0 and nc == 0:
            verdict = 'ISOLATED'         # the shape a clean tenant should have
        elif sa == 200 and sc == 200 and nc > 0 and ba == bc:
            verdict = 'IDENTICAL'        # same bytes for both -> global reference data, or a leak
        elif sa == 200 and sc == 200 and nc > 0:
            verdict = 'OWN-DATA'         # non-empty but its own (e.g. its own 3 users)
        elif sa == 200 and sc == 200:
            verdict = 'BOTH-EMPTY'       # seeded org has none either: unproven, not isolated
        else:
            verdict = f'{sa}/{sc}'
        buckets[verdict] = buckets.get(verdict, 0) + 1
        rows.append((sku, path, sa, na, sc, nc, verdict))
        print(f'   {verdict:16s} {sku:11s} {path:52s} admin={sa}:{na:<5} '
              f'clean={sc}:{nc} [{who}]')

    print()
    for k in sorted(buckets):
        print(f'   {k:16s} {buckets[k]}')
    return rows


# ── probe C: the rows the engine wrote belong to the clean tenant, and only to it ─────────────
def probe_isolation(org):
    print()
    print('── C. tenant isolation of what the engine wrote ' + '─' * 29)
    c = db().cursor()
    mine = rowcount(c, 'SELECT COUNT(*) FROM workflow.requests WHERE organisation_id = ? '
                       'AND CHARINDEX(?, description) > 0', org, TAG)
    leaked = rowcount(c, 'SELECT COUNT(*) FROM workflow.requests WHERE organisation_id <> ? '
                         'AND CHARINDEX(?, title_en) > 0 AND CHARINDEX(?, description) > 0',
                      org, TAG, 'clean-tenant')
    ev = rowcount(c, 'SELECT COUNT(*) FROM workflow.request_events e JOIN workflow.requests r '
                     'ON r.id = e.request_id WHERE r.organisation_id = ?', org)
    print(f'   requests owned by the clean tenant: {mine}')
    print(f'   the same rows visible under another organisation_id: {leaked}')
    print(f'   request_events written for the clean tenant: {ev}')

    tok_admin, _ = login('admin')
    st, body = call('GET', '/api/v1/workflows/requests?size=200', tok_admin)
    titles = [r.get('titleEn', '') for r in (body.get('content') or [])] if isinstance(body, dict) else []
    seen = [t for t in titles if 'clean-tenant engine probe' in t]
    print(f'   admin (seeded org) sees the clean tenant\'s request: {bool(seen)}  ({st})')
    return {'mine': mine, 'leaked': leaked, 'events': ev, 'admin_sees': bool(seen)}


# ── teardown ──────────────────────────────────────────────────────────────────────────────────
def teardown():
    print()
    print('── teardown ' + '─' * 65)
    cn = db()
    c = cn.cursor()
    org = c.execute('SELECT id FROM platform.organisations WHERE short_code = ?', 'RTCLEAN').fetchval()
    if not org:
        print('   nothing to remove')
        return
    uids = [r[0] for r in c.execute('SELECT id FROM iam.users WHERE organisation_id = ?', org)]
    steps = [
        ('workflow.request_recipients', 'DELETE rr FROM workflow.request_recipients rr '
                                        'JOIN workflow.requests r ON r.id = rr.request_id '
                                        'WHERE r.organisation_id = ?', (org,)),
        ('workflow.request_events', 'DELETE e FROM workflow.request_events e '
                                    'JOIN workflow.requests r ON r.id = e.request_id '
                                    'WHERE r.organisation_id = ?', (org,)),
        ('workflow.requests', 'DELETE FROM workflow.requests WHERE organisation_id = ?', (org,)),
        ('platform.audit_log', 'DELETE FROM platform.audit_log WHERE organisation_id = ?', (org,)),
        ('platform.notifications', 'DELETE FROM platform.notifications WHERE organisation_id = ?', (org,)),
    ]
    for label, sql, args in steps:
        try:
            c.execute(sql, *args)
            print(f'   {label}: {c.rowcount} removed')
        except Exception as e:
            print(f'   {label}: skipped ({str(e)[:70]})')
    if uids:
        qs = ','.join('?' * len(uids))
        for label, sql in (('iam.user_roles', f'DELETE FROM iam.user_roles WHERE user_id IN ({qs})'),
                           # platform.refresh_token has NO FK to iam.users (F2-62's keeper), so an
                           # FK-walk cleanup orphans rows — sweep it by column name explicitly.
                           ('platform.refresh_token', f'DELETE FROM platform.refresh_token WHERE user_id IN ({qs})'),
                           ('iam.users', f'DELETE FROM iam.users WHERE id IN ({qs})')):
            try:
                c.execute(sql, *uids)
                print(f'   {label}: {c.rowcount} removed')
            except Exception as e:
                print(f'   {label}: skipped ({str(e)[:70]})')
    c.execute('DELETE FROM platform.organisation_licenses WHERE organisation_id = ?', org)
    print(f'   platform.organisation_licenses: {c.rowcount} removed')
    c.execute('DELETE FROM platform.organisations WHERE id = ?', org)
    print(f'   platform.organisations: {c.rowcount} removed (org {org})')


def main():
    args = set(sys.argv[1:])
    if '--teardown-only' in args:
        teardown()
        return
    st, _ = call('GET', '/actuator/health')
    if st != 200:
        sys.exit(f'backend not answering at {BASE} (health {st}) — start it first')
    org, ids = setup()
    try:
        eng = probe_engine(org, ids)
        rd = probe_reads(org)
        iso = probe_isolation(org)
    finally:
        if '--keep' not in args:
            teardown()
        else:
            print('\n   --keep: the clean tenant was left in place; '
                  'run --teardown-only when finished')

    print()
    print('══ summary ' + '═' * 66)
    passed = sum(1 for _, ok, _ in eng if ok)
    print(f'   engine gates: {passed}/{len(eng)} pass')
    for name, ok, detail in eng:
        if not ok:
            print(f'      FAIL {name}: {detail}')
    b = {}
    for *_, verdict in rd:
        b[verdict] = b.get(verdict, 0) + 1
    print(f'   reads: ' + '  '.join(f'{k}={v}' for k, v in sorted(b.items())))
    print(f'   isolation: {iso}')
    return 0 if passed == len(eng) else 1


if __name__ == '__main__':
    sys.exit(main() or 0)
