-- Equity ledger: ownership is tracked in units; percentage = units / company.total_units.
-- Existing companies are converted at application startup by EquityBackfill (total_units = 0).
ALTER TABLE companies ADD COLUMN total_units bigint NOT NULL DEFAULT 0;
ALTER TABLE shares    ADD COLUMN units       bigint NOT NULL DEFAULT 0;

-- Stripe Connect: sellers receive share-sale proceeds in their own connected account.
ALTER TABLE users ADD COLUMN stripe_account_id varchar(255);
ALTER TABLE users ADD COLUMN payouts_enabled   boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD CONSTRAINT users_stripe_account_id_key UNIQUE (stripe_account_id);

ALTER TABLE stripe_share_purchases ADD COLUMN seller_account_id varchar(255);
