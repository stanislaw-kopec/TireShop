CREATE TABLE manufacturer (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL CHECK (length(trim(name)) > 0),
    normalized_name varchar(100) NOT NULL UNIQUE CHECK (length(normalized_name) > 0)
);

CREATE TABLE tire_product (
    id uuid PRIMARY KEY,
    manufacturer_id uuid NOT NULL REFERENCES manufacturer(id),
    model varchar(120) NOT NULL CHECK (length(trim(model)) > 0),
    normalized_model varchar(120) NOT NULL CHECK (length(normalized_model) > 0),
    width_mm integer NOT NULL CHECK (width_mm BETWEEN 100 AND 500),
    aspect_ratio integer NOT NULL CHECK (aspect_ratio BETWEEN 20 AND 100),
    rim_diameter_in integer NOT NULL CHECK (rim_diameter_in BETWEEN 10 AND 30),
    season varchar(20) NOT NULL CHECK (season IN ('SUMMER', 'WINTER', 'ALL_SEASON')),
    image_url varchar(2048),
    price_gross numeric(12,2) CHECK (price_gross > 0),
    on_sale boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT tire_variant_unique UNIQUE (manufacturer_id, normalized_model, width_mm, aspect_ratio, rim_diameter_in, season),
    CHECK (NOT on_sale OR price_gross IS NOT NULL)
);
CREATE INDEX tire_product_size_idx ON tire_product(width_mm, aspect_ratio, rim_diameter_in);
CREATE INDEX tire_product_offer_price_idx ON tire_product(price_gross, id) WHERE on_sale;

CREATE TABLE inventory (
    product_id uuid PRIMARY KEY REFERENCES tire_product(id),
    on_hand integer NOT NULL DEFAULT 0,
    reserved integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    CHECK (reserved >= 0 AND on_hand >= reserved)
);

CREATE TABLE inventory_change (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES tire_product(id),
    actor_account_id uuid NOT NULL REFERENCES app_account(id),
    type varchar(20) NOT NULL CHECK (type IN ('RECEIPT', 'CORRECTION')),
    quantity_delta integer NOT NULL CHECK (quantity_delta <> 0),
    reason varchar(500) NOT NULL CHECK (length(trim(reason)) > 0),
    on_hand_after integer NOT NULL,
    reserved_after integer NOT NULL,
    created_at timestamptz NOT NULL,
    CHECK (type <> 'RECEIPT' OR quantity_delta > 0),
    CHECK (reserved_after >= 0 AND on_hand_after >= reserved_after)
);
CREATE INDEX inventory_change_history_idx ON inventory_change(product_id, created_at DESC, id);
