-- SCRUM-143: los eventos pasan a ser un record por evento. Las entregas sin completar que se guardaron con la clase
-- anterior (el record genérico EventoDominio, que ahora es una interfaz) ya no se pueden leer para reintentarlas.
-- Se cierran aquí para que no queden como entregas pendientes para siempre. Antes de desplegar hay que comprobar
-- que no quede ninguna (README, sección "Eventos"): si se siguió ese paso, esta migración no cambia ninguna fila.
-- Las entregas completadas no se tocan.
UPDATE event_publication
SET completion_date = now(),
    status = 'COMPLETED'
WHERE completion_date IS NULL
  AND event_type = 'com.inversioneslr.marketplace.compartido.EventoDominio';
