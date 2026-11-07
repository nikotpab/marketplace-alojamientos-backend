-- El detalle muestra la política de cancelación como texto (RF-16).
ALTER TABLE politica_cancelacion ADD COLUMN descripcion text;

UPDATE politica_cancelacion
SET descripcion = '100 % de devolución hasta 5 días antes de la llegada y 50 % entre 5 y 2 días antes. Después no hay devolución. La tarifa de servicio no es reembolsable.'
WHERE nombre = 'Flexible';
