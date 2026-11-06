-- Catálogos que exigen las precondiciones de CU-06 y CU-16. Valores tomados de los mockups.
INSERT INTO tipo_alojamiento (nombre, activo) VALUES
    ('Casa', true), ('Apartamento', true), ('Cabaña', true), ('Loft', true),
    ('Habitación privada', true), ('Glamping', false);

INSERT INTO servicio (nombre) VALUES
    ('Wi-Fi'), ('Cocina'), ('Parqueadero'), ('Chimenea'), ('Piscina'), ('Admite mascotas'), ('Aire acondicionado');

-- El nombre selecciona la estrategia en PoliticaCancelacion.porNombre.
INSERT INTO politica_cancelacion (nombre) VALUES ('Flexible');

INSERT INTO configuracion_tarifa (clave, porcentaje, noches_minimas) VALUES
    ('LIMPIEZA', NULL, NULL),
    ('SERVICIO', 10, NULL),
    ('DESCUENTO_PROMOCIONAL', 5, NULL),
    ('DESCUENTO_ESTANCIA_LARGA', 10, 7);
