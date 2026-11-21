-- SCRUM-117 (decisión pendiente 9): cada token lleva la versión de sesión de su usuario y se compara en cada petición.
-- Restringir, desactivar o restablecer la contraseña la sube, y los tokens anteriores dejan de servir de inmediato.
ALTER TABLE usuario ADD COLUMN version_sesion integer NOT NULL DEFAULT 0;
