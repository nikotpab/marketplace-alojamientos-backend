package com.inversioneslr.marketplace;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DetalleIT extends PruebaIntegracion {

    private final LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
    private UUID publicacion;

    @BeforeEach
    void preparar() {
        publicacion = comoAplicacion.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, tipo_alojamiento_id, politica_cancelacion_id, titulo, descripcion, ciudad,
                                         ubicacion_textual, capacidad, precio_noche, estado)
                VALUES (?, (SELECT id FROM tipo_alojamiento WHERE codigo = 'APARTAMENTO'), (SELECT id FROM politica_cancelacion WHERE nombre = 'Flexible'),
                        'Apartamento con vista al mar', 'Frente a la playa', 'Santa Marta', 'El Rodadero, carrera 1', 4, 320000, 'ACTIVA')
                RETURNING id""", UUID.class, crearUsuario(Rol.ANFITRION));
        comoAplicacion.update("INSERT INTO publicacion_servicio (publicacion_id, servicio_id) SELECT ?, id FROM servicio WHERE nombre IN ('WiFi', 'Piscina')", publicacion);
        comoAplicacion.update("INSERT INTO regla_alojamiento (publicacion_id, descripcion) VALUES (?, 'No se permiten fiestas')", publicacion);
        comoAplicacion.update("INSERT INTO imagen_publicacion (publicacion_id, ruta, orden) VALUES (?, 'sala.jpg', 0), (?, 'balcon.jpg', 1)", publicacion, publicacion);
        periodo(hoy, hoy.plusDays(60), "DISPONIBLE");
        periodo(hoy.plusDays(20), hoy.plusDays(22), "BLOQUEADO");
        reservar(hoy.plusDays(10), hoy.plusDays(13), "CONFIRMADA");
    }

    @Test
    void entregaElDetalleConElFormatoDelFrontend() throws Exception {
        mvc.perform(get("/api/v1/alojamientos/{id}", publicacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicacion.toString()))
                .andExpect(jsonPath("$.titulo").value("Apartamento con vista al mar"))
                .andExpect(jsonPath("$.descripcion").value("Frente a la playa"))
                .andExpect(jsonPath("$.direccion").value("El Rodadero, carrera 1"))
                .andExpect(jsonPath("$.tipo").value("APARTAMENTO"))
                .andExpect(jsonPath("$.capacidad").value(4))
                .andExpect(jsonPath("$.precioNoche").value(320000.00))
                .andExpect(jsonPath("$.servicios").value(hasItems("WiFi", "Piscina")))
                .andExpect(jsonPath("$.reglas[0]").value("No se permiten fiestas"))
                .andExpect(jsonPath("$.imagenes[0]").value("sala.jpg"))
                .andExpect(jsonPath("$.imagenes[1]").value("balcon.jpg"))
                .andExpect(jsonPath("$.politicaCancelacion").value(org.hamcrest.Matchers.startsWith("100 % de devolución")));
    }

    @Test
    void incluyeLaCalificacionPromedioYLasResenasVisibles() throws Exception {
        resenar(5, false);
        resenar(3, false);
        resenar(1, true);

        mvc.perform(get("/api/v1/alojamientos/{id}", publicacion))
                .andExpect(jsonPath("$.calificacionPromedio").value(4.0))
                .andExpect(jsonPath("$.resenas.length()").value(2))
                .andExpect(jsonPath("$.resenas[0].autor").value("Nombre A."));
    }

    @Test
    void marcaComoNoDisponiblesLosDiasReservadosBloqueadosYFueraDelPeriodo() throws Exception {
        mvc.perform(get("/api/v1/alojamientos/{id}", publicacion))
                .andExpect(jsonPath("$.fechasNoDisponibles").value(hasItems(
                        dia(10), dia(11), dia(12), dia(20), dia(21), dia(60), dia(100))))
                .andExpect(jsonPath("$.fechasNoDisponibles").value(not(hasItem(dia(13)))))
                .andExpect(jsonPath("$.fechasNoDisponibles").value(not(hasItem(dia(5)))))
                .andExpect(jsonPath("$.fechasNoDisponibles").value(not(hasItem(dia(22)))));
    }

    @Test
    void unaPublicacionQueNoEstaActivaNoSeMuestra() throws Exception {
        comoAplicacion.update("UPDATE publicacion SET estado = 'PAUSADA' WHERE id = ?", publicacion);

        mvc.perform(get("/api/v1/alojamientos/{id}", publicacion)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/alojamientos/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    private String dia(int desdeHoy) {
        return hoy.plusDays(desdeHoy).toString();
    }

    private void periodo(LocalDate inicio, LocalDate fin, String tipo) {
        comoAplicacion.update("INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo) VALUES (?, ?, ?, ?)", publicacion, inicio, fin, tipo);
    }

    private UUID reservar(LocalDate llegada, LocalDate salida, String estado) {
        return comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, ?, ?, 2, ?) RETURNING id""", UUID.class, crearUsuario(Rol.HUESPED), publicacion, llegada, salida, estado);
    }

    private void resenar(int calificacion, boolean oculta) {
        UUID reserva = reservar(LocalDate.of(2029, 1, 1), LocalDate.of(2029, 1, 3), "COMPLETADA");
        comoAplicacion.update("INSERT INTO resena (reserva_id, calificacion, contenido, oculta) VALUES (?, ?, 'Reseña', ?)", reserva, calificacion, oculta);
    }
}
