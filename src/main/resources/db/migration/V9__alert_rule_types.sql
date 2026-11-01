-- T6c: alert rule types (S6, L32 a).
--
-- alert_rule gains a type next to today's THRESHOLD rule. Every row stored before this migration becomes THRESHOLD
-- through the column default, so its kind, instrument, threshold and alert_match state behave exactly as before.
-- The other three types carry no threshold (threshold_rate becomes nullable), and one check ties the fields of a
-- row to its type, so the table refuses a combination the API would never write:
--   THRESHOLD     threshold required (0 < t <= 3, the old range), kind and instrument optional
--   BEST_CHANGED  optional kind, no instrument, no threshold
--   RATE_DROP     instrument required (a plazo fijo or a wallet) and the kind it belongs to, no threshold
--   WEEKLY_DIGEST optional kind, no instrument, no threshold

alter table alert_rule add column type text not null default 'THRESHOLD';

alter table alert_rule alter column threshold_rate drop not null;
alter table alert_rule drop constraint alert_rule_threshold;

alter table alert_rule add constraint alert_rule_type
    check (type in ('THRESHOLD', 'BEST_CHANGED', 'RATE_DROP', 'WEEKLY_DIGEST'));

alter table alert_rule add constraint alert_rule_shape check (
    (type = 'THRESHOLD'
        and threshold_rate is not null and threshold_rate > 0 and threshold_rate <= 3)
    or (type = 'BEST_CHANGED'
        and threshold_rate is null and instrument_id is null)
    or (type = 'RATE_DROP'
        and threshold_rate is null and instrument_id is not null and kind in ('PLAZO_FIJO', 'WALLET'))
    or (type = 'WEEKLY_DIGEST'
        and threshold_rate is null and instrument_id is null)
);

-- What BEST_CHANGED and RATE_DROP remember from the previous evaluation, one row per rule.
--   BEST_CHANGED: subject_key is the top of the rule's filter ('id:<instrument>' or 'fund:<normalized fund name>',
--                 so a change of class inside one fund is not a change), subject_name its display name.
--   RATE_DROP:    subject_key is 'id:<instrument>', rate the TNA the ranking used for it; the stored rate moves on
--                 every evaluation that ranks the instrument.
-- observed_on is the data date of the evaluation that wrote the row.
create table alert_state (
    rule_id bigint primary key references alert_rule (id) on delete cascade,
    subject_key text not null,
    subject_name text not null,
    rate numeric,
    observed_on date not null
);

-- WEEKLY_DIGEST: one row per digest sent. The primary key is the guarantee that a rule gets at most one digest per
-- ISO week (week_start is the Monday, in the Argentine calendar): the evaluation inserts the row and the outbox
-- event in one transaction, and a re-run of the same week conflicts on the key and sends nothing. sent_for is the
-- data date that triggered it; top_key/top_name are the top of the digest, which the next digest compares against.
create table alert_digest (
    rule_id bigint not null references alert_rule (id) on delete cascade,
    week_start date not null,
    sent_for date not null,
    top_key text not null,
    top_name text not null,
    primary key (rule_id, week_start)
);
