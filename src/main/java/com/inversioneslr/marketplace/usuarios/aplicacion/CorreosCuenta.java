package com.inversioneslr.marketplace.usuarios.aplicacion;

import com.inversioneslr.marketplace.usuarios.dominio.Correos;
import com.inversioneslr.marketplace.usuarios.dominio.TokensCorreo;
import com.inversioneslr.marketplace.usuarios.dominio.TokensCorreo.Proposito;
import com.inversioneslr.marketplace.usuarios.dominio.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SCRUM-109 y SCRUM-110: emite el token y envía el correo con el enlace. Los enlaces llevan a pantallas del frontend
 * ({@code /verificar-correo} y {@code /recuperar}), que mandan el token a la API.
 */
@Component
class CorreosCuenta {

    static final Duration VIGENCIA_VERIFICACION = Duration.ofHours(24);
    static final Duration VIGENCIA_RECUPERACION = Duration.ofMinutes(15);
    /** Entre dos correos del mismo tipo a la misma cuenta, para que nadie llene la bandeja de otro. */
    static final Duration ESPERA_ENTRE_ENVIOS = Duration.ofMinutes(1);

    private final TokensCorreo tokens;
    private final Correos correos;
    private final Clock reloj;
    private final String enlaceBase;

    CorreosCuenta(TokensCorreo tokens, Correos correos, Clock reloj,
                  @Value("${marketplace.correo.enlace-base}") String enlaceBase) {
        this.tokens = tokens;
        this.correos = correos;
        this.reloj = reloj;
        this.enlaceBase = enlaceBase.endsWith("/") ? enlaceBase.substring(0, enlaceBase.length() - 1) : enlaceBase;
    }

    void enviarVerificacion(Usuario usuario) {
        String token = tokens.emitir(usuario.id(), Proposito.VERIFICAR_CORREO, reloj.instant().plus(VIGENCIA_VERIFICACION));
        correos.enviar(usuario.correo(), "Confirma tu correo en Marketplace de Alojamientos", """
                Hola, %s:

                Gracias por registrarte en Marketplace de Alojamientos. Para activar tu cuenta, confirma tu correo con este enlace:

                %s/verificar-correo?token=%s

                El enlace vence en 24 horas. Si no creaste esta cuenta, ignora este mensaje.
                """.formatted(usuario.nombre(), enlaceBase, token));
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

    /** Falso si a esa cuenta ya se le envió un correo de este tipo hace menos de un minuto. */
    boolean puedeEnviar(Usuario usuario, Proposito proposito) {
        Instant limite = reloj.instant().minus(ESPERA_ENTRE_ENVIOS);
        return tokens.ultimaEmision(usuario.id(), proposito).map(ultima -> ultima.isBefore(limite)).orElse(true);
    }
}
