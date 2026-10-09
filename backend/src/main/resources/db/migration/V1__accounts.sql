CREATE TABLE app_account (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    password_hash varchar(255),
    role varchar(20) NOT NULL CHECK (role IN ('CUSTOMER', 'EMPLOYEE', 'ADMIN')),
    status varchar(20) NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED')),
    first_name varchar(100) NOT NULL,
    last_name varchar(100) NOT NULL,
    phone varchar(30),
    auth_version bigint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (email = lower(trim(email))),
    CHECK (status = 'INVITED' OR password_hash IS NOT NULL)
);

CREATE TABLE account_address (
    account_id uuid PRIMARY KEY REFERENCES app_account(id),
    recipient_name varchar(200) NOT NULL,
    street varchar(200) NOT NULL,
    building_number varchar(20) NOT NULL,
    apartment_number varchar(20),
    postal_code varchar(20) NOT NULL,
    city varchar(100) NOT NULL,
    country_code varchar(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE account_action_token (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES app_account(id),
    purpose varchar(30) NOT NULL CHECK (purpose IN ('INVITATION', 'PASSWORD_RESET')),
    token_hash varchar(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    CHECK (expires_at > created_at)
);
CREATE INDEX account_action_token_account_idx ON account_action_token(account_id);
CREATE INDEX account_action_token_expiry_idx ON account_action_token(expires_at);
