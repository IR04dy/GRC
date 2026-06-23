import bcrypt, pyodbc

pw = b'Admin@12345'
h = bcrypt.hashpw(pw, bcrypt.gensalt(10)).decode()
print(f"Hash: {h}")

conn = pyodbc.connect("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes")
cur = conn.cursor()
cur.execute("UPDATE iam.users SET password_hash=?, failed_login_attempts=0, is_locked=0, locked_until=NULL WHERE username='admin'", h)
conn.commit()
print(f"Updated: {cur.rowcount} rows")

# Verify
cur.execute("SELECT password_hash FROM iam.users WHERE username='admin'")
row = cur.fetchone()
stored = row[0].encode()
print(f"Stored matches: {bcrypt.checkpw(pw, stored)}")
conn.close()
