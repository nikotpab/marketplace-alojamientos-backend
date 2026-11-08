-- Código legible de la reserva, con el formato que muestra el frontend: LR-2026-0001.
CREATE SEQUENCE reserva_codigo_seq;
GRANT USAGE, SELECT ON SEQUENCE reserva_codigo_seq TO marketplace_app;

-- Desde la reserva 10.000 el número crece en vez de recortarse.
CREATE FUNCTION nuevo_codigo_reserva() RETURNS varchar
    LANGUAGE sql
    SET search_path FROM CURRENT
AS $$
    SELECT 'LR-' || to_char(now() AT TIME ZONE 'America/Bogota', 'YYYY') || '-' || lpad(n::text, greatest(4, length(n::text)), '0')
    FROM (SELECT nextval('reserva_codigo_seq') AS n) AS siguiente
$$;

-- El valor por defecto se evalúa por cada fila, así que las reservas existentes también reciben código.
ALTER TABLE reserva ADD COLUMN codigo varchar(20) NOT NULL UNIQUE DEFAULT nuevo_codigo_reserva();
