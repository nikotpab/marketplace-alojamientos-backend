-- Datos de demostración. Solo se cargan con el perfil demo (application-demo.yml), nunca en producción.
-- Los usuarios de ejemplo no pueden iniciar sesión: su hash de contraseña no es válido.
-- Para entrar a la plataforma, regístrate desde el frontend.
-- Las imágenes son fotos genéricas de picsum.photos; reemplázalas por fotos reales.

INSERT INTO usuario (id, nombre, apellido, correo, telefono, hash_contrasena, rol) VALUES
    ('d0000000-0000-0000-0000-000000000001', 'Carlos', 'Ramírez', 'carlos.ramirez@example.com', '3001112233', 'sin-acceso', 'ANFITRION'),
    ('d0000000-0000-0000-0000-000000000002', 'Laura', 'Gómez', 'laura.gomez@example.com', '3002223344', 'sin-acceso', 'HUESPED'),
    ('d0000000-0000-0000-0000-000000000003', 'Andrés', 'Pérez', 'andres.perez@example.com', '3003334455', 'sin-acceso', 'HUESPED'),
    ('d0000000-0000-0000-0000-000000000004', 'Camila', 'Rojas', 'camila.rojas@example.com', '3004445566', 'sin-acceso', 'HUESPED');

INSERT INTO publicacion (id, anfitrion_id, tipo_alojamiento_id, politica_cancelacion_id, titulo, descripcion, ciudad,
                         ubicacion_textual, capacidad, precio_noche, estado)
SELECT v.id::uuid, 'd0000000-0000-0000-0000-000000000001', t.id,
       (SELECT id FROM politica_cancelacion WHERE nombre = 'Flexible'),
       v.titulo, v.descripcion, v.ciudad, v.ubicacion, v.capacidad, v.precio, 'ACTIVA'
FROM (VALUES
    ('a0000000-0000-0000-0000-000000000001', 'CASA', 'Casa colonial con patio',
     'Casa de dos pisos en el centro histórico, con patio interior, terraza y piscina pequeña. A pocos pasos de la Torre del Reloj.',
     'Cartagena', 'Centro histórico, calle de la Factoría', 4, 280000),
    ('a0000000-0000-0000-0000-000000000002', 'CABANA', 'Cabaña cafetera',
     'Cabaña de madera entre cafetales, con chimenea y vista al valle de Cocora. Ideal para descansar en familia.',
     'Salento', 'Vereda Palestina, a 10 minutos del pueblo', 5, 190000),
    ('a0000000-0000-0000-0000-000000000003', 'APARTAMENTO', 'Apartamento con vista al mar',
     'Apartamento en piso alto frente a la playa, con balcón, piscina en el edificio y parqueadero.',
     'Santa Marta', 'El Rodadero, carrera 1', 4, 320000),
    ('a0000000-0000-0000-0000-000000000004', 'APARTAMENTO', 'Apartamento en El Poblado',
     'Apartamento moderno cerca del parque Lleras, con espacio de trabajo y aire acondicionado.',
     'Medellín', 'El Poblado, calle 10', 2, 250000),
    ('a0000000-0000-0000-0000-000000000005', 'HABITACION', 'Habitación en La Candelaria',
     'Habitación privada con baño propio en una casa colonial restaurada, a pasos del Museo del Oro.',
     'Bogotá', 'La Candelaria, calle 11', 2, 120000),
    ('a0000000-0000-0000-0000-000000000006', 'CASA', 'Casa de campo en Villa de Leyva',
     'Casa amplia con jardín, chimenea y zona de asados, a cinco minutos de la plaza principal.',
     'Villa de Leyva', 'Vía a Sáchica, kilómetro 1', 8, 450000),
    ('a0000000-0000-0000-0000-000000000007', 'APARTAMENTO', 'Apartamento en Bocagrande',
     'Apartamento a una cuadra de la playa, con piscina en el edificio y aire acondicionado.',
     'Cartagena', 'Bocagrande, avenida San Martín', 3, 230000),
    ('a0000000-0000-0000-0000-000000000008', 'CABANA', 'Cabaña junto al embalse',
     'Cabaña con muelle propio frente al embalse, kayaks y zona de fogata.',
     'Guatapé', 'Vía a la Piedra del Peñol', 6, 300000)
) AS v(id, tipo, titulo, descripcion, ciudad, ubicacion, capacidad, precio)
JOIN tipo_alojamiento t ON t.codigo = v.tipo;

INSERT INTO publicacion_servicio (publicacion_id, servicio_id)
SELECT v.publicacion::uuid, s.id
FROM (VALUES
    ('a0000000-0000-0000-0000-000000000001', 'WiFi'), ('a0000000-0000-0000-0000-000000000001', 'Cocina'),
    ('a0000000-0000-0000-0000-000000000001', 'Piscina'), ('a0000000-0000-0000-0000-000000000001', 'Aire acondicionado'),
    ('a0000000-0000-0000-0000-000000000002', 'WiFi'), ('a0000000-0000-0000-0000-000000000002', 'Cocina'),
    ('a0000000-0000-0000-0000-000000000002', 'Parqueadero'), ('a0000000-0000-0000-0000-000000000002', 'Mascotas'),
    ('a0000000-0000-0000-0000-000000000002', 'Chimenea'),
    ('a0000000-0000-0000-0000-000000000003', 'WiFi'), ('a0000000-0000-0000-0000-000000000003', 'Cocina'),
    ('a0000000-0000-0000-0000-000000000003', 'Piscina'), ('a0000000-0000-0000-0000-000000000003', 'Parqueadero'),
    ('a0000000-0000-0000-0000-000000000003', 'Aire acondicionado'),
    ('a0000000-0000-0000-0000-000000000004', 'WiFi'), ('a0000000-0000-0000-0000-000000000004', 'Cocina'),
    ('a0000000-0000-0000-0000-000000000004', 'Aire acondicionado'),
    ('a0000000-0000-0000-0000-000000000005', 'WiFi'),
    ('a0000000-0000-0000-0000-000000000006', 'WiFi'), ('a0000000-0000-0000-0000-000000000006', 'Cocina'),
    ('a0000000-0000-0000-0000-000000000006', 'Parqueadero'), ('a0000000-0000-0000-0000-000000000006', 'Mascotas'),
    ('a0000000-0000-0000-0000-000000000006', 'Chimenea'),
    ('a0000000-0000-0000-0000-000000000007', 'WiFi'), ('a0000000-0000-0000-0000-000000000007', 'Piscina'),
    ('a0000000-0000-0000-0000-000000000007', 'Aire acondicionado'),
    ('a0000000-0000-0000-0000-000000000008', 'Cocina'), ('a0000000-0000-0000-0000-000000000008', 'Parqueadero'),
    ('a0000000-0000-0000-0000-000000000008', 'Mascotas')
) AS v(publicacion, servicio)
JOIN servicio s ON s.nombre = v.servicio;

INSERT INTO regla_alojamiento (publicacion_id, descripcion)
SELECT v.publicacion::uuid, v.regla
FROM (VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Llegada desde las 3:00 p. m. y salida hasta las 11:00 a. m.'),
    ('a0000000-0000-0000-0000-000000000001', 'No se permiten fiestas ni eventos'),
    ('a0000000-0000-0000-0000-000000000002', 'Mascotas permitidas con aviso previo'),
    ('a0000000-0000-0000-0000-000000000002', 'Silencio después de las 10:00 p. m.'),
    ('a0000000-0000-0000-0000-000000000003', 'No se permite fumar dentro del apartamento'),
    ('a0000000-0000-0000-0000-000000000004', 'No se permiten visitas después de las 10:00 p. m.'),
    ('a0000000-0000-0000-0000-000000000005', 'Baño privado; cocina compartida con el anfitrión'),
    ('a0000000-0000-0000-0000-000000000006', 'Máximo 8 personas, incluidos los niños'),
    ('a0000000-0000-0000-0000-000000000007', 'No se permiten fiestas'),
    ('a0000000-0000-0000-0000-000000000008', 'Uso de chaleco salvavidas obligatorio en los kayaks')
) AS v(publicacion, regla);

INSERT INTO imagen_publicacion (publicacion_id, ruta, orden)
SELECT p.id, 'https://picsum.photos/seed/lr-' || right(p.id::text, 2) || '-' || n || '/1200/800', n - 1
FROM publicacion p
CROSS JOIN generate_series(1, 3) AS n
WHERE p.id::text LIKE 'a0000000-0000-0000-0000-0000000000%';

INSERT INTO servicio_adicional (publicacion_id, nombre, costo) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Desayuno para 2 personas', 120000),
    ('a0000000-0000-0000-0000-000000000001', 'Traslado desde el aeropuerto', 90000),
    ('a0000000-0000-0000-0000-000000000003', 'Salida tardía hasta las 2:00 p. m.', 50000),
    ('a0000000-0000-0000-0000-000000000006', 'Kit de asado', 80000);

INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo)
SELECT id, '2026-08-01', '2028-01-01', 'DISPONIBLE'
FROM publicacion
WHERE id::text LIKE 'a0000000-0000-0000-0000-0000000000%';

INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo) VALUES
    ('a0000000-0000-0000-0000-000000000001', '2026-12-20', '2026-12-27', 'BLOQUEADO');

-- Reservas confirmadas que ocupan días del calendario.
INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                     noches, precio_base, tarifa_limpieza, tarifa_servicio, descuento, total) VALUES
    ('d0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001', '2026-11-14', '2026-11-17', 2, 'CONFIRMADA', 3, 840000, 60000, 84000, 42000, 942000),
    ('d0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000003', '2026-11-20', '2026-11-24', 3, 'CONFIRMADA', 4, 1280000, 60000, 128000, 0, 1468000),
    ('d0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000002', '2026-12-05', '2026-12-08', 4, 'CONFIRMADA', 3, 570000, 60000, 57000, 0, 687000);

-- Estadías pasadas con sus reseñas.
INSERT INTO reserva (id, huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                     noches, precio_base, tarifa_limpieza, tarifa_servicio, descuento, total) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001', '2026-08-10', '2026-08-13', 2, 'COMPLETADA', 3, 840000, 60000, 84000, 0, 984000),
    ('c0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001', '2026-09-05', '2026-09-08', 4, 'COMPLETADA', 3, 840000, 60000, 84000, 0, 984000),
    ('c0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000002', '2026-09-12', '2026-09-15', 3, 'COMPLETADA', 3, 570000, 60000, 57000, 0, 687000),
    ('c0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000003', '2026-10-01', '2026-10-05', 2, 'COMPLETADA', 4, 1280000, 60000, 128000, 0, 1468000),
    ('c0000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000006', '2026-10-10', '2026-10-12', 6, 'COMPLETADA', 2, 900000, 60000, 90000, 0, 1050000),
    ('c0000000-0000-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000004', '2026-10-18', '2026-10-20', 2, 'COMPLETADA', 2, 500000, 60000, 50000, 0, 610000);

INSERT INTO resena (reserva_id, calificacion, contenido) VALUES
    ('c0000000-0000-0000-0000-000000000001', 5, 'La casa es preciosa y el patio es perfecto para descansar después de recorrer la ciudad.'),
    ('c0000000-0000-0000-0000-000000000002', 4, 'Muy buena ubicación. La piscina es pequeña, pero suficiente para refrescarse.'),
    ('c0000000-0000-0000-0000-000000000003', 5, 'Despertar con la vista de los cafetales no tiene precio. Volveremos.'),
    ('c0000000-0000-0000-0000-000000000004', 5, 'El balcón tiene la mejor vista del Rodadero. Todo estaba muy limpio.'),
    ('c0000000-0000-0000-0000-000000000005', 4, 'Casa amplia y cómoda para todo el grupo. La chimenea fue lo mejor.'),
    ('c0000000-0000-0000-0000-000000000006', 3, 'Buen apartamento, aunque el ruido de la calle se siente en las noches.');
