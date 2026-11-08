package com.inversioneslr.marketplace.publicaciones.aplicacion;

import com.inversioneslr.marketplace.compartido.dominio.EventoDominio;
import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.compartido.dominio.UsuarioAutenticado;
import com.inversioneslr.marketplace.publicaciones.api.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.PeriodoDisponibilidad;
import com.inversioneslr.marketplace.publicaciones.dominio.PoliticaOfrecida;
import com.inversioneslr.marketplace.publicaciones.dominio.Publicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.RepositorioPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.Servicio;
import com.inversioneslr.marketplace.publicaciones.dominio.TipoConfigurado;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La propiedad de la publicación se verifica en cada método (ADR-07). */
@Service
public class ServicioPublicaciones implements FachadaPublicaciones {

    private final RepositorioPublicaciones publicaciones;
    private final PublicadorEventos eventos;

    public ServicioPublicaciones(RepositorioPublicaciones publicaciones, PublicadorEventos eventos) {
        this.publicaciones = publicaciones;
        this.eventos = eventos;
    }

    public Publicacion crear(UUID anfitrionId, PublicacionComando comando) {
        throw new UnsupportedOperationException("CU-06");
    }

    /**
     * CU-07: solo el anfitrión propietario o un administrador. Conserva el estado: si editar una publicación
     * activa debe devolverla a revisión sigue sin definirse (pendiente 12 del README).
     */
    @Transactional
    public Publicacion editar(UsuarioAutenticado usuario, UUID publicacionId, PublicacionComando comando) {
        Publicacion actual = publicaciones.buscarPorId(publicacionId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La publicación no existe."));
        if (usuario.rol() != Rol.ADMINISTRADOR && !actual.anfitrionId().equals(usuario.id())) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.ACCESO_DENEGADO, "Esta publicación no es tuya.");
        }
        Publicacion editada = publicaciones.guardar(new Publicacion(actual.id(), actual.anfitrionId(),
                comando.tipoAlojamientoId(), comando.politicaCancelacionId(), comando.titulo(), comando.descripcion(),
                comando.ciudad(), comando.ubicacionTextual(), comando.capacidad(), comando.precioNoche(),
                listaOVacia(comando.servicios()), listaOVacia(comando.reglas()), listaOVacia(comando.imagenes()),
                actual.estado(), actual.motivoEstado()));
        eventos.publicar(new EventoDominio("PublicacionEditada", usuario.id(), "publicacion", publicacionId, "EXITO", Map.of(), List.of()));
        return editada;
    }

    private static <T> List<T> listaOVacia(List<T> lista) {
        return lista == null ? List.of() : lista;
    }

    public Publicacion solicitarActivacion(UUID anfitrionId, UUID publicacionId) {
        throw new UnsupportedOperationException("CU-08");
    }

    public Publicacion revisar(UUID administradorId, UUID publicacionId, RevisionComando comando) {
        throw new UnsupportedOperationException("CU-09");
    }

    public Publicacion pausar(UUID anfitrionId, UUID publicacionId) {
        throw new UnsupportedOperationException("CU-10");
    }

    /** CU-11: anfitrión, las suyas; administrador, todas; huésped, solo las activas. */
    public List<Publicacion> consultar(UsuarioAutenticado usuario) {
        throw new UnsupportedOperationException("CU-11");
    }

    /** CU-12: no se pueden alterar los periodos con una reserva confirmada (RF-13). */
    public PeriodoDisponibilidad definirPeriodo(UUID anfitrionId, UUID publicacionId, PeriodoComando comando) {
        throw new UnsupportedOperationException("CU-12");
    }

    public List<PeriodoDisponibilidad> consultarCalendario(UUID publicacionId) {
        throw new UnsupportedOperationException("CU-13");
    }

    public List<TipoConfigurado> listarTiposActivos() {
        return publicaciones.listarTiposActivos();
    }

    public Catalogos consultarCatalogos() {
        throw new UnsupportedOperationException("CU-06");
    }

    @Override
    public DatosPublicacion datosPublicacion(UUID publicacionId) {
        return publicaciones.buscarDatos(publicacionId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "El alojamiento no existe."));
    }

    @Override
    public boolean periodoDisponible(UUID publicacionId, LocalDate llegada, LocalDate salida) {
        return publicaciones.periodoDisponible(publicacionId, llegada, salida);
    }

    @Override
    public void bloquear(UUID administradorId, UUID publicacionId, String motivo) {
        throw new UnsupportedOperationException("CU-24");
    }

    @Override
    public List<TipoAlojamiento> listarTipos() {
        throw new UnsupportedOperationException("CU-31");
    }

    @Override
    public TipoAlojamiento guardarTipo(UUID administradorId, TipoAlojamiento tipo) {
        throw new UnsupportedOperationException("CU-31");
    }

    public record PublicacionComando(
            @NotNull UUID tipoAlojamientoId,
            @NotNull UUID politicaCancelacionId,
            @NotBlank @Size(max = 180) String titulo,
            @NotBlank String descripcion,
            @NotBlank @Size(max = 120) String ciudad,
            @NotBlank @Size(max = 255) String ubicacionTextual,
            @NotNull @Positive(message = "La capacidad debe ser mayor que cero. Ingresa 1 o más.") Integer capacidad,
            @NotNull @Positive(message = "El precio por noche debe ser positivo. Ingresa un valor mayor que cero.") BigDecimal precioNoche,
            List<UUID> servicios,
            List<String> reglas,
            List<String> imagenes,
            List<ServicioAdicional> serviciosAdicionales) {
    }

    public record RevisionComando(boolean aprobar, String motivo) {
    }

    public record PeriodoComando(@NotNull LocalDate fechaInicio,
                                 @NotNull LocalDate fechaFin,
                                 @NotNull PeriodoDisponibilidad.Tipo tipo) {
    }

    public record Catalogos(List<TipoAlojamiento> tipos, List<Servicio> servicios, List<PoliticaOfrecida> politicas) {
    }
}
