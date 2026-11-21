# marketplace-alojamientos-backend

API REST en Spring Boot 4.1 y Java 21 sobre PostgreSQL 17. Es un monolito modular con [Spring Modulith](https://docs.spring.io/spring-modulith/reference/).

## Requisitos

- Java 21 y Maven 3.9.
- Docker: PostgreSQL y Mailpit en desarrollo, y Testcontainers en las pruebas de integración.

## Arrancar

```bash
cp .env.example .env                    # cambiar las contraseñas; JWT_SECRETO necesita al menos 32 caracteres
docker compose up -d --wait db correo   # PostgreSQL en el 5432 y Mailpit en el 1025
mvn spring-boot:run                     # API en http://localhost:8080
curl localhost:8080/actuator/health
```

Los correos de desarrollo llegan a la bandeja de Mailpit: http://localhost:8025.

Para arrancar con datos de ejemplo (publicaciones, reseñas y reservas): `SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run`. Para volver a una base vacía: `docker compose down -v`.

### Crear un administrador

El rol `ADMINISTRADOR` no se elige al registrarse. Se registra un usuario con cualquier rol y se cambia por SQL:

```bash
docker compose exec db psql -U marketplace_owner -d marketplace \
  -c "UPDATE marketplace.usuario SET rol = 'ADMINISTRADOR' WHERE correo = 'tu-correo@ejemplo.com'"
```

El rol va en el token, así que hay que volver a iniciar sesión.

### Con el frontend

El frontend llama a `/api/...` y su interceptor (`version-api.interceptor.ts`) agrega la versión: `/api/v1/...`. En desarrollo, el proxy de Angular reenvía `/api` a `http://localhost:8080` sin reescribir la ruta. En el servidor, Caddy reenvía `/api/*` tal cual (`deploy/caddy/Caddyfile`); un `uri replace` dejaría las peticiones en `/api/v1/v1/...`.

## Configuración

Spring Boot y `docker compose` leen `.env`, que no se versiona. La plantilla es `.env.example`.

| Variable | Uso |
|---|---|
| `DB_URL` | URL JDBC de PostgreSQL, con `currentSchema=marketplace` |
| `DB_USUARIO_MIGRACION`, `DB_CONTRASENA_MIGRACION` | Dueño de los esquemas; lo usa Flyway |
| `DB_USUARIO_APP`, `DB_CONTRASENA_APP` | Usuario con el que corre la aplicación |
| `JWT_SECRETO` | Firma de los tokens; mínimo 32 caracteres (`openssl rand -hex 32`) |
| `PAGOS_PROVEEDOR` | `simulado`, `alterno` o `stripe` |
| `STRIPE_CLAVE_SECRETA` | Clave secreta de Stripe en modo de pruebas; solo con `stripe` |
| `STRIPE_METODO_PAGO` | Método de prueba si la reserva no trae `metodoPagoId` (por defecto `pm_card_visa`) |
| `CORREO_*`, `URL_PUBLICA` | Ver [Correos](#correos) |

### Correos

La API envía dos correos por SMTP: el código que verifica el correo al registrarse y el enlace para recuperar la contraseña. Se envían después del commit y en otro hilo, así que una falla del servidor de correo no hace fallar la solicitud.

| Variable | Desarrollo (Mailpit) | Gmail | Amazon SES |
|---|---|---|---|
| `CORREO_SERVIDOR` | `localhost` | `smtp.gmail.com` | `email-smtp.us-east-1.amazonaws.com` |
| `CORREO_PUERTO` | `1025` | `587` | `587` |
| `CORREO_USUARIO` / `CORREO_CONTRASENA` | vacías | la cuenta y una contraseña de aplicación | las credenciales SMTP de SES |
| `CORREO_SEGURO` | `false` | `true` | `true` |
| `CORREO_REMITENTE` | por defecto | la misma cuenta de Gmail | una identidad verificada en SES |
| `URL_PUBLICA` | `http://localhost:4200` | `https://` + el dominio | igual |

Sin estas variables se usa Mailpit. `URL_PUBLICA` es la dirección del frontend, adonde llevan los enlaces de los correos; en el servidor, `deploy/compose.yaml` la arma con `DOMINIO`.

## Documentación de la API

Con la API corriendo, Swagger UI está en http://localhost:8080/swagger-ui.html y la especificación en http://localhost:8080/v3/api-docs. Se generan desde los controladores y solo cubren `/api/v1/**`. Para probar un endpoint con sesión: se ejecuta `POST /api/v1/auth/login`, se copia el `token` de la respuesta y se pega en **Authorize**, sin el prefijo `Bearer`.

`docs/openapi.json` es el contrato versionado. `ContratoApiIT` falla si la especificación en vivo no coincide con él; para regenerarlo después de cambiar la API: `mvn verify -Dopenapi.actualizar=true`.

En el servidor, Caddy solo enruta `/api/*` hacia la API, así que la documentación no queda expuesta.

## Distribución del código

```
.
├── src/main/java/com/inversioneslr/marketplace   Un paquete por módulo
├── src/main/resources
│   ├── application.yml                           Configuración; application-demo.yml agrega los datos de ejemplo
│   ├── db/migration                              Migraciones de Flyway
│   └── db/demo                                   Datos de ejemplo del perfil demo
├── src/test                                      Pruebas unitarias (*Test) y de integración (*IT)
├── docs                                          Contrato OpenAPI y estrategia de pruebas
├── pruebas/carga                                 Plan de JMeter y sus scripts
├── deploy                                        Despliegue: compose, Caddy, scripts y plantillas de CloudFormation
├── compose.yaml                                  PostgreSQL y Mailpit para desarrollo
└── Dockerfile                                    Imagen de la API
```

### Módulos

```
com.inversioneslr.marketplace
├── compartido       Seguridad, errores, identificadores y eventos
├── usuarios         Registro, sesión, perfil y correos
├── publicaciones    Publicaciones, disponibilidad y catálogos
├── busqueda         Búsqueda con filtros y detalle
├── cotizacion       Cotización y tarifas
├── reservas         Confirmación e historial
├── pagos            Pasarela de pagos (Stripe, simulada y alterna)
├── cancelaciones    Cancelación y políticas de devolución
├── notificaciones   Notificaciones del usuario
├── resenas          Reseñas, calificaciones y reportes
├── administracion   Moderación, configuración, dashboard y reportes
└── auditoria        Registro de solo inserción
```

Cada paquete directo de `marketplace` es un módulo de Spring Modulith:

- **Lo público** es su paquete raíz: la fachada y los tipos que usa, por ejemplo `reservas.FachadaReservas` y `reservas.EstadoReserva`. En `compartido` son los tipos que usan todos, como `ExcepcionNegocio`, `EventoDominio` y `UsuarioAutenticado`.
- **Lo interno** son sus subpaquetes, y otro módulo no puede usarlos. `reservas` puede usar `publicaciones.FachadaPublicaciones`, pero no `publicaciones.dominio.Publicacion`.

Dentro de cada módulo, las capas que necesita:

| Paquete | Contiene | Puede depender de |
|---|---|---|
| raíz del módulo | La fachada y sus tipos, lo único visible para otros módulos | nada interno |
| `presentacion` | Controladores REST | `aplicacion`, `dominio`, raíz |
| `aplicacion` | `Servicio<Módulo>`, con un método por caso de uso | `dominio`, raíz propia y de otros módulos |
| `dominio` | Modelo y puertos, sin frameworks | raíz propia |
| `infraestructura` | SQL y adaptadores | `dominio` |

Cada módulo se declara en su `package-info.java` con `@ApplicationModule`, que lista los módulos de los que puede depender (`allowedDependencies`):

| Módulo | Puede depender de |
|---|---|
| `compartido` | ninguno |
| `notificaciones`, `pagos`, `usuarios` | `compartido` |
| `auditoria`, `publicaciones` | `compartido`, `usuarios` |
| `cotizacion` | `compartido`, `publicaciones` |
| `reservas` | `compartido`, `cotizacion`, `pagos`, `publicaciones`, `usuarios` |
| `cancelaciones` | `compartido`, `pagos`, `publicaciones`, `reservas` |
| `resenas` | `compartido`, `reservas`, `usuarios` |
| `busqueda` | `compartido`, `resenas` |
| `administracion` | `auditoria`, `compartido`, `cotizacion`, `publicaciones`, `resenas`, `reservas`, `usuarios` |

Dos pruebas hacen cumplir estas reglas:

- `ModulosTest`, con Spring Modulith: sin ciclos entre módulos, sin accesos a la parte interna de otro y sin dependencias que el módulo no declara. También genera los diagramas de los módulos en `target/spring-modulith-docs`.
- `ArquitecturaTest`, con ArchUnit: las capas dentro de cada módulo.

Para depender de un módulo nuevo hay que agregarlo a `allowedDependencies`; si no, `ModulosTest` falla.

Cada módulo lee solo sus tablas, y lo de otro módulo lo pide a su fachada, en consultas por lote. Cuando el que necesita el dato no puede depender del que lo tiene, declara un puerto que el otro implementa: `publicaciones.OcupacionReservas` lo implementa `reservas`, y `reservas.CancelacionesDeReservas` y `reservas.ResenasDeReservas` los implementan `cancelaciones` y `resenas`. `busqueda` y `administracion` son modelos de lectura: consultan tablas de otros módulos, pero nunca escriben en ellas.

### Eventos

Los módulos se avisan con eventos de Spring. Por ejemplo, al confirmar una reserva:

1. `ServicioReservas` publica un `ReservaConfirmada` con `PublicadorEventos`.
2. En la misma transacción, `ServicioAuditoria` lo registra (`@EventListener`). Si falla, la reserva no se confirma.
3. En la misma transacción, `ServicioNotificaciones` crea una notificación por destinatario. Si falla, la reserva tampoco se confirma.

Cada evento es un record en la raíz del módulo que lo emite, agrupados en `EventosReservas`, `EventosPublicaciones`, `EventosUsuarios`, etc. Todos implementan `compartido.EventoDominio`: el nombre del record es la acción que queda en la auditoría, y cada record dice su entidad, su resultado, su detalle y sus destinatarios. El `id` del evento hace que una entrega repetida no duplique la notificación. Para agregar un evento se crea un record; si debe notificar, se agrega su mensaje en `Notificacion`.

## Base de datos

Flyway aplica `src/main/resources/db/migration` al arrancar, con el usuario de migración. Hay dos esquemas:

- `marketplace`: los datos de los módulos.
- `auditoria`: solo inserción. El usuario de la aplicación tiene permisos de `INSERT` y `SELECT`, y unos triggers rechazan `UPDATE` y `DELETE` a cualquier rol.

Una migración aplicada no se modifica, porque Flyway rechaza el checksum distinto: cada cambio va en una migración nueva. Los datos de ejemplo (`db/demo`) solo se cargan con el perfil `demo`.

## Pruebas

```bash
mvn test     # unitarias (*Test) y de arquitectura
mvn verify   # además, las de integración (*IT) contra PostgreSQL con Testcontainers; necesita Docker
```

`mvn verify` deja el reporte de cobertura de JaCoCo en `target/site/jacoco/index.html`. Solo de las unitarias: `mvn test jacoco:report`.

### Pruebas de carga

`pruebas/carga/ejecutar.sh` corre todos los escenarios a la vez con JMeter 5.6 durante 2 minutos, contra la API con el perfil `demo`. Crea un huésped y un administrador, ejecuta `plan.jmx`, imprime p95 y p99 por escenario y, al terminar, borra las reservas que hizo. El reporte HTML queda en `pruebas/carga/resultados/`.

```bash
pruebas/carga/ejecutar.sh          # o ejecutar.sh 300 para 5 minutos
```

## Integración continua

`.github/workflows/ci.yml` corre en cada PR y en cada push a `main`:

- **`construir-y-probar`:** `mvn verify` y el análisis de [SonarQube Cloud](https://sonarcloud.io/project/overview?id=nikotpab_marketplace-alojamientos-backend) con la cobertura de JaCoCo. Si el quality gate queda en ERROR, el job falla y el PR no se puede fusionar. Necesita el secreto `SONAR_TOKEN` y el análisis automático apagado en SonarQube Cloud. Sube como artefactos la cobertura (`cobertura-jacoco`), el contrato (`openapi-contrato`) y, si algo falla, los reportes de las pruebas.
- **`credenciales`:** gitleaks bloquea credenciales en el repositorio. Los falsos positivos van en `.gitleaksignore`.
- **`desplegar`:** solo en `main`. Ver [Despliegue en AWS](#despliegue-en-aws).

Las reglas de SonarQube que no aplican están en el `pom.xml`, cada una con su razón, y `src/main/resources/db` no se analiza. Dependabot (`.github/dependabot.yml`) propone cada lunes las actualizaciones de Maven, de las acciones de GitHub y de las imágenes del `Dockerfile`.

## Consultar los logs

Los logs son JSON y enmascaran tokens y datos personales (`EnmascaradorLogs`). Cada línea lleva `correlationId`, y los eventos agregan `tipoEvento`, `usuario`, `entidad`, `entidadId` y `resultado`. Cada respuesta trae el mismo identificador en la cabecera `X-Correlation-Id`.

En producción se consultan en CloudWatch Logs Insights, en el grupo `/marketplace/api`.

Para seguir una petición que falló:

```
fields @timestamp, level, message, codigo, ruta, stack_trace
| filter correlationId = "ID-DE-LA-CABECERA"
| sort @timestamp asc
```

Por tipo de evento y usuario, en el rango de fechas que se elija en la consola:

```
fields @timestamp, tipoEvento, usuario, entidadId, resultado
| filter tipoEvento = "ReservaConfirmada" and usuario = "ID-DEL-USUARIO"
| sort @timestamp desc
```

Los errores recientes:

```
fields @timestamp, logger_name, message, correlationId
| filter level = "ERROR"
| sort @timestamp desc
| limit 50
```

## Despliegue en AWS

La API y el frontend corren en una EC2 (t4g.small, Amazon Linux 2023) con Docker Compose: Caddy sirve el frontend, termina HTTPS y reenvía `/api/*` a la API, que usa RDS (`deploy/compose.yaml`). Cada push a `main` que pasa el CI se despliega solo:

1. El job `desplegar` entra a AWS por OIDC, sin llaves guardadas, construye la imagen para ARM y la publica en ECR como `marketplace-api:<commit>`.
2. Envía a la EC2 el documento de SSM del stack, que solo acepta un commit como parámetro. El documento copia a `/opt/marketplace` los archivos de despliegue que trae la misma imagen y corre `deploy/desplegar.sh api <commit>`.
3. `desplegar.sh`:
   - escribe `.env` con los parámetros de `/marketplace/prod`; solo root lo lee, y falla si falta alguno obligatorio;
   - crea o actualiza en RDS el usuario de la aplicación;
   - instala o actualiza la autorreparación (ver [Monitoreo](#monitoreo));
   - levanta la imagen nueva y espera a que su healthcheck quede sano;
   - si no queda sana en 5 minutos, vuelve a la imagen anterior y el job falla.

El frontend hace lo mismo con su propio workflow: publica `marketplace-web:<commit>`, y `desplegar.sh web <commit>` copia sus archivos a `web/versiones/<commit>` y cambia de una vez el enlace `web/actual`, que es lo que sirve Caddy.

El log de GitHub Actions no muestra valores de parámetros ni logs de la API; esos se ven en CloudWatch.

### Preparación (una sola vez)

Requiere una identidad de AWS con permisos de CloudFormation, IAM, EC2 y SSM. En los comandos, `REGION=us-east-1` e `INSTANCIA=i-...`, el id de la EC2.

1. **Stack de despliegue.** Crea el rol y el perfil de la instancia, el grupo de seguridad web, los repositorios de ECR, el grupo de logs `/marketplace/api`, el proveedor OIDC de GitHub, un rol por repositorio y los dos documentos de SSM:

   ```bash
   aws cloudformation deploy --region $REGION --stack-name marketplace-despliegue \
     --template-file deploy/aws/despliegue.yaml --capabilities CAPABILITY_IAM \
     --parameter-overrides InstanciaId=$INSTANCIA VpcId=<vpc de la instancia>
   aws cloudformation describe-stacks --region $REGION --stack-name marketplace-despliegue --query 'Stacks[0].Outputs'
   ```

   Los roles solo aceptan tokens de `main` de cada repositorio, identificado como lo hace el token de GitHub: con su dueño y sus ids. Los valores por defecto de `SujetoGithubApi` y `SujetoGithubWeb` son los de estos repositorios; para otros, salen del campo `sub_claim_prefix` de `gh api repos/<dueño>/<repo>/actions/oidc/customization/sub`. Si la cuenta ya tiene el proveedor OIDC de GitHub, se pasa su ARN en `ProveedorOidcExistente`.

2. **Perfil y grupo de seguridad de la EC2:**

   ```bash
   aws ec2 associate-iam-instance-profile --region $REGION --instance-id $INSTANCIA \
     --iam-instance-profile Name=<salida PerfilInstancia>
   aws ec2 modify-instance-attribute --region $REGION --instance-id $INSTANCIA \
     --groups <grupos actuales> <salida GrupoSeguridadWeb>
   ```

   El grupo nuevo abre el 80 y el 443. El despliegue y la consola usan SSM, así que el puerto 22 no necesita estar abierto: se entra con `aws ssm start-session --target $INSTANCIA`.

3. **Docker en la instancia.** Con la instancia encendida y su agente de SSM registrado:

   ```bash
   aws ssm send-command --region $REGION --instance-ids $INSTANCIA --document-name AWS-RunShellScript \
     --parameters 'commands=["curl -fsSL https://raw.githubusercontent.com/nikotpab/marketplace-alojamientos-backend/main/deploy/preparar-instancia.sh | bash"]'
   ```

   `deploy/preparar-instancia.sh` instala Docker y Docker Compose (verifica el hash del binario) y crea `/opt/marketplace`.

4. **Parámetros de SSM.** `desplegar.sh` exige estos en `/marketplace/prod`:
   - `DB_URL`, `DB_USUARIO_APP`, `DB_CONTRASENA_APP`, `DB_USUARIO_MIGRACION`, `DB_CONTRASENA_MIGRACION`;
   - `JWT_SECRETO`, `PAGOS_PROVEEDOR`, `DOMINIO`;
   - `CORREO_SERVIDOR`, `CORREO_PUERTO`, `CORREO_USUARIO`, `CORREO_CONTRASENA`, `CORREO_SEGURO`, `CORREO_REMITENTE`.

   Los secretos van como SecureString, creados desde la consola. `STRIPE_CLAVE_SECRETA` y `STRIPE_METODO_PAGO` son opcionales.

5. **Variables de GitHub.** En cada repositorio, con las salidas del stack:

   ```bash
   gh variable set AWS_ROL_DESPLIEGUE --body <salida RolDespliegueApi>
   gh variable set AWS_DOCUMENTO_DESPLIEGUE --body <salida DocumentoDesplegarApi>
   gh variable set AWS_INSTANCIA --body $INSTANCIA
   ```

   En el frontend van `RolDespliegueWeb` y `DocumentoDesplegarWeb` (`-R nikotpab/marketplace-alojamientos-frontend`).

6. **Primer despliegue.** Se corre el workflow en `main` de cada repositorio, primero el backend: `gh workflow run CI --ref main`.

### Operación

- **Volver a una versión anterior:** se busca el commit en ECR y se corre el documento con esa etiqueta. Lo mismo con `marketplace-web` y `DocumentoDesplegarWeb`. ECR conserva las 10 imágenes más recientes de cada repositorio.

  ```bash
  aws ecr describe-images --region $REGION --repository-name marketplace-api \
    --query 'reverse(sort_by(imageDetails,&imagePushedAt))[].[imageTags[0],imagePushedAt]' --output text
  aws ssm send-command --region $REGION --instance-ids $INSTANCIA \
    --document-name <salida DocumentoDesplegarApi> --parameters etiqueta=<commit>
  ```

- **Cambiar un secreto:** se actualiza el parámetro en SSM y se vuelve a desplegar. `desplegar.sh` reescribe `.env` y, si cambió `DB_CONTRASENA_APP`, también la contraseña del usuario en RDS.
- **Entrar a la instancia:** `aws ssm start-session --target $INSTANCIA`. El despliegue vive en `/opt/marketplace`.

## Monitoreo

Cada 30 s la API escribe una línea `Latido` con el campo `latido` (`OK` o `FALLA`) y el estado de cada componente: `baseDeDatos` y `auditoria`. `auditoria` figura caída si algún registro fue alterado: cada 10 min, `VerificacionAuditoria` recalcula el hash de cada uno.

`deploy/aws/monitoreo.yaml` crea:

1. **Un filtro de métrica en `/marketplace/api`:** con el patrón `{ $.latido = "OK" }`, suma 1 a `Marketplace/LatidoOk` por cada latido correcto.
2. **La alarma del latido:** se dispara si la suma es menor que 1 durante 2 periodos de 1 minuto, con los datos faltantes como incumplidos. Cubre los dos casos: si la API se detiene, faltan los datos; si un componente falla, el latido deja de ser `OK`. Avisa por correo al tema de SNS, también cuando se recupera.
3. **Las alarmas de la EC2:** si falla el host (`StatusCheckFailed_System`), la recuperación automática simplificada de EC2, activa por defecto en la t4g, la mueve a otro host con la misma IP y el mismo disco. Si deja de responder su sistema (`StatusCheckFailed_Instance`), hay que reiniciarla con `aws ec2 reboot-instances`. En los dos casos, una alarma avisa.

Se crea después del stack de despliegue, que es el que crea el grupo de logs:

```bash
aws cloudformation deploy --region us-east-1 --stack-name marketplace-monitoreo \
  --template-file deploy/aws/monitoreo.yaml \
  --parameter-overrides InstanciaId=<id de la EC2> CorreoAlertas=<correo>
```

SNS envía un correo para confirmar la suscripción; hasta que se confirme, las alarmas no llegan. Para probarla, se detiene la API y en unos 2 minutos debe llegar el aviso:

```bash
aws ssm start-session --target <id de la EC2>
sudo docker stop marketplace-marketplace-api-1   # detenida a mano, Docker no la vuelve a levantar
sudo docker start marketplace-marketplace-api-1  # después del aviso
```

**Autorreparación.** `restart: always` no reinicia un contenedor que solo está `unhealthy`. El timer de systemd `marketplace-autorreparacion` corre cada minuto `deploy/autorreparacion/reiniciar-colgados.sh`, que reinicia los contenedores del proyecto `marketplace` que su healthcheck marcó `unhealthy`. Con el healthcheck de la imagen (cada 30 s, 3 fallos), una API colgada vuelve en menos de 3 minutos. Cada reinicio queda en el journal: `journalctl -t marketplace-autorreparacion`. `desplegar.sh` lo instala; a mano: `sudo sh /opt/marketplace/autorreparacion/instalar.sh`.
