package com.inversioneslr.marketplace.busqueda.aplicacion;

import com.inversioneslr.marketplace.busqueda.dominio.AlojamientoDetalle;
import com.inversioneslr.marketplace.busqueda.dominio.FiltrosBusqueda;
import com.inversioneslr.marketplace.busqueda.dominio.PaginaAlojamientos;
import com.inversioneslr.marketplace.busqueda.dominio.RepositorioBusqueda;
import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.resenas.api.FachadaResenas;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServicioBusqueda {

    private static final int TAMANO_MAXIMO = 50;

    private final RepositorioBusqueda busqueda;
    private final FachadaResenas resenas;

    public ServicioBusqueda(RepositorioBusqueda busqueda, FachadaResenas resenas) {
        this.busqueda = busqueda;
        this.resenas = resenas;
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

    /** CU-15: solo publicaciones activas, con sus reseñas visibles. */
    public Detalle consultarDetalle(UUID publicacionId) {
        throw new UnsupportedOperationException("CU-15");
    }

    public record Detalle(AlojamientoDetalle alojamiento, List<FachadaResenas.ResenaVisible> resenas) {
    }
}
