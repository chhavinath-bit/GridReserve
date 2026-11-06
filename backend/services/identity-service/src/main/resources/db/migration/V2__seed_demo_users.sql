-- Demo accounts (FR-SEED-02). Fictional people on the reserved .test domain; BCrypt hashes (cost 10),
-- never plaintext. The demo passwords are listed in the README as demo-only.
-- ADMIN accounts exist only through migrations: self-registration always creates a MEMBER (FR-AUTH-04).

INSERT INTO app_user (email, password_hash, display_name, role, created_at)
VALUES ('admin@gridreserve.test', '$2a$10$oQ/73O3rGub5TMoCYPKIgeo3GSz2KhnpdTUaOz.GgvJwHMtoiLwrW', 'Demo Admin', 'ADMIN',  now()),
       ('alice@gridreserve.test', '$2a$10$9mJ59kLa.tEYq51iMl2U5.SyGeDP5llZAfT7q/HqHhYLMWBrCBiqK', 'Alice Demo', 'MEMBER', now()),
       ('bob@gridreserve.test',   '$2a$10$FfzEcxdb2KhmmzsH0yTgBeto9GBpaMIFvQjUNAl.M3Pr9K/ZDpIC6', 'Bob Demo',   'MEMBER', now());
