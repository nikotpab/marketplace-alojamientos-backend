package com.inversioneslr.marketplace.usuarios.aplicacion;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import com.inversioneslr.marketplace.compartido.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.Tokens;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.compartido.UsuarioId;
import com.inversioneslr.marketplace.usuarios.EventosUsuarios.ContrasenaRestablecida;
import com.inversioneslr.marketplace.usuarios.EventosUsuarios.CuentaActivada;
import com.inversioneslr.marketplace.usuarios.EventosUsuarios.CuentaDesactivada;
import com.inversioneslr.marketplace.usuarios.EventosUsuarios.InicioSesionExitoso;
import com.inversioneslr.marketplace.usuarios.EventosUsuarios.InicioSesionFallido;
import com.inversioneslr.marketplace.usuarios.EventosUsuarios.PerfilActualizado;
import com.inversioneslr.marketplace.usuarios.EventosUsuarios.RecuperacionSolicitada;
import com.inversioneslr.marketplace.usuarios.EventosUsuarios.UsuarioRegistrado;
import com.inversioneslr.marketplace.usuarios.EventosUsuarios.UsuarioRestringido;
import com.inversioneslr.marketplace.usuarios.FachadaUsuarios;
import com.inversioneslr.marketplace.usuarios.dominio.CodigosRegistro;
import com.inversioneslr.marketplace.usuarios.dominio.CodigosRegistro.Intento;
import com.inversioneslr.marketplace.usuarios.dominio.RepositorioUsuarios;
import com.inversioneslr.marketplace.usuarios.dominio.TokensCorreo;
import com.inversioneslr.marketplace.usuarios.dominio.TokensCorreo.Proposito;
import com.inversioneslr.marketplace.usuarios.dominio.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ServicioAutenticacion implements FachadaUsuarios {

    private static final String TOKEN = "token";

    /** RNF-23: 5 intentos fallidos seguidos bloquean el inicio de sesión por 30 minutos. */
    private static final int MAXIMO_INTENTOS = 5;
    private static final Duration BLOQUEO = Duration.ofMinutes(30);
    /** SCRUM-151: al quinto intento fallido el código se anula y hay que pedir otro. */
    static final int INTENTOS_POR_CODIGO = 5;

    /** SCRUM-154: celular de Colombia, 10 dígitos que empiezan por 3. */
    static final String FORMATO_TELEFONO = "3\\d{9}";
    static final String MENSAJE_TELEFONO = "El teléfono debe tener 10 dígitos y empezar por 3.";
    /** SCRUM-154: @Email acepta "ana@gmail"; además se exige un dominio con punto y terminación de 2 letras o más. */
    static final String FORMATO_CORREO = "[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}";
    static final String MENSAJE_CORREO = "Escribe un correo completo, por ejemplo nombre@gmail.com.";

    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder cifrador;
    private final Tokens tokens;
    private final PublicadorEventos eventos;
    private final Clock reloj;
    private final TokensCorreo tokensCorreo;
    private final CorreosCuenta correos;
    private final CodigosRegistro codigos;
    private final TransactionTemplate transaccion;
    /** Se compara contra este hash cuando el correo no existe, para que la respuesta tarde lo mismo. */
    private final String hashDeRelleno;

    ServicioAutenticacion(RepositorioUsuarios usuarios, PasswordEncoder cifrador, Tokens tokens, PublicadorEventos eventos,
                          Clock reloj, TokensCorreo tokensCorreo, CorreosCuenta correos, CodigosRegistro codigos,
                          TransactionTemplate transaccion) {
        this.usuarios = usuarios;
        this.cifrador = cifrador;
        this.tokens = tokens;
        this.eventos = eventos;
        this.reloj = reloj;
        this.tokensCorreo = tokensCorreo;
        this.correos = correos;
        this.codigos = codigos;
        this.transaccion = transaccion;
        this.hashDeRelleno = cifrador.encode(UUID.randomUUID().toString());
    }

    /**
     * SCRUM-151: primer paso del registro. Envía al correo un código de 6 dígitos que vence en 15 minutos y anula el
     * anterior. Si ya se envió uno hace menos de un minuto no envía otro, y responde igual. El correo sale después del
     * commit; si falla, se puede pedir otro código.
     */
    @Transactional
    public void solicitarCodigoRegistro(CorreoComando comando) {
        String correo = normalizar(comando.correo());
        if (usuarios.buscarPorCorreo(correo).isPresent()) {
            throw correoRegistrado();
        }
        if (correos.puedeEnviarCodigo(correo)) {
            correos.enviarCodigoRegistro(correo);
        }
    }

    /**
     * CU-01: solo HUESPED o ANFITRION; el rol ADMINISTRADOR lo asigna el equipo. SCRUM-151: solo con el código que llegó
     * al correo, así que la cuenta nace verificada. El código se comprueba fuera de la transacción a propósito: el
     * intento fallido debe quedar guardado aunque se responda con error.
     */
    public UsuarioResumen registrar(RegistroComando comando) {
        if (comando.rol() == Rol.ADMINISTRADOR) {
            String mensaje = "Este rol no se puede elegir. Regístrate como huésped o anfitrión.";
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of("rol", mensaje));
        }
        String correo = normalizar(comando.correo());
        if (usuarios.buscarPorCorreo(correo).isPresent()) {
            throw correoRegistrado();
        }
        comprobarCodigo(correo, comando.codigo());
        return transaccion.execute(estado -> {
            Usuario usuario = usuarios.guardar(new Usuario(UUID.randomUUID(), comando.nombre(), comando.apellido(),
                    correo, comando.telefono(), cifrador.encode(comando.contrasena()),
                    comando.rol(), true, false, null, 0, null, true));
            eventos.publicar(new UsuarioRegistrado(usuario.id(), usuario.rol().name()));
            return resumen(usuario);
        });
    }

    /** SCRUM-151: el intento se gasta antes de comparar, así que nunca se prueban más de 5 códigos. */
    private void comprobarCodigo(String correo, String codigo) {
        String invalido = "El código no es correcto o venció. Revisa el correo o pide uno nuevo.";
        Intento intento = codigos.gastarIntento(correo, INTENTOS_POR_CODIGO, reloj.instant())
                .orElseThrow(() -> codigoInvalido(invalido));
        if (cifrador.matches(codigo, intento.hashCodigo()) && codigos.usar(correo, intento.hashCodigo())) {
            return;
        }
        throw codigoInvalido(intento.numero() >= INTENTOS_POR_CODIGO
                ? "Fallaste 5 veces y el código se anuló. Pide uno nuevo."
                : invalido);
    }

    private static ExcepcionNegocio codigoInvalido(String mensaje) {
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of("codigo", mensaje));
    }

    private static ExcepcionNegocio correoRegistrado() {
        String mensaje = "Este correo ya está registrado. Inicia sesión o usa otro correo.";
        return new ExcepcionNegocio(ExcepcionNegocio.Tipo.CONFLICTO, mensaje, Map.of("correo", mensaje));
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
            eventos.publicar(new InicioSesionFallido(null, "CORREO_DESCONOCIDO"));
            throw credencialesInvalidas();
        }
        Instant ahora = reloj.instant();
        if (usuario.bloqueadoEn(ahora)) {
            eventos.publicar(new InicioSesionFallido(usuario.id(), "CUENTA_BLOQUEADA"));
            throw cuentaBloqueada();
        }
        if (!cifrador.matches(comando.contrasena(), usuario.hashContrasena())) {
            // Sin transacción a propósito: el intento debe quedar guardado aunque se responda con error.
            Usuario conIntento = usuarios.guardar(usuario.registrarIntentoFallido(ahora, MAXIMO_INTENTOS, BLOQUEO));
            eventos.publicar(new InicioSesionFallido(usuario.id(), "FALLIDO"));
            throw conIntento.bloqueadoEn(ahora) ? cuentaBloqueada() : credencialesInvalidas();
        }
        if (usuario.restringido()) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CUENTA_RESTRINGIDA, "Tu cuenta está restringida y no puede iniciar sesión.");
        }
        if (!usuario.cuentaActiva()) {
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CUENTA_DESACTIVADA, "Tu cuenta está desactivada.");
        }
        if (!usuario.correoVerificado()) {
            eventos.publicar(new InicioSesionFallido(usuario.id(), "CORREO_NO_VERIFICADO"));
            throw new ExcepcionNegocio(ExcepcionNegocio.Tipo.CORREO_NO_VERIFICADO,
                    "Confirma tu correo para iniciar sesión: recupera tu contraseña y el enlace que te llegue también lo confirma.");
        }
        if (usuario.intentosFallidos() > 0) {
            usuarios.guardar(usuario.reiniciarIntentos());
        }
        eventos.publicar(new InicioSesionExitoso(usuario.id()));
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

    private static UsuarioResumen resumen(Usuario usuario) {
        return new UsuarioResumen(usuario.id(), usuario.nombre(), usuario.apellido(), usuario.correo(), usuario.telefono(),
                usuario.rol(), usuario.cuentaActiva(), usuario.restringido(), usuario.correoVerificado());
    }

    /**
     * CU-03 · RF-03 · SCRUM-110: envía un enlace de un solo uso, válido 15 minutos. La respuesta es la misma exista o no
     * el correo, y no lleva el token: solo lo recibe el dueño del correo.
     */
    @Transactional
    public void solicitarRecuperacion(CorreoComando comando) {
        usuarios.buscarPorCorreo(normalizar(comando.correo()))
                .filter(usuario -> correos.puedeEnviar(usuario, Proposito.RESTABLECER_CONTRASENA))
                .ifPresent(usuario -> {
                    correos.enviarRecuperacion(usuario);
                    eventos.publicar(new RecuperacionSolicitada(usuario.id()));
                });
    }

    /** SCRUM-110: abrir el enlace también prueba que el correo es suyo, así que queda verificado. */
    @Transactional
    public void restablecerContrasena(RestablecerContrasenaComando comando) {
        String mensaje = "El enlace de recuperación no es válido o ya venció. Vuelve a empezar la recuperación.";
        Usuario usuario = tokensCorreo.consumir(comando.token(), Proposito.RESTABLECER_CONTRASENA, reloj.instant())
                .flatMap(usuarios::buscarPorId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.DATOS_INVALIDOS, mensaje, Map.of(TOKEN, mensaje)));
        usuarios.guardar(usuario.cambiarContrasena(cifrador.encode(comando.contrasenaNueva())).verificarCorreo());
        usuarios.invalidarSesiones(usuario.id());
        eventos.publicar(new ContrasenaRestablecida(usuario.id()));
    }

    /** CU-04: cada usuario consulta y modifica solo su propio perfil, identificado por el token. */
    public Perfil consultarPerfil(UUID usuarioId) {
        return perfil(buscar(usuarioId));
    }

    /** CU-04: el correo y el rol no se editan aquí. */
    public Perfil actualizarPerfil(UUID usuarioId, PerfilComando comando) {
        Usuario usuario = usuarios.guardar(buscar(usuarioId).actualizarDatos(comando.nombre(), comando.apellido(), comando.telefono()));
        eventos.publicar(new PerfilActualizado(usuario.id()));
        return perfil(usuario);
    }

    private Usuario buscar(UUID usuarioId) {
        return usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new ExcepcionNegocio(ExcepcionNegocio.Tipo.NO_ENCONTRADO, "El usuario no existe."));
    }

    private static Perfil perfil(Usuario usuario) {
        return new Perfil(usuario.nombre(), usuario.apellido(), usuario.correo(), usuario.telefono(), usuario.rol(), usuario.cuentaActiva());
    }

    /**
     * CU-05: cada cambio de estado queda en la auditoría. Una cuenta desactivada no puede iniciar sesión, y sus tokens
     * dejan de servir en la siguiente petición (SCRUM-117).
     */
    @Transactional
    public Perfil cambiarEstadoCuenta(UUID usuarioId, EstadoCuentaComando comando) {
        Usuario actual = buscar(usuarioId);
        Usuario usuario = usuarios.guardar(comando.activo() ? actual.activar() : actual.desactivar());
        if (!comando.activo()) {
            usuarios.invalidarSesiones(usuarioId);
        }
        eventos.publicar(comando.activo() ? new CuentaActivada(usuario.id()) : new CuentaDesactivada(usuario.id()));
        return perfil(usuario);
    }

    /** CU-25: del más reciente al más antiguo. */
    @Override
    public List<UsuarioResumen> listar() {
        return usuarios.listar().stream().map(ServicioAutenticacion::resumen).toList();
    }

    /** CU-25: queda en la auditoría con el motivo. Los tokens del usuario dejan de servir de inmediato (SCRUM-117). */
    @Override
    @Transactional
    public void restringir(UsuarioId administradorId, UsuarioId usuarioId, String motivo) {
        Usuario restringido = usuarios.guardar(buscar(usuarioId.valor()).restringir(motivo));
        usuarios.invalidarSesiones(usuarioId.valor());
        eventos.publicar(new UsuarioRestringido(administradorId.valor(), usuarioId.valor(), restringido.motivoRestriccion()));
    }

    @Override
    public Map<UsuarioId, Persona> personas(Collection<UsuarioId> usuarioIds) {
        return usuarios.buscarPorIds(usuarioIds.stream().map(UsuarioId::valor).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(usuario -> new UsuarioId(usuario.id()), usuario -> new Persona(usuario.nombre(), usuario.apellido(), usuario.rol())));
    }

    @Override
    public Map<UsuarioId, DatosContacto> datosContacto(Collection<UsuarioId> usuarioIds) {
        return usuarios.buscarPorIds(usuarioIds.stream().map(UsuarioId::valor).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(usuario -> new UsuarioId(usuario.id()), usuario -> new DatosContacto(usuario.telefono(), usuario.correo())));
    }

    public record RegistroComando(@NotBlank @Size(max = 120) String nombre,
                                  @NotBlank @Size(max = 120) String apellido,
                                  @NotBlank @Email(regexp = FORMATO_CORREO, message = MENSAJE_CORREO) @Size(max = 255) String correo,
                                  @NotNull @Pattern(regexp = FORMATO_TELEFONO, message = MENSAJE_TELEFONO) String telefono,
                                  @NotBlank @Size(min = 8, max = 72) String contrasena,
                                  @NotNull Rol rol,
                                  @NotBlank @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos.") String codigo) {
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

    public record CorreoComando(@NotBlank @Email(regexp = FORMATO_CORREO, message = MENSAJE_CORREO) String correo) {
    }

    public record RestablecerContrasenaComando(@NotBlank String token,
                                               @NotBlank @Size(min = 8, max = 72) String contrasenaNueva) {
        @Override
        public String toString() {
            return "RestablecerContrasenaComando[***]";
        }
    }

    public record PerfilComando(@NotBlank @Size(max = 120) String nombre,
                                @NotBlank @Size(max = 120) String apellido,
                                @NotNull @Pattern(regexp = FORMATO_TELEFONO, message = MENSAJE_TELEFONO) String telefono) {
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
