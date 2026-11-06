package com.inversioneslr.marketplace.busqueda.aplicacion;

import com.inversioneslr.marketplace.busqueda.dominio.AlojamientoDetalle;
import com.inversioneslr.marketplace.busqueda.dominio.AlojamientoResumen;
import com.inversioneslr.marketplace.busqueda.dominio.FiltrosBusqueda;
import com.inversioneslr.marketplace.busqueda.dominio.RepositorioBusqueda;
import com.inversioneslr.marketplace.resenas.api.FachadaResenas;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServicioBusqueda {

    private static final int TAMANO_PAGINA = 20;

    private final RepositorioBusqueda busqueda;
    private final FachadaResenas resenas;

    public ServicioBusqueda(RepositorioBusqueda busqueda, FachadaResenas resenas) {
        this.busqueda = busqueda;
        this.resenas = resenas;
    }

    /** CU-14 · SEC-06: valida salida posterior a llegada y precio mínimo no mayor que el máximo. */
    public List<AlojamientoResumen> buscarAlojamientos(FiltrosBusqueda filtros, int pagina) {
        throw new UnsupportedOperationException("CU-14");
    }

    /** CU-15: solo publicaciones activas, con sus reseñas visibles. */
    public Detalle consultarDetalle(UUID publicacionId) {
        throw new UnsupportedOperationException("CU-15");
    }

    public record Detalle(AlojamientoDetalle alojamiento, List<FachadaResenas.ResenaVisible> resenas) {
    }
}
