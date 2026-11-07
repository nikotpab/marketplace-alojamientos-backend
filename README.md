# marketplace-alojamientos-backend

API del Marketplace de Alojamientos de Inversiones LR. Monolito modular con capas y regla de dependencia (ADR-01).

**Estado: en construcción.** La aplicación arranca y todas las rutas existen. Lo que aún no se implementa responde `501`.

Ya funcionan: el esquema de base de datos, la seguridad con denegación por defecto, el registro de usuarios, la edición de publicaciones, la confirmación de reservas con idempotencia, los pagos, la auditoría, las notificaciones, los logs y las pruebas de arquitectura.

Aún no se puede reservar de punta a punta: faltan la cotización (SCRUM-32) y las sesiones JWT (SCRUM-17). Las pruebas las simulan.

## Ejecutar

Requiere Java 21, Maven y Docker.

```bash
cp .env.example .env      # y cambiar las contraseñas
docker compose up -d --wait db
mvn spring-boot:run
curl localhost:8080/actuator/health
```

`mvn test` corre las pruebas de arquitectura. `mvn verify` agrega la prueba de integración, que usa Docker.

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

Cada módulo se divide en las capas que necesita:

| Paquete | Contiene | Puede depender de |
|---|---|---|
| `presentacion` | Controladores REST | `aplicacion`, `dominio`, `api` |
| `aplicacion` | `Servicio<Módulo>`, con un método por caso de uso | `dominio`, `api` de otros módulos |
| `dominio` | Modelo y puertos, sin frameworks | `api` propia |
| `infraestructura` | SQL y adaptadores | `dominio` |
| `api` | Lo único visible para otros módulos | nada interno |

`ArquitecturaTest` hace cumplir estas fronteras y bloquea el merge si se rompen.

## Dónde vive cada decisión

| Decisión | Dónde |
|---|---|
| ADR-01 Capas y módulos | `ArquitecturaTest` |
| ADR-02 Búsqueda indexada, 20 por página, tiempo máximo | `RepositorioBusquedaSql`, `ServicioBusqueda`, índices de `V1` |
| ADR-03 Anti-solape | Restricción `reserva_sin_solape` en `V1` |
| ADR-04 Idempotencia | Cabecera `Idempotency-Key`, tabla `solicitud_procesada`, `ServicioReservas.confirmarReserva` |
| ADR-05 Timeout y circuit breaker | `CircuitBreakerPagos`, `AdaptadorStripe` |
| ADR-06 Eventos, auditoría y outbox | `PublicadorEventosOutbox`, `ProcesadorEventos`, `ServicioAuditoria`, `V2` |
| ADR-07 Autorización | Tabla de rutas en `ConfiguracionSeguridad`, `ManejadorDenegaciones` |
| ADR-08 Proveedor de pagos | `PasarelaPagos`, `ConfiguracionPagos` |
| RNF-18, RNF-25 Logs | `FiltroCorrelacion`, `EnmascaradorLogs` |
| RNF-26 HTTPS | `deploy/Caddyfile` |
| RNF-27 Credenciales | `.env`, gitleaks en `.github/workflows/ci.yml` |
| RNF-31 Nueva política | `PoliticaCancelacion` |

## Simplificaciones respecto a los documentos

Para mantener el MVP mínimo:

- **Filtro de autorización:** el `FiltroAutorizacion` del Patrón 6 es el `AuthorizationFilter` de Spring Security con una sola tabla de rutas, no una clase propia.
- **Fachadas:** solo tienen `api/` los módulos a los que otro módulo llama. `busqueda`, `cancelaciones`, `notificaciones` y `administracion` no la necesitan todavía.
- **Casos de uso:** son métodos del `Servicio` de cada módulo. Por ejemplo, `ServicioReservas.confirmarReserva` es el `ConfirmarReserva` de SEC-01.
- **Eventos:** hay un solo tipo, `EventoDominio`, con un campo `tipo` (por ejemplo, `"ReservaConfirmada"`), en vez de una clase por evento.

Elecciones técnicas que los documentos dejaban abiertas: Spring JDBC en vez de JPA, Flyway, Resilience4j y BCrypt.

## Ajustes al diseño

Contradicciones que hubo que resolver para poder escribir el esqueleto. Conviene validarlas con el equipo:

1. **Credenciales de auditoría.** Los documentos piden credenciales propias y además escribir en la misma transacción del caso de uso. Con otro usuario habría otra conexión y otra transacción. Se resolvió con un esquema aparte cuyo dueño es el usuario de migración, permisos de solo `INSERT` y `SELECT` para el usuario de la aplicación, y triggers que rechazan cambios a cualquier rol.
2. **`numero_huespedes`.** Está en el diagrama de clases y en los mockups, pero no en el modelo de datos. Se agregó a `reserva`.
3. **`NOT NULL`.** El modelo de datos solo lo marca en claves y `creado_en`. Se agregó donde la integridad lo exige, por ejemplo en las fechas de `reserva`: con fechas nulas, la restricción anti-solape bloquearía todo el calendario.
4. **Cruces entre módulos.** `Reserva.cancelar()` no devuelve una `Cancelacion`, porque está en otro módulo. `calcularDevolucion` recibe `(reserva, fecha)` como en SEC-04, no solo la reserva como en el diagrama de clases.
5. **Nombres.** Se usan los del modelo de datos (`solicitud_procesada`, `evento_pendiente`) y `RepositorioBusqueda` del Patrón 1, no `RepositorioPublicaciones` de SEC-06.

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
3. **Devolución fallida.** CU-20 E4 supone que la reserva no cambia de estado. El Proceso 4 la deja cancelada con "devolución no procesada".
4. **Reintento tras un solape.** Resuelto en SCRUM-34: el rechazo se guarda con su clave en una transacción aparte, y un reintento recibe el mismo rechazo sin volver a cobrar.

**Faltan reglas o datos**

5. **Tarifa de limpieza.** Los mockups muestran un monto fijo (COP 60.000); RF-32, CU-28 y el modelo de datos, un porcentaje. Por eso `LIMPIEZA` queda en `NULL` en `V3`.
6. **Políticas Moderada y Estricta.** El mockup las ofrece, pero no tienen reglas definidas. Solo existe `PoliticaFlexible`. Además, `politica_cancelacion` guarda un solo tramo y la flexible tiene dos, así que sus reglas viven en la clase.
7. **Código de recuperación (CU-03).** No hay tabla donde guardarlo. Por ahora vive en memoria 15 minutos y, como la recuperación es simulada, la API lo devuelve en la respuesta. **Cualquiera que conozca un correo puede cambiar esa contraseña: no debe llegar a producción así.**
8. **Retención de claves de idempotencia (ADR-04).** Propuesta en SCRUM-34: 24 horas, lo mínimo que Stripe conserva las suyas. Falta validarla con el equipo.
9. **Duración del JWT y revocación.** Por ahora los tokens duran 8 horas. Un usuario restringido o desactivado conserva su token hasta que venza, salvo que se verifique en cada petición.

**Transiciones sin caso de uso**

10. Reactivar una cuenta desactivada (CU-05) o una publicación pausada (CU-10), y desbloquear una publicación.
11. Resolver una reserva `EN_REVISION`. Además, la restricción anti-solape solo cubre `CONFIRMADA`, como pide ADR-03.
12. Si editar una publicación activa la devuelve a revisión (CU-07).
13. Pasar una reserva a `COMPLETADA`: ningún caso de uso lo define y RF-25 lo necesita. Queda una tarea programada vacía en `ServicioReservas`.

**Despliegue**

14. **Contenedor colgado.** `restart: always` no reinicia un contenedor que solo está `unhealthy`. Para cumplir "un healthcheck si se cuelga" hace falta algo más.
15. **Secretos e imagen.** Falta el paso que pasa los secretos de SSM a `deploy/.env`, y definir dónde se publica la imagen. La infraestructura AWS no es parte de este repositorio.
16. **"Estado del dominio" en el latido.** El diagrama de despliegue y SCRUM-57 lo piden, pero no definen qué significa. Por ahora se reporta el procesador de eventos, que ADR-06 pide monitorear con latido. Si se refería al dominio web, falta agregarlo.
