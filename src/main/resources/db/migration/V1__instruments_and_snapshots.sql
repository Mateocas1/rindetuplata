-- Domain schema of T4a: instruments, their daily snapshots, and the record of what each source produced.
-- One instrument per (kind, source_id, natural_key); its history is keyed by the source's own dates.

create table instrument (
    id           bigserial primary key,
    kind         text not null,
    source_id    text not null,
    natural_key  text not null,
    display_name text not null,
    entity_name  text,
    segment      text,
    first_seen   date not null,
    last_seen    date not null,
    unique (kind, source_id, natural_key)
);

-- Plazo fijo, crypto, bank and wallet rates: one row per instrument and capture date.
-- rate = official TEA, bank non-client TNA, crypto APY, wallet base TNA (first unconditional tier).
-- client_rate = bank client TNA. details = terms / tiers / conditions as given.
create table rate_snapshot (
    instrument_id  bigint not null references instrument (id),
    capture_date   date not null,
    data_date      date,
    basis          text not null,
    rate           numeric,
    client_rate    numeric,
    minimum_amount numeric,
    details        jsonb,
    confidence     text not null,
    verified_at    date,
    fetched_at     timestamptz not null,
    flags          jsonb not null,
    invalid        boolean not null default false,
    primary key (instrument_id, capture_date)
);

-- Fund classes: a time series keyed by the source's own quote date.
create table fund_quote (
    instrument_id bigint not null references instrument (id),
    quote_date    date not null,
    vcp           numeric not null,
    ccp           numeric,
    patrimonio    numeric,
    horizon       text,
    fetched_at    timestamptz not null,
    confidence    text not null,
    flags         jsonb not null,
    invalid       boolean not null default false,
    primary key (instrument_id, quote_date)
);

create table macro_observation (
    variable         text not null,
    observation_date date not null,
    source_id        text not null,
    value            numeric not null,
    fetched_at       timestamptz not null,
    confidence       text not null,
    flags            jsonb not null,
    primary key (variable, observation_date, source_id)
);

create table dollar_quote (
    source_id    text not null,
    capture_date date not null,
    buy          numeric not null,
    sell         numeric not null,
    updated_at   timestamptz,
    fetched_at   timestamptz not null,
    confidence   text not null,
    flags        jsonb not null,
    primary key (source_id, capture_date)
);

-- What each source produced for a capture date: OK, DEGRADED, FAILED or MISSING_IN_ARCHIVE.
create table source_run (
    capture_date date not null,
    source_id    text not null,
    mode         text not null,
    status       text not null,
    rows         int not null,
    failure      text,
    recorded_at  timestamptz not null,
    primary key (capture_date, source_id)
);
