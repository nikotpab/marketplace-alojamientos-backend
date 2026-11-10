package com.inversioneslr.marketplace.usuarios.aplicacion;

import com.inversioneslr.marketplace.compartido.EventoDominio;
import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.Tokens;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios;
import com.inversioneslr.marketplace.usuarios.dominio.CodigosRecuperacion;
import com.inversioneslr.marketplace.usuarios.dominio.RepositorioUsuarios;
import com.inversioneslr.marketplace.usuarios.dominio.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioAutenticacion implements FachadaUsuarios {

    /** RNF-23: 5 intentos fallidos seguidos bloquean el inicio de sesión por 30 minutos. */
    private static final int MAXIMO_INTENTOS = 5;
    private static final Duration BLOQUEO = Duration.ofMinutes(30);

    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder cifrador;
    private final Tokens tokens;
    private final PublicadorEventos eventos;
    private final Clock reloj;
    private final CodigosRecuperacion codigos;
    /** Se compara contra este hash cuando el correo no existe, para que la respuesta tarde lo mismo. */
    private final String hashDeRelleno;

    public ServicioAutenticacion(RepositorioUsuarios usuarios, PasswordEncoder cifrador, Tokens tokens,
                                 PublicadorEventos eventos, Clock reloj, CodigosRecuperacion codigos) {
        this.usuarios = usuarios;
        this.cifrador = cifrador;
        this.tokens = tokens;
        this.eventos = eventos;
        this.reloj = reloj;
        this.codigos = codigos;
        this.hashDeRelleno = cifrador.encode(UUID.randomUUID().toString());
    }

    /** CU-01: solo HUESPED o ANFITRION; el rol ADMINISTRADOR lo asigna el equipo. */
    @Transactional
    public UsuarioResumen registrar(RegistroComando comando) {
        if (comando.rol() == Rol.ADMINISTRADOR) {
            String mensaje = "Este rol no se puede elegir. Regístrate como huésped o anfitrión.";
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of("rol", mensaje));
        }
        Usuario usuario = usuarios.guardar(new Usuario(UUID.randomUUID(), comando.nombre(), comando.apellido(),
                normalizar(comando.correo()), comando.telefono(), cifrador.encode(comando.contrasena()),
                comando.rol(), true, false, null, 0, null));
        eventos.publicar(evento("UsuarioRegistrado", usuario, "EXITO", Map.of("rol", usuario.rol().name())));
        return resumen(usuario);
    }

    /**
     * CU-02 · RNF-23: un mensaje general para correo o contraseña incorrectos, y el estado de la cuenta solo
     * después de validar la contraseña. Cada intento queda registrado (RF-31).
     */
    public Sesion iniciarSesion(CredencialesComando comando) {
        Usuario usuario = usuarios.buscarPorCorreo(normalizar(comando.correo())).orElse(null);
        if (usuario == null) {
            cifrador.matches(comando.contrasena(), hashDeRelleno);
            // RNF-23: también se registra, sin el correo, para detectar fuerza bruta contra cuentas que no existen.
            eventos.publicar(new EventoDominio("InicioSesionFallido", null, "usuario", null, "CORREO_DESCONOCIDO", Map.of(), List.of()));
            throw credencialesInvalidas();
        }
        Instant ahora = reloj.instant();
        if (usuario.bloqueadoEn(ahora)) {
            eventos.publicar(evento("InicioSesionFallido", usuario, "CUENTA_BLOQUEADA", Map.of()));
            throw cuentaBloqueada();
        }
        if (!cifrador.matches(comando.contrasena(), usuario.hashContrasena())) {
            // Sin transacción a propósito: el intento debe quedar guardado aunque se responda con error.
            Usuario conIntento = usuarios.guardar(usuario.registrarIntentoFallido(ahora, MAXIMO_INTENTOS, BLOQUEO));
            eventos.publicar(evento("InicioSesionFallido", usuario, "FALLIDO", Map.of()));
            throw conIntento.bloqueadoEn(ahora) ? cuentaBloqueada() : credencialesInvalidas();
        }
        if (usuario.restringido()) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CUENTA_RESTRINGIDA, "Tu cuenta está restringida y no puede iniciar sesión.");
        }
        if (!usuario.cuentaActiva()) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CUENTA_DESACTIVADA, "Tu cuenta está desactivada.");
        }
        if (usuario.intentosFallidos() > 0) {
            usuarios.guardar(usuario.reiniciarIntentos());
        }
        eventos.publicar(evento("InicioSesionExitoso", usuario, "EXITO", Map.of()));
        return new Sesion(tokens.emitir(new UsuarioAutenticado(usuario.id(), usuario.rol())),
                new UsuarioSesion(usuario.id(), usuario.nombre(), usuario.correo(), usuario.rol()));
    }

    private static ExcepcionNegocio cuentaBloqueada() {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.CUENTA_BLOQUEADA, "Cuenta bloqueada por 30 minutos por intentos fallidos.");
    }

    private static ExcepcionNegocio credencialesInvalidas() {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.CREDENCIALES_INVALIDAS, "Correo o contraseña incorrectos.");
    }

    private static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private static EventoDominio evento(String tipo, Usuario usuario, String resultado, Map<String, Object> detalle) {
        return new EventoDominio(tipo, usuario.id(), "usuario", usuario.id(), resultado, detalle, List.of());
    }

    private static UsuarioResumen resumen(Usuario usuario) {
        return new UsuarioResumen(usuario.id(), usuario.nombre(), usuario.apellido(), usuario.correo(), usuario.telefono(),
                usuario.rol(), usuario.cuentaActiva(), usuario.restringido());
    }

    /**
     * CU-03 · RF-03: recuperación simulada, sin correo real, así que el código vuelve en la respuesta.
     * Si el correo no existe, se entrega un código que no sirve, para no revelar qué cuentas existen.
     */
    public Recuperacion solicitarRecuperacion(SolicitudRecuperacionComando comando) {
        return usuarios.buscarPorCorreo(normalizar(comando.correo()))
                .map(usuario -> {
                    eventos.publicar(evento("RecuperacionSolicitada", usuario, "EXITO", Map.of()));
                    return new Recuperacion(codigos.generar(usuario.id()));
                })
                .orElseGet(() -> new Recuperacion(codigos.generar(UUID.randomUUID())));
    }

    public void restablecerContrasena(RestablecerContrasenaComando comando) {
        String mensaje = "El código de recuperación no es válido o ya venció. Vuelve a empezar la recuperación.";
        Usuario usuario = codigos.consumir(comando.token())
                .flatMap(usuarios::buscarPorId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of("token", mensaje)));
        usuarios.guardar(usuario.cambiarContrasena(cifrador.encode(comando.contrasenaNueva())));
        eventos.publicar(evento("ContrasenaRestablecida", usuario, "EXITO", Map.of()));
    }

    /** CU-04: cada usuario consulta y modifica solo su propio perfil, identificado por el token. */
    public Perfil consultarPerfil(UUID usuarioId) {
        return perfil(buscar(usuarioId));
    }

    /** CU-04: el correo y el rol no se editan aquí. */
    public Perfil actualizarPerfil(UUID usuarioId, PerfilComando comando) {
        Usuario usuario = usuarios.guardar(buscar(usuarioId).actualizarDatos(comando.nombre(), comando.apellido(), comando.telefono()));
        eventos.publicar(evento("PerfilActualizado", usuario, "EXITO", Map.of()));
        return perfil(usuario);
    }

    private Usuario buscar(UUID usuarioId) {
        return usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "El usuario no existe."));
    }

    private static Perfil perfil(Usuario usuario) {
        return new Perfil(usuario.nombre(), usuario.apellido(), usuario.correo(), usuario.telefono(), usuario.rol(), usuario.cuentaActiva());
    }

    /** CU-05: cada cambio de estado queda en la auditoría. Una cuenta desactivada no puede iniciar sesión. */
    public Perfil cambiarEstadoCuenta(UUID usuarioId, EstadoCuentaComando comando) {
        Usuario actual = buscar(usuarioId);
        Usuario usuario = usuarios.guardar(comando.activo() ? actual.activar() : actual.desactivar());
        eventos.publicar(evento(comando.activo() ? "CuentaActivada" : "CuentaDesactivada", usuario, "EXITO", Map.of()));
        return perfil(usuario);
    }

    /** CU-25: del más reciente al más antiguo. */
    @Override
    public List<UsuarioResumen> listar() {
        return usuarios.listar().stream().map(ServicioAutenticacion::resumen).toList();
    }

    /** CU-25: queda en la auditoría con el motivo. */
    @Override
    @Transactional
    public void restringir(UUID administradorId, UUID usuarioId, String motivo) {
        Usuario restringido = usuarios.guardar(buscar(usuarioId).restringir(motivo));
        eventos.publicar(new EventoDominio("UsuarioRestringido", administradorId, "usuario", usuarioId, "EXITO",
                Map.of("motivo", restringido.motivoRestriccion()), List.of()));
    }

    @Override
    public DatosContacto datosContacto(UUID usuarioId) {
        Usuario usuario = buscar(usuarioId);
        return new DatosContacto(usuario.telefono(), usuario.correo());
    }

    public record RegistroComando(@NotBlank @Size(max = 120) String nombre,
                                  @NotBlank @Size(max = 120) String apellido,
                                  @NotBlank @Email @Size(max = 255) String correo,
                                  @NotBlank @Size(max = 30) String telefono,
                                  @NotBlank @Size(min = 8, max = 72) String contrasena,
                                  @NotNull Rol rol) {
        @Override
        public String toString() {
            return "RegistroComando[***]";
        }
    }

    public record CredencialesComando(@NotBlank String correo, @NotBlank String contrasena) {
        @Override
        public String toString() {
            return "CredencialesComando[***]";
        }
    }

    public record SolicitudRecuperacionComando(@NotBlank @Email String correo) {
    }

    public record RestablecerContrasenaComando(@NotBlank String token,
                                               @NotBlank @Size(min = 8, max = 72) String contrasenaNueva) {
        @Override
        public String toString() {
            return "RestablecerContrasenaComando[***]";
        }
    }

    public record Recuperacion(String token) {
    }

    public record PerfilComando(@NotBlank @Size(max = 120) String nombre,
                                @NotBlank @Size(max = 120) String apellido,
                                @NotBlank @Size(max = 30) String telefono) {
    }

    public record Perfil(String nombre, String apellido, String correo, String telefono, Rol rol, boolean activo) {
    }

    public record EstadoCuentaComando(@NotNull Boolean activo) {
    }

    /** El formato que espera el frontend. */
    public record Sesion(String token, UsuarioSesion usuario) {
    }

    public record UsuarioSesion(UUID id, String nombre, String correo, Rol rol) {
    }
}
