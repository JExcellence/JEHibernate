-- JEHibernate migration fixture.
-- Flyway applies this on an empty database before Hibernate builds its SessionFactory.
CREATE TABLE app_setting (
    id            BIGINT       NOT NULL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    setting_value VARCHAR(255)
);

INSERT INTO app_setting (id, name, setting_value) VALUES (1, 'site.title', 'JEHibernate Demo');
