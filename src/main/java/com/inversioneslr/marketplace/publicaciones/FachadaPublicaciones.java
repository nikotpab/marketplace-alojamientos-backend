package com.inversioneslr.marketplace.publicaciones;

import com.inversioneslr.marketplace.compartido.PublicacionId;
import com.inversioneslr.marketplace.compartido.UsuarioId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FachadaPublicaciones {

    DatosPublicacion datosPublicacion(PublicacionId publicacionId);

    boolean periodoDisponible(PublicacionId publicacionId, LocalDate llegada, LocalDate salida);

    /** SCRUM-142: una sola consulta para todas; las que no existen no aparecen. */
    Map<PublicacionId, String> titulos(Collection<PublicacionId> publicacionIds);

    /** SCRUM-142: todas las del anfitrión, en cualquier estado. */
    List<PublicacionId> publicacionesDelAnfitrion(UsuarioId anfitrionId);

    /** CU-24. */
    void bloquear(UsuarioId administradorId, PublicacionId publicacionId, String motivo);

    List<TipoAlojamiento> listarTipos();

    /** CU-31: agrega un tipo, o lo edita o desactiva si ya tiene id. */
    TipoAlojamiento guardarTipo(UsuarioId administradorId, TipoAlojamiento tipo);

    record DatosPublicacion(UUID id, UUID anfitrionId, boolean activa, BigDecimal precioNoche, int capacidad,
                            String politicaCancelacion, List<ServicioAdicional> serviciosAdicionales) {
    }

    record ServicioAdicional(UUID id, String nombre, BigDecimal costo) {
    }

    record TipoAlojamiento(UUID id, String nombre, boolean activo) {
    }
}
