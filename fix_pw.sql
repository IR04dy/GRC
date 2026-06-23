UPDATE iam.users SET password_hash='$2b$10$59/R99s8zd.SOQyR1MGv/ulyB9lFKOn06yuTgZ4NR0w.dBphZw856', failed_login_attempts=0, is_locked=0, locked_until=NULL WHERE username='admin';
SELECT password_hash FROM iam.users WHERE username='admin';
