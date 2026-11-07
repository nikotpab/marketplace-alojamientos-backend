package com.inversioneslr.marketplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.dominio.Rol;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResenasIT extends PruebaIntegracion {

    @Test
    void listaLasResenasVisiblesConSuAutorSinLasOcultas() throws Exception {
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        resenar(publicacion, 5, "Excelente ubicación", false);
        resenar(publicacion, 1, "Contenido ofensivo", true);

        mvc.perform(get("/api/v1/publicaciones/{id}/resenas", publicacion).header("Authorization", sesionDe(crearUsuario(Rol.HUESPED), Rol.HUESPED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].autor").value("Nombre A."))
                .andExpect(jsonPath("$[0].calificacion").value(5))
                .andExpect(jsonPath("$[0].comentario").value("Excelente ubicación"))
                .andExpect(jsonPath("$[0].fecha").exists());
    }

    private void resenar(UUID publicacion, int calificacion, String comentario, boolean oculta) {
        UUID reserva = comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2029-01-01', '2029-01-03', 1, 'COMPLETADA') RETURNING id""", UUID.class, crearUsuario(Rol.HUESPED), publicacion);
        comoAplicacion.update("INSERT INTO resena (reserva_id, calificacion, contenido, oculta) VALUES (?, ?, ?, ?)", reserva, calificacion, comentario, oculta);
    }
}
