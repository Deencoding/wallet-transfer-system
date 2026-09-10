CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    account_code VARCHAR(80) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_ledger_accounts_wallet UNIQUE (wallet_id),
    CONSTRAINT uq_ledger_accounts_code UNIQUE (account_code),
    CONSTRAINT ck_ledger_accounts_type CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    CONSTRAINT ck_ledger_accounts_currency CHECK (currency IN ('NGN')),
    CONSTRAINT ck_ledger_accounts_status CHECK (status IN ('ACTIVE','CLOSED'))
);

CREATE TABLE journal_transactions (
    id UUID PRIMARY KEY,
    reference VARCHAR(80) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_reference VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    reversal_of_journal_id UUID REFERENCES journal_transactions(id),
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_journal_reference UNIQUE (reference),
    CONSTRAINT uq_journal_source UNIQUE (source_type, source_reference),
    CONSTRAINT uq_journal_reversal UNIQUE (reversal_of_journal_id),
    CONSTRAINT ck_journal_source_type CHECK (source_type IN ('TRANSFER','REVERSAL','ADJUSTMENT','OPENING_BALANCE')),
    CONSTRAINT ck_journal_currency CHECK (currency IN ('NGN'))
);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    journal_transaction_id UUID NOT NULL REFERENCES journal_transactions(id),
    ledger_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    entry_sequence SMALLINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_journal_entry_sequence UNIQUE (journal_transaction_id, entry_sequence),
    CONSTRAINT ck_journal_entry_sequence CHECK (entry_sequence > 0),
    CONSTRAINT ck_journal_entry_type CHECK (entry_type IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_journal_entry_amount CHECK (amount > 0),
    CONSTRAINT ck_journal_entry_currency CHECK (currency IN ('NGN'))
);

CREATE INDEX ix_journal_entries_journal ON journal_entries(journal_transaction_id);
CREATE INDEX ix_journal_entries_account ON journal_entries(ledger_account_id);
CREATE INDEX ix_journal_posted_at ON journal_transactions(posted_at);

INSERT INTO ledger_accounts(id,wallet_id,account_code,account_type,currency,status,created_at)
SELECT gen_random_uuid(), id, 'WALLET-' || currency || '-' || replace(id::text,'-',''),
       'LIABILITY', currency, 'ACTIVE', CURRENT_TIMESTAMP
FROM wallets ON CONFLICT (wallet_id) DO NOTHING;

CREATE FUNCTION validate_balanced_journal() RETURNS trigger AS $$
DECLARE journal_id UUID; debit_total NUMERIC(19,2); credit_total NUMERIC(19,2); entry_count INTEGER; invalid_currency INTEGER;
BEGIN
  journal_id := COALESCE(NEW.journal_transaction_id, OLD.journal_transaction_id);
  IF NOT EXISTS (SELECT 1 FROM journal_transactions WHERE id=journal_id) THEN RETURN NULL; END IF;
  SELECT count(*), COALESCE(sum(amount) FILTER (WHERE entry_type='DEBIT'),0),
         COALESCE(sum(amount) FILTER (WHERE entry_type='CREDIT'),0),
         count(*) FILTER (WHERE e.currency<>j.currency OR a.currency<>j.currency)
  INTO entry_count,debit_total,credit_total,invalid_currency
  FROM journal_entries e JOIN journal_transactions j ON j.id=e.journal_transaction_id
  JOIN ledger_accounts a ON a.id=e.ledger_account_id WHERE e.journal_transaction_id=journal_id;
  IF entry_count < 2 OR debit_total <> credit_total OR invalid_currency > 0 THEN
    RAISE EXCEPTION 'journal % is unbalanced or has invalid currency', journal_id;
  END IF;
  RETURN NULL;
END; $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER journal_balance_guard AFTER INSERT ON journal_entries
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_balanced_journal();

CREATE FUNCTION reject_ledger_mutation() RETURNS trigger AS $$ BEGIN
  RAISE EXCEPTION 'posted ledger records are immutable';
END; $$ LANGUAGE plpgsql;
CREATE TRIGGER immutable_journal_entries BEFORE UPDATE OR DELETE ON journal_entries
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
CREATE TRIGGER immutable_journal_transactions BEFORE UPDATE OR DELETE ON journal_transactions
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
