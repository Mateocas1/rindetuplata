-- T6d: API keys with plan limits (S6, L32b).
--
-- A key is `rtp_` + 32 random bytes in base64url, shown once at creation. The table keeps only the lower-case hex
-- SHA-256 of the whole key (key_hash, unique: the lookup column) and its first ten characters (key_prefix, what the
-- owner sees in the list). The plaintext is never stored. A revoked key keeps its row (revoked_at) and stops working
-- at once, because every keyed request looks the hash up with `revoked_at is null`.
--
-- plan is FREE for every key a user creates; the operator promotes a key to PARTNER by hand:
--   update api_key set plan = 'PARTNER' where id = <id>;
-- The limits of each plan are configuration (rindetuplata.api.plans.*), not data.

create table api_key (
    id bigserial primary key,
    user_id bigint not null references app_user (id) on delete cascade,
    name text,
    key_prefix text not null,
    key_hash text not null,
    plan text not null default 'FREE',
    created_at timestamptz not null,
    revoked_at timestamptz,
    constraint api_key_hash_unique unique (key_hash),
    constraint api_key_hash_shape check (key_hash ~ '^[0-9a-f]{64}$'),
    constraint api_key_plan check (plan in ('FREE', 'PARTNER'))
);

create index api_key_user on api_key (user_id) where revoked_at is null;
