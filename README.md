# marketplace-alojamientos-backend

API del Marketplace de Alojamientos de Inversiones LR. Monolito modular con [Spring Modulith](https://docs.spring.io/spring-modulith/reference/), capas y regla de dependencia (ADR-01).

**Estado:** ya no quedan rutas sin implementar. Lo que falta, como la reconciliación de reservas a medias, depende de las [decisiones pendientes](#decisiones-pendientes).

Ya funcionan: el esquema de base de datos, la seguridad con denegación por defecto, el registro y el inicio de sesión, las publicaciones y su revisión, la búsqueda, la cotización, la confirmación de reservas con idempotencia, el historial, la cancelación con devolución, las reseñas, la moderación, la configuración de tarifas y de tipos de alojamiento, el dashboard, los reportes en PDF y Excel, los pagos, la auditoría, las notificaciones, los logs y las pruebas de arquitectura.

## Ejecutar

Requiere Java 21, Maven y Docker.

```bash
cp .env.example .env      # y cambiar las contraseñas
docker compose up -d --wait db
mvn spring-boot:run
curl localhost:8080/actuator/health
```

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

El frontend llama a `/api/...` y el backend responde en `/api/v1/...`. En desarrollo, el proxy de Angular une los dos: reenvía `/api` a `http://localhost:8080` y lo reescribe como `/api/v1`.

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
| RNF-31 Nueva política | `PoliticaCancelacion` |

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

Cada 30 s la aplicación escribe una línea `Latido` con el campo `latido` (`OK` o `FALLA`) y el estado de cada componente: `baseDeDatos`, `pasarelaPagos` y `procesadorEventos`.

`pasarelaPagos` no llama a Stripe: solo figura caída si el circuit breaker está abierto. Sin cobros recientes, una caída de Stripe no se detecta.

**Falta configurar en AWS:**

1. Un filtro de métrica en `/marketplace/api`, con el patrón `{ $.latido = "OK" }` y valor 1.
2. Una alarma sobre esa métrica: suma menor que 1 durante 2 periodos de 1 minuto, con los datos faltantes tratados como incumplidos, que avise al tema de SNS del equipo.

Con un solo filtro se cubren los dos casos: si la aplicación se detiene, faltan los datos; si un componente falla, el latido deja de ser `OK`.

## Decisiones pendientes

Los documentos no las resuelven y bloquean la implementación de lo indicado:

**Afectan el flujo crítico**

1. **Reserva a medias (RNF-12).** SEC-01 solo escribe el pago en el paso 10. Si la aplicación cae después de cobrar, no queda rastro local y el reconciliador no tiene qué revisar. Hace falta registrar el intento antes de llamar a Stripe.
2. **Método de pago de prueba.** CU-17 y CU-19 lo piden como entrada, pero `cobrar(monto, clave)` no lo recibe. Por ahora sale de la variable `STRIPE_METODO_PAGO`, así que no se puede elegir por petición.
3. **Devolución fallida.** CU-20 E4 supone que la reserva no cambia de estado. El Proceso 4 la deja cancelada con "devolución no procesada". Decisión tomada en SCRUM-37: se sigue el Proceso 4. La reserva queda cancelada con `DEVOLUCION_NO_PROCESADA`, la falla queda en la auditoría como `DevolucionFallida` y se notifica al huésped. El pago sigue `APROBADO`, para poder devolverlo después.
4. **Reintento tras un solape.** Resuelto en SCRUM-34: el rechazo se guarda con su clave en una transacción aparte, y un reintento recibe el mismo rechazo sin volver a cobrar.

**Faltan reglas o datos**

5. **Tarifa de limpieza.** Los mockups muestran un monto fijo (COP 60.000); RF-32, CU-28 y el modelo de datos, un porcentaje. Por eso `LIMPIEZA` queda en `NULL` en `V3`, y la cotización la cobra en 0 hasta que el administrador le asigne un porcentaje en `PUT /api/v1/admin/tarifas`.
6. **Políticas Moderada y Estricta.** El mockup las ofrece, pero no tienen reglas definidas. Solo existe `PoliticaFlexible`. Además, `politica_cancelacion` guarda un solo tramo y la flexible tiene dos, así que sus reglas viven en la clase. Decisión tomada en SCRUM-38: mientras no se definan, una política sin clase responde 409 con un mensaje claro, en vez de inventar reglas.
7. **Código de recuperación (CU-03).** No hay tabla donde guardarlo. Por ahora vive en memoria 15 minutos y, como la recuperación es simulada, la API lo devuelve en la respuesta. **Cualquiera que conozca un correo puede cambiar esa contraseña: no debe llegar a producción así.**
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
