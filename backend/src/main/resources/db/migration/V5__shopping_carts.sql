CREATE TABLE shopping_cart (
    id uuid PRIMARY KEY,
    account_id uuid UNIQUE REFERENCES app_account(id),
    guest_token_hash varchar(64) UNIQUE,
    expires_at timestamptz,
    merged_account_id uuid REFERENCES app_account(id),
    created_at timestamptz NOT NULL,
    CHECK ((account_id IS NOT NULL AND guest_token_hash IS NULL AND expires_at IS NULL AND merged_account_id IS NULL)
        OR (account_id IS NULL AND guest_token_hash IS NOT NULL AND expires_at IS NOT NULL))
);
CREATE TABLE cart_item (
    cart_id uuid NOT NULL REFERENCES shopping_cart(id),
    product_id uuid NOT NULL REFERENCES tire_product(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (cart_id, product_id)
);
CREATE INDEX shopping_cart_expiry_idx ON shopping_cart(expires_at) WHERE account_id IS NULL;
