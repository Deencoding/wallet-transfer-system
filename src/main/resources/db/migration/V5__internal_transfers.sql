ALTER TABLE ledger_accounts ALTER COLUMN wallet_id DROP NOT NULL;
ALTER TABLE ledger_accounts ADD CONSTRAINT ck_ledger_account_ownership CHECK (
  (account_type='LIABILITY' AND wallet_id IS NOT NULL) OR
  (account_type<>'LIABILITY' AND wallet_id IS NULL)
);
INSERT INTO ledger_accounts(id,wallet_id,account_code,account_type,currency,status,created_at)
VALUES(gen_random_uuid(),NULL,'PLATFORM-NGN-SETTLEMENT','ASSET','NGN','ACTIVE',CURRENT_TIMESTAMP)
ON CONFLICT(account_code) DO NOTHING;

CREATE TABLE transfers (
 id UUID PRIMARY KEY,
 reference VARCHAR(80) NOT NULL,
 sender_wallet_id UUID NOT NULL REFERENCES wallets(id),
 receiver_wallet_id UUID NOT NULL REFERENCES wallets(id),
 amount NUMERIC(19,2) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 description VARCHAR(255),
 status VARCHAR(20) NOT NULL,
 failure_reason VARCHAR(50),
 idempotency_key VARCHAR(255),
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 completed_at TIMESTAMP WITH TIME ZONE,
 CONSTRAINT uq_transfers_reference UNIQUE(reference),
 CONSTRAINT ck_transfers_different_wallets CHECK(sender_wallet_id<>receiver_wallet_id),
 CONSTRAINT ck_transfers_amount CHECK(amount>0),
 CONSTRAINT ck_transfers_currency CHECK(currency IN('NGN')),
 CONSTRAINT ck_transfers_status CHECK(status IN('PENDING','PROCESSING','SUCCESSFUL','FAILED','REVERSED')),
 CONSTRAINT ck_transfers_failure CHECK((status='FAILED' AND failure_reason IS NOT NULL) OR (status<>'FAILED' AND failure_reason IS NULL)),
 CONSTRAINT ck_transfers_completion CHECK((status IN('SUCCESSFUL','FAILED','REVERSED') AND completed_at IS NOT NULL) OR (status IN('PENDING','PROCESSING') AND completed_at IS NULL))
);
CREATE INDEX ix_transfers_sender_created ON transfers(sender_wallet_id,created_at DESC);
CREATE INDEX ix_transfers_receiver_created ON transfers(receiver_wallet_id,created_at DESC);
CREATE INDEX ix_transfers_status_created ON transfers(status,created_at);
