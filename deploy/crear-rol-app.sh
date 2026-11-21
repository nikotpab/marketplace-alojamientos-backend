#!/bin/sh
# Crea el usuario de la aplicación con su contraseña, que no se versiona.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE "$DB_USUARIO_APP" LOGIN PASSWORD '$DB_CONTRASENA_APP';
SQL
