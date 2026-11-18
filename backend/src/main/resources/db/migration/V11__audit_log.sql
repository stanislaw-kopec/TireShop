CREATE TABLE audit_log (
    id uuid PRIMARY KEY,
    event_type varchar(30) NOT NULL CHECK (event_type IN ('ORDER_CREATED', 'ORDER_CANCELLED')),
    occurred_at timestamptz NOT NULL,
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX audit_log_recent_idx ON audit_log (occurred_at DESC, id DESC);
CREATE INDEX audit_log_type_recent_idx ON audit_log (event_type, occurred_at DESC, id DESC);
