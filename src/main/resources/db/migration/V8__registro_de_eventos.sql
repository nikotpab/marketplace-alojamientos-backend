-- ADR-06: registro de publicaciones de eventos de Spring Modulith, que hace de outbox (SCRUM-98).
-- Es el esquema v2 oficial de spring-modulith-events-jdbc 2.1.2 para PostgreSQL. Lo crea Flyway y no la
-- aplicación, porque el usuario de la aplicación no tiene permisos de DDL.
CREATE TABLE event_publication (
    id                     uuid                     NOT NULL,
    listener_id            text                     NOT NULL,
    event_type             text                     NOT NULL,
    serialized_event       text                     NOT NULL,
    publication_date       timestamp with time zone NOT NULL,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
