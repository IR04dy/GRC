# -*- coding: utf-8 -*-
"""Apply V25 (audit_eng), V26 (board_pack), V27 (nca_ecc), V28 (kb_seed) to dev."""
import os, re, sys
import pyodbc
sys.stdout.reconfigure(encoding='utf-8')

CS = ('DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
      'UID=sa;PWD=tss.123;Encrypt=No;')

MIG_DIR = r'C:\AYMAN\Github\Claude-GRC-2\.claude\worktrees\tender-curie-cee787\tss-grc-backend\src\main\resources\db\migration\sqlserver'
FILES = ['V25__audit_eng.sql', 'V26__board_pack.sql', 'V27__nca_ecc.sql', 'V28__kb_seed.sql']

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
                print(f'  batch {i} ERR: {str(e)[:200]}')
                raise
print('\nAll applied.')
