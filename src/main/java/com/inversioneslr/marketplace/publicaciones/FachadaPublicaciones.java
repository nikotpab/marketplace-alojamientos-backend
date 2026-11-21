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

    /** Una sola consulta para todas; las que no existen no aparecen. */
    Map<PublicacionId, String> titulos(Collection<PublicacionId> publicacionIds);

    /** Todas las del anfitrión, en cualquier estado. */
    List<PublicacionId> publicacionesDelAnfitrion(UsuarioId anfitrionId);

    void bloquear(UsuarioId administradorId, PublicacionId publicacionId, String motivo);

    /** De BLOQUEADA a ACTIVA, y se avisa al anfitrión. */
    void desbloquear(UsuarioId administradorId, PublicacionId publicacionId);

    List<TipoAlojamiento> listarTipos();

    /** Agrega un tipo, o lo edita o desactiva si ya tiene id. */
    TipoAlojamiento guardarTipo(UsuarioId administradorId, TipoAlojamiento tipo);

    record DatosPublicacion(UUID id, UUID anfitrionId, boolean activa, BigDecimal precioNoche, int capacidad,
                            String politicaCancelacion, List<ServicioAdicional> serviciosAdicionales) {
    }

    record ServicioAdicional(UUID id, String nombre, BigDecimal costo) {
    }

    record TipoAlojamiento(UUID id, String nombre, boolean activo) {
    }
}
