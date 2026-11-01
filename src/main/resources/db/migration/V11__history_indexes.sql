-- T4a (S4, L33): indexes for the history the backfill stores (a year of fund classes is hundreds of thousands of
-- rows). The primary keys of both tables lead with the instrument, so a predicate or an aggregate on the date alone,
-- such as the earliest stored day the monthly report looks up, had to scan the table. Indexes only: no data changes.

create index rate_snapshot_capture_date_idx on rate_snapshot (capture_date);

create index fund_quote_quote_date_idx on fund_quote (quote_date);
