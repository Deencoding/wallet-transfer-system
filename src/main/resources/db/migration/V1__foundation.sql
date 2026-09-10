CREATE TABLE schema_metadata (
    id SMALLINT PRIMARY KEY,
    description VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_schema_metadata_singleton CHECK (id = 1)
);

INSERT INTO schema_metadata (id, description)
VALUES (1, 'Wallet transfer system schema initialized');
