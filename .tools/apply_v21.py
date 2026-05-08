# -*- coding: utf-8 -*-
import pyodbc, re, sys
sys.stdout.reconfigure(encoding='utf-8')
cs = ('DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
      'UID=sa;PWD=tss.123;Encrypt=No;')
c = pyodbc.connect(cs); c.autocommit = True; cur = c.cursor()
sql = open(r'C:\AYMAN\Github\Claude-GRC-2\.claude\worktrees\tender-curie-cee787\tss-grc-backend\src\main\resources\db\migration\sqlserver\V21__knowledge_base.sql', encoding='utf-8').read()
batches = [b.strip() for b in re.split(r'^\s*GO\s*$', sql, flags=re.M | re.I) if b.strip()]
for i, b in enumerate(batches, 1):
    try:
        cur.execute(b); print(f'  V21 batch {i} OK')
    except Exception as e:
        msg = str(e).lower()
        if 'already' in msg or 'there is already an object named' in msg:
            print(f'  V21 batch {i} (already exists)')
        else:
            print(f'  V21 batch {i} ERR: {str(e)[:200]}')
            raise
print('V21 applied.')
