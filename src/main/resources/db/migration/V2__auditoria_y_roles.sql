-- ADR-06 / RNF-24: auditoría de solo inserción en esquema aparte.
-- La aplicación usa su misma conexión para escribir en la transacción del caso de uso,
-- pero solo puede insertar y consultar. El esquema pertenece al usuario de migración.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'marketplace_app') THEN
        CREATE ROLE marketplace_app NOLOGIN;
    END IF;
END $$;

CREATE SCHEMA auditoria;

CREATE TABLE auditoria.evento_auditoria (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id      uuid REFERENCES usuario (id),
    accion        varchar(120) NOT NULL,
    entidad       varchar(120) NOT NULL,
    entidad_id    uuid,
    resultado     varchar(40)  NOT NULL,
    detalle       jsonb,
    hash_registro text         NOT NULL,
    creado_en     timestamptz  NOT NULL DEFAULT now()
);

-- Rechaza modificar o borrar a cualquier rol, incluido el dueño.
CREATE FUNCTION auditoria.rechazar_cambio() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'auditoria.evento_auditoria es de solo inserción: % rechazado', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END $$;

CREATE TRIGGER solo_insercion BEFORE UPDATE OR DELETE ON auditoria.evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria.rechazar_cambio();
CREATE TRIGGER sin_truncate BEFORE TRUNCATE ON auditoria.evento_auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria.rechazar_cambio();

-- Permisos de la aplicación.
GRANT USAGE ON SCHEMA marketplace TO marketplace_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA marketplace TO marketplace_app;
REVOKE ALL ON flyway_schema_history FROM marketplace_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA marketplace GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO marketplace_app;

GRANT USAGE ON SCHEMA auditoria TO marketplace_app;
GRANT SELECT, INSERT ON auditoria.evento_auditoria TO marketplace_app;
