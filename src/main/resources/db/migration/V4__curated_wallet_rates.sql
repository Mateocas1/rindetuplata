-- T6b: wallet rates curated by hand live in the database (S12). V4 replaces the T3 classpath seed: the admin panel
-- writes these rows, CuratedWalletRateAdapter reads them, and saving or confirming publishes today's snapshot.
--
-- tiers and conditions keep the document shape of the retired seed, with TNA in percent and floor/ceiling in ARS:
--   tiers:      [{"floor": 10000, "ceiling": 1000000, "tnaPercent": 20}]
--   conditions: [{"condition": "Si ...", "tiers": [{"ceiling": null, "tnaPercent": 24}]}]
-- The adapter converts percent to fractions at the edge, exactly like every other source adapter.
-- floor is optional (absent = no floor); ceiling is optional (absent = no ceiling).

create table curated_wallet_rate (
    wallet_id   text primary key,
    name        text not null,
    tiers       jsonb not null,
    conditions  jsonb not null default '[]'::jsonb,
    note        text,
    verified_at date not null,
    updated_at  timestamptz not null,
    updated_by  text
);

-- Every admin write (create, replace, confirm): what changed, by whom and when. Newest first is a read concern,
-- so the query orders by at desc, id desc.
create table curated_wallet_rate_audit (
    id        bigserial primary key,
    wallet_id text not null,
    action    text not null,
    payload   jsonb not null,
    actor     text not null,
    at        timestamptz not null
);

-- The owner-verified Naranja X row of the retired classpath seed: 20% TNA up to $1.000.000, no conditions.
insert into curated_wallet_rate (wallet_id, name, tiers, conditions, note, verified_at, updated_at, updated_by)
values ('naranja-x',
        'Naranja X',
        '[{"ceiling": 1000000, "tnaPercent": 20}]'::jsonb,
        '[]'::jsonb,
        'Balance above the ceiling earns nothing. Manual activation. Owner-verified; matches TN, La Voz and Diario Huarpe. naranjax.com blocks automation.',
        date '2026-10-13',
        timestamptz '2026-10-13 00:00:00-03',
        'seed');
