-- identity_db schema, part 1 (ARCHITECTURE.md §5.4): user accounts. Refresh tokens follow in M1.3b.
-- Every constraint is named: the service maps database errors to API errors by constraint name.

CREATE TABLE app_user (
    id             bigint       GENERATED ALWAYS AS IDENTITY,   -- = booking_db member.id (JWT sub)
    email          varchar(254) NOT NULL,
    password_hash  varchar(100) NOT NULL,                       -- BCrypt, never the password
    display_name   varchar(100) NOT NULL,
    role           varchar(20)  NOT NULL,
    enabled        boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    -- Emails are stored lower-cased (FR-AUTH-02), so a plain unique constraint is case-insensitive
    -- and two concurrent registrations of the same address cannot both succeed.
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_app_user_role CHECK (role IN ('MEMBER', 'ADMIN'))
);
