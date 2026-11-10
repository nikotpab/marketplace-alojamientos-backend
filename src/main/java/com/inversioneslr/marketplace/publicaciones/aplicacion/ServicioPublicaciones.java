package com.inversioneslr.marketplace.publicaciones.aplicacion;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.publicaciones.FachadaPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.Disponibilidad;
import com.inversioneslr.marketplace.publicaciones.dominio.EstadoPublicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.PeriodoDisponibilidad;
import com.inversioneslr.marketplace.publicaciones.dominio.PoliticaOfrecida;
import com.inversioneslr.marketplace.publicaciones.dominio.Publicacion;
import com.inversioneslr.marketplace.publicaciones.dominio.PublicacionVista;
import com.inversioneslr.marketplace.publicaciones.dominio.RepositorioPublicaciones;
import com.inversioneslr.marketplace.publicaciones.dominio.Servicio;
import com.inversioneslr.marketplace.publicaciones.dominio.TipoConfigurado;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La propiedad de la publicación se verifica en cada método (ADR-07). */
@Service
public class ServicioPublicaciones implements FachadaPublicaciones {

    /** Decisión temporal: queda disponible dos años y el anfitrión bloquea lo que no (ajuste 9 del README). */
    private static final Period DISPONIBILIDAD_INICIAL = Period.ofYears(2);
    /** La única política implementada. */
    private static final String POLITICA_POR_DEFECTO = "Flexible";
    private static final int LARGO_CIUDAD = 120;
    private static final int MAXIMO_DIAS_RANGO = 366;

    private final RepositorioPublicaciones publicaciones;
    private final PublicadorEventos eventos;
    private final Clock reloj;

    public ServicioPublicaciones(RepositorioPublicaciones publicaciones, PublicadorEventos eventos, Clock reloj) {
        this.publicaciones = publicaciones;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * CU-06: queda en BORRADOR y pertenece al anfitrión del token. Puede estar incompleto: la información
     * mínima se valida al solicitar la activación (RF-08).
     */
    @Transactional
    public PublicacionVista crear(UUID anfitrionId, DatosPublicacionComando datos) {
        Publicacion publicacion = publicaciones.guardar(aPublicacion(UUID.randomUUID(), anfitrionId, datos, EstadoPublicacion.BORRADOR, null));
        LocalDate hoy = LocalDate.now(reloj);
        publicaciones.guardarPeriodo(new PeriodoDisponibilidad(UUID.randomUUID(), publicacion.id(), hoy,
                hoy.plus(DISPONIBILIDAD_INICIAL), PeriodoDisponibilidad.Tipo.DISPONIBLE));
        eventos.publicar(new EventoDominio("PublicacionCreada", anfitrionId, "publicacion", publicacion.id(), "EXITO", Map.of(), List.of()));
        return vista(publicacion.id());
    }

    private Publicacion aPublicacion(UUID id, UUID anfitrionId, DatosPublicacionComando datos, EstadoPublicacion estado, String motivo) {
        return new Publicacion(id, anfitrionId, tipoDesde(datos.tipo()), politicaDesde(datos.politicaCancelacion()),
                vacioComoNulo(datos.titulo()), vacioComoNulo(datos.descripcion()), ciudadDe(datos.ubicacion()),
                vacioComoNulo(datos.ubicacion()), datos.capacidad(), datos.precioNoche(), serviciosDesde(datos.servicios()),
                listaOVacia(datos.reglas()), listaOVacia(datos.imagenes()), estado, motivo);
    }

    private UUID tipoDesde(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        return publicaciones.buscarTipoPorCodigo(codigo.trim())
                .orElseThrow(() -> datoInvalido("tipo", "El tipo de alojamiento no existe. Elige uno de la lista."));
    }

    private List<UUID> serviciosDesde(List<String> nombres) {
        if (nombres == null || nombres.isEmpty()) {
            return List.of();
        }
        Map<String, UUID> porNombre = publicaciones.listarServicios().stream().collect(Collectors.toMap(Servicio::nombre, Servicio::id));
        List<UUID> ids = nombres.stream().distinct().map(porNombre::get).toList();
        if (ids.contains(null)) {
            throw datoInvalido("servicios", "Algún servicio no existe. Elige solo servicios de la lista.");
        }
        return ids;
    }

    /**
     * Decisión temporal: el frontend envía la política como texto libre. Se asocia a la política cuyo nombre
     * aparece en el texto, y si ninguna aparece, a la Flexible, la única implementada (ajuste 8 del README).
     */
    private UUID politicaDesde(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String buscado = sinTildes(texto);
        List<PoliticaOfrecida> politicas = publicaciones.listarPoliticas();
        return politicas.stream().filter(politica -> buscado.contains(sinTildes(politica.nombre()))).findFirst()
                .or(() -> politicas.stream().filter(politica -> politica.nombre().equals(POLITICA_POR_DEFECTO)).findFirst())
                .map(PoliticaOfrecida::id)
                .orElse(null);
    }

    /** Decisión temporal: el formulario no pide ciudad; es lo que va después de la última coma (ajuste 7 del README). */
    static String ciudadDe(String ubicacion) {
        if (ubicacion == null || ubicacion.isBlank()) {
            return null;
        }
        String ciudad = ubicacion.substring(ubicacion.lastIndexOf(',') + 1).trim();
        return ciudad.length() > LARGO_CIUDAD ? ciudad.substring(0, LARGO_CIUDAD) : ciudad;
    }

    private PublicacionVista vista(UUID publicacionId) {
        return publicaciones.buscarVista(publicacionId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La publicación no existe."));
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String vacioComoNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static ExcepcionNegocio datoInvalido(String campo, String mensaje) {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of(campo, mensaje));
    }

    /**
     * CU-07: solo el anfitrión propietario o un administrador. Conserva el estado: si editar una publicación
     * activa debe devolverla a revisión sigue sin definirse (pendiente 12 del README). Solo un borrador
     * puede quedar incompleto.
     */
    @Transactional
    public PublicacionVista editar(UsuarioAutenticado usuario, UUID publicacionId, DatosPublicacionComando datos) {
        Publicacion actual = propia(usuario, publicacionId);
        Publicacion editada = aPublicacion(actual.id(), actual.anfitrionId(), datos, actual.estado(), actual.motivoEstado());
        if (editada.estado() != EstadoPublicacion.BORRADOR) {
            editada.exigirInformacionMinima();
        }
        publicaciones.guardar(editada);
        eventos.publicar(new EventoDominio("PublicacionEditada", usuario.id(), "publicacion", publicacionId, "EXITO", Map.of(), List.of()));
        return vista(publicacionId);
    }

    private static <T> List<T> listaOVacia(List<T> lista) {
        return lista == null ? List.of() : lista;
    }

    /** CU-08: pasa a PENDIENTE_REVISION para que la revise un administrador. */
    @Transactional
    public PublicacionVista solicitarActivacion(UsuarioAutenticado anfitrion, UUID publicacionId) {
        publicaciones.guardar(propia(anfitrion, publicacionId).solicitarActivacion());
        eventos.publicar(new EventoDominio("PublicacionEnviadaARevision", anfitrion.id(), "publicacion", publicacionId, "EXITO", Map.of(), List.of()));
        return vista(publicacionId);
    }

    /** CU-09: pasa a ACTIVA y se avisa al anfitrión. */
    @Transactional
    public PublicacionVista aprobar(UUID administradorId, UUID publicacionId) {
        Publicacion aprobada = publicaciones.guardar(existente(publicacionId).aprobar());
        eventos.publicar(new EventoDominio("PublicacionAprobada", administradorId, "publicacion", publicacionId, "EXITO",
                Map.of(), List.of(aprobada.anfitrionId())));
        return vista(publicacionId);
    }

    /** CU-09: vuelve a BORRADOR con el motivo y se avisa al anfitrión. */
    @Transactional
    public PublicacionVista rechazar(UUID administradorId, UUID publicacionId, String motivo) {
        Publicacion rechazada = publicaciones.guardar(existente(publicacionId).rechazar(motivo));
        eventos.publicar(new EventoDominio("PublicacionRechazada", administradorId, "publicacion", publicacionId, "EXITO",
                Map.of("motivo", rechazada.motivoEstado()), List.of(rechazada.anfitrionId())));
        return vista(publicacionId);
    }

    private Publicacion existente(UUID publicacionId) {
        return publicaciones.buscarPorId(publicacionId).orElseThrow(ServicioPublicaciones::noExiste);
    }

    /** CU-10. */
    @Transactional
    public PublicacionVista pausar(UsuarioAutenticado anfitrion, UUID publicacionId) {
        publicaciones.guardar(propia(anfitrion, publicacionId).pausar());
        eventos.publicar(new EventoDominio("PublicacionPausada", anfitrion.id(), "publicacion", publicacionId, "EXITO", Map.of(), List.of()));
        return vista(publicacionId);
    }

    @Transactional
    public PublicacionVista reactivar(UsuarioAutenticado anfitrion, UUID publicacionId) {
        publicaciones.guardar(propia(anfitrion, publicacionId).reactivar());
        eventos.publicar(new EventoDominio("PublicacionReactivada", anfitrion.id(), "publicacion", publicacionId, "EXITO", Map.of(), List.of()));
        return vista(publicacionId);
    }

    /** CU-11: el anfitrión ve las suyas en cualquier estado. El huésped ve las activas en la búsqueda. */
    public List<PublicacionVista> listarDelAnfitrion(UUID anfitrionId) {
        return publicaciones.listarVistas(anfitrionId, null);
    }

    /** CU-11: el administrador ve todas. */
    public List<PublicacionVista> listarTodas(EstadoPublicacion estado) {
        return publicaciones.listarVistas(null, estado);
    }

    /** Para editarla: solo el propietario o un administrador. */
    public PublicacionVista consultar(UsuarioAutenticado usuario, UUID publicacionId) {
        return vista(propia(usuario, publicacionId).id());
    }

    /** ADR-07: la publicación existe y es del usuario, salvo que sea administrador. */
    private Publicacion propia(UsuarioAutenticado usuario, UUID publicacionId) {
        Publicacion publicacion = publicaciones.buscarPorId(publicacionId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La publicación no existe."));
        if (usuario.rol() != Rol.ADMINISTRADOR && !publicacion.anfitrionId().equals(usuario.id())) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.ACCESO_DENEGADO, "Esta publicación no es tuya.");
        }
        return publicacion;
    }

    /**
     * CU-13: la de una publicación activa es pública; la de las demás solo la ven el propietario y un administrador.
     *
     * @param usuario null si no hay sesión
     */
    public Disponibilidad consultarDisponibilidad(UsuarioAutenticado usuario, UUID publicacionId, LocalDate desde, LocalDate hasta) {
        Publicacion publicacion = publicaciones.buscarPorId(publicacionId).orElseThrow(ServicioPublicaciones::noExiste);
        boolean puedeVerla = publicacion.estado() == EstadoPublicacion.ACTIVA
                || usuario != null && (usuario.rol() == Rol.ADMINISTRADOR || publicacion.anfitrionId().equals(usuario.id()));
        if (!puedeVerla) {
            throw noExiste();
        }
        validarRango(desde, hasta);
        return publicaciones.disponibilidad(publicacionId, desde, hasta);
    }

    /**
     * CU-12: el rango es de días, con ambos extremos incluidos. No se cambian días con reservas confirmadas (RF-13).
     * La respuesta cubre desde el mes anterior al del inicio hasta el mes siguiente al del fin, para que el
     * calendario de dos meses del frontend se vuelva a pintar completo.
     */
    @Transactional
    public Disponibilidad cambiarDisponibilidad(UsuarioAutenticado anfitrion, UUID publicacionId, CambioDisponibilidad cambio) {
        propia(anfitrion, publicacionId);
        validarRango(cambio.desde(), cambio.hasta());
        if (cambio.desde().isBefore(LocalDate.now(reloj))) {
            throw datoInvalido("desde", "No se pueden cambiar fechas pasadas. Elige desde hoy en adelante.");
        }
        LocalDate fin = cambio.hasta().plusDays(1);
        if (publicaciones.hayReservasConfirmadas(publicacionId, cambio.desde(), fin)) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO,
                    "El rango incluye días con reservas confirmadas. Elige un rango sin días reservados.");
        }
        if (cambio.estado() == PeriodoDisponibilidad.Tipo.BLOQUEADO) {
            publicaciones.guardarPeriodo(new PeriodoDisponibilidad(UUID.randomUUID(), publicacionId, cambio.desde(), fin, PeriodoDisponibilidad.Tipo.BLOQUEADO));
        } else {
            publicaciones.liberar(publicacionId, cambio.desde(), fin);
        }
        eventos.publicar(new EventoDominio("DisponibilidadModificada", anfitrion.id(), "publicacion", publicacionId, "EXITO",
                Map.of("desde", cambio.desde().toString(), "hasta", cambio.hasta().toString(), "estado", cambio.estado().name()), List.of()));
        return publicaciones.disponibilidad(publicacionId, cambio.desde().withDayOfMonth(1).minusMonths(1),
                cambio.hasta().plusMonths(2).withDayOfMonth(1).minusDays(1));
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw datoInvalido("hasta", "La fecha final debe ser igual o posterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS_RANGO) {
            throw datoInvalido("hasta", "El rango no puede superar un año. Elige un rango más corto.");
        }
    }

    private static ExcepcionNegocio noExiste() {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "La publicación no existe.");
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

    /** CU-24: deja de aparecer en la búsqueda, que solo muestra las activas, y se avisa al anfitrión. */
    @Override
    @Transactional
    public void bloquear(UUID administradorId, UUID publicacionId, String motivo) {
        Publicacion bloqueada = publicaciones.guardar(existente(publicacionId).bloquear(motivo));
        eventos.publicar(new EventoDominio("PublicacionBloqueada", administradorId, "publicacion", publicacionId, "EXITO",
                Map.of("motivo", bloqueada.motivoEstado()), List.of(bloqueada.anfitrionId())));
    }

    @Override
    public List<TipoAlojamiento> listarTipos() {
        throw new UnsupportedOperationException("CU-31");
    }

    @Override
    public TipoAlojamiento guardarTipo(UUID administradorId, TipoAlojamiento tipo) {
        throw new UnsupportedOperationException("CU-31");
    }

    /** Lo que envía el formulario del frontend. En borrador todos los campos pueden ir vacíos. */
    public record DatosPublicacionComando(
            @Size(max = 180) String titulo,
            String descripcion,
            @Size(max = 255) String ubicacion,
            String tipo,
            @Positive(message = "La capacidad debe ser mayor que cero. Ingresa 1 o más.") Integer capacidad,
            @Positive(message = "El precio por noche debe ser positivo. Ingresa un valor mayor que cero.") BigDecimal precioNoche,
            List<String> servicios,
            List<String> reglas,
            List<String> imagenes,
            String politicaCancelacion) {
    }

    public record RechazoComando(@Size(max = 1000) String motivo) {
    }

    /** El rango incluye ambos días. */
    public record CambioDisponibilidad(@NotNull LocalDate desde,
                                       @NotNull LocalDate hasta,
                                       @NotNull PeriodoDisponibilidad.Tipo estado) {
    }

    public record Catalogos(List<TipoAlojamiento> tipos, List<Servicio> servicios, List<PoliticaOfrecida> politicas) {
    }
}
