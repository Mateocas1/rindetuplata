-- The upstream Spring Security and Spring Session schemas (V3) cap the principal at 50 and 100 characters, but an
-- email address may hold up to 254 (Emails.isValid). Widening is non-destructive: existing rows keep their values.
alter table one_time_tokens alter column username type varchar(254);
alter table SPRING_SESSION alter column PRINCIPAL_NAME type varchar(254);
