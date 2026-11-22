package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.Tokens;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
import com.inversioneslr.marketplace.usuarios.dominio.RepositorioUsuarios;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT firmado con HS256. El token identifica al usuario, su rol y su versión de sesión; no guarda datos personales.
 * Cada verificación compara la versión con la del usuario, en una consulta por llave primaria. Así, restringir,
 * desactivar o restablecer la contraseña invalida los tokens anteriores en la siguiente petición, sin esperar las 8 h.
 */
@Component
@SuppressWarnings("java:S2143") // Nimbus exige java.util.Date en las fechas del token; se convierte desde Instant.
public class TokensJwt implements Tokens {

    private static final Duration DURACION = Duration.ofHours(8);
    private static final String ROL = "rol";
    private static final String VERSION = "ver";

    private final MACSigner firmador;
    private final MACVerifier verificador;
    private final Clock reloj;
    private final RepositorioUsuarios usuarios;

    public TokensJwt(@Value("${marketplace.jwt-secreto}") String secreto, Clock reloj, RepositorioUsuarios usuarios) {
        byte[] clave = secreto.getBytes(StandardCharsets.UTF_8);
        try {
            this.firmador = new MACSigner(clave);
            this.verificador = new MACVerifier(clave);
        } catch (JOSEException e) {
            throw new IllegalArgumentException("JWT_SECRETO debe tener al menos 32 caracteres", e);
        }
        this.reloj = reloj;
        this.usuarios = usuarios;
    }

    @Override
    public String emitir(UsuarioAutenticado usuario) {
        Instant ahora = reloj.instant();
        JWTClaimsSet datos = new JWTClaimsSet.Builder()
                .subject(usuario.id().toString())
                .claim(ROL, usuario.rol().name())
                .claim(VERSION, usuarios.versionSesionVigente(usuario.id()).orElse(0))
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plus(DURACION)))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), datos);
        try {
            token.sign(firmador);
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
        return token.serialize();
    }

    @Override
    public Optional<UsuarioAutenticado> verificar(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verificador)) {
                return Optional.empty();
            }
            JWTClaimsSet datos = jwt.getJWTClaimsSet();
            Date vence = datos.getExpirationTime();
            if (vence == null || !vence.toInstant().isAfter(reloj.instant())) {
                return Optional.empty();
            }
            UUID id = UUID.fromString(datos.getSubject());
            // Los tokens emitidos antes de la versión de sesión no la traen: valen como la 0 hasta que se invalide la sesión.
            Integer version = datos.getIntegerClaim(VERSION);
            int esperada = version == null ? 0 : version;
            if (usuarios.versionSesionVigente(id).filter(vigente -> vigente == esperada).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new UsuarioAutenticado(id, Rol.valueOf(datos.getStringClaim(ROL))));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
