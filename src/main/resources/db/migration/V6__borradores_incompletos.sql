-- RF-08 valida la información mínima al solicitar la activación: un borrador puede estar incompleto.
ALTER TABLE publicacion ALTER COLUMN tipo_alojamiento_id DROP NOT NULL;
ALTER TABLE publicacion ALTER COLUMN politica_cancelacion_id DROP NOT NULL;

-- El frontend muestra cuándo se modificó por última vez.
ALTER TABLE publicacion ADD COLUMN actualizado_en timestamptz NOT NULL DEFAULT now();

-- Conserva el orden en que el anfitrión escribe las reglas.
ALTER TABLE regla_alojamiento ADD COLUMN orden integer NOT NULL DEFAULT 0;
