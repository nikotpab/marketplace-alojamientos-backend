package com.inversioneslr.marketplace;

import static org.mockito.Mockito.when;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.compartido.dominio.Tokens;
import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Base de las pruebas de integración: Postgres real, usuario de aplicación restringido y sesiones simuladas. */
@SpringBootTest(properties = {"marketplace.jwt-secreto=solo-para-pruebas", "marketplace.pagos.proveedor=simulado",
        "marketplace.eventos.cron=-"})
@AutoConfigureMockMvc
abstract class PruebaIntegracion {

    private static final String CLAVE = "solo-para-pruebas";

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("crear-roles.sql");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry propiedades) {
        propiedades.add("DB_URL", () -> postgres.getJdbcUrl() + "&currentSchema=marketplace");
        propiedades.add("DB_USUARIO_APP", () -> "marketplace_app");
        propiedades.add("DB_CONTRASENA_APP", () -> CLAVE);
        propiedades.add("DB_USUARIO_MIGRACION", () -> "migrador");
        propiedades.add("DB_CONTRASENA_MIGRACION", () -> CLAVE);
    }

    @Autowired
    MockMvc mvc;

    /** Conectado como marketplace_app, igual que la aplicación. */
    @Autowired
    JdbcTemplate comoAplicacion;

    @MockitoBean
    Tokens tokens;

    UUID crearUsuario(Rol rol) {
        return comoAplicacion.queryForObject("""
                INSERT INTO usuario (nombre, apellido, correo, telefono, hash_contrasena, rol)
                VALUES ('Nombre', 'Apellido', ?, '3000000000', 'hash', ?) RETURNING id""",
                UUID.class, UUID.randomUUID() + "@prueba.co", rol.name());
    }

    /** Publicación ACTIVA con capacidad 4, precio 100.000 y disponible durante todo 2030. */
    UUID crearPublicacionActiva(UUID anfitrion) {
        UUID publicacion = comoAplicacion.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, tipo_alojamiento_id, politica_cancelacion_id, titulo, ciudad, capacidad, precio_noche, estado)
                VALUES (?, (SELECT id FROM tipo_alojamiento LIMIT 1), (SELECT id FROM politica_cancelacion LIMIT 1),
                        'Casa de prueba', 'Cartagena', 4, 100000, 'ACTIVA')
                RETURNING id""", UUID.class, anfitrion);
        comoAplicacion.update("""
                INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo)
                VALUES (?, '2030-01-01', '2031-01-01', 'DISPONIBLE')""", publicacion);
        return publicacion;
    }

    /** @return valor de la cabecera Authorization para ese usuario */
    String sesionDe(UUID usuarioId, Rol rol) {
        String token = UUID.randomUUID().toString();
        when(tokens.verificar(token)).thenReturn(Optional.of(new UsuarioAutenticado(usuarioId, rol)));
        return "Bearer " + token;
    }
}
