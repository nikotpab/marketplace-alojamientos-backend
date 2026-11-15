-- SCRUM-151: el correo se verifica con un código antes de crear la cuenta. Reemplaza el enlace de SCRUM-109.

-- Un código vigente por correo. Solo se guarda su hash BCrypt: quien lea la base no lo ve. Los intentos se cuentan
-- para anularlo al quinto.
CREATE TABLE codigo_registro (
    correo      text        PRIMARY KEY,
    hash_codigo text        NOT NULL,
    intentos    integer     NOT NULL DEFAULT 0,
    expira_en   timestamptz NOT NULL,
    creado_en   timestamptz NOT NULL
);

-- Los enlaces de verificación pendientes ya no tienen pantalla ni endpoint. Las cuentas antiguas sin verificar lo
-- hacen recuperando la contraseña (SCRUM-110), que también confirma el correo.
DELETE FROM token_correo WHERE proposito = 'VERIFICAR_CORREO';

-- El evento CorreoVerificado deja de existir. Una entrega sin completar guardada con esa clase ya no se podría leer,
-- igual que en V10; se cierra para que no quede pendiente para siempre. Las completadas no se tocan.
UPDATE event_publication
SET completion_date = now(),
    status = 'COMPLETED'
WHERE completion_date IS NULL
  AND event_type = 'com.inversioneslr.marketplace.usuarios.EventosUsuarios$CorreoVerificado';
