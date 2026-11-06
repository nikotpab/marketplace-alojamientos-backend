package com.inversioneslr.marketplace.usuarios.aplicacion;

import com.inversioneslr.marketplace.compartido.dominio.PublicadorEventos;
import com.inversioneslr.marketplace.compartido.dominio.Rol;
import com.inversioneslr.marketplace.compartido.dominio.Tokens;
import com.inversioneslr.marketplace.usuarios.api.FachadaUsuarios;
import com.inversioneslr.marketplace.usuarios.dominio.RepositorioUsuarios;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ServicioAutenticacion implements FachadaUsuarios {

    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder cifrador;
    private final Tokens tokens;
    private final PublicadorEventos eventos;

    public ServicioAutenticacion(RepositorioUsuarios usuarios, PasswordEncoder cifrador, Tokens tokens,
                                 PublicadorEventos eventos) {
        this.usuarios = usuarios;
        this.cifrador = cifrador;
        this.tokens = tokens;
        this.eventos = eventos;
    }

    /** CU-01: solo HUESPED o ANFITRION; el rol ADMINISTRADOR lo asigna el equipo. */
    public UsuarioResumen registrar(RegistroComando comando) {
        throw new UnsupportedOperationException("CU-01");
    }

    /** CU-02: bloquea 30 min tras 5 intentos fallidos seguidos y registra cada intento (RNF-23). */
    public Sesion iniciarSesion(CredencialesComando comando) {
        throw new UnsupportedOperationException("CU-02");
    }

    /** CU-03: código simulado; responde igual aunque el correo no exista. */
    public void solicitarRecuperacion(SolicitudRecuperacionComando comando) {
        throw new UnsupportedOperationException("CU-03");
    }

    public void confirmarRecuperacion(RecuperacionComando comando) {
        throw new UnsupportedOperationException("CU-03");
    }

    public UsuarioResumen consultarPerfil(UUID usuarioId) {
        throw new UnsupportedOperationException("CU-04");
    }

    public UsuarioResumen actualizarPerfil(UUID usuarioId, PerfilComando comando) {
        throw new UnsupportedOperationException("CU-04");
    }

    public UsuarioResumen cambiarEstadoCuenta(UUID usuarioId, EstadoCuentaComando comando) {
        throw new UnsupportedOperationException("CU-05");
    }

    @Override
    public List<UsuarioResumen> listar() {
        throw new UnsupportedOperationException("CU-25");
    }

    @Override
    public void restringir(UUID administradorId, UUID usuarioId, String motivo) {
        throw new UnsupportedOperationException("CU-25");
    }

    @Override
    public DatosContacto datosContacto(UUID usuarioId) {
        throw new UnsupportedOperationException("RNF-21");
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

    public record RecuperacionComando(@NotBlank @Email String correo,
                                      @NotBlank String codigo,
                                      @NotBlank @Size(min = 8, max = 72) String nuevaContrasena) {
        @Override
        public String toString() {
            return "RecuperacionComando[***]";
        }
    }

    public record PerfilComando(@NotBlank @Size(max = 120) String nombre,
                                @NotBlank @Size(max = 120) String apellido,
                                @NotBlank @Email @Size(max = 255) String correo,
                                @NotBlank @Size(max = 30) String telefono) {
    }

    public record EstadoCuentaComando(boolean activa) {
    }

    public record Sesion(String token, Rol rol) {
    }
}
