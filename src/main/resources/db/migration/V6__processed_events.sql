-- T6d: idempotence of the alert email consumer (S4, S13).
--
-- The relay publishes at least once, so the same AlertTriggered event can arrive twice (a redelivery after a
-- rejected message, or a publish repeated after a crash). The consumer records the event here only after the email
-- was sent: a row present means that email already went out, so a redelivery sends nothing.

create table processed_event (
    event_id uuid primary key,
    processed_at timestamptz not null
);
