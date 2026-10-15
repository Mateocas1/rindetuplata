-- T6c: user alert rules, their edge-triggered matches and the transactional outbox (S3, S4, S13).
--
-- alert_rule is per user (app_user of V3); kind is null for "any kind" and instrument_id narrows a rule to one
-- instrument. threshold_rate is a fraction (the API takes a percent and divides by 100).
--
-- alert_match is the edge-trigger state: one row per (rule, instrument) currently above the threshold, written on
-- the first evaluation that saw it and deleted when the instrument drops below, which re-arms the rule. The primary
-- key is exactly the design note's (rule_id, instrument_id); matched_on is the date it fired.
--
-- outbox_event is the transactional outbox: the alert evaluation inserts the row in the same transaction as the
-- match, and the T6d relay publishes it and sets published_at. The partial index keeps the relay's scan cheap.

create table alert_rule (
    id bigserial primary key,
    user_id bigint not null references app_user (id) on delete cascade,
    kind text,
    instrument_id bigint references instrument (id),
    threshold_rate numeric not null,
    active boolean not null default true,
    created_at timestamptz not null,
    constraint alert_rule_kind check (kind is null or kind in ('PLAZO_FIJO', 'WALLET', 'FUND', 'CRYPTO')),
    constraint alert_rule_threshold check (threshold_rate > 0 and threshold_rate <= 3)
);

create index alert_rule_user on alert_rule (user_id);

create table alert_match (
    rule_id bigint not null references alert_rule (id) on delete cascade,
    instrument_id bigint not null references instrument (id),
    matched_on date not null,
    primary key (rule_id, instrument_id)
);

create index alert_match_instrument on alert_match (instrument_id);

create table outbox_event (
    id uuid primary key,
    event_type text not null,
    aggregate_id text not null,
    payload jsonb not null,
    created_at timestamptz not null,
    published_at timestamptz,
    attempts integer not null default 0,
    last_error text
);

create index outbox_event_pending on outbox_event (created_at) where published_at is null;
