# Estrategia de pruebas

La pirámide de pruebas organiza los tests en capas: muchas pruebas rápidas, aisladas y baratas en la base
(unitarias), y pocas pruebas lentas y costosas en la cima (E2E). Cuanto más abajo está una prueba, más
rápido falla y más fácil es localizar la causa. Usamos esta jerarquía para maximizar la cobertura con el
menor tiempo de ejecución posible: las unitarias corren en segundos en cualquier máquina; las de integración
necesitan Docker; las E2E necesitan el stack completo.

## Capas

| # | Capa | Qué prueba | Herramienta | Dónde está | Cuántas | Cómo se corre | Estado | Ticket |
|---|------|-----------|-------------|-----------|---------|--------------|--------|--------|
| 7 | Unitarias del dominio — backend | Lógica de negocio pura: políticas, cotización, cancelaciones, tokens, logs | JUnit 5 + Mockito | `src/test/**/*Test.java` (8 archivos, 43 pruebas) | 43 | `mvn test` | parcial | SCRUM-126 |
| 7 | Unitarias del dominio — frontend | Servicios, pipes, guards | Jest / Vitest | repo frontend | por confirmar | por confirmar | planeado | SCRUM-126 |
| 6 | Componente — módulos y arquitectura | Fronteras entre módulos (Spring Modulith), reglas de capas (ArchUnit) | Spring Modulith + ArchUnit | `ModulosTest.java` (2), `ArquitecturaTest.java` (5 reglas) | 7 | `mvn test` | parcial | SCRUM-127 |
| 6 | Componente — pantallas del frontend | Componentes Angular aislados | Angular Testing Library | repo frontend | por confirmar | por confirmar | planeado | SCRUM-127 |
| 5 | Integración — backend completo | Controladores + base de datos real (Flyway incluido) vía MockMvc + Testcontainers | Spring Boot Test + Testcontainers (Postgres 17) | `src/test/**/*IT.java` (43 archivos, 237 pruebas) | 237 | `mvn verify` (requiere Docker) | parcial | SCRUM-128 |
| 5 | Integración — servicios del frontend | Llamadas HTTP reales desde los servicios Angular | por definir | repo frontend | por confirmar | por confirmar | planeado | SCRUM-128 |
| 4 | Contrato front/back con OpenAPI | El spec OpenAPI se publica y coincide con el código | springdoc-openapi + `DocumentacionApiIT.java` | `src/test/.../DocumentacionApiIT.java` (2 pruebas) | 2 | `mvn verify` | parcial | SCRUM-129 |
| 3 | Sistema — flujo completo por la API | Arranque, restricciones de BD, seguridad base | Spring Boot Test | `src/test/.../EsqueletoIT.java` (5 pruebas) | 5 | `mvn verify` | parcial | SCRUM-130 |
| 2 | E2E — flujos críticos | Registro, búsqueda, reserva, pago, cancelación end-to-end en navegador real | Playwright | por definir | — | por definir | planeado | SCRUM-131 |
| 1 | Exploratorias | Sesiones manuales con checklists sobre el entorno de staging | — | por definir | — | — | planeado | SCRUM-132 |

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

# Pruebas de carga (requiere backend en :8080, Docker, Mailpit en :8025 y JMeter 5.6)
pruebas/carga/ejecutar.sh 120
# Resumen de p95/p99 en pruebas/carga/resultados/<timestamp>/resumen.md
```

## Pendiente y decisiones

- **Cobertura mínima**: no hay umbral configurado en JaCoCo; definir un porcentaje mínimo antes de que el
  pipeline falle.
- **Capas 7 y 6 del frontend**: se confirmarán con el equipo del repo `nikotpab/marketplace-alojamientos-frontend`.
- **Contrato real (capa 4)**: `DocumentacionApiIT` solo verifica que el spec se publique; falta una prueba
  que consuma el spec y valide que las respuestas reales lo cumplen (Pact u OpenAPI diff).
- **E2E (capa 2)**: pendiente definir qué flujos cubre Playwright, el entorno donde corre y cómo se integra
  al pipeline de CI.
- **Transversales OWASP, accesibilidad y humo**: pendiente definir herramientas, entorno y criterios de
  aceptación (SCRUM-133).
- **Pruebas unitarias faltantes**: los módulos `busqueda`, `notificaciones`, `auditoria` y `administracion`
  no tienen `*Test.java`; se cubren solo por integración.
