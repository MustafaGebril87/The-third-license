-- V1: Full initial schema
-- Generated to match Hibernate entity mappings (Spring Boot 3.2 / Hibernate 6)

-- ── users ─────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id              uuid            NOT NULL,
    username        varchar(50)     NOT NULL,
    email           varchar(100)    NOT NULL,
    password        varchar(255)    NOT NULL,
    full_name       varchar(255),
    balance         numeric(19, 4)  NOT NULL DEFAULT 0,
    shares_owned    integer         NOT NULL DEFAULT 0,
    reputation_score integer        NOT NULL DEFAULT 0,
    date_joined     timestamp       NOT NULL,
    last_login      timestamp,
    coin_balance    float8,
    CONSTRAINT users_pkey           PRIMARY KEY (id),
    CONSTRAINT users_username_key   UNIQUE (username),
    CONSTRAINT users_email_key      UNIQUE (email)
);

-- ── user_roles (ElementCollection) ───────────────────────────────────────────
CREATE TABLE user_roles (
    user_id uuid        NOT NULL,
    role    varchar(255),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- ── companies ─────────────────────────────────────────────────────────────────
CREATE TABLE companies (
    id       uuid         NOT NULL,
    name     varchar(255) NOT NULL,
    owner_id uuid         NOT NULL,
    CONSTRAINT companies_pkey     PRIMARY KEY (id),
    CONSTRAINT companies_name_key UNIQUE (name),
    CONSTRAINT fk_companies_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

-- ── company_users (ManyToMany join table) ────────────────────────────────────
CREATE TABLE company_users (
    company_id uuid NOT NULL,
    user_id    uuid NOT NULL,
    CONSTRAINT fk_company_users_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_company_users_user    FOREIGN KEY (user_id)    REFERENCES users (id)
);

-- ── repositories ──────────────────────────────────────────────────────────────
CREATE TABLE repositories (
    id         uuid         NOT NULL,
    name       varchar(255),
    git_url    varchar(255),
    statue     varchar(255),
    company_id uuid,
    owner_id   uuid         NOT NULL,
    CONSTRAINT repositories_pkey          PRIMARY KEY (id),
    CONSTRAINT fk_repositories_company   FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_repositories_owner     FOREIGN KEY (owner_id)   REFERENCES users (id)
);

-- ── user_contributions (ManyToMany join table on User) ────────────────────────
CREATE TABLE user_contributions (
    user_id       uuid NOT NULL,
    repository_id uuid NOT NULL,
    CONSTRAINT fk_user_contributions_user       FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT fk_user_contributions_repository FOREIGN KEY (repository_id) REFERENCES repositories (id)
);

-- ── contributions ─────────────────────────────────────────────────────────────
CREATE TABLE contributions (
    id                   uuid         NOT NULL,
    repository_id        uuid         NOT NULL,
    contributor_id       uuid,
    code_size            integer,
    approved             boolean,
    contribution_date    timestamp,
    filename             varchar(255),
    branch               varchar(255) NOT NULL,
    message              varchar(255),
    status               varchar(255) NOT NULL,
    original_commit_hash varchar(255),
    new_commit_hash      varchar(255),
    modified_code_size   integer      NOT NULL DEFAULT 0,
    CONSTRAINT contributions_pkey          PRIMARY KEY (id),
    CONSTRAINT fk_contributions_repository FOREIGN KEY (repository_id)  REFERENCES repositories (id),
    CONSTRAINT fk_contributions_user       FOREIGN KEY (contributor_id) REFERENCES users (id)
);

-- ── shares ────────────────────────────────────────────────────────────────────
CREATE TABLE shares (
    id          uuid           NOT NULL,
    company_id  uuid,
    owner_id    uuid           NOT NULL,
    percentage  float8,
    is_for_sale boolean        NOT NULL DEFAULT false,
    price       numeric(38, 2),
    CONSTRAINT shares_pkey        PRIMARY KEY (id),
    CONSTRAINT fk_shares_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_shares_owner    FOREIGN KEY (owner_id)   REFERENCES users (id)
);

-- ── merge_request ─────────────────────────────────────────────────────────────
CREATE TABLE merge_request (
    id            uuid         NOT NULL,
    repository_id uuid,
    branch        varchar(255),
    file_path     varchar(255),
    initiator_id  uuid,
    status        varchar(255),
    created_at    timestamp,
    CONSTRAINT merge_request_pkey          PRIMARY KEY (id),
    CONSTRAINT fk_merge_request_repository FOREIGN KEY (repository_id) REFERENCES repositories (id),
    CONSTRAINT fk_merge_request_initiator  FOREIGN KEY (initiator_id)  REFERENCES users (id)
);

-- ── refresh_tokens ────────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          uuid         NOT NULL,
    user_id     uuid,
    token       varchar(255) NOT NULL,
    expiry_date timestamp    NOT NULL,
    CONSTRAINT refresh_tokens_pkey      PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_key UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user   FOREIGN KEY (user_id) REFERENCES users (id)
);

-- ── revoked_token ─────────────────────────────────────────────────────────────
CREATE TABLE revoked_token (
    token      varchar(255) NOT NULL,
    revoked_at timestamp,
    CONSTRAINT revoked_token_pkey PRIMARY KEY (token)
);

-- ── access_request ────────────────────────────────────────────────────────────
CREATE TABLE access_request (
    id            uuid         NOT NULL,
    user_id       uuid         NOT NULL,
    repository_id uuid         NOT NULL,
    status        varchar(255),
    CONSTRAINT access_request_pkey          PRIMARY KEY (id),
    CONSTRAINT fk_access_request_user       FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT fk_access_request_repository FOREIGN KEY (repository_id) REFERENCES repositories (id)
);

-- ── repository_access ─────────────────────────────────────────────────────────
CREATE TABLE repository_access (
    id            bigint       GENERATED BY DEFAULT AS IDENTITY,
    user_id       uuid         NOT NULL,
    repository_id uuid         NOT NULL,
    access_level  varchar(255) NOT NULL DEFAULT 'CONTRIBUTOR',
    granted_at    timestamp    NOT NULL,
    CONSTRAINT repository_access_pkey          PRIMARY KEY (id),
    CONSTRAINT fk_repository_access_user       FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT fk_repository_access_repository FOREIGN KEY (repository_id) REFERENCES repositories (id)
);

-- ── transactions ──────────────────────────────────────────────────────────────
CREATE TABLE transactions (
    id               uuid    NOT NULL,
    buyer_id         uuid    NOT NULL,
    seller_id        uuid    NOT NULL,
    share_id         uuid    NOT NULL,
    quantity         integer NOT NULL,
    price_per_share  float8  NOT NULL,
    total_amount     float8  NOT NULL,
    transaction_time timestamp NOT NULL,
    CONSTRAINT transactions_pkey        PRIMARY KEY (id),
    CONSTRAINT fk_transactions_buyer    FOREIGN KEY (buyer_id)  REFERENCES users (id),
    CONSTRAINT fk_transactions_seller   FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_share    FOREIGN KEY (share_id)  REFERENCES shares (id)
);

-- ── stripe_share_purchases ────────────────────────────────────────────────────
CREATE TABLE stripe_share_purchases (
    id                uuid         NOT NULL,
    stripe_session_id varchar(255) NOT NULL,
    share_id          uuid         NOT NULL,
    buyer_id          uuid         NOT NULL,
    seller_id         uuid         NOT NULL,
    price_usd         numeric(38, 2) NOT NULL,
    status            varchar(255) NOT NULL,
    created_at        timestamp    NOT NULL,
    CONSTRAINT stripe_share_purchases_pkey            PRIMARY KEY (id),
    CONSTRAINT stripe_share_purchases_session_id_key  UNIQUE (stripe_session_id)
);
