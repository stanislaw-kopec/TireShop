ALTER TABLE customer_order ADD COLUMN tracking_number varchar(100);

CREATE TABLE order_access_token (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES customer_order(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    initial boolean NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    screen_ciphertext bytea,
    screen_expires_at timestamptz,
    CHECK (initial OR screen_ciphertext IS NULL),
    CHECK (screen_ciphertext IS NULL OR screen_expires_at IS NOT NULL)
);
CREATE UNIQUE INDEX order_initial_tracking_link ON order_access_token(order_id) WHERE initial;
CREATE INDEX order_access_token_order_idx ON order_access_token(order_id);

CREATE TABLE order_email_outbox (
    id uuid PRIMARY KEY REFERENCES order_access_token(id),
    payload bytea,
    status varchar(10) NOT NULL CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    next_attempt_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    finished_at timestamptz,
    CHECK ((status='PENDING' AND payload IS NOT NULL AND finished_at IS NULL AND attempts<5)
        OR (status<>'PENDING' AND payload IS NULL AND finished_at IS NOT NULL))
);
CREATE INDEX order_mail_pending_idx ON order_email_outbox(next_attempt_at,id) WHERE status='PENDING';

CREATE TABLE tracking_request_limit (
    key_hash varchar(64) PRIMARY KEY,
    window_start timestamptz NOT NULL,
    requests integer NOT NULL CHECK (requests > 0)
);
CREATE INDEX tracking_limit_cleanup_idx ON tracking_request_limit(window_start);
