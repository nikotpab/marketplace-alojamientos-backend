-- Códigos estables de tipo para filtrar sin depender del nombre visible, y nombres de servicio
-- iguales a los que usa el frontend. unaccent permite buscar ciudades sin importar las tildes.
CREATE EXTENSION IF NOT EXISTS unaccent;

ALTER TABLE tipo_alojamiento ADD COLUMN codigo varchar(40);
UPDATE tipo_alojamiento SET codigo = CASE nombre
    WHEN 'Casa' THEN 'CASA'
    WHEN 'Apartamento' THEN 'APARTAMENTO'
    WHEN 'Cabaña' THEN 'CABANA'
    WHEN 'Loft' THEN 'LOFT'
    WHEN 'Habitación privada' THEN 'HABITACION'
    WHEN 'Glamping' THEN 'GLAMPING'
END;
ALTER TABLE tipo_alojamiento ALTER COLUMN codigo SET NOT NULL;
ALTER TABLE tipo_alojamiento ADD CONSTRAINT tipo_alojamiento_codigo_unico UNIQUE (codigo);

UPDATE servicio SET nombre = 'WiFi' WHERE nombre = 'Wi-Fi';
UPDATE servicio SET nombre = 'Mascotas' WHERE nombre = 'Admite mascotas';
