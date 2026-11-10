package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.compartido.Rol;
import com.inversioneslr.marketplace.compartido.Tokens;
import com.inversioneslr.marketplace.compartido.UsuarioAutenticado;
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

/** JWT firmado con HS256. El token identifica al usuario y su rol; no guarda datos personales. */
@Component
public class TokensJwt implements Tokens {

    private static final Duration DURACION = Duration.ofHours(8);
    private static final String ROL = "rol";

    private final MACSigner firmador;
    private final MACVerifier verificador;
    private final Clock reloj;

    public TokensJwt(@Value("${marketplace.jwt-secreto}") String secreto, Clock reloj) {
        byte[] clave = secreto.getBytes(StandardCharsets.UTF_8);
        try {
            this.firmador = new MACSigner(clave);
            this.verificador = new MACVerifier(clave);
        } catch (JOSEException e) {
            throw new IllegalArgumentException("JWT_SECRETO debe tener al menos 32 caracteres", e);
        }
        this.reloj = reloj;
    }

    @Override
    public String emitir(UsuarioAutenticado usuario) {
        Instant ahora = reloj.instant();
        JWTClaimsSet datos = new JWTClaimsSet.Builder()
                .subject(usuario.id().toString())
                .claim(ROL, usuario.rol().name())
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
            return Optional.of(new UsuarioAutenticado(UUID.fromString(datos.getSubject()), Rol.valueOf(datos.getStringClaim(ROL))));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
