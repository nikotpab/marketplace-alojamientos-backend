-- SCRUM-114 (decisión pendiente 6): las políticas Moderada y Estricta del mockup. Sus tramos viven en
-- PoliticaModerada y PoliticaEstricta, como los de la flexible; el nombre selecciona la clase en PoliticaCancelacion.
INSERT INTO politica_cancelacion (nombre, descripcion) VALUES
    ('Moderada', '100 % de devolución hasta 14 días antes de la llegada y 50 % entre 13 y 7 días antes. Después no hay devolución. La tarifa de servicio no es reembolsable.'),
    ('Estricta', '50 % de devolución hasta 14 días antes de la llegada. Después no hay devolución. La tarifa de servicio no es reembolsable.');
