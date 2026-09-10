CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    available_balance NUMERIC(19,2) NOT NULL,
    ledger_balance NUMERIC(19,2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_wallets_owner_currency UNIQUE (owner_id, currency),
    CONSTRAINT ck_wallets_currency CHECK (currency IN ('NGN')),
    CONSTRAINT ck_wallets_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_wallets_available_non_negative CHECK (available_balance >= 0),
    CONSTRAINT ck_wallets_ledger_non_negative CHECK (ledger_balance >= 0),
    CONSTRAINT ck_wallets_available_not_above_ledger CHECK (available_balance <= ledger_balance)
);

CREATE INDEX ix_wallets_owner_id ON wallets(owner_id);
CREATE INDEX ix_wallets_status ON wallets(status);

INSERT INTO wallets (id, owner_id, currency, status, available_balance, ledger_balance, created_at, updated_at)
SELECT gen_random_uuid(), u.id, 'NGN', 'ACTIVE', 0.00, 0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
WHERE r.name = 'CUSTOMER'
ON CONFLICT (owner_id, currency) DO NOTHING;
