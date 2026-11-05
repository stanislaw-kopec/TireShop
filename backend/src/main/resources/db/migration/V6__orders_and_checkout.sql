CREATE TABLE checkout_quote (
    id uuid PRIMARY KEY,
    cart_id uuid NOT NULL REFERENCES shopping_cart(id),
    account_id uuid REFERENCES app_account(id),
    guest_token_hash varchar(64),
    access_expires_at timestamptz,
    expires_at timestamptz NOT NULL,
    snapshot jsonb NOT NULL,
    CHECK ((account_id IS NOT NULL AND guest_token_hash IS NULL AND access_expires_at IS NULL)
        OR (account_id IS NULL AND guest_token_hash IS NOT NULL AND access_expires_at IS NOT NULL))
);
CREATE TABLE customer_order (
    id uuid PRIMARY KEY,
    quote_id uuid NOT NULL UNIQUE REFERENCES checkout_quote(id),
    checkout_key uuid NOT NULL UNIQUE,
    account_id uuid REFERENCES app_account(id),
    guest_token_hash varchar(64),
    access_expires_at timestamptz,
    status varchar(30) NOT NULL CHECK (status IN ('PENDING_PAYMENT','PAID','PROCESSING','SHIPPED','DELIVERED','CANCELLED')),
    created_at timestamptz NOT NULL,
    reservation_expires_at timestamptz NOT NULL,
    cancellation_reason varchar(30) CHECK (cancellation_reason IN ('CUSTOMER','EXPIRED')),
    delivery jsonb NOT NULL,
    items_total_gross numeric(24,2) NOT NULL CHECK (items_total_gross > 0),
    shipping_gross numeric(12,2) NOT NULL CHECK (shipping_gross IN (0,20)),
    total_gross numeric(24,2) NOT NULL,
    currency varchar(3) NOT NULL CHECK (currency = 'PLN'),
    CHECK (total_gross = items_total_gross + shipping_gross),
    CHECK (reservation_expires_at = created_at + interval '15 minutes'),
    CHECK ((status = 'CANCELLED') = (cancellation_reason IS NOT NULL)),
    CHECK ((account_id IS NOT NULL AND guest_token_hash IS NULL AND access_expires_at IS NULL)
        OR (account_id IS NULL AND guest_token_hash IS NOT NULL AND access_expires_at IS NOT NULL))
);
CREATE TABLE order_item (
    order_id uuid NOT NULL REFERENCES customer_order(id),
    product_id uuid NOT NULL REFERENCES tire_product(id),
    product_snapshot jsonb NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price_gross numeric(12,2) NOT NULL CHECK (unit_price_gross > 0),
    line_total_gross numeric(24,2) NOT NULL CHECK (line_total_gross = quantity * unit_price_gross),
    PRIMARY KEY (order_id, product_id)
);
CREATE INDEX order_account_history_idx ON customer_order(account_id,created_at DESC,id);
CREATE INDEX order_pending_expiry_idx ON customer_order(reservation_expires_at,id) WHERE status='PENDING_PAYMENT';
