ALTER TABLE users DROP COLUMN email;
ALTER TABLE users RENAME COLUMN email_normalized TO email;

ALTER TABLE users RENAME CONSTRAINT uq_users_email_normalized TO uq_users_email;
ALTER TABLE users RENAME CONSTRAINT ck_users_email_normalized TO ck_users_email;
