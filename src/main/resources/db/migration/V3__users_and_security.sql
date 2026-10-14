-- T6a: users, magic-link tokens and HTTP sessions.
--
-- The one_time_tokens table is the schema of Spring Security's JdbcOneTimeTokenService, copied from
-- org/springframework/security/core/ott/jdbc/one-time-tokens-schema.sql inside the spring-security-core jar,
-- with the single change the upstream file needs to run on PostgreSQL: `varchar_ignorecase` (an HSQLDB type)
-- becomes `varchar`. Column names, order and units are kept verbatim.

create table one_time_tokens(
    token_value varchar(36) not null primary key,
    username    varchar(50) not null,
    expires_at  timestamp   not null
);

-- The PostgreSQL schema of Spring Session JDBC, copied verbatim from
-- org/springframework/session/jdbc/schema-postgresql.sql inside the spring-session-jdbc jar.
-- spring.session.jdbc.initialize-schema=never: Flyway owns it.

CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

-- T6a accounts: created on the first successful login, never by a magic-link request (S13).

create table app_user (
    id bigserial primary key,
    email text not null unique,
    created_at timestamptz not null,
    last_login_at timestamptz
);
