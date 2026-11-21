-- SCRUM-111 (decisión pendiente 17) · RF-29: cualquier usuario con sesión, salvo el autor, reporta una reseña una sola
-- vez, con un motivo de la lista y un comentario opcional. Moderación lista las reportadas con su número de reportes.
CREATE TABLE reporte_resena (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    resena_id  uuid        NOT NULL REFERENCES resena (id),
    usuario_id uuid        NOT NULL REFERENCES usuario (id),
    motivo     varchar(40) NOT NULL CHECK (motivo IN ('OFENSIVA', 'SPAM', 'INFORMACION_FALSA', 'OTRO')),
    comentario varchar(500),
    creado_en  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (resena_id, usuario_id)
);
