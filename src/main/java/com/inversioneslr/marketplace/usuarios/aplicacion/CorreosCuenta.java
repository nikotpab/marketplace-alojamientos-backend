package com.inversioneslr.marketplace.usuarios.aplicacion;

import com.inversioneslr.marketplace.usuarios.dominio.CodigosRegistro;
import com.inversioneslr.marketplace.usuarios.dominio.Correos;
import com.inversioneslr.marketplace.usuarios.dominio.TokensCorreo;
import com.inversioneslr.marketplace.usuarios.dominio.TokensCorreo.Proposito;
import com.inversioneslr.marketplace.usuarios.dominio.Usuario;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * SCRUM-151: el código que verifica el correo antes de crear la cuenta. SCRUM-110: el enlace de recuperación, que lleva
 * a la pantalla {@code /recuperar} del frontend.
 */
@Component
class CorreosCuenta {

    static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(15);
    static final Duration VIGENCIA_RECUPERACION = Duration.ofMinutes(15);
    /** Entre dos correos del mismo tipo a la misma dirección, para que nadie llene la bandeja de otro. */
    static final Duration ESPERA_ENTRE_ENVIOS = Duration.ofMinutes(1);

    private final SecureRandom aleatorio = new SecureRandom();
    private final TokensCorreo tokens;
    private final CodigosRegistro codigos;
    private final PasswordEncoder cifrador;
    private final Correos correos;
    private final Clock reloj;
    private final String enlaceBase;

    CorreosCuenta(TokensCorreo tokens, CodigosRegistro codigos, PasswordEncoder cifrador, Correos correos, Clock reloj,
                  @Value("${marketplace.correo.enlace-base}") String enlaceBase) {
        this.tokens = tokens;
        this.codigos = codigos;
        this.cifrador = cifrador;
        this.correos = correos;
        this.reloj = reloj;
        this.enlaceBase = enlaceBase.endsWith("/") ? enlaceBase.substring(0, enlaceBase.length() - 1) : enlaceBase;
    }

    /** Anula el código anterior de ese correo. En la base queda solo el hash. */
    void enviarCodigoRegistro(String correo) {
        String codigo = "%06d".formatted(aleatorio.nextInt(1_000_000));
        codigos.guardar(correo, cifrador.encode(codigo), reloj.instant().plus(VIGENCIA_CODIGO));
        correos.enviar(correo, "Tu código para crear la cuenta en Marketplace de Alojamientos", """
                Hola:

                Tu código para crear la cuenta en Marketplace de Alojamientos es:

                %s

                Escríbelo en la ventana del registro. Vence en 15 minutos y sirve una sola vez. Si no estás creando una cuenta, ignora este mensaje.
                """.formatted(codigo));
    }

    void enviarRecuperacion(Usuario usuario) {
        String token = tokens.emitir(usuario.id(), Proposito.RESTABLECER_CONTRASENA, reloj.instant().plus(VIGENCIA_RECUPERACION));
        correos.enviar(usuario.correo(), "Restablece tu contraseña de Marketplace de Alojamientos", """
                Hola, %s:

                Recibimos una solicitud para restablecer tu contraseña. Para elegir una nueva, abre este enlace:

                %s/recuperar?token=%s

                El enlace vence en 15 minutos y sirve una sola vez. Si no pediste el cambio, ignora este mensaje: tu contraseña sigue igual.
                """.formatted(usuario.nombre(), enlaceBase, token));
    }

    /** Falso si a esa dirección ya se le envió un código hace menos de un minuto. */
    boolean puedeEnviarCodigo(String correo) {
        return pasoLaEspera(codigos.ultimoEnvio(correo));
    }

    /** Falso si a esa cuenta ya se le envió un correo de este tipo hace menos de un minuto. */
    boolean puedeEnviar(Usuario usuario, Proposito proposito) {
        return pasoLaEspera(tokens.ultimaEmision(usuario.id(), proposito));
    }

    private boolean pasoLaEspera(Optional<Instant> ultimoEnvio) {
        Instant limite = reloj.instant().minus(ESPERA_ENTRE_ENVIOS);
        return ultimoEnvio.map(ultimo -> ultimo.isBefore(limite)).orElse(true);
    }
}
