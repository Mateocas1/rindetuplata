-- T11b: the relay's lease on an outbox row (S4).
--
-- The relay claims due rows in a short transaction and stamps locked_until = now + lease, then commits before it
-- publishes, so no row lock is held while it waits for the broker's confirm. A row is due while it is unpublished,
-- under the attempt cap and either never leased or past its lease: a crashed relay therefore loses its rows only
-- until the lease expires. Each outcome is its own short update and a failure clears the lease.

alter table outbox_event add column locked_until timestamptz;
