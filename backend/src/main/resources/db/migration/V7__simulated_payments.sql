ALTER TABLE customer_order ADD COLUMN paid_at timestamptz;

CREATE TABLE payment_attempt (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES customer_order(id),
    request_key uuid NOT NULL,
    amount_gross numeric(24,2) NOT NULL CHECK (amount_gross > 0),
    currency varchar(3) NOT NULL CHECK (currency = 'PLN'),
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','FAILED','CANCELLED')),
    created_at timestamptz NOT NULL,
    finished_at timestamptz,
    reason varchar(30),
    UNIQUE (order_id,request_key),
    CHECK ((status='PENDING' AND finished_at IS NULL AND reason IS NULL)
        OR (status<>'PENDING' AND finished_at IS NOT NULL AND reason IS NOT NULL))
);
CREATE UNIQUE INDEX payment_one_pending ON payment_attempt(order_id) WHERE status='PENDING';
CREATE UNIQUE INDEX payment_one_success ON payment_attempt(order_id) WHERE status='SUCCEEDED';
CREATE INDEX payment_history_idx ON payment_attempt(order_id,created_at DESC,id);

ALTER TABLE inventory_change DROP CONSTRAINT inventory_change_type_check;
ALTER TABLE inventory_change ADD CONSTRAINT inventory_change_type_check CHECK (type IN ('RECEIPT','CORRECTION','SALE'));
ALTER TABLE inventory_change ALTER COLUMN actor_account_id DROP NOT NULL;
ALTER TABLE inventory_change ADD COLUMN order_id uuid;
ALTER TABLE inventory_change ADD CONSTRAINT inventory_sale_item_fk FOREIGN KEY(order_id,product_id) REFERENCES order_item(order_id,product_id);
ALTER TABLE inventory_change ADD CONSTRAINT inventory_sale_once UNIQUE(order_id,product_id);
ALTER TABLE inventory_change ADD CONSTRAINT inventory_change_source CHECK (
    (type='SALE' AND order_id IS NOT NULL AND actor_account_id IS NULL AND quantity_delta<0)
    OR (type<>'SALE' AND order_id IS NULL AND actor_account_id IS NOT NULL)
);
