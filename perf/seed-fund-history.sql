-- Back-fills the fund history a production database has and one startup ingestion does not (it stores only the
-- latest and the previous quote). For every fund class quoted on the newest date, adds one SYNTHETIC quote per
-- business day in the 21 days before its latest one, on the geometric curve through its two real quotes. It is
-- the volume FundEvaluator and fundSeries() read in steady state; the values are not real data.
insert into fund_quote (instrument_id, quote_date, vcp, ccp, patrimonio, horizon, fetched_at, confidence, flags, invalid)
with ranked as (
    select f.*, row_number() over (partition by f.instrument_id order by f.quote_date desc) as rn
    from fund_quote f
),
latest as (select * from ranked where rn = 1),
previous as (select * from ranked where rn = 2)
select l.instrument_id,
       d::date,
       round(l.vcp * power(p.vcp / l.vcp, (l.quote_date - d::date)::numeric / greatest(l.quote_date - p.quote_date, 1)), 3),
       l.ccp, l.patrimonio, l.horizon, l.fetched_at, l.confidence, '[]'::jsonb, false
from latest l
join previous p on p.instrument_id = l.instrument_id
cross join generate_series(l.quote_date - 21, l.quote_date - 1, interval '1 day') d
where l.quote_date >= (select max(quote_date) from fund_quote) - 7
  and extract(isodow from d) < 6
  and d::date < p.quote_date
on conflict do nothing;
