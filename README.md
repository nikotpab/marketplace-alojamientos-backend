# marketplace-alojamientos-backend

API del Marketplace de Alojamientos de Inversiones LR. Monolito modular con [Spring Modulith](https://docs.spring.io/spring-modulith/reference/), capas y regla de dependencia (ADR-01).

**Estado:** ya no quedan rutas sin implementar. Lo que falta, como la reconciliación de reservas a medias, depende de las [decisiones pendientes](#decisiones-pendientes).

Ya funcionan: el esquema de base de datos, la seguridad con denegación por defecto, el registro y el inicio de sesión, las publicaciones y su revisión, la búsqueda, la cotización, la confirmación de reservas con idempotencia, el historial, la cancelación con devolución, las reseñas, la moderación, la configuración de tarifas y de tipos de alojamiento, el dashboard, los reportes en PDF y Excel, los pagos, la auditoría, las notificaciones, los logs y las pruebas de arquitectura.

## Ejecutar

Requiere Java 21, Maven y Docker.

```bash
cp .env.example .env      # y cambiar las contraseñas
docker compose up -d --wait db correo
mvn spring-boot:run
curl localhost:8080/actuator/health
```

Los correos de desarrollo (código de registro y recuperación) llegan a Mailpit: http://localhost:8025.

`mvn test` corre las pruebas unitarias y de arquitectura, y deja los diagramas de los módulos en `target/spring-modulith-docs`. `mvn verify` agrega las de integración, que usan Docker.

### Con el frontend y datos de demostración

```bash
cp .env.example .env                      # JWT_SECRETO debe tener al menos 32 caracteres
docker compose up -d --wait db
SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run
```

El perfil `demo` carga 8 publicaciones de ejemplo. Para empezar con una base limpia: `docker compose down -v`.

Para aprobar publicaciones hace falta un administrador. Ese rol no se elige al registrarse (CU-01): regístrate con cualquier rol y ejecuta

```bash
docker compose exec db psql -U marketplace_owner -d marketplace \
  -c "UPDATE marketplace.usuario SET rol = 'ADMINISTRADOR' WHERE correo = 'tu-correo@ejemplo.com'"
```

Luego vuelve a iniciar sesión, porque el rol va en el token.

El frontend agrega la versión: sus servicios llaman a `/api/...` y su interceptor (`version-api.interceptor.ts`) las convierte en `/api/v1/...`. En desarrollo, el proxy de Angular reenvía `/api` a `http://localhost:8080` sin reescribir la ruta, y en el servidor Caddy reenvía `/api/*` tal cual (`deploy/caddy/Caddyfile`). No hay que agregar un `uri replace` en Caddy: las peticiones quedarían en `/api/v1/v1/...`.

### Correos (SCRUM-151, SCRUM-110)

**Código de registro (SCRUM-151).** El correo se verifica antes de crear la cuenta: el frontend pide un código de 6 dígitos para el correo, la persona lo escribe en una ventana y el registro lo envía junto con los demás datos. La cuenta nace verificada. El código vence en 15 minutos y sirve una vez; en la base (`codigo_registro`) solo queda su hash BCrypt. Al quinto intento fallido se anula: cada intento se gasta antes de comparar, así que ni con solicitudes simultáneas se prueban más de cinco. Reemplaza el enlace de verificación de SCRUM-109.

**Recuperación de contraseña (SCRUM-110).** Envía un enlace de un solo uso: el token tiene 256 bits aleatorios y en la base (`token_correo`) solo se guarda su hash SHA-256.

En los dos casos, pedir uno nuevo anula el anterior, y entre dos correos del mismo tipo a la misma dirección pasa al menos 1 minuto. El correo se envía por SMTP después del commit y en otro hilo; si el servidor de correo falla, la solicitud no falla y se puede pedir otro.

| Variable | Desarrollo (Mailpit) | Gmail | Amazon SES |
|---|---|---|---|
| `CORREO_SERVIDOR` | `localhost` | `smtp.gmail.com` | `email-smtp.us-east-1.amazonaws.com` |
| `CORREO_PUERTO` | `1025` | `587` | `587` |
| `CORREO_USUARIO` / `CORREO_CONTRASENA` | vacías | la cuenta y una contraseña de aplicación | las credenciales SMTP de SES |
| `CORREO_SEGURO` | `false` | `true` | `true` |
| `CORREO_REMITENTE` | por defecto | la misma cuenta de Gmail | una identidad verificada en SES |
| `URL_PUBLICA` | `http://localhost:4200` | `https://` + el dominio | igual |

En el servidor, `deploy/compose.yaml` arma `URL_PUBLICA` con `DOMINIO`, y las demás van a SSM Parameter Store (`CORREO_CONTRASENA` como SecureString). Sin dominio propio, Gmail es lo más simple. SES necesita verificar el remitente y, para escribirle a cualquier dirección, pedir que la cuenta salga del sandbox.

**Contrato para el frontend:**

| Endpoint | Cuerpo | Respuesta |
|---|---|---|
| `POST /api/v1/auth/register/code` | `{"correo"}` | 202 con el código en camino (si ya se envió uno hace menos de 1 minuto, no envía otro); 409 con `campos.correo` si ya tiene cuenta |
| `POST /api/v1/auth/register` | los datos de antes más `codigo` (6 dígitos) | 201 con `correoVerificado: true`; 400 con `campos.codigo` si no es correcto, venció o se anuló; 409 si el correo ya tiene cuenta |
| `POST /api/v1/auth/login` | igual | 403 con `codigo: CORREO_NO_VERIFICADO` para una cuenta antigua sin verificar, solo con la contraseña correcta; se verifica recuperando la contraseña |
| `POST /api/v1/auth/password/recover` | `{"correo"}` | 202 siempre, **sin token** (antes lo devolvía) |
| `POST /api/v1/auth/password/reset` | `{"token", "contrasenaNueva"}` | 204; también deja verificado el correo |

Formatos (SCRUM-154): el `telefono` del registro y del perfil (`PUT /usuarios/me`) tiene 10 dígitos y empieza por 3; el `correo` del registro, del código y de la recuperación necesita un dominio con punto y terminación de al menos 2 letras (`luicuadros07@gmail` no sirve). Si no, 400 con `campos.telefono` o `campos.correo`. Las cuentas guardadas no se tocan: un teléfono antiguo fuera de formato se corrige al editar el perfil.

El enlace de recuperación lleva a `URL_PUBLICA + /recuperar?token=…` (15 minutos). El frontend lee el token de la dirección y lo envía a la API. `POST /auth/email/verify`, `POST /auth/email/resend` y la pantalla `/verificar-correo` se retiraron en SCRUM-151.

### Documentación de la API (Swagger)

Con el backend corriendo, Swagger UI queda en http://localhost:8080/swagger-ui.html y la especificación OpenAPI en http://localhost:8080/v3/api-docs. Se generan desde los controladores, así que siempre coinciden con el código. Solo documentan `/api/v1/**`.

Para probar un endpoint con sesión: ejecuta `POST /api/v1/auth/login`, copia el campo `token` de la respuesta, pulsa **Authorize** y pégalo sin el prefijo `Bearer`.

En el servidor, Caddy solo enruta `/api/*` hacia la API, así que la documentación no queda expuesta a internet.

## Estructura

```
com.inversioneslr.marketplace
├── compartido       Núcleo común: seguridad, errores y eventos
├── usuarios         Registro, sesión y perfil
├── publicaciones    Publicaciones, disponibilidad y catálogos
├── busqueda         Búsqueda con filtros y detalle
├── cotizacion       Cotización y tarifas
├── reservas         Confirmación e historial
├── pagos            Pasarela de pagos (Stripe y simulada)
├── cancelaciones    Cancelación y políticas de devolución
├── notificaciones   Notificaciones del usuario
├── resenas          Reseñas y calificaciones
├── administracion   Moderación, dashboard y reportes
└── auditoria        Registro de solo inserción
```

Cada paquete directo de `marketplace` es un módulo, como pide Spring Modulith:

- **Lo público** de un módulo es su paquete raíz: la fachada y los tipos que usa, por ejemplo `reservas.FachadaReservas` y `reservas.EstadoReserva`. En `compartido` son los tipos que usan todos, como `ExcepcionNegocio`, `EventoDominio` y `UsuarioAutenticado`.
- **Lo interno** son sus subpaquetes, y otro módulo no puede usarlos. Por ejemplo, `reservas` puede usar `publicaciones.FachadaPublicaciones`, pero no `publicaciones.dominio.Publicacion`.

Dentro de cada módulo, las capas que necesita:

| Paquete | Contiene | Puede depender de |
|---|---|---|
| raíz del módulo | La fachada y sus tipos, lo único visible para otros módulos | nada interno |
| `presentacion` | Controladores REST | `aplicacion`, `dominio`, raíz |
| `aplicacion` | `Servicio<Módulo>`, con un método por caso de uso | `dominio`, raíz propia y de otros módulos |
| `dominio` | Modelo y puertos, sin frameworks | raíz propia |
| `infraestructura` | SQL y adaptadores | `dominio` |

Cada módulo se declara en su `package-info.java` con `@ApplicationModule`: un nombre legible (`displayName`) y los módulos de los que puede depender (`allowedDependencies`, SCRUM-145). Las dependencias declaradas son las reales:

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

Para depender de un módulo nuevo hay que agregarlo a esa lista a propósito; si no, `ModulosTest` falla.

Dos pruebas hacen cumplir estas fronteras y bloquean el merge si se rompen:

- `ModulosTest`: con Spring Modulith, que no haya ciclos entre módulos, ni accesos a la parte interna de otro, ni dependencias que el módulo no declara. También genera los diagramas.
- `ArquitecturaTest`: con ArchUnit, las capas dentro de cada módulo.

### Eventos

Los módulos se avisan con eventos de Spring (ADR-06). Por ejemplo, al confirmar una reserva:

1. `ServicioReservas` publica un `ReservaConfirmada` con `PublicadorEventos`.
2. En la misma transacción, `ServicioAuditoria` lo registra (`@EventListener`). Si falla, la reserva no se confirma.
3. En la misma transacción, `ServicioNotificaciones` crea una notificación por destinatario (`@EventListener`). Si falla, la reserva tampoco se confirma.

Una entrega repetida no duplica la notificación, porque su id sale del id del evento y del destinatario.

Cada evento es un record en la raíz del módulo que lo emite, agrupados en `EventosReservas`, `EventosPublicaciones`, `EventosUsuarios`, etc. (SCRUM-143). Todos implementan `compartido.EventoDominio`, que es lo que atienden la auditoría y las notificaciones: el nombre del record es la acción que queda en la auditoría, y cada record dice su entidad, su resultado, su detalle y sus destinatarios. Para agregar un evento se crea un record; si debe notificar, se agrega su mensaje en `Notificacion`.

## Dónde vive cada decisión

| Decisión | Dónde |
|---|---|
| ADR-01 Capas y módulos | `ModulosTest`, `ArquitecturaTest` |
| ADR-02 Búsqueda indexada, 20 por página, tiempo máximo | `RepositorioBusquedaSql`, `ServicioBusqueda`, índices de `V1` |
| ADR-03 Anti-solape | Restricción `reserva_sin_solape` en `V1` |
| ADR-04 Idempotencia | Cabecera `Idempotency-Key`, tabla `solicitud_procesada`, `ServicioReservas.confirmarReserva` |
| ADR-05 Timeout de pasarela | `AdaptadorStripe` (2 s conexión + 5 s lectura) |
| ADR-06 Eventos síncronos y auditoría | `PublicadorEventosSpring`, `ServicioAuditoria`, `ServicioNotificaciones`, `V2` |
| ADR-07 Autorización | Tabla de rutas en `ConfiguracionSeguridad`, `ManejadorDenegaciones` |
| ADR-08 Proveedor de pagos | `PasarelaPagos`, `ConfiguracionPagos` |
| RNF-18, RNF-25 Logs | `FiltroCorrelacion`, `EnmascaradorLogs` |
| RNF-26 HTTPS | `deploy/caddy/Caddyfile` |
| RNF-27 Credenciales | `.env`, gitleaks en `.github/workflows/ci.yml` |
| RNF-12 Reservas a medias | `ServicioPagos.revertirCobrosSinReserva` |
| RNF-31 Nueva política | `PoliticaCancelacion` |

## Cómo se verifica cada ASR

| ASR | Qué se comprueba | Prueba |
|---|---|---|
| ASR-01 Rendimiento | p95 y p99 de cada escenario con la carga pedida | `pruebas/carga` (JMeter), ver la sección siguiente |
| ASR-02 Concurrencia | 10 solicitudes simultáneas por las mismas fechas: 1 confirmada, 9 rechazadas, 0 superposiciones | `ConfirmarReservaIT.conDiezSolicitudesSimultaneasSoloSeConfirmaUna` |
| ASR-03 Idempotencia | 10 envíos de la misma solicitud: 1 reserva, 1 cobro y 1 notificación | `IdempotenciaReservaIT.diezEnviosSimultaneosProducenUnSoloEfecto` |
| ASR-04 Pasarela caída | Respuesta antes de 7 s sin reserva ni periodo bloqueado | `AdaptadorStripeTest`, `ConfirmarReservaIT.pagoNoProcesadoNoCreaLaReservaNiBloqueaElPeriodo` |
| ASR-06 Autorización | Todos los endpoints con cada rol: 403 y registro en la auditoría en cada denegación | `AutorizacionIT`, `HistorialReservasIT` (contacto, RNF-21) |
| ASR-07 Auditoría inalterable | UPDATE y DELETE rechazados para cualquier rol; un hash alterado deja el latido en falla | `EsqueletoIT.auditoriaEsSoloInsercionParaCualquierRol`, `AuditoriaIT` |
| ASR-08b Modificabilidad | Segundo proveedor de pagos sin tocar dominio ni aplicación | `PagosAlternoIT`, ver "Experimento de modificabilidad" |
| RNF-12 Reservas a medias | Un cobro sin reserva se devuelve en máximo 15 min | `ReconciliacionIT` |
| RNF-19, RNF-22, RNF-23 Seguridad | Inyección SQL, XSS, fuerza bruta y contraseñas con hash | `SeguridadIT`, `InicioSesionIT` |

Todas, salvo la de carga, corren en cada PR con `mvn verify` contra PostgreSQL real.

## Cobertura de pruebas (JaCoCo)

`mvn verify` mide la cobertura de las pruebas unitarias y de integración, y deja el reporte en `target/site/jacoco/index.html`, más `jacoco.xml` para otras herramientas. Para ver solo la de las unitarias: `mvn test jacoco:report`.

En cada PR, el CI sube el reporte como el artefacto `cobertura-jacoco`.

## Análisis estático (SonarQube Cloud)

El CI analiza cada PR y `main` en [SonarQube Cloud](https://sonarcloud.io/project/overview?id=nikotpab_marketplace-alojamientos-backend), con la cobertura de JaCoCo. Si el quality gate queda en ERROR, falla `construir-y-probar` y el PR no se puede fusionar. Necesita el secreto `SONAR_TOKEN` en GitHub y el análisis automático apagado en SonarQube Cloud (Administration > Analysis Method). Las migraciones de Flyway y los datos de demostración (`src/main/resources/db`) no se analizan, porque no se pueden modificar una vez aplicados. Las reglas que no aplican están en el `pom.xml`, cada una con su razón.

## Pruebas de carga (ASR-01)

`pruebas/carga/ejecutar.sh` corre todos los escenarios a la vez con JMeter durante 2 minutos, contra el backend con el perfil `demo`. Crea un huésped y un administrador, ejecuta `plan.jmx`, imprime p95 y p99 por escenario y, al terminar, borra las reservas que hizo. El reporte HTML de JMeter queda en `pruebas/carga/resultados/`.

```bash
pruebas/carga/ejecutar.sh          # o ejecutar.sh 300 para 5 minutos
```

Resultado del 10 de noviembre, en local (Apple M5, 10 núcleos, 16 GB, PostgreSQL en Docker y los 9 alojamientos de la demo):

| Escenario | Carga | p95 (meta) | p99 (meta) |
|---|---|---|---|
| Búsqueda | 580/min | 4 ms (≤ 1 s) | 5 ms (≤ 2 s) |
| Detalle | 580/min | 7 ms (≤ 2 s) | 16 ms |
| Calendario | 580/min | 5 ms (≤ 2,5 s) | 7 ms |
| Cotización | 100/min | 4 ms (≤ 500 ms) | 6 ms (≤ 1 s) |
| Confirmación de reserva | 30/min | 17 ms (≤ 5 s) | 20 ms |
| Cancelación | 10/min | 13 ms (≤ 4 s) | 16 ms |
| Dashboard | 5 administradores sin pausa | 3 ms (≤ 4 s) | 4 ms |
| Reporte | 5 exportaciones sin pausa | 8 ms (≤ 10 s) | 9 ms |

Con 10.000 reservas sembradas, 5 exportaciones simultáneas tardaron 0,5 s en PDF y 0,31 s en Excel (meta: 10 s). Todos los escenarios cumplen sin errores, así que no hace falta la caché de la búsqueda que ADR-02 dejó como decisión diferida. JMeter mide desde el cliente, pero en local la red no suma tiempo. Falta repetir la medición en el servidor desplegado y con más datos.

## Experimento de modificabilidad (ASR-08b)

SCRUM-66 pide agregar un segundo proveedor de pagos simulado y medir el cambio. `AdaptadorPagosAlterno` se elige con `PAGOS_PROVEEDOR=alterno`: tiene sus propias referencias, rechaza los cobros de más de 20 millones y no cobra dos veces con la misma clave.

| Medida | Meta | Resultado |
|---|---|---|
| Esfuerzo | ≤ 3 días-persona | Menos de 1 día-persona |
| Componentes modificados | 1 | 1: la infraestructura de pagos (una clase nueva de 34 líneas y una línea en `ConfiguracionPagos`) |
| Cambios en dominio y aplicación | 0 | 0 |
| Pruebas del flujo de pago | Pasan | `PagosAlternoIT` confirma, reintenta y rechaza con el proveedor alterno, y las demás pruebas siguen pasando con el simulado |

Para comprobarlo: `git show --stat` sobre el commit "SCRUM-66: agregar un segundo proveedor de pagos simulado". Funciona porque el resto del sistema solo conoce `PasarelaPagos` (Adapter, ADR-08).

## Simplificaciones respecto a los documentos

Para mantener el MVP mínimo:

- **Filtro de autorización:** el `FiltroAutorizacion` del Patrón 6 es el `AuthorizationFilter` de Spring Security con una sola tabla de rutas, no una clase propia.
- **Fachadas:** solo tienen fachada los módulos a los que otro módulo llama. `busqueda`, `cancelaciones`, `notificaciones` y `administracion` no la necesitan todavía.
- **Casos de uso:** son métodos del `Servicio` de cada módulo. Por ejemplo, `ServicioReservas.confirmarReserva` es el `ConfirmarReserva` de SEC-01.
- **Eventos:** hay un record por evento (SCRUM-143), pero la auditoría y las notificaciones atienden una sola interfaz, `EventoDominio`, en vez de un listener por evento.
- **Historial de tarifas:** RF-32 pide mostrar quién cambió las tarifas y cuándo. Cada cambio queda en la auditoría como `TarifasActualizadas`, con el valor anterior y el nuevo (por ejemplo, "10 % → 12 %"), así que el historial se consulta en `GET /api/v1/admin/auditoria`. La tabla solo guarda quién hizo el último cambio.

Elecciones técnicas que los documentos dejaban abiertas: Spring JDBC en vez de JPA, Flyway y BCrypt.

## Ajustes al diseño

Contradicciones que hubo que resolver para poder escribir el esqueleto. Conviene validarlas con el equipo:

1. **Credenciales de auditoría.** Los documentos piden credenciales propias y además escribir en la misma transacción del caso de uso. Con otro usuario habría otra conexión y otra transacción. Se resolvió con un esquema aparte cuyo dueño es el usuario de migración, permisos de solo `INSERT` y `SELECT` para el usuario de la aplicación, y triggers que rechazan cambios a cualquier rol.
2. **`numero_huespedes`.** Está en el diagrama de clases y en los mockups, pero no en el modelo de datos. Se agregó a `reserva`.
3. **`NOT NULL`.** El modelo de datos solo lo marca en claves y `creado_en`. Se agregó donde la integridad lo exige, por ejemplo en las fechas de `reserva`: con fechas nulas, la restricción anti-solape bloquearía todo el calendario.
4. **Cruces entre módulos.** `Reserva.cancelar()` no devuelve una `Cancelacion`, porque está en otro módulo. `calcularDevolucion` recibe `(reserva, fecha)` como en SEC-04, no solo la reserva como en el diagrama de clases.
5. **Nombres.** Se usan los del modelo de datos (`solicitud_procesada`) y `RepositorioBusqueda` del Patrón 1, no `RepositorioPublicaciones` de SEC-06.
6. **Borradores incompletos.** CU-06 pide rechazar un borrador sin datos obligatorios, pero el frontend permite guardarlo incompleto y RF-08 valida la información mínima al solicitar la activación. Se sigue a RF-08: el tipo y la política son opcionales en un borrador. Una publicación que ya no es borrador no puede quedar incompleta al editarla.
7. **Ciudad.** El formulario del frontend envía `ciudad` como campo propio. El backend lo almacena directamente y la búsqueda filtra solo por ese campo (SCRUM-148).
8. **Política de cancelación (temporal).** El frontend la envía como texto libre. Se asocia a la política cuyo nombre aparece en el texto y, si ninguna aparece, a la Flexible. Lo correcto es que el formulario ofrezca las políticas para elegir, porque la devolución se calcula según la política elegida.
9. **Disponibilidad por defecto (temporal).** El frontend asume que todo está disponible salvo lo bloqueado, y el backend exige periodos disponibles. Una publicación nueva queda disponible dos años desde su creación, y el anfitrión bloquea o libera rangos de días, con ambos extremos incluidos. Al liberar, el rango se une con los periodos disponibles que toca, y así una estancia siempre cabe en un único periodo, como exigen la reserva y la búsqueda.
10. **Errores por campo.** Los errores por campo van en `campos` y también en `errores`, el nombre que lee el frontend. La información mínima que falta para publicar responde 422.
11. **Código de reserva.** El frontend muestra un código legible, como `LR-2026-0001`, que no está en el modelo de datos. Se agregó la columna `codigo`, que asigna la base de datos al guardar la reserva. Las peticiones y respuestas de reserva usan los nombres del frontend (`llegada`, `salida`, `huespedes`, `adicionales`).
12. **Base de la cotización.** RF-17 no dice sobre qué monto se aplican los porcentajes. Como en el mockup, la tarifa de servicio y los descuentos se calculan sobre el subtotal (precio por noche × noches) y se redondean al peso. El descuento promocional y el de estancia larga se suman.
13. **Interfaz pública en la raíz del módulo.** ADR-01 y SCRUM-54 la ubican en un paquete `api/`. Spring Modulith, el estándar de Spring para monolitos modulares, trata los subpaquetes como internos, así que la fachada y sus tipos están en el paquete raíz de cada módulo (SCRUM-98). La regla no cambia: otro módulo solo usa lo público.
14. **Tabla de eventos.** El modelo de datos define `evento_pendiente` para almacenamiento temporal de eventos. Con Spring Modulith se mantiene la estructura de módulos, pero la entrega de eventos es síncrona en la misma transacción (`@EventListener`), sin tabla intermedia (ADR-06). `EventoDominio` lleva un `id`, que no está en los documentos, para que una entrega repetida no duplique la notificación.
15. **Indicadores del dashboard.** RF-33 no define "ingresos estimados" ni el periodo. Los ingresos son la suma de las tarifas de servicio de las reservas confirmadas y completadas, que es lo que gana la plataforma. El periodo cuenta las reservas por la fecha en que se hicieron y, si no se elige, son los últimos 30 días. Usuarios y publicaciones activas son el total actual.
16. **Confirmaciones simultáneas (ADR-03).** La restricción de exclusión sola no basta: con varios `INSERT` simultáneos por la misma publicación, PostgreSQL puede detectar un deadlock al verificarla y abortar solicitudes que debían rechazarse con "periodo no disponible". Antes del `INSERT`, cada confirmación toma un candado de transacción por publicación (`pg_advisory_xact_lock`), así que entran de a una. La restricción sigue siendo la garantía de no solape (SCRUM-102).
17. **Verificación del correo (SCRUM-109, SCRUM-151).** Los documentos no la piden: CU-01 crea la cuenta lista para usarse. Se agregó para que nadie se registre con un correo ajeno, ahora que la recuperación de contraseña llega por correo. Desde SCRUM-151 el registro exige el código que llega al correo, así que la cuenta nace verificada; antes era un enlace que se usaba después de crearla. Las cuentas de esa época que no usaron el enlace siguen recibiendo 403 `CORREO_NO_VERIFICADO` (solo con la contraseña correcta, para no revelar el estado de la cuenta) y se verifican recuperando la contraseña. Las cuentas creadas por SQL (como el primer administrador) y las de los datos de demostración quedan verificadas: la columna nace en `true`.

- **Lecturas entre módulos (SCRUM-142).** Cada módulo lee solo sus tablas; lo de otro módulo se pide a su fachada, en consultas por lote. Publicaciones no lee `reserva`: declara el puerto `OcupacionReservas`, que implementa reservas. Igual, el historial de reservas declara `CancelacionesDeReservas` y `ResenasDeReservas`, que implementan cancelaciones y reseñas (SCRUM-113). La única excepción son `busqueda` y `administracion`, que son modelos de lectura: consultan tablas de otros módulos solo para leer, nunca escriben en ellas, y ningún otro módulo puede hacerlo.

## Consultar los logs

Los logs son JSON. Cada línea lleva `correlationId`, y los eventos agregan `tipoEvento`, `usuario`, `entidad`, `entidadId` y `resultado`. Cada respuesta incluye el mismo identificador en la cabecera `X-Correlation-Id`.

En producción se consultan en CloudWatch Logs Insights, en el grupo `/marketplace/api`.

Para seguir una petición que falló:

```
fields @timestamp, level, message, codigo, ruta, stack_trace
| filter correlationId = "ID-DE-LA-CABECERA"
| sort @timestamp asc
```

Para buscar por tipo de evento y usuario en un rango de fechas, elegido en la consola:

```
fields @timestamp, tipoEvento, usuario, entidadId, resultado
| filter tipoEvento = "ReservaConfirmada" and usuario = "ID-DEL-USUARIO"
| sort @timestamp desc
```

Para ver los errores recientes:

```
fields @timestamp, logger_name, message, correlationId
| filter level = "ERROR"
| sort @timestamp desc
| limit 50
```

## Despliegue en AWS

La API y el frontend corren en una EC2 (t4g.small, Amazon Linux 2023) con Docker Compose: Caddy sirve el frontend y reenvía `/api/*` a la API, que usa RDS (`deploy/compose.yaml`). Cada push a `main` que pasa el CI se despliega solo (SCRUM-121):

1. El job `desplegar` de `.github/workflows/ci.yml` entra a AWS por OIDC, sin llaves guardadas, construye la imagen para ARM y la publica en ECR como `marketplace-api:<commit>`.
2. Envía a la EC2 el documento de SSM del stack, que solo acepta un commit como parámetro. El documento copia a `/opt/marketplace` los archivos de despliegue que trae la misma imagen y corre `deploy/desplegar.sh api <commit>`.
3. `desplegar.sh`:
   - escribe `.env` con los parámetros de `/marketplace/prod`; solo root lo lee y falla si falta alguno obligatorio;
   - crea o actualiza en RDS el usuario de la aplicación;
   - levanta la imagen nueva y espera a que su healthcheck quede sano;
   - si no queda sana en 5 minutos, vuelve a la imagen anterior y el job falla.

El frontend hace lo mismo con su propio workflow: publica `marketplace-web:<commit>`, y `desplegar.sh web <commit>` copia sus archivos a `web/versiones/<commit>`. Luego cambia de una vez el enlace `web/actual`, que es lo que sirve Caddy.

Lo que imprime el despliegue queda en el log público de GitHub Actions: no muestra valores de parámetros ni logs de la API. Esos se ven en CloudWatch (ver "Consultar los logs").

### Preparación (una sola vez)

Requiere una identidad de AWS con permisos de CloudFormation, IAM, EC2 y SSM. `REGION=us-east-1` e `INSTANCIA=i-...`, el id de la EC2.

1. **Stack de despliegue.** Crea el rol y el perfil de la instancia, el grupo de seguridad web, los repositorios ECR, el grupo de logs `/marketplace/api`, el proveedor OIDC de GitHub, un rol por repositorio y los dos documentos de SSM:

   ```bash
   aws cloudformation deploy --region $REGION --stack-name marketplace-despliegue \
     --template-file deploy/aws/despliegue.yaml --capabilities CAPABILITY_IAM \
     --parameter-overrides InstanciaId=$INSTANCIA VpcId=<vpc de la instancia>
   aws cloudformation describe-stacks --region $REGION --stack-name marketplace-despliegue --query 'Stacks[0].Outputs'
   ```

   Si la cuenta ya tiene el proveedor OIDC de GitHub, se pasa su ARN en `ProveedorOidcExistente`. Los roles solo aceptan tokens de `main` de cada repositorio, identificado como lo hace el token de GitHub: con su dueño y sus ids (`SujetoGithubApi` y `SujetoGithubWeb`, del campo `sub_claim_prefix` de `gh api repos/<dueño>/<repo>/actions/oidc/customization/sub`).

2. **Perfil y grupo de seguridad de la EC2.** Con la instancia detenida o encendida:

   ```bash
   aws ec2 associate-iam-instance-profile --region $REGION --instance-id $INSTANCIA \
     --iam-instance-profile Name=<salida PerfilInstancia>
   aws ec2 modify-instance-attribute --region $REGION --instance-id $INSTANCIA \
     --groups <grupos actuales> <salida GrupoSeguridadWeb>
   ```

   El grupo nuevo abre 80 y 443. El despliegue y la consola usan SSM, así que el puerto 22 abierto a internet ya no hace falta: conviene quitarlo y entrar con `aws ssm start-session --target $INSTANCIA`.

3. **Docker en la instancia.** Con la instancia encendida, el agente de SSM ya registrado y el script en `main`:

   ```bash
   aws ssm send-command --region $REGION --instance-ids $INSTANCIA --document-name AWS-RunShellScript \
     --parameters 'commands=["curl -fsSL https://raw.githubusercontent.com/nikotpab/marketplace-alojamientos-backend/main/deploy/preparar-instancia.sh | bash"]'
   ```

   `deploy/preparar-instancia.sh` instala Docker y Docker Compose (verifica el hash del binario) y crea `/opt/marketplace`.

4. **Parámetros de SSM.** `desplegar.sh` exige estos en `/marketplace/prod`:
   - `DB_URL`, `DB_USUARIO_APP`, `DB_CONTRASENA_APP`, `DB_USUARIO_MIGRACION`, `DB_CONTRASENA_MIGRACION`;
   - `JWT_SECRETO`, `PAGOS_PROVEEDOR`, `DOMINIO`;
   - `CORREO_SERVIDOR`, `CORREO_PUERTO`, `CORREO_USUARIO`, `CORREO_CONTRASENA`, `CORREO_SEGURO`, `CORREO_REMITENTE` (ver "Correos").

   Los secretos van como SecureString, creados desde la consola. `STRIPE_CLAVE_SECRETA` y `STRIPE_METODO_PAGO` son opcionales. `IMAGEN_API` ya no se usa: la imagen la elige el despliegue.

5. **Variables de GitHub.** En cada repositorio, con las salidas del stack:

   ```bash
   gh variable set AWS_ROL_DESPLIEGUE --body <salida RolDespliegueApi>
   gh variable set AWS_DOCUMENTO_DESPLIEGUE --body <salida DocumentoDesplegarApi>
   gh variable set AWS_INSTANCIA --body $INSTANCIA
   ```

   En el frontend van `RolDespliegueWeb` y `DocumentoDesplegarWeb` (`-R nikotpab/marketplace-alojamientos-frontend`).

6. **Primer despliegue.** Se corre el workflow CI en `main` de cada repositorio, primero el backend: `gh workflow run CI --ref main`.

### Operación

- **Volver a una versión anterior:** se busca el commit en ECR y se corre el documento con esa etiqueta:

  ```bash
  aws ecr describe-images --region $REGION --repository-name marketplace-api \
    --query 'reverse(sort_by(imageDetails,&imagePushedAt))[].[imageTags[0],imagePushedAt]' --output text
  aws ssm send-command --region $REGION --instance-ids $INSTANCIA \
    --document-name <salida DocumentoDesplegarApi> --parameters etiqueta=<commit>
  ```

  Lo mismo con `marketplace-web` y `DocumentoDesplegarWeb`. ECR conserva las 10 imágenes más recientes de cada repositorio.
- **Cambiar un secreto:** se actualiza el parámetro en SSM y se vuelve a desplegar. `desplegar.sh` reescribe `.env` y, si cambió `DB_CONTRASENA_APP`, también la contraseña del usuario en RDS.
- **Entrar a la instancia:** `aws ssm start-session --target $INSTANCIA`; el despliegue vive en `/opt/marketplace`.

## Monitoreo

Cada 30 s la aplicación escribe una línea `Latido` con el campo `latido` (`OK` o `FALLA`) y el estado de cada componente: `baseDeDatos` y `auditoria`. `auditoria` figura caída si algún registro de auditoría fue alterado: cada 10 min, `VerificacionAuditoria` recalcula el hash de cada uno (ASR-07).

En AWS, `deploy/aws/monitoreo.yaml` (SCRUM-57) crea:

1. **Filtro de métrica en `/marketplace/api`:** con el patrón `{ $.latido = "OK" }`, suma 1 a `Marketplace/LatidoOk` por cada latido correcto.
2. **Alarma de latido:** se dispara si la suma es menor que 1 durante 2 periodos de 1 minuto, con los datos faltantes tratados como incumplidos. Avisa por correo al tema de SNS del equipo, también cuando se recupera, y su historial registra cada falla.
   - Un solo filtro cubre los dos casos: si la aplicación se detiene, faltan los datos; si un componente falla, el latido deja de ser `OK`.
3. **Recuperación de la EC2:**
   - si falla el host (`StatusCheckFailed_System`), la recuperación automática simplificada de EC2, activa por defecto en la t4g (`MaintenanceOptions.AutoRecovery = default`), la mueve a otro host con la misma IP y el mismo disco;
   - si deja de responder su sistema (`StatusCheckFailed_Instance`), hay que reiniciarla: `aws ec2 reboot-instances`;
   - en los dos casos, una alarma avisa al equipo. CloudWatch no aceptó una acción `Recover` en esta instancia, así que las alarmas solo avisan.

Si la API se cuelga sin caerse, la reinicia el timer de SCRUM-122 (decisión pendiente 14). La disponibilidad mensual de RNF-09 se mide con la misma métrica: son los minutos con `LatidoOk` mayor o igual a 1 sobre los minutos del mes.

Se crea después del stack de despliegue, que es el que crea el grupo de logs:

```bash
aws cloudformation deploy --region us-east-1 --stack-name marketplace-monitoreo \
  --template-file deploy/aws/monitoreo.yaml \
  --parameter-overrides InstanciaId=<id de la EC2> CorreoAlertas=<correo del equipo>
```

SNS envía un correo para confirmar la suscripción; hasta que alguien lo confirme, las alarmas no llegan. Para probarla, se detiene la API y en unos 2 minutos debe llegar el aviso:

```bash
aws ssm start-session --target <id de la EC2>
sudo docker stop marketplace-marketplace-api-1   # detenida a mano, Docker no la vuelve a levantar
sudo docker start marketplace-marketplace-api-1  # después del aviso
```

## Decisiones pendientes

Los documentos no las resuelven y bloquean la implementación de lo indicado:

**Afectan el flujo crítico**

1. **Reserva a medias (RNF-12).** SEC-01 solo escribe el pago en el paso 10. Si la aplicación cae después de cobrar, no queda rastro local y el reconciliador no tiene qué revisar. Hace falta registrar el intento antes de llamar a Stripe. Resuelto en SCRUM-59: el cobro se registra como `PENDIENTE` antes de llamar a la pasarela y pasa a `APROBADO` en la transacción de la reserva. Cada 5 min, `ServicioPagos.revertirCobrosSinReserva` devuelve los que llevan 10 min pendientes, así que quedan resueltos en máximo 15 min. Pide el cobro con la misma clave: si ya se hizo, la pasarela devuelve el mismo pago sin cobrar otra vez (ADR-04); si nunca se hizo, se cobra y se devuelve en el acto. Un periodo bloqueado sin pago no puede darse, porque la reserva y el pago se guardan en la misma transacción.
2. **Método de pago de prueba.** Resuelto en SCRUM-116: `POST /api/v1/reservas` recibe `metodoPagoId`, el identificador de un método de prueba de Stripe (`pm_card_...`); otro valor responde 400. Sin él se usa `STRIPE_METODO_PAGO`. El método queda en `pago.metodo_pago`, porque la reconciliación repite el cobro con la misma clave y Stripe exige los mismos parámetros. La pasarela simulada rechaza, como Stripe, los que empiezan por `pm_card_charge` (por ejemplo `pm_card_chargeDeclined`).
3. **Devolución fallida.** CU-20 E4 supone que la reserva no cambia de estado. El Proceso 4 la deja cancelada con "devolución no procesada". Decisión tomada en SCRUM-37: se sigue el Proceso 4. La reserva queda cancelada con `DEVOLUCION_NO_PROCESADA`, la falla queda en la auditoría como `DevolucionFallida` y se notifica al huésped. El pago sigue `APROBADO`, para poder devolverlo después.
4. **Reintento tras un solape.** Resuelto en SCRUM-34: el rechazo se guarda con su clave en una transacción aparte, y un reintento recibe el mismo rechazo sin volver a cobrar.

**Faltan reglas o datos**

5. **Tarifa de limpieza.** Los mockups muestran un monto fijo (COP 60.000); RF-32, CU-28 y el modelo de datos, un porcentaje. Decisión tomada en SCRUM-115: es un porcentaje del subtotal, como dicen los documentos, y arranca en 5 % (`V13`); en 3 noches de COP 300.000 son COP 45.000, cerca del mockup. El administrador lo cambia en `PUT /api/v1/admin/tarifas`.
6. **Políticas Moderada y Estricta.** El mockup las ofrece, pero no tenían reglas definidas. Decisión tomada en SCRUM-114, con la tarifa de servicio no reembolsable en las tres:

   | Días antes de la llegada | Flexible | Moderada | Estricta |
   |---|---|---|---|
   | 14 o más | 100 % | 100 % | 50 % |
   | 7 a 13 | 100 % | 50 % | 0 % |
   | 5 a 6 | 100 % | 0 % | 0 % |
   | 2 a 4 | 50 % | 0 % | 0 % |
   | Menos de 2 | 0 % | 0 % | 0 % |

   `politica_cancelacion` guarda un solo tramo y las políticas tienen hasta dos, así que los tramos viven en una clase por política (`PoliticaFlexible`, `PoliticaModerada`, `PoliticaEstricta`) y la tabla solo da el nombre y la descripción (`V14`). Una política sin clase sigue respondiendo 409 con un mensaje claro (SCRUM-38).
7. **Código de recuperación (CU-03).** Resuelto en SCRUM-110: el enlace llega por correo y la API ya no devuelve el código. Los tokens se guardan en `token_correo`, solo con su hash. Ver "Correos".
8. **Retención de claves de idempotencia (ADR-04).** Decisión tomada en SCRUM-118: 24 horas, lo mínimo que Stripe conserva las suyas (propuesta de SCRUM-34). Cada hora, `ServicioReservas.eliminarSolicitudesVencidas` borra las solicitudes vencidas y `ServicioPagos.liberarClavesVencidas` quita la clave de los pagos ya resueltos. Así, un reintento con la misma clave después de 24 h es un intento nuevo y no sobrescribe el pago anterior. Los pagos `PENDIENTE` conservan la clave, porque la reconciliación la usa.
9. **Duración del JWT y revocación.** Los tokens duran 8 horas. Resuelto en SCRUM-117: cada token lleva la versión de sesión del usuario (`usuario.version_sesion`, `V15`), y `TokensJwt` la compara en cada petición con una consulta por llave primaria, sin caché, así que el efecto es inmediato. Restringir, desactivar la cuenta o restablecer la contraseña sube la versión, y la siguiente petición con un token anterior responde 401. Un token de una cuenta desactivada o restringida tampoco sirve. Se eligió una versión y no la fecha de emisión porque el `iat` del JWT va en segundos: un token anterior y un inicio de sesión del mismo segundo no se distinguirían.

**Transiciones sin caso de uso**

10. Reactivar una cuenta desactivada (CU-05) y desbloquear una publicación. Resuelto en SCRUM-112: el administrador desbloquea una publicación (`POST /api/v1/admin/publicaciones/{id}/desbloqueo`), que vuelve a ACTIVA sin pasar por revisión, como una pausada (CU-10). También levanta una restricción (`DELETE /api/v1/admin/usuarios/{id}/restriccion`) y reactiva una cuenta desactivada (`POST /api/v1/admin/usuarios/{id}/reactivacion`); el usuario no puede reactivarla solo, porque una cuenta desactivada no inicia sesión. Cada acción queda en la auditoría y avisa al afectado.
11. Resolver una reserva `EN_REVISION`. Además, la restricción anti-solape solo cubre `CONFIRMADA`, como pide ADR-03. Decisión tomada en SCRUM-112 y SCRUM-118:
    - al desbloquear la publicación, sus reservas en revisión vuelven a `CONFIRMADA` y se avisa a cada huésped. Mientras estuvo bloqueada nadie pudo reservar, así que no chocan con la restricción anti-solape;
    - mientras tanto, el huésped puede cancelarla con devolución total, incluida la tarifa de servicio, porque el bloqueo no es culpa suya.
12. Si editar una publicación activa la devuelve a revisión (CU-07). Decisión tomada en SCRUM-118: si el anfitrión edita una publicación `ACTIVA` o `PAUSADA`, vuelve a `PENDIENTE_REVISION` y sale de la búsqueda hasta que se apruebe. Una pausada también, porque se reactiva sin revisión y sería un atajo. Las reservas que ya tiene no cambian, y lo que edita un administrador conserva el estado.
13. Pasar una reserva a `COMPLETADA`: ningún caso de uso lo define y RF-25 lo necesita. Decisión tomada en SCRUM-40: cada noche a la 1:00, hora de Bogotá, `ServicioReservas.completarReservasFinalizadas` pasa a `COMPLETADA` las confirmadas cuya fecha de salida ya pasó y avisa al huésped de que ya puede reseñar.

**Despliegue**

14. **Contenedor colgado.** `restart: always` no reinicia un contenedor que solo está `unhealthy`. Resuelto en SCRUM-122: el timer de systemd `marketplace-autorreparacion` corre cada minuto `deploy/autorreparacion/reiniciar-colgados.sh`, que reinicia los contenedores del proyecto `marketplace` que su healthcheck marcó `unhealthy`, sin darle el socket de Docker a otro contenedor. Con el healthcheck de la imagen (cada 30 s, 3 fallos), una API colgada vuelve en menos de 3 minutos, dentro del MTTR de 15 de SCRUM-57. Cada reinicio queda en el journal (`journalctl -t marketplace-autorreparacion`), y mientras la API está caída falta el latido, así que salta la alarma del monitoreo. Se instala en la EC2 con `sudo sh /opt/marketplace/autorreparacion/instalar.sh`, que se puede repetir.
15. **Secretos e imagen.** Resuelto en SCRUM-121: la imagen se publica en ECR (`marketplace-api`, con el commit como etiqueta) y `deploy/desplegar.sh` escribe en la EC2 el `.env` con los parámetros de SSM. La infraestructura nueva está en `deploy/aws/despliegue.yaml`; la EC2 y RDS, que ya existían, siguen fuera de este repositorio. Ver "Despliegue en AWS".
16. **"Estado del dominio" en el latido.** El diagrama de despliegue y SCRUM-57 lo piden, pero no definen qué significa. Por ahora el latido reporta `baseDeDatos` y `auditoria`. Si se refería al dominio web o a la pasarela de pagos, falta definirlo y agregarlo.

**Sin caso de uso**

17. **Reportar una reseña (RF-29).** RF-29 pide que las reseñas reportadas aparezcan en Moderación con su número de reportes, pero ningún caso de uso permitía reportarlas y el modelo de datos no tenía dónde guardar los reportes. Decisión tomada en SCRUM-111:
    - cualquier usuario con sesión, salvo el autor, reporta una reseña visible una sola vez (`POST /api/v1/resenas/{id}/reportes`), con un motivo de la lista (`OFENSIVA`, `SPAM`, `INFORMACION_FALSA` u `OTRO`) y un comentario opcional de hasta 500 caracteres. Repetir o reportar la propia responde 409;
    - los reportes viven en `reporte_resena` (`V16`) y quedan en la auditoría como `ResenaReportada`;
    - `GET /api/v1/admin/resenas/reportadas` lista las reportadas que siguen visibles, de la más reportada a la menos, con su número de reportes y sus motivos;
    - el administrador las oculta con `POST /api/v1/admin/resenas/{id}/ocultamiento`.
