CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_users_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_users_email_normalized CHECK (email_normalized = lower(btrim(email_normalized)))
);

CREATE TABLE roles (
    id SMALLINT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('CUSTOMER', 'ADMIN', 'SUPPORT'))
);

INSERT INTO roles (id, name) VALUES
    (1, 'CUSTOMER'),
    (2, 'ADMIN'),
    (3, 'SUPPORT');

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id SMALLINT NOT NULL REFERENCES roles(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    jwt_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    revocation_reason VARCHAR(30),
    replaced_by_token_id UUID REFERENCES refresh_tokens(id),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_refresh_tokens_jwt_id UNIQUE (jwt_id),
    CONSTRAINT uq_refresh_tokens_fingerprint UNIQUE (token_fingerprint),
    CONSTRAINT ck_refresh_tokens_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_refresh_tokens_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL AND revocation_reason IS NULL)
        OR (status <> 'ACTIVE' AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX ix_refresh_tokens_user_status ON refresh_tokens(user_id, status);
CREATE INDEX ix_refresh_tokens_family_status ON refresh_tokens(family_id, status);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens(expires_at);
