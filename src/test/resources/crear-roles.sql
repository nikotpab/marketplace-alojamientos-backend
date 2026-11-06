-- Usuario de la aplicación, y usuario de migración sin superusuario, como el maestro de RDS.
CREATE ROLE marketplace_app LOGIN PASSWORD 'solo-para-pruebas';
CREATE ROLE migrador LOGIN CREATEROLE PASSWORD 'solo-para-pruebas';
GRANT CREATE ON DATABASE test TO migrador;
