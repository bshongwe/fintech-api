CREATE TABLE IF NOT EXISTS account (
    account_id UUID PRIMARY KEY,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19,2) NOT NULL
);

INSERT INTO account (account_id, currency, balance)
VALUES ('11111111-1111-1111-1111-111111111111','ZAR', 1000.00)
ON CONFLICT (account_id) DO NOTHING;
