\set ON_ERROR_STOP on

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM transfers t
    JOIN wallets w ON w.id=t.sender_wallet_id
    JOIN users u ON u.id=w.owner_id
    WHERE u.email_normalized LIKE 'load-user-%@example.test'
  ) THEN
    RAISE EXCEPTION 'load fixtures already contain transfers; reset the wallet-load Compose volume before reseeding';
  END IF;
END $$;

BEGIN;

INSERT INTO users(id,email,email_normalized,password_hash,status,created_at,updated_at)
SELECT md5('load-user-' || i)::uuid,
       'load-user-' || lpad(i::text,3,'0') || '@example.test',
       'load-user-' || lpad(i::text,3,'0') || '@example.test',
       '$2b$12$0CXyp1TKXarYzzkITjbQFOGTXXhdltIEeHAVcqTV6yDnuwCAYJJ5m',
       'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM generate_series(1,20) AS i
ON CONFLICT(id) DO NOTHING;

INSERT INTO user_roles(user_id,role_id)
SELECT md5('load-user-' || i)::uuid,1 FROM generate_series(1,20) AS i
ON CONFLICT DO NOTHING;

INSERT INTO wallets(id,owner_id,currency,status,available_balance,ledger_balance,created_at,updated_at)
SELECT md5('load-wallet-' || i)::uuid,md5('load-user-' || i)::uuid,
       'NGN','ACTIVE',1000.00,1000.00,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM generate_series(1,20) AS i
ON CONFLICT(id) DO NOTHING;

INSERT INTO ledger_accounts(id,wallet_id,account_code,account_type,currency,status,created_at)
SELECT md5('load-account-' || i)::uuid,md5('load-wallet-' || i)::uuid,
       'LOAD-WALLET-NGN-' || lpad(i::text,3,'0'),'LIABILITY','NGN','ACTIVE',CURRENT_TIMESTAMP
FROM generate_series(1,20) AS i
ON CONFLICT(wallet_id) DO NOTHING;

INSERT INTO journal_transactions(id,reference,source_type,source_reference,currency,description,posted_at,created_at)
SELECT md5('load-opening-journal-' || i)::uuid,'JRN-LOAD-OPEN-' || lpad(i::text,3,'0'),
       'OPENING_BALANCE','LOAD-OPEN-' || lpad(i::text,3,'0'),'NGN','Load-test opening balance',
       CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM generate_series(1,20) AS i
ON CONFLICT(source_type,source_reference) DO NOTHING;

INSERT INTO journal_entries(id,journal_transaction_id,ledger_account_id,entry_sequence,entry_type,amount,currency,created_at)
SELECT md5('load-opening-debit-' || i)::uuid,md5('load-opening-journal-' || i)::uuid,
       (SELECT id FROM ledger_accounts WHERE account_code='PLATFORM-NGN-SETTLEMENT'),
       1,'DEBIT',1000.00,'NGN',CURRENT_TIMESTAMP
FROM generate_series(1,20) AS i
ON CONFLICT(journal_transaction_id,entry_sequence) DO NOTHING;

INSERT INTO journal_entries(id,journal_transaction_id,ledger_account_id,entry_sequence,entry_type,amount,currency,created_at)
SELECT md5('load-opening-credit-' || i)::uuid,md5('load-opening-journal-' || i)::uuid,
       md5('load-account-' || i)::uuid,2,'CREDIT',1000.00,'NGN',CURRENT_TIMESTAMP
FROM generate_series(1,20) AS i
ON CONFLICT(journal_transaction_id,entry_sequence) DO NOTHING;

COMMIT;

SELECT count(*) AS seeded_users FROM users WHERE email_normalized LIKE 'load-user-%@example.test';
