# Estrategia de pruebas

La pirámide de pruebas organiza los tests en capas: muchas pruebas rápidas, aisladas y baratas en la base
(unitarias), y pocas pruebas lentas y costosas en la cima (E2E). Cuanto más abajo está una prueba, más
rápido falla y más fácil es localizar la causa. Usamos esta jerarquía para maximizar la cobertura con el
menor tiempo de ejecución posible: las unitarias corren en segundos en cualquier máquina; las de integración
necesitan Docker; las E2E necesitan el stack completo.

La trazabilidad completa de requisitos a pruebas está en la **matriz de trazabilidad (SCRUM-75)**.

## Por qué forma de trofeo y no de pirámide

En este proyecto hay más pruebas de integración (43 archivos, 237 pruebas) que unitarias (8 archivos,
43 pruebas). Eso produce una silueta de trofeo, no de pirámide clásica. Las razones son estructurales:

**El backend es SQL-intensivo.** La mayor parte de la lógica crítica vive en la base de datos: la
restricción de exclusión que impide reservas solapadas (ADR-03), el candado de transacción por publicación
(`pg_advisory_xact_lock`), la auditoría de solo inserción con triggers que rechazan UPDATE y DELETE para
cualquier rol (ASR-07), la idempotencia de pagos garantizada por la clave única en `solicitud_procesada`
(ASR-03). Ninguna de estas garantías se puede verificar con mocks: exigen Postgres real. Testcontainers
levanta el contenedor una sola vez por suite y lo comparte, por lo que el coste extra es tolerable en CI.

**El dominio tiene poca lógica aislable.** Las reglas de negocio más complejas (política de cancelación,
cotización, disponibilidad) se prueban como unidad. El resto son coordinadores que delegan a la base, así
que añadir mocks solo duplicaría cobertura sin añadir confianza.

**Cómo evitamos el cono de helado.** El cono de helado ocurre cuando la mayoría de las pruebas son E2E:
lentas, frágiles y difíciles de depurar. Lo evitamos de dos formas:
- Las pruebas E2E con Playwright cubren solo los flujos críticos del usuario (registro, búsqueda, reserva,
  pago, cancelación). Todo lo que se puede verificar más abajo en la pirámide no llega a E2E.
- Las sesiones exploratorias son manuales y con checklists, no automatizadas. Automatizar exploración
  produce scripts frágiles que se rompen con cualquier cambio de UI; la mirada humana detecta problemas
  de usabilidad que ningún script encontraría.

## Capas

| # | Capa | Qué prueba | Herramienta | Dónde está | Cuántas | Cómo se corre | Estado | Ticket | ASR o RF que cubren |
|---|------|-----------|-------------|-----------|---------|--------------|--------|--------|---------------------|
| 7 | Unitarias del dominio — backend | Lógica de negocio pura: políticas, cotización, cancelaciones, tokens, logs | JUnit 5 + Mockito | `src/test/**/*Test.java` (8 archivos, 43 pruebas) | 43 | `mvn test` | parcial | SCRUM-126 | RF-13–16 (cancelación), RF-17 (cotización), RNF-18/25 (logs), ASR-04 (pasarela caída) |
| 7 | Unitarias del dominio — frontend | Servicios, pipes, guards | Jest / Vitest | repo frontend | por confirmar | por confirmar | planeado | SCRUM-126 | por confirmar |
| 6 | Componente — módulos y arquitectura | Fronteras entre módulos (Spring Modulith), reglas de capas (ArchUnit) | Spring Modulith + ArchUnit | `ModulosTest.java` (2), `ArquitecturaTest.java` (5 reglas) | 7 | `mvn test` | parcial | SCRUM-127 | ADR-01 (capas), EC-28 (módulos sin ciclos ni acceso interno) |
| 6 | Componente — pantallas del frontend | Componentes Angular aislados | Angular Testing Library | repo frontend | por confirmar | por confirmar | planeado | SCRUM-127 | por confirmar |
| 5 | Integración — backend completo | Controladores + base de datos real (Flyway incluido) vía MockMvc + Testcontainers | Spring Boot Test + Testcontainers (Postgres 17) | `src/test/**/*IT.java` (43 archivos, 237 pruebas) | 237 | `mvn verify` (requiere Docker) | parcial | SCRUM-128 | CU-01–31, ASR-02 (concurrencia), ASR-03 (idempotencia), ASR-06 (autorización), ASR-07 (auditoría), ASR-08b (modificabilidad), RNF-12 (reserva a medias), RNF-19/22/23 (seguridad) |
| 5 | Integración — servicios del frontend | Llamadas HTTP reales desde los servicios Angular | por definir | repo frontend | por confirmar | por confirmar | planeado | SCRUM-128 | por confirmar |
| 4 | Contrato con OpenAPI | El spec se publica, coincide con el archivo versionado en `docs/openapi.json` y las respuestas reales lo cumplen | springdoc-openapi + `swagger-request-validator-core` | `DocumentacionApiIT.java` (2), `ContratoApiIT.java` (1), `ContratoRespuestasIT.java` (11) | 14 | `mvn verify` | hecho | SCRUM-129 | RNF (API documentada y accesible sin sesión); regresión de contrato detectada en CI |
| 3 | Sistema — flujo completo por la API | Arranque, restricciones de BD, seguridad base | Spring Boot Test | `src/test/.../EsqueletoIT.java` (5 pruebas) | 5 | `mvn verify` | parcial | SCRUM-130 | ASR-06 (denegación por defecto), ASR-07 (auditoría solo inserción) |
| 2 | E2E — flujos críticos | Registro, búsqueda, reserva, pago, cancelación end-to-end en navegador real | Playwright | por definir | — | por definir | planeado | SCRUM-131 | CU-01, CU-06, CU-14, CU-19, CU-20 (flujos críticos del usuario) |
| 1 | Exploratorias | Sesiones manuales con checklists sobre el entorno de staging | — | por definir | — | — | planeado | SCRUM-132 | UX, flujos no automatizados, regresiones visuales |

## Transversales

| Tipo | Qué cubre | Herramienta | Dónde está | Cómo se corre | Estado |
|------|----------|-------------|-----------|--------------|--------|
| Seguridad básica | SQL injection, brute-force, hash de contraseñas, XSS | JUnit 5 | `src/test/.../SeguridadIT.java` (5 pruebas) | `mvn verify` | hecho |
| Cobertura | Líneas y ramas cubiertas por unitarias + integración | JaCoCo | reporte en `target/site/jacoco/index.html` | `mvn verify` | hecho |
| Calidad estática | Cobertura, deuda técnica, bugs | SonarCloud | sonarcloud.io — proyecto `nikotpab_marketplace-alojamientos-backend` | `mvn verify sonar:sonar` | hecho |
| Carga | p95 y p99 por escenario a 120 s con usuarios reales | JMeter 5.6 | `pruebas/carga/plan.jmx`, `pruebas/carga/ejecutar.sh` | `pruebas/carga/ejecutar.sh` (requiere backend + Docker + Mailpit) | hecho |
| Seguridad OWASP | Escaneo DAST automático | OWASP ZAP | por definir | por definir | planeado |
| Accesibilidad | WCAG sobre pantallas críticas | por definir | por definir | por definir | planeado |
| Prueba de humo | Respuesta básica tras un despliegue | por definir | por definir | por definir | planeado |

## Cobertura

| Proyecto | Dashboard | Estado |
|----------|-----------|--------|
| Backend | [SonarCloud — nikotpab_marketplace-alojamientos-backend](https://sonarcloud.io/dashboard?id=nikotpab_marketplace-alojamientos-backend) | hecho |
| Frontend | por confirmar | — |

El reporte local de JaCoCo queda en `target/site/jacoco/index.html` tras `mvn verify`. No hay umbral mínimo
configurado; ver "Pendiente y decisiones".

## Cómo se ejecuta todo

```bash
# Unitarias y arquitectura (sin Docker)
mvn test

# Unitarias + integración (requiere Docker Desktop activo)
mvn verify

# Solo cobertura de unitarias
mvn test jacoco:report
# Reporte en target/site/jacoco/index.html

# Integración + análisis Sonar (requiere SONAR_TOKEN)
mvn verify sonar:sonar

# Regenerar docs/openapi.json cuando el spec cambie (SCRUM-129)
mvn verify -Dopenapi.actualizar=true

# Pruebas de carga (requiere backend en :8080, Docker, Mailpit en :8025 y JMeter 5.6)
pruebas/carga/ejecutar.sh 120
# Resumen de p95/p99 en pruebas/carga/resultados/<timestamp>/resumen.md
```

## Pendiente y decisiones

- **Cobertura mínima**: no hay umbral configurado en JaCoCo; definir un porcentaje mínimo antes de que el
  pipeline falle.
- **Capas 7 y 6 del frontend**: se confirmarán con el equipo del repo `nikotpab/marketplace-alojamientos-frontend`.
- **E2E (capa 2)**: pendiente definir qué flujos cubre Playwright, el entorno donde corre y cómo se integra
  al pipeline de CI.
- **Transversales OWASP, accesibilidad y humo**: pendiente definir herramientas, entorno y criterios de
  aceptación (SCRUM-133).
- **Pruebas unitarias faltantes**: los módulos `busqueda`, `notificaciones`, `auditoria` y `administracion`
  no tienen `*Test.java`; se cubren solo por integración.
