import bcrypt

pw = b'Admin@12345'
h = bcrypt.hashpw(pw, bcrypt.gensalt(10)).decode()

with open("D:/AYMAN/GRC/fix_pw.sql", "w") as f:
    f.write(f"UPDATE iam.users SET password_hash='{h}', failed_login_attempts=0, is_locked=0, locked_until=NULL WHERE username='admin';\n")
    f.write(f"SELECT password_hash FROM iam.users WHERE username='admin';\n")

print(f"Hash written: {h}")
print(f"Hash len: {len(h)}")

import bcrypt as b2
stored = h.encode()
print(f"Self-verify: {b2.checkpw(pw, stored)}")
