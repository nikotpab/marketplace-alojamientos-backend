-- SCRUM-115 (decisión pendiente 5): la tarifa de limpieza es un porcentaje del subtotal, como piden RF-32, CU-28 y el
-- modelo de datos, y no el monto fijo de los mockups. Arranca en 5 % (unos COP 45.000 en 3 noches de COP 300.000) y
-- el administrador la cambia en PUT /api/v1/admin/tarifas. No pisa un valor que el administrador ya haya puesto.
UPDATE configuracion_tarifa SET porcentaje = 5 WHERE clave = 'LIMPIEZA' AND porcentaje IS NULL;
