package com.inversioneslr.marketplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.compartido.Rol;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Cada prueba usa una ciudad propia para no ver las publicaciones de otras pruebas. */
class BusquedaIT extends PruebaIntegracion {

    private UUID anfitrion;
    private String ciudad;

    @BeforeEach
    void preparar() {
        anfitrion = crearUsuario(Rol.ANFITRION);
        ciudad = "Bogotá " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void encuentraPorCiudadSinImportarTildesNiMayusculasYConElFormatoDelFrontend() throws Exception {
        UUID publicacion = publicar("CASA", 4, 280000, "WiFi", "Cocina");
        comoAplicacion.update("INSERT INTO imagen_publicacion (publicacion_id, ruta, orden) VALUES (?, 'portada.jpg', 0), (?, 'otra.jpg', 1)", publicacion, publicacion);

        buscar("ciudad", ciudad.replace("Bogotá", "bogota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(publicacion.toString()))
                .andExpect(jsonPath("$.contenido[0].tipo").value("CASA"))
                .andExpect(jsonPath("$.contenido[0].capacidad").value(4))
                .andExpect(jsonPath("$.contenido[0].precioNoche").value(280000.00))
                .andExpect(jsonPath("$.contenido[0].imagen").value("portada.jpg"))
                .andExpect(jsonPath("$.contenido[0].servicios[0]").value("Cocina"))
                .andExpect(jsonPath("$.contenido[0].calificacion").doesNotExist());
    }

    @Test
    void combinaHuespedesTipoPrecioYServicios() throws Exception {
        UUID buscada = publicar("CABANA", 5, 190000, "WiFi", "Mascotas", "Cocina");
        publicar("CABANA", 2, 190000, "WiFi", "Mascotas");
        publicar("CASA", 6, 190000, "WiFi", "Mascotas");
        publicar("CABANA", 6, 500000, "WiFi", "Mascotas");
        publicar("CABANA", 6, 150000, "WiFi");

        buscar("ciudad", ciudad, "huespedes", "4", "tipo", "CABANA", "precioMin", "150000", "precioMax", "200000", "servicios", "WiFi,Mascotas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(buscada.toString()));
    }

    @Test
    void conFechasSoloMuestraLasPublicacionesLibres() throws Exception {
        UUID libre = publicar("CASA", 4, 100000);
        UUID reservada = publicar("CASA", 4, 100000);
        UUID bloqueada = publicar("CASA", 4, 100000);
        comoAplicacion.update("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2030-03-11', '2030-03-13', 2, 'CONFIRMADA')""", crearUsuario(Rol.HUESPED), reservada);
        comoAplicacion.update("INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo) VALUES (?, '2030-03-12', '2030-03-13', 'BLOQUEADO')", bloqueada);

        buscar("ciudad", ciudad, "llegada", "2030-03-10", "salida", "2030-03-14")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(libre.toString()));
        buscar("ciudad", ciudad, "llegada", "2031-03-10", "salida", "2031-03-14").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void soloMuestraPublicacionesActivas() throws Exception {
        UUID pausada = publicar("CASA", 4, 100000);
        comoAplicacion.update("UPDATE publicacion SET estado = 'PAUSADA' WHERE id = ?", pausada);

        buscar("ciudad", ciudad).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void paginaLosResultados() throws Exception {
        publicar("CASA", 4, 100000);
        publicar("CASA", 4, 110000);
        publicar("CASA", 4, 120000);

        buscar("ciudad", ciudad, "tamano", "2")
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        buscar("ciudad", ciudad, "tamano", "2", "pagina", "1")
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.pagina").value(1));
    }

    @Test
    void ordenaPorCalificacionPromedioDeLasResenasVisibles() throws Exception {
        UUID sinResenas = publicar("CASA", 4, 100000);
        UUID conResenas = publicar("CASA", 4, 300000);
        resenar(conResenas, 5, false);
        resenar(conResenas, 4, false);
        resenar(conResenas, 1, true);

        buscar("ciudad", ciudad)
                .andExpect(jsonPath("$.contenido[0].id").value(conResenas.toString()))
                .andExpect(jsonPath("$.contenido[0].calificacion").value(4.5))
                .andExpect(jsonPath("$.contenido[1].id").value(sinResenas.toString()));
    }

    @Test
    void lasFechasYPreciosInvalidosSeRechazanIndicandoElCampo() throws Exception {
        buscar("llegada", "2030-03-14", "salida", "2030-03-10").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.salida").exists());
        buscar("llegada", "2030-03-14").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.salida").exists());
        buscar("precioMin", "300", "precioMax", "100").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.precioMax").exists());
        buscar("huespedes", "0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.huespedes").exists());
    }

    private ResultActions buscar(String... parametros) throws Exception {
        var peticion = get("/api/v1/alojamientos").param("orden", "relevancia");
        for (int i = 0; i < parametros.length; i += 2) {
            peticion.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(peticion);
    }

    private UUID publicar(String tipo, int capacidad, int precio, String... servicios) {
        UUID publicacion = comoAplicacion.queryForObject("""
                INSERT INTO publicacion (anfitrion_id, tipo_alojamiento_id, politica_cancelacion_id, titulo, ciudad, capacidad, precio_noche, estado)
                VALUES (?, (SELECT id FROM tipo_alojamiento WHERE codigo = ?), (SELECT id FROM politica_cancelacion LIMIT 1),
                        'Alojamiento de prueba', ?, ?, ?, 'ACTIVA')
                RETURNING id""", UUID.class, anfitrion, tipo, ciudad, capacidad, precio);
        comoAplicacion.update("INSERT INTO periodo_disponibilidad (publicacion_id, fecha_inicio, fecha_fin, tipo) VALUES (?, '2030-01-01', '2031-01-01', 'DISPONIBLE')", publicacion);
        for (String servicio : servicios) {
            comoAplicacion.update("INSERT INTO publicacion_servicio (publicacion_id, servicio_id) SELECT ?, id FROM servicio WHERE nombre = ?", publicacion, servicio);
        }
        return publicacion;
    }

    private void resenar(UUID publicacion, int calificacion, boolean oculta) {
        UUID reserva = comoAplicacion.queryForObject("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado)
                VALUES (?, ?, '2029-01-01', '2029-01-03', 1, 'COMPLETADA') RETURNING id""", UUID.class, crearUsuario(Rol.HUESPED), publicacion);
        comoAplicacion.update("INSERT INTO resena (reserva_id, calificacion, contenido, oculta) VALUES (?, ?, 'Reseña', ?)", reserva, calificacion, oculta);
    }
}
