package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.pagos.dominio.PasarelaPagos;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/** CU-20 · RF-22, RF-23. La reserva cuesta 330.000, con 30.000 de tarifa de servicio. */
class CancelacionesIT extends PruebaIntegracion {

    @MockitoBean
    PasarelaPagos pasarela;

    @Autowired
    FachadaPublicaciones publicaciones;

    UUID huesped;
    UUID anfitrion;
    UUID publicacion;

    @BeforeEach
    void preparar() {
        huesped = crearUsuario(Rol.HUESPED);
        anfitrion = crearUsuario(Rol.ANFITRION);
        publicacion = crearPublicacionActiva(anfitrion);
        // crearPublicacionActiva solo la deja disponible en 2030; estas reservas son de los próximos días.
        comoAplicacion.update("""
                INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo)
                VALUES (?, CURRENT_DATE, CURRENT_DATE + 60, 'DISPONIBLE')""", publicacion);
    }

    @Test
    void elHuespedVeCuantoLeDevuelvenAntesDeCancelar() throws Exception {
        UUID reserva = reservar(10, "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", reserva).header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorDevolucion").value(300000))
                .andExpect(jsonPath("$.politica").value("Flexible"));
    }

    @Test
    void entreCincoYDosDiasAntesSeDevuelveLaMitad() throws Exception {
        UUID reserva = reservar(3, "CONFIRMADA");

        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", reserva).header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(jsonPath("$.valorDevolucion").value(150000));
    }

    @Test
    void otroHuespedNoPuedeConsultarLaEstimacion() throws Exception {
        UUID reserva = reservar(10, "CONFIRMADA");
        UUID otro = crearUsuario(Rol.HUESPED);

        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", reserva).header("Authorization", sesionDe(otro, Rol.HUESPED)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaReservaYaCanceladaNoSeEstima() throws Exception {
        UUID reserva = reservar(10, "CANCELADA");

        mvc.perform(get("/api/v1/reservas/{id}/cancelacion/estimacion", reserva).header("Authorization", sesionDe(huesped, Rol.HUESPED)))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelarDevuelveElDineroLiberaElPeriodoYAvisaAAmbos() throws Exception {
        UUID reserva = reservar(10, "CONFIRMADA");
        String referencia = pagar(reserva);
        LocalDate llegada = hoy().plusDays(10);
        assertThat(publicaciones.periodoDisponible(new PublicacionId(publicacion), llegada, llegada.plusDays(3))).isFalse();

        cancelar(reserva, "Cambié de planes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorDevolucion").value(300000))
                .andExpect(jsonPath("$.estadoFinal").value("DEVOLUCION_PROCESADA"))
                .andExpect(jsonPath("$.motivo").value("Cambié de planes"));

        verify(pasarela).devolver(referencia, new BigDecimal("300000"));
        assertThat(texto("SELECT estado FROM reserva WHERE id = ?", reserva)).isEqualTo("CANCELADA");
        assertThat(texto("SELECT estado FROM pago WHERE reserva_id = ?", reserva)).isEqualTo("DEVUELTO");
        assertThat(texto("SELECT estado_final FROM cancelacion WHERE reserva_id = ?", reserva)).isEqualTo("DEVOLUCION_PROCESADA");
        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'ReservaCancelada'", reserva)).isEqualTo(1);
        assertThat(publicaciones.periodoDisponible(new PublicacionId(publicacion), llegada, llegada.plusDays(3))).isTrue();
        esperarEventosDe(reserva);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND tipo = 'ReservaCancelada'", huesped)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND tipo = 'ReservaCancelada'", anfitrion)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND tipo = 'DevolucionProcesada'", huesped)).isEqualTo(1);
    }

    @Test
    void sinDevolucionNoSeLlamaALaPasarela() throws Exception {
        UUID reserva = reservar(1, "CONFIRMADA");
        pagar(reserva);

        cancelar(reserva, "Ya no viajo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorDevolucion").value(0))
                .andExpect(jsonPath("$.estadoFinal").value("SIN_DEVOLUCION"));

        verify(pasarela, never()).devolver(any(), any());
        assertThat(texto("SELECT estado FROM pago WHERE reserva_id = ?", reserva)).isEqualTo("APROBADO");
    }

    @Test
    void siLaDevolucionFallaLaReservaSeCancelaIgualYSeAvisa() throws Exception {
        UUID reserva = reservar(10, "CONFIRMADA");
        pagar(reserva);
        doThrow(new ExcepcionNegocio(ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, "Pasarela caída")).when(pasarela).devolver(any(), any());

        cancelar(reserva, "Cambié de planes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoFinal").value("DEVOLUCION_NO_PROCESADA"));

        assertThat(texto("SELECT estado FROM reserva WHERE id = ?", reserva)).isEqualTo("CANCELADA");
        assertThat(texto("SELECT estado FROM pago WHERE reserva_id = ?", reserva)).isEqualTo("APROBADO");
        assertThat(contar("SELECT count(*) FROM auditoria.evento_auditoria WHERE entidad_id = ? AND accion = 'DevolucionFallida'", reserva)).isEqualTo(1);
        esperarEventosDe(reserva);
        assertThat(contar("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND tipo = 'DevolucionFallida'", huesped)).isEqualTo(1);
    }

    @Test
    void unaReservaSeCancelaYSeDevuelveUnaSolaVez() throws Exception {
        UUID reserva = reservar(10, "CONFIRMADA");
        pagar(reserva);
        cancelar(reserva, "Cambié de planes").andExpect(status().isOk());

        cancelar(reserva, "Otra vez").andExpect(status().isConflict());

        verify(pasarela, times(1)).devolver(any(), any());
    }

    @Test
    void elMotivoEsObligatorio() throws Exception {
        UUID reserva = reservar(10, "CONFIRMADA");

        cancelar(reserva, " ").andExpect(status().isBadRequest());

        assertThat(texto("SELECT estado FROM reserva WHERE id = ?", reserva)).isEqualTo("CONFIRMADA");
    }

    @Test
    void otroHuespedNoPuedeCancelarla() throws Exception {
        UUID reserva = reservar(10, "CONFIRMADA");

        mvc.perform(post("/api/v1/reservas/{id}/cancelacion", reserva)
                        .header("Authorization", sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"No es mía\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions cancelar(UUID reserva, String motivo) throws Exception {
        return mvc.perform(post("/api/v1/reservas/{id}/cancelacion", reserva)
                .header("Authorization", sesionDe(huesped, Rol.HUESPED))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + motivo + "\"}"));
    }

    private String pagar(UUID reserva) {
        String referencia = "pi_" + UUID.randomUUID();
        comoAplicacion.update("INSERT INTO pago (reserva_id, id_pago_stripe, clave_idempotencia, monto, estado) VALUES (?, ?, ?, 330000, 'APROBADO')",
                reserva, referencia, UUID.randomUUID().toString());
        return referencia;
    }

    private String texto(String sql, UUID id) {
        return comoAplicacion.queryForObject(sql, String.class, id);
    }

    private long contar(String sql, UUID id) {
        return comoAplicacion.queryForObject(sql, Long.class, id);
    }

    /** @param diasAntes cuántos días faltan, desde hoy, para la llegada */
    UUID reservar(int diasAntes, String estado) {
        LocalDate llegada = hoy().plusDays(diasAntes);
        return comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                                     noches, precio_base, tarifa_limpieza, tarifa_servicio, descuento, total)
                VALUES (?, ?, ?, ?, 2, ?, 3, 300000, 0, 30000, 0, 330000) RETURNING id""",
                UUID.class, huesped, publicacion, llegada, llegada.plusDays(3), estado);
    }
}
