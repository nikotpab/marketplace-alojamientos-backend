package com.inversioneslr.marketplace.publicaciones.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FachadaPublicaciones {

    DatosPublicacion datosPublicacion(UUID publicacionId);

    boolean periodoDisponible(UUID publicacionId, LocalDate llegada, LocalDate salida);

    /** CU-24. */
    void bloquear(UUID administradorId, UUID publicacionId, String motivo);

    List<TipoAlojamiento> listarTipos();

    /** CU-31: agrega un tipo, o lo edita o desactiva si ya tiene id. */
    TipoAlojamiento guardarTipo(UUID administradorId, TipoAlojamiento tipo);

    record DatosPublicacion(UUID id, UUID anfitrionId, boolean activa, BigDecimal precioNoche, int capacidad,
                            String politicaCancelacion, List<ServicioAdicional> serviciosAdicionales) {
    }

    record ServicioAdicional(UUID id, String nombre, BigDecimal costo) {
    }

    record TipoAlojamiento(UUID id, String nombre, boolean activo) {
    }
}
