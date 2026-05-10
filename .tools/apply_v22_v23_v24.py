# -*- coding: utf-8 -*-
"""Apply V22 (approvals), V23 (capa), V24 (regchange) to the dev DB."""
import os, re, sys
import pyodbc
sys.stdout.reconfigure(encoding='utf-8')

CS = ('DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
      'UID=sa;PWD=tss.123;Encrypt=No;')

MIG_DIR = r'C:\AYMAN\Github\Claude-GRC-2\.claude\worktrees\tender-curie-cee787\tss-grc-backend\src\main\resources\db\migration\sqlserver'
FILES = ['V22__approvals.sql', 'V23__capa.sql', 'V24__regchange.sql']

c = pyodbc.connect(CS); c.autocommit = True; cur = c.cursor()
for f in FILES:
    print(f'\n── {f} ──')
    sql = open(os.path.join(MIG_DIR, f), encoding='utf-8').read()
    batches = [b.strip() for b in re.split(r'^\s*GO\s*$', sql, flags=re.M | re.I) if b.strip()]
    for i, b in enumerate(batches, 1):
        try:
            cur.execute(b); print(f'  batch {i} OK')
        except Exception as e:
            msg = str(e).lower()
            if 'already' in msg or 'there is already' in msg:
                print(f'  batch {i} (already)')
            else:
                print(f'  batch {i} ERR: {str(e)[:200]}'); raise
print('\nAll applied.')
