-- SCRUM-109 y SCRUM-110: verificación del correo y recuperación de contraseña por correo.

-- El registro guarda false de forma explícita. Las cuentas creadas por otra vía (las que ya existían, los datos de
-- demostración, las de las pruebas) quedan verificadas.
ALTER TABLE usuario ADD COLUMN correo_verificado boolean NOT NULL DEFAULT true;

-- Tokens de un solo uso que viajan en los enlaces de los correos. Solo se guarda su hash SHA-256: quien lea la base
-- no puede usarlos. Pedir uno nuevo borra el anterior del mismo propósito.
CREATE TABLE token_correo (
    hash_token text        PRIMARY KEY,
    usuario_id uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    proposito  text        NOT NULL CHECK (proposito IN ('VERIFICAR_CORREO', 'RESTABLECER_CONTRASENA')),
    expira_en  timestamptz NOT NULL,
    creado_en  timestamptz NOT NULL
);

CREATE INDEX token_correo_usuario ON token_correo (usuario_id, proposito);
