-- Closing vouchers for the storefront chatbot (see docs/vouchers/VOUCHERS.md).
-- Additive only: new tables, nothing existing is touched. voucher_settings is
-- seeded with enabled = FALSE, so this migration changes no behaviour.

-- Single settings row (id = 1). Every limit here is enforced in code
-- (VoucherService); nothing typed in chat can change it.
CREATE TABLE IF NOT EXISTS voucher_settings (
    id                   BIGINT        PRIMARY KEY,
    enabled              BOOLEAN       NOT NULL DEFAULT FALSE,
    daily_pool           NUMERIC(10,2) NOT NULL DEFAULT 50.00,
    max_pct              NUMERIC(5,4)  NOT NULL DEFAULT 0.1000,
    max_amount           NUMERIC(10,2) NOT NULL DEFAULT 10.00,
    min_subtotal         NUMERIC(10,2) NOT NULL DEFAULT 150.00,
    validity_minutes     INTEGER       NOT NULL DEFAULT 60,
    holdout_pct          INTEGER       NOT NULL DEFAULT 30,
    max_per_ip_per_day   INTEGER       NOT NULL DEFAULT 1,
    -- Ask for the checkout email and lock the Shopify code to that customer.
    require_email        BOOLEAN       NOT NULL DEFAULT TRUE,
    -- {"vendors":[],"productTypes":[],"tags":[]} (case-insensitive match)
    exclusions           TEXT          NOT NULL DEFAULT '{"vendors":[],"productTypes":[],"tags":[]}',
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(255)
);

INSERT INTO voucher_settings (id, enabled) VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;

-- One row per Melbourne calendar day. reserved = value of codes currently
-- ISSUED or REDEEMED from that day's pool. Reservation is a single
-- conditional UPDATE (reserved + amount <= limit), so the pool can't go
-- negative under concurrent issues.
CREATE TABLE IF NOT EXISTS voucher_pool_days (
    pool_date  DATE          PRIMARY KEY,
    reserved   NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (reserved >= 0)
);

-- Every code ever created. status: PENDING (reserved, Shopify call in
-- flight), ISSUED, REDEEMED, EXPIRED (unused, deleted, value returned),
-- REVOKED (admin cancelled, value returned), FAILED (never created, value returned).
CREATE TABLE IF NOT EXISTS voucher_ledger (
    id                   BIGSERIAL     PRIMARY KEY,
    code                 VARCHAR(64)   NOT NULL UNIQUE,
    amount               NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    pool_date            DATE          NOT NULL,
    session_id           VARCHAR(255)  NOT NULL,
    customer_key         VARCHAR(128),
    -- Shopify customer the code is locked to (null when require_email is off).
    shopify_customer_id  VARCHAR(255),
    ip_hash              VARCHAR(128),
    cart_subtotal        NUMERIC(10,2) NOT NULL,
    min_subtotal         NUMERIC(10,2) NOT NULL,
    reason               VARCHAR(500),
    status               VARCHAR(20)   NOT NULL,
    shopify_discount_id  VARCHAR(255),
    order_id             VARCHAR(255),
    order_total          NUMERIC(10,2),
    issued_at            TIMESTAMP     NOT NULL,
    expires_at           TIMESTAMP     NOT NULL,
    resolved_at          TIMESTAMP,
    reconcile_attempts   INTEGER       NOT NULL DEFAULT 0,
    last_error           VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_voucher_ledger_status_expires ON voucher_ledger(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_voucher_ledger_pool_date ON voucher_ledger(pool_date);
CREATE INDEX IF NOT EXISTS idx_voucher_ledger_ip_day ON voucher_ledger(ip_hash, pool_date);

-- Uniqueness claims: "s:<session>" (one code per chat session, ever) and
-- "c:<day>:<customer hash>" (one per customer per day). The primary key makes
-- the limit race-safe; FAILED issues delete their claims.
CREATE TABLE IF NOT EXISTS voucher_claims (
    claim_key   VARCHAR(255) PRIMARY KEY,
    ledger_id   BIGINT       NOT NULL REFERENCES voucher_ledger(id) ON DELETE CASCADE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Owner overrides (X-Owner-Key). Temporary and audited; never deleted.
-- type: POOL_RAISE (override_value = extra AUD for pool_date), SESSION_AMOUNT
-- (override_value = max AUD for target session), PAUSE.
CREATE TABLE IF NOT EXISTS voucher_overrides (
    id          BIGSERIAL     PRIMARY KEY,
    created_by  VARCHAR(255)  NOT NULL,
    type        VARCHAR(30)   NOT NULL,
    target      VARCHAR(255),
    override_value NUMERIC(10,2),
    pool_date   DATE,
    reason      VARCHAR(500)  NOT NULL,
    source_ip   VARCHAR(64),
    expires_at  TIMESTAMP     NOT NULL,
    revoked_at  TIMESTAMP,
    revoked_by  VARCHAR(255),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_voucher_overrides_active ON voucher_overrides(type, expires_at);

-- One row per offer_discount call, for the daily report (offered / holdout /
-- refusal reasons). holdout = session was in the no-offer control group.
CREATE TABLE IF NOT EXISTS voucher_offers (
    id              BIGSERIAL     PRIMARY KEY,
    session_id      VARCHAR(255),
    pool_date       DATE          NOT NULL,
    cart_subtotal   NUMERIC(10,2),
    requested       NUMERIC(10,2),
    outcome         VARCHAR(30)   NOT NULL,
    eligible        BOOLEAN       NOT NULL DEFAULT FALSE,
    holdout         BOOLEAN       NOT NULL DEFAULT FALSE,
    ledger_id       BIGINT,
    reason          VARCHAR(500),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_voucher_offers_day ON voucher_offers(pool_date);
