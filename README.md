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

Los correos de desarrollo (verificación y recuperación) llegan a Mailpit: http://localhost:8025.

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

El frontend agrega la versión: sus servicios llaman a `/api/...` y su interceptor (`version-api.interceptor.ts`) las convierte en `/api/v1/...`. En desarrollo, el proxy de Angular reenvía `/api` a `http://localhost:8080` sin reescribir la ruta, y en el servidor Caddy reenvía `/api/*` tal cual (`deploy/Caddyfile`). No hay que agregar un `uri replace` en Caddy: las peticiones quedarían en `/api/v1/v1/...`.

### Correos (SCRUM-109, SCRUM-110)

Al registrarse, el usuario recibe un enlace para verificar su correo y, hasta usarlo, no puede iniciar sesión. La recuperación de contraseña también envía un enlace. Los dos son de un solo uso: el token tiene 256 bits aleatorios y en la base (`token_correo`) solo se guarda su hash SHA-256. Pedir un enlace nuevo anula el anterior, y entre dos correos del mismo tipo a la misma cuenta pasa al menos 1 minuto.

El correo se envía por SMTP después del commit y en otro hilo. Si el servidor de correo falla, el registro no se deshace (ASR-05) y el usuario puede pedir otro enlace.

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
| `POST /api/v1/auth/register` | igual que antes | 201; ahora incluye `correoVerificado: false` |
| `POST /api/v1/auth/login` | igual | 403 con `codigo: CORREO_NO_VERIFICADO` si falta verificar, solo con la contraseña correcta |
| `POST /api/v1/auth/email/verify` | `{"token"}` | 204, o 400 con `campos.token` si no sirve o venció |
| `POST /api/v1/auth/email/resend` | `{"correo"}` | 202 siempre |
| `POST /api/v1/auth/password/recover` | `{"correo"}` | 202 siempre, **sin token** (antes lo devolvía) |
| `POST /api/v1/auth/password/reset` | `{"token", "contrasenaNueva"}` | 204; también deja verificado el correo |

Los enlaces llevan a `URL_PUBLICA + /verificar-correo?token=…` (vence en 24 horas) y `URL_PUBLICA + /recuperar?token=…` (15 minutos). El frontend lee el token de la dirección y lo envía a la API.

### Documentación de la API (Swagger)

Con el backend corriendo, Swagger UI queda en http://localhost:8080/swagger-ui.html y la especificación OpenAPI en http://localhost:8080/v3/api-docs. Se generan desde los controladores, así que siempre coinciden con el código. Solo documentan `/api/v1/**`.

Para probar un endpoint con sesión: ejecuta `POST /api/v1/auth/login`, copia el campo `token` de la respuesta, pulsa **Authorize** y pégalo sin el prefijo `Bearer`.

En el servidor, Caddy solo enruta `/api/*` hacia la API, así que la documentación no queda expuesta a internet.

## Estructura

```
com.inversioneslr.marketplace
├── compartido       Núcleo común: seguridad, errores, eventos y outbox
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

Dos pruebas hacen cumplir estas fronteras y bloquean el merge si se rompen:

- `ModulosTest`: con Spring Modulith, que no haya ciclos entre módulos ni accesos a la parte interna de otro. También genera los diagramas.
- `ArquitecturaTest`: con ArchUnit, las capas dentro de cada módulo.

### Eventos

Los módulos se avisan con eventos de Spring (ADR-06). Por ejemplo, al confirmar una reserva:

1. `ServicioReservas` publica un `EventoDominio` con `PublicadorEventos`.
2. En la misma transacción, `ServicioAuditoria` lo registra (`@EventListener`). Si falla, la reserva no se confirma.
3. También en esa transacción, Spring Modulith lo guarda en `event_publication`, que hace de outbox.
4. Después de confirmar, en otro hilo, `ServicioNotificaciones` crea una notificación por destinatario (`@ApplicationModuleListener`).
5. Si eso falla, `ProcesadorEventos` lo reintenta cada 5 s, hasta 5 intentos. Una entrega que queda a medias, por ejemplo porque la aplicación se reinició, se marca fallida al minuto y también se reintenta.

Una entrega repetida no duplica la notificación, porque su id sale del id del evento y del destinatario.

## Dónde vive cada decisión

| Decisión | Dónde |
|---|---|
| ADR-01 Capas y módulos | `ModulosTest`, `ArquitecturaTest` |
| ADR-02 Búsqueda indexada, 20 por página, tiempo máximo | `RepositorioBusquedaSql`, `ServicioBusqueda`, índices de `V1` |
| ADR-03 Anti-solape | Restricción `reserva_sin_solape` en `V1` |
| ADR-04 Idempotencia | Cabecera `Idempotency-Key`, tabla `solicitud_procesada`, `ServicioReservas.confirmarReserva` |
| ADR-05 Timeout y circuit breaker | `CircuitBreakerPagos`, `AdaptadorStripe` |
| ADR-06 Eventos, auditoría y outbox | `PublicadorEventosSpring`, `ServicioAuditoria`, `ServicioNotificaciones`, `ProcesadorEventos`, `V2`, `V8` |
| ADR-07 Autorización | Tabla de rutas en `ConfiguracionSeguridad`, `ManejadorDenegaciones` |
| ADR-08 Proveedor de pagos | `PasarelaPagos`, `ConfiguracionPagos` |
| RNF-18, RNF-25 Logs | `FiltroCorrelacion`, `EnmascaradorLogs` |
| RNF-26 HTTPS | `deploy/Caddyfile` |
| RNF-27 Credenciales | `.env`, gitleaks en `.github/workflows/ci.yml` |
| RNF-12 Reservas a medias | `ServicioPagos.revertirCobrosSinReserva` |
| RNF-31 Nueva política | `PoliticaCancelacion` |

## Cómo se verifica cada ASR

| ASR | Qué se comprueba | Prueba |
|---|---|---|
| ASR-01 Rendimiento | p95 y p99 de cada escenario con la carga pedida | `pruebas/carga` (JMeter), ver la sección siguiente |
| ASR-02 Concurrencia | 50 solicitudes simultáneas por las mismas fechas: 1 confirmada, 49 rechazadas, 0 superposiciones | `ConfirmarReservaIT.conCincuentaSolicitudesSimultaneasSoloSeConfirmaUna` |
| ASR-03 Idempotencia | 10 envíos de la misma solicitud: 1 reserva, 1 cobro y 1 notificación | `IdempotenciaReservaIT.diezEnviosSimultaneosProducenUnSoloEfecto` |
| ASR-04 Pasarela caída | Respuesta antes de 7 s sin reserva ni periodo bloqueado; circuito abierto tras 5 fallas y prueba a los 30 s | `AdaptadorStripeTest`, `CircuitBreakerPagosTest`, `ConfirmarReservaIT.pagoNoProcesadoNoCreaLaReservaNiBloqueaElPeriodo` |
| ASR-05 Componente no crítico | Notificaciones lentas o caídas no frenan buscar, reservar ni cancelar; los avisos se entregan al volver | `ComponenteNoCriticoIT` |
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
- **Eventos:** hay un solo tipo, `EventoDominio`, con un campo `tipo` (por ejemplo, `"ReservaConfirmada"`), en vez de una clase por evento.
- **Historial de tarifas:** RF-32 pide mostrar quién cambió las tarifas y cuándo. Cada cambio queda en la auditoría como `TarifasActualizadas`, con el valor anterior y el nuevo (por ejemplo, "10 % → 12 %"), así que el historial se consulta en `GET /api/v1/admin/auditoria`. La tabla solo guarda quién hizo el último cambio.

Elecciones técnicas que los documentos dejaban abiertas: Spring JDBC en vez de JPA, Flyway, Resilience4j y BCrypt.

## Ajustes al diseño

Contradicciones que hubo que resolver para poder escribir el esqueleto. Conviene validarlas con el equipo:

1. **Credenciales de auditoría.** Los documentos piden credenciales propias y además escribir en la misma transacción del caso de uso. Con otro usuario habría otra conexión y otra transacción. Se resolvió con un esquema aparte cuyo dueño es el usuario de migración, permisos de solo `INSERT` y `SELECT` para el usuario de la aplicación, y triggers que rechazan cambios a cualquier rol.
2. **`numero_huespedes`.** Está en el diagrama de clases y en los mockups, pero no en el modelo de datos. Se agregó a `reserva`.
3. **`NOT NULL`.** El modelo de datos solo lo marca en claves y `creado_en`. Se agregó donde la integridad lo exige, por ejemplo en las fechas de `reserva`: con fechas nulas, la restricción anti-solape bloquearía todo el calendario.
4. **Cruces entre módulos.** `Reserva.cancelar()` no devuelve una `Cancelacion`, porque está en otro módulo. `calcularDevolucion` recibe `(reserva, fecha)` como en SEC-04, no solo la reserva como en el diagrama de clases.
5. **Nombres.** Se usan los del modelo de datos (`solicitud_procesada`) y `RepositorioBusqueda` del Patrón 1, no `RepositorioPublicaciones` de SEC-06.
6. **Borradores incompletos.** CU-06 pide rechazar un borrador sin datos obligatorios, pero el frontend permite guardarlo incompleto y RF-08 valida la información mínima al solicitar la activación. Se sigue a RF-08: el tipo y la política son opcionales en un borrador. Una publicación que ya no es borrador no puede quedar incompleta al editarla.
7. **Ciudad (temporal).** El formulario del frontend no pide ciudad. Se toma lo que va después de la última coma de la ubicación, y la búsqueda también busca la ciudad en la ubicación. Lo correcto es agregar el campo en el formulario.
8. **Política de cancelación (temporal).** El frontend la envía como texto libre. Se asocia a la política cuyo nombre aparece en el texto y, si ninguna aparece, a la Flexible. Lo correcto es que el formulario ofrezca las políticas para elegir, porque la devolución se calcula según la política elegida.
9. **Disponibilidad por defecto (temporal).** El frontend asume que todo está disponible salvo lo bloqueado, y el backend exige periodos disponibles. Una publicación nueva queda disponible dos años desde su creación, y el anfitrión bloquea o libera rangos de días, con ambos extremos incluidos. Al liberar, el rango se une con los periodos disponibles que toca, y así una estancia siempre cabe en un único periodo, como exigen la reserva y la búsqueda.
10. **Errores por campo.** Los errores por campo van en `campos` y también en `errores`, el nombre que lee el frontend. La información mínima que falta para publicar responde 422.
11. **Código de reserva.** El frontend muestra un código legible, como `LR-2026-0001`, que no está en el modelo de datos. Se agregó la columna `codigo`, que asigna la base de datos al guardar la reserva. Las peticiones y respuestas de reserva usan los nombres del frontend (`llegada`, `salida`, `huespedes`, `adicionales`).
12. **Base de la cotización.** RF-17 no dice sobre qué monto se aplican los porcentajes. Como en el mockup, la tarifa de servicio y los descuentos se calculan sobre el subtotal (precio por noche × noches) y se redondean al peso. El descuento promocional y el de estancia larga se suman.
13. **Interfaz pública en la raíz del módulo.** ADR-01 y SCRUM-54 la ubican en un paquete `api/`. Spring Modulith, el estándar de Spring para monolitos modulares, trata los subpaquetes como internos, así que la fachada y sus tipos están en el paquete raíz de cada módulo (SCRUM-98). La regla no cambia: otro módulo solo usa lo público.
14. **Tabla de eventos.** El modelo de datos define `evento_pendiente` para el outbox. Con Spring Modulith, el outbox es su tabla `event_publication`, con el esquema oficial (`V8`, SCRUM-98). Además, `EventoDominio` lleva un `id`, que no está en los documentos, para que una entrega repetida no duplique la notificación.
15. **Indicadores del dashboard.** RF-33 no define "ingresos estimados" ni el periodo. Los ingresos son la suma de las tarifas de servicio de las reservas confirmadas y completadas, que es lo que gana la plataforma. El periodo cuenta las reservas por la fecha en que se hicieron y, si no se elige, son los últimos 30 días. Usuarios y publicaciones activas son el total actual.
16. **Confirmaciones simultáneas (ADR-03).** La restricción de exclusión sola no basta: con varios `INSERT` simultáneos por la misma publicación, PostgreSQL puede detectar un deadlock al verificarla y abortar solicitudes que debían rechazarse con "periodo no disponible". Antes del `INSERT`, cada confirmación toma un candado de transacción por publicación (`pg_advisory_xact_lock`), así que entran de a una. La restricción sigue siendo la garantía de no solape (SCRUM-102).
17. **Verificación del correo (SCRUM-109).** Los documentos no la piden: CU-01 crea la cuenta lista para usarse. Se agregó para que nadie se registre con un correo ajeno, ahora que la recuperación de contraseña llega por correo. Hasta verificar, el inicio de sesión responde 403 `CORREO_NO_VERIFICADO`, pero solo con la contraseña correcta, para no revelar el estado de la cuenta. Las cuentas que ya existían, las de los datos de demostración y las creadas por SQL (como el primer administrador) quedan verificadas: la columna nace en `true` y el registro guarda `false`.

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

## Monitoreo

Cada 30 s la aplicación escribe una línea `Latido` con el campo `latido` (`OK` o `FALLA`) y el estado de cada componente: `baseDeDatos`, `pasarelaPagos`, `procesadorEventos` y `auditoria`. `auditoria` figura caída si algún registro de auditoría fue alterado: cada 10 min, `VerificacionAuditoria` recalcula el hash de cada uno (ASR-07).

`pasarelaPagos` no llama a Stripe: solo figura caída si el circuit breaker está abierto. Sin cobros recientes, una caída de Stripe no se detecta.

**Falta configurar en AWS:**

1. Un filtro de métrica en `/marketplace/api`, con el patrón `{ $.latido = "OK" }` y valor 1.
2. Una alarma sobre esa métrica: suma menor que 1 durante 2 periodos de 1 minuto, con los datos faltantes tratados como incumplidos, que avise al tema de SNS del equipo.

Con un solo filtro se cubren los dos casos: si la aplicación se detiene, faltan los datos; si un componente falla, el latido deja de ser `OK`.

## Decisiones pendientes

Los documentos no las resuelven y bloquean la implementación de lo indicado:

**Afectan el flujo crítico**

1. **Reserva a medias (RNF-12).** SEC-01 solo escribe el pago en el paso 10. Si la aplicación cae después de cobrar, no queda rastro local y el reconciliador no tiene qué revisar. Hace falta registrar el intento antes de llamar a Stripe. Resuelto en SCRUM-59: el cobro se registra como `PENDIENTE` antes de llamar a la pasarela y pasa a `APROBADO` en la transacción de la reserva. Cada 5 min, `ServicioPagos.revertirCobrosSinReserva` devuelve los que llevan 10 min pendientes, así que quedan resueltos en máximo 15 min. Pide el cobro con la misma clave: si ya se hizo, la pasarela devuelve el mismo pago sin cobrar otra vez (ADR-04); si nunca se hizo, se cobra y se devuelve en el acto. Un periodo bloqueado sin pago no puede darse, porque la reserva y el pago se guardan en la misma transacción.
2. **Método de pago de prueba.** CU-17 y CU-19 lo piden como entrada, pero `cobrar(monto, clave)` no lo recibe. Por ahora sale de la variable `STRIPE_METODO_PAGO`, así que no se puede elegir por petición.
3. **Devolución fallida.** CU-20 E4 supone que la reserva no cambia de estado. El Proceso 4 la deja cancelada con "devolución no procesada". Decisión tomada en SCRUM-37: se sigue el Proceso 4. La reserva queda cancelada con `DEVOLUCION_NO_PROCESADA`, la falla queda en la auditoría como `DevolucionFallida` y se notifica al huésped. El pago sigue `APROBADO`, para poder devolverlo después.
4. **Reintento tras un solape.** Resuelto en SCRUM-34: el rechazo se guarda con su clave en una transacción aparte, y un reintento recibe el mismo rechazo sin volver a cobrar.

**Faltan reglas o datos**

5. **Tarifa de limpieza.** Los mockups muestran un monto fijo (COP 60.000); RF-32, CU-28 y el modelo de datos, un porcentaje. Por eso `LIMPIEZA` queda en `NULL` en `V3`, y la cotización la cobra en 0 hasta que el administrador le asigne un porcentaje en `PUT /api/v1/admin/tarifas`.
6. **Políticas Moderada y Estricta.** El mockup las ofrece, pero no tienen reglas definidas. Solo existe `PoliticaFlexible`. Además, `politica_cancelacion` guarda un solo tramo y la flexible tiene dos, así que sus reglas viven en la clase. Decisión tomada en SCRUM-38: mientras no se definan, una política sin clase responde 409 con un mensaje claro, en vez de inventar reglas.
7. **Código de recuperación (CU-03).** Resuelto en SCRUM-110: el enlace llega por correo y la API ya no devuelve el código. Los tokens se guardan en `token_correo`, solo con su hash. Ver "Correos".
8. **Retención de claves de idempotencia (ADR-04).** Propuesta en SCRUM-34: 24 horas, lo mínimo que Stripe conserva las suyas. Falta validarla con el equipo.
9. **Duración del JWT y revocación.** Por ahora los tokens duran 8 horas. Un usuario restringido o desactivado conserva su token hasta que venza, salvo que se verifique en cada petición.

**Transiciones sin caso de uso**

10. Reactivar una cuenta desactivada (CU-05) y desbloquear una publicación. Por ahora una publicación pausada (CU-10) vuelve a ACTIVA sin pasar por revisión, y una bloqueada no se puede reactivar.
11. Resolver una reserva `EN_REVISION`. Además, la restricción anti-solape solo cubre `CONFIRMADA`, como pide ADR-03.
12. Si editar una publicación activa la devuelve a revisión (CU-07).
13. Pasar una reserva a `COMPLETADA`: ningún caso de uso lo define y RF-25 lo necesita. Decisión tomada en SCRUM-40: cada noche a la 1:00, hora de Bogotá, `ServicioReservas.completarReservasFinalizadas` pasa a `COMPLETADA` las confirmadas cuya fecha de salida ya pasó y avisa al huésped de que ya puede reseñar.

**Despliegue**

14. **Contenedor colgado.** `restart: always` no reinicia un contenedor que solo está `unhealthy`. Para cumplir "un healthcheck si se cuelga" hace falta algo más.
15. **Secretos e imagen.** Falta el paso que pasa los secretos de SSM a `deploy/.env`, y definir dónde se publica la imagen. La infraestructura AWS no es parte de este repositorio.
16. **"Estado del dominio" en el latido.** El diagrama de despliegue y SCRUM-57 lo piden, pero no definen qué significa. Por ahora se reporta el procesador de eventos, que ADR-06 pide monitorear con latido. Si se refería al dominio web, falta agregarlo.

**Sin caso de uso**

17. **Reportar una reseña (RF-29).** RF-29 pide que las reseñas reportadas aparezcan en Moderación con su número de reportes, pero ningún caso de uso permite reportarlas y el modelo de datos no tiene dónde guardar los reportes. Por ahora el administrador oculta una reseña desde la lista de la publicación (`POST /api/v1/admin/resenas/{id}/ocultamiento`).
