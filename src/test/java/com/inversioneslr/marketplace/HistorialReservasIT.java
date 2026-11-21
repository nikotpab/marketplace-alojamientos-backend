package com.inversioneslr.marketplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** CU-18 · RF-20. */
class HistorialReservasIT extends PruebaIntegracion {

    UUID huesped;
    UUID anfitrion;
    UUID publicacion;

    @BeforeEach
    void preparar() {
        huesped = crearUsuario(Rol.HUESPED);
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
    }

    @Test
    void elHuespedVeSusReservasDeLaMasRecienteALaMasAntiguaSinDatosDeContacto() throws Exception {
        reservar(huesped, "2030-02-01", "2030-02-03", "CANCELADA");
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");
        reservar(crearUsuario(Rol.HUESPED), "2030-04-01", "2030-04-02", "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].llegada").value("2030-03-01"))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$[0].alojamiento").value("Casa de prueba"))
                .andExpect(jsonPath("$[0].codigo").exists())
                .andExpect(jsonPath("$[0].contactoHuesped").doesNotExist())
                .andExpect(jsonPath("$[0].nombreHuesped").doesNotExist())
                .andExpect(jsonPath("$[1].estado").value("CANCELADA"));
    }

    /** SCRUM-113: el motivo, cuánto se devolvió y cómo terminó la devolución, incluida una no procesada. */
    @Test
    void lasCanceladasTraenElMotivoYLaDevolucion() throws Exception {
        UUID procesada = reservar(huesped, "2030-02-01", "2030-02-03", "CANCELADA");
        UUID fallida = reservar(huesped, "2030-01-10", "2030-01-12", "CANCELADA");
        cancelar(procesada, "Cambié de planes", 180000, "DEVOLUCION_PROCESADA");
        cancelar(fallida, "Me enfermé", 90000, "DEVOLUCION_NO_PROCESADA");
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(jsonPath("$[0].cancelacion").doesNotExist())
                .andExpect(jsonPath("$[1].cancelacion.motivo").value("Cambié de planes"))
                .andExpect(jsonPath("$[1].cancelacion.valorDevolucion").value(180000))
                .andExpect(jsonPath("$[1].cancelacion.estadoFinal").value("DEVOLUCION_PROCESADA"))
                .andExpect(jsonPath("$[1].cancelacion.fecha").exists())
                .andExpect(jsonPath("$[2].cancelacion.estadoFinal").value("DEVOLUCION_NO_PROCESADA"))
                .andExpect(jsonPath("$[2].cancelacion.valorDevolucion").value(90000));
    }

    /** SCRUM-113: con reseña, aunque esté oculta, el frontend ya no ofrece "Reseñar" (una reserva admite una sola). */
    @Test
    void cadaReservaDiceSiYaTieneResena() throws Exception {
        UUID resenada = reservar(huesped, "2030-02-01", "2030-02-03", "COMPLETADA");
        UUID oculta = reservar(huesped, "2030-01-10", "2030-01-12", "COMPLETADA");
        reservar(huesped, "2030-03-01", "2030-03-04", "COMPLETADA");
        resenar(resenada, false);
        resenar(oculta, true);

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(jsonPath("$[0].resenada").value(false))
                .andExpect(jsonPath("$[1].resenada").value(true))
                .andExpect(jsonPath("$[2].resenada").value(true));
    }

    /** SCRUM-113: el nombre del huésped llega en todas; el contacto, solo en las confirmadas (RNF-21). */
    @Test
    void elAnfitrionVeElNombreDelHuespedEnTodasLasReservas() throws Exception {
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");
        reservar(huesped, "2030-02-01", "2030-02-03", "CANCELADA");

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(jsonPath("$[0].nombreHuesped").value("Nombre Apellido"))
                .andExpect(jsonPath("$[1].nombreHuesped").value("Nombre Apellido"))
                .andExpect(jsonPath("$[1].contactoHuesped").doesNotExist());
    }

    /** ASR-06: un huésped no ve la cancelación ni la reseña de las reservas de otro. */
    @Test
    void unHuespedNoVeLosDatosDeLasReservasDeOtro() throws Exception {
        UUID otro = crearUsuario(Rol.HUESPED);
        UUID ajena = reservar(otro, "2030-02-01", "2030-02-03", "CANCELADA");
        cancelar(ajena, "Motivo del otro", 50000, "DEVOLUCION_PROCESADA");
        resenar(reservar(otro, "2030-01-10", "2030-01-12", "COMPLETADA"), false);
        reservar(huesped, "2030-03-01", "2030-03-04", "COMPLETADA");

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cancelacion").doesNotExist())
                .andExpect(jsonPath("$[0].resenada").value(false));
    }

    @Test
    void sePuedeFiltrarPorEstado() throws Exception {
        reservar(huesped, "2030-02-01", "2030-02-03", "CANCELADA");
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas").param("estado", "CANCELADA").header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("CANCELADA"));
    }

    @Test
    void elAnfitrionVeElContactoDelHuespedSoloEnLasConfirmadas() throws Exception {
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");
        reservar(huesped, "2030-02-01", "2030-02-03", "CANCELADA");
        String correo = comoAplicacion.queryForObject("SELECT correo FROM usuario WHERE id = ?", String.class, huesped);

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(anfitrion, Rol.ANFITRION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].contactoHuesped.correo").value(correo))
                .andExpect(jsonPath("$[0].contactoHuesped.telefono").value("3000000000"))
                .andExpect(jsonPath("$[1].contactoHuesped").doesNotExist());
    }

    @Test
    void unAnfitrionNoVeLasReservasDeAlojamientosAjenos() throws Exception {
        reservar(huesped, "2030-03-01", "2030-03-04", "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(crearUsuario(Rol.ANFITRION), Rol.ANFITRION)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elAdministradorNoUsaEsteHistorial() throws Exception {
        mvc.perform(get("/api/v1/reservas").header("Authorization", sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR)))
                .andExpect(status().isForbidden());
    }

    private UUID reservar(UUID quien, String llegada, String salida, String estado) {
        return comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado, noches, total)
                VALUES (?, ?, CAST(? AS date), CAST(? AS date), 2, ?, 2, 200000) RETURNING id""",
                UUID.class, quien, publicacion, llegada, salida, estado);
    }

    private void cancelar(UUID reserva, String motivo, int devolucion, String estadoFinal) {
        comoAplicacion.update("""
                INSERT INTO cancelacion (reserva_id, usuario_id, fecha, motivo, valor_devolucion, estado_final)
                SELECT id, huesped_id, now(), ?, ?, ? FROM reserva WHERE id = ?""", motivo, devolucion, estadoFinal, reserva);
    }

    private void resenar(UUID reserva, boolean oculta) {
        comoAplicacion.update("INSERT INTO resena (reserva_id, calificacion, contenido, oculta) VALUES (?, 5, 'Muy bien', ?)", reserva, oculta);
    }
}
