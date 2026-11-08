package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.reservas.api.EstadoReserva;
import com.inversioneslr.marketplace.reservas.api.FachadaReservas;
import com.inversioneslr.marketplace.reservas.dominio.RepositorioReservas;
import com.inversioneslr.marketplace.reservas.dominio.Reserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CU-18 · C1–C4: historial de reservas para huésped y anfitrión.
 * Cubre criterios positivos y negativos de acceso y visibilidad de contacto (RNF-21).
 */
class HistorialReservasIT extends PruebaIntegracion {

    private static final LocalDate FUTURA = LocalDate.of(2030, 6, 10);
    private static final LocalDate FUTURA_FIN = LocalDate.of(2030, 6, 13);

    @Autowired
    RepositorioReservas repositorio;

    @Autowired
    FachadaReservas fachada;

    // ── C1: huésped ──────────────────────────────────────────────────────────

    /** C1-positivo: huésped ve sus reservas con titulo, ciudad y grupo correcto. */
    @Test
    void huespedVeSusReservasConDatosEnriquecidos() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reserva.titulo").value("Casa de prueba"))
                .andExpect(jsonPath("$[0].reserva.ciudad").value("Cartagena"))
                .andExpect(jsonPath("$[0].reserva.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$[0].grupo").value("PROXIMA"));
    }

    /** C1-positivo: huésped ve reservas agrupadas: CONFIRMADA futura → PROXIMA, CANCELADA → CANCELADA. */
    @Test
    void huespedVeGruposCorrectamente() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());
        repositorio.guardar(reserva(huesped, publicacion, FUTURA.plusMonths(1), FUTURA_FIN.plusMonths(1), EstadoReserva.CANCELADA), List.of());
        repositorio.guardar(reserva(huesped, publicacion, FUTURA.plusMonths(2), FUTURA_FIN.plusMonths(2), EstadoReserva.COMPLETADA), List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.grupo == 'PROXIMA')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.grupo == 'CANCELADA')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.grupo == 'COMPLETADA')]", hasSize(1)));
    }

    /** C1-positivo: filtro ?estado= acota los resultados. */
    @Test
    void huespedFiltraPorEstado() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());
        repositorio.guardar(reserva(huesped, publicacion, FUTURA.plusMonths(1), FUTURA_FIN.plusMonths(1), EstadoReserva.CANCELADA), List.of());

        mvc.perform(get("/api/v1/reservas?estado=CANCELADA").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reserva.estado").value("CANCELADA"));
    }

    /** C1-negativo (RNF-36): huésped no ve el contacto del huésped en sus propias reservas. */
    @Test
    void huespedNoVeContactoEnSusReservas() throws Exception {
        UUID huesped = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactoHuesped").value(nullValue()));
    }

    /** C1-negativo: huésped no ve reservas de otro huésped. */
    @Test
    void huespedNoVeReservasAjenas() throws Exception {
        UUID huesped1 = crearUsuario(Rol.HUESPED);
        UUID huesped2 = crearUsuario(Rol.HUESPED);
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));

        repositorio.guardar(reserva(huesped2, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped1, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── C2: anfitrión ────────────────────────────────────────────────────────

    /** C2-positivo: anfitrión ve reservas de sus publicaciones con nombre del huésped. */
    @Test
    void anfitrionVeReservasDeSusPublicacionesConNombreHuesped() throws Exception {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        UUID huesped = crearUsuario(Rol.HUESPED);

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reserva.nombreHuesped").value("Nombre Apellido"))
                .andExpect(jsonPath("$[0].grupo").value("PROXIMA"));
    }

    /** C2-positivo: anfitrión filtra por estado. */
    @Test
    void anfitrionFiltraPorEstado() throws Exception {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        UUID huesped = crearUsuario(Rol.HUESPED);

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());
        repositorio.guardar(reserva(huesped, publicacion, FUTURA.plusMonths(1), FUTURA_FIN.plusMonths(1), EstadoReserva.CANCELADA), List.of());

        mvc.perform(get("/api/v1/reservas?estado=CONFIRMADA").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reserva.estado").value("CONFIRMADA"));
    }

    /** C2-negativo: anfitrión no ve reservas de publicaciones de otro anfitrión. */
    @Test
    void anfitrionNoVeReservasDeOtroAnfitrion() throws Exception {
        UUID anfitrion1 = crearUsuario(Rol.ANFITRION);
        UUID anfitrion2 = crearUsuario(Rol.ANFITRION);
        UUID publicacionDeAnfitrion2 = crearPublicacionActiva(anfitrion2);
        UUID huesped = crearUsuario(Rol.HUESPED);

        repositorio.guardar(reserva(huesped, publicacionDeAnfitrion2, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(anfitrion1, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── C3: contacto del huésped (RNF-21, EC-21) ─────────────────────────────

    /** C3-positivo: anfitrión ve teléfono y correo del huésped en reserva CONFIRMADA. */
    @Test
    void anfitrionVeContactoEnReservaConfirmada() throws Exception {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        UUID huesped = crearUsuario(Rol.HUESPED);

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactoHuesped.telefono").value("3000000000"))
                .andExpect(jsonPath("$[0].contactoHuesped.correo").exists());
    }

    /** C3-negativo (EC-21): anfitrión NO ve contacto en reserva CANCELADA. */
    @Test
    void anfitrionNoVeContactoEnReservaCancelada() throws Exception {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        UUID huesped = crearUsuario(Rol.HUESPED);

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CANCELADA), List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactoHuesped").value(nullValue()));
    }

    /** C3-negativo (EC-21): anfitrión NO ve contacto en reserva COMPLETADA. */
    @Test
    void anfitrionNoVeContactoEnReservaCompletada() throws Exception {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        UUID huesped = crearUsuario(Rol.HUESPED);

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.COMPLETADA), List.of());

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactoHuesped").value(nullValue()));
    }

    // ── C4: publicación bloqueada → EN_REVISION ───────────────────────────────

    /** C4-positivo: marcarFuturasEnRevision cambia CONFIRMADA futura a EN_REVISION. */
    @Test
    void marcarFuturasEnRevisionSoloCambiaConfirmadasFuturas() {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        UUID huesped = crearUsuario(Rol.HUESPED);

        Reserva futura = reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA);
        Reserva cancelada = reserva(huesped, publicacion, FUTURA.plusMonths(1), FUTURA_FIN.plusMonths(1), EstadoReserva.CANCELADA);
        repositorio.guardar(futura, List.of());
        repositorio.guardar(cancelada, List.of());

        fachada.marcarFuturasEnRevision(publicacion);

        assertThat(fachada.datosReserva(futura.id()).estado()).isEqualTo(EstadoReserva.EN_REVISION);
        assertThat(fachada.datosReserva(cancelada.id()).estado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    /** C4-positivo: reserva EN_REVISION aparece en el historial del huésped con estado visible y grupo PROXIMA. */
    @Test
    void reservaEnRevisionAparececonGrupoProxima() throws Exception {
        UUID anfitrion = crearUsuario(Rol.ANFITRION);
        UUID publicacion = crearPublicacionActiva(anfitrion);
        UUID huesped = crearUsuario(Rol.HUESPED);

        repositorio.guardar(reserva(huesped, publicacion, FUTURA, FUTURA_FIN, EstadoReserva.CONFIRMADA), List.of());
        fachada.marcarFuturasEnRevision(publicacion);

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reserva.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$[0].grupo").value("PROXIMA"));
    }

    // ── Seguridad (ADR-07) ────────────────────────────────────────────────────

    /** Sin sesión recibe 401. */
    @Test
    void sinSesionRecibe401() throws Exception {
        mvc.perform(get("/api/v1/reservas")).andExpect(status().isUnauthorized());
    }

    /** ADMINISTRADOR recibe 403 desde la capa de seguridad (ConfiguracionSeguridad). */
    @Test
    void administradorRecibe403() throws Exception {
        UUID admin = crearUsuario(Rol.ADMINISTRADOR);
        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(admin, Rol.ADMINISTRADOR)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Reserva reserva(UUID huesped, UUID publicacion, LocalDate llegada, LocalDate salida, EstadoReserva estado) {
        BigDecimal valor = new BigDecimal("100000");
        return new Reserva(UUID.randomUUID(), huesped, publicacion, llegada, salida, 2, estado,
                3, valor, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, valor);
    }
}
