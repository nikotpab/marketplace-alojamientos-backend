package com.inversioneslr.marketplace.busqueda.aplicacion;

import com.inversioneslr.marketplace.busqueda.dominio.AlojamientoDetalle;
import com.inversioneslr.marketplace.busqueda.dominio.FiltrosBusqueda;
import com.inversioneslr.marketplace.busqueda.dominio.PaginaAlojamientos;
import com.inversioneslr.marketplace.busqueda.dominio.RepositorioBusqueda;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.resenas.api.FachadaResenas;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServicioBusqueda {

    private static final int TAMANO_MAXIMO = 50;
    /** El detalle solo muestra publicaciones activas. */
    private static final String ESTADO_VISIBLE = "ACTIVA";
    /** Hasta dónde se calculan los días no disponibles del calendario. */
    private static final Period HORIZONTE_CALENDARIO = Period.ofYears(1);

    private final RepositorioBusqueda busqueda;
    private final FachadaResenas resenas;
    private final Clock reloj;

    public ServicioBusqueda(RepositorioBusqueda busqueda, FachadaResenas resenas, Clock reloj) {
        this.busqueda = busqueda;
        this.resenas = resenas;
        this.reloj = reloj;
    }

    /** CU-14 · SEC-06: solo publicaciones ACTIVAS y libres en las fechas pedidas. */
    public PaginaAlojamientos buscarAlojamientos(FiltrosBusqueda filtros, int pagina, int tamano) {
        validar(filtros);
        return busqueda.buscar(filtros, Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO));
    }

    private static void validar(FiltrosBusqueda filtros) {
        if ((filtros.llegada() == null) != (filtros.salida() == null)) {
            throw datoInvalido(filtros.llegada() == null ? "llegada" : "salida", "Indica la llegada y la salida, o ninguna de las dos.");
        }
        if (filtros.llegada() != null && !filtros.salida().isAfter(filtros.llegada())) {
            throw datoInvalido("salida", "La salida debe ser posterior a la llegada. Elige una fecha de salida más tardía.");
        }
        if (filtros.huespedes() != null && filtros.huespedes() < 1) {
            throw datoInvalido("huespedes", "Indica al menos 1 huésped.");
        }
        if (filtros.precioMin() != null && filtros.precioMax() != null && filtros.precioMin().compareTo(filtros.precioMax()) > 0) {
            throw datoInvalido("precioMax", "El precio máximo debe ser mayor o igual al mínimo.");
        }
    }

    private static ExcepcionNegocio datoInvalido(String campo, String mensaje) {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of(campo, mensaje));
    }

    /** CU-15 · CU-13: solo publicaciones activas, con sus reseñas visibles y los días que no se pueden reservar. */
    public Detalle consultarDetalle(UUID publicacionId) {
        AlojamientoDetalle alojamiento = busqueda.buscarDetalle(publicacionId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "Este alojamiento no existe o ya no está disponible."));
        LocalDate hoy = LocalDate.now(reloj);
        return new Detalle(alojamiento.id(), ESTADO_VISIBLE, alojamiento.titulo(), alojamiento.descripcion(), alojamiento.ciudad(),
                alojamiento.direccion(), alojamiento.tipo(), alojamiento.capacidad(), alojamiento.precioNoche(),
                alojamiento.servicios(), alojamiento.reglas(), alojamiento.politicaCancelacion(), alojamiento.imagenes(),
                alojamiento.calificacionPromedio(), resenas.listarVisibles(publicacionId),
                busqueda.fechasNoDisponibles(publicacionId, hoy, hoy.plus(HORIZONTE_CALENDARIO)));
    }

    /** El formato que espera la pantalla de detalle. */
    /** @param estado siempre ACTIVA; el frontend lo usa para mostrar el calendario y la reserva */
    public record Detalle(UUID id, String estado, String titulo, String descripcion, String ciudad, String direccion, String tipo,
                          int capacidad, BigDecimal precioNoche, List<String> servicios, List<String> reglas,
                          String politicaCancelacion, List<String> imagenes, BigDecimal calificacionPromedio,
                          List<FachadaResenas.ResenaVisible> resenas, List<LocalDate> fechasNoDisponibles) {
    }
}
